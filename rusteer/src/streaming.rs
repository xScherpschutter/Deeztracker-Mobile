use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use once_cell::sync::Lazy;
use tokio::sync::Notify;
use tokio::runtime::Runtime;

use crate::api::GatewayApi;
use crate::crypto;
use crate::error::{DeezerError, Result};
use crate::rusteer::DownloadQuality;

#[derive(Debug, Clone)]
pub struct SharedBuffer {
    pub data: Arc<Mutex<Vec<u8>>>,
    pub is_complete: Arc<AtomicBool>,
    pub is_cancelled: Arc<AtomicBool>,
    pub notify: Arc<Notify>,
}

impl SharedBuffer {
    pub fn new() -> Self {
        Self {
            data: Arc::new(Mutex::new(Vec::with_capacity(5 * 1024 * 1024))), // Pre-alloc 5MB
            is_complete: Arc::new(AtomicBool::new(false)),
            is_cancelled: Arc::new(AtomicBool::new(false)),
            notify: Arc::new(Notify::new()),
        }
    }
}

#[derive(Debug, Clone)]
pub struct StreamCache {
    pub track_id: String,
    pub quality: DownloadQuality,
    pub buffer: SharedBuffer,
}

// Global cache for up to 3 streams (LRU)
pub static STREAM_CACHE: Lazy<Mutex<Vec<StreamCache>>> = Lazy::new(|| Mutex::new(Vec::with_capacity(3)));

// Global runtime for background tasks
pub static RUNTIME: Lazy<Runtime> = Lazy::new(|| {
    Runtime::new().expect("Failed to create Tokio runtime")
});

pub async fn preload_track(arl: &str, track_id: &str, preferred_quality: DownloadQuality) -> Result<()> {
    // 1. Check if already in cache
    {
        let mut cache = STREAM_CACHE.lock().unwrap();
        if let Some(pos) = cache.iter().position(|c| c.track_id == track_id && c.quality == preferred_quality) {
            // Move to end (most recently used)
            let item = cache.remove(pos);
            cache.push(item);
            return Ok(());
        }
    }

    // 2. Need to fetch song data
    let gateway_api = GatewayApi::new(arl).await?;
    let song_data = gateway_api.get_song_data(track_id).await?;

    if !song_data.readable {
        return Err(DeezerError::TrackNotFound(format!("Track {} not readable", track_id)));
    }

    let track_token = song_data
        .track_token
        .ok_or_else(|| DeezerError::NoDataApi("No track token".to_string()))?;

    // 3. Find media URL
    let qualities = match preferred_quality {
        DownloadQuality::Flac => vec![DownloadQuality::Flac, DownloadQuality::Mp3_320, DownloadQuality::Mp3_128],
        DownloadQuality::Mp3_320 => vec![DownloadQuality::Mp3_320, DownloadQuality::Mp3_128],
        DownloadQuality::Mp3_128 => vec![DownloadQuality::Mp3_128],
    };

    let mut media_url_str = String::new();
    let mut quality_found = preferred_quality;

    for quality in qualities {
        if let Ok(urls) = gateway_api.get_media_url(&[track_token.clone()], quality.format()).await {
            if let Some(url) = urls.into_iter().next() {
                media_url_str = url.url;
                quality_found = quality;
                break;
            }
        }
    }

    if media_url_str.is_empty() {
        return Err(DeezerError::NoRightOnMedia("No media URL".to_string()));
    }

    let shared_buffer = SharedBuffer::new();
    let cache_item = StreamCache {
        track_id: track_id.to_string(),
        quality: quality_found,
        buffer: shared_buffer.clone(),
    };

    // Add to global cache
    {
        let mut cache = STREAM_CACHE.lock().unwrap();
        if cache.len() >= 3 {
            let old = cache.remove(0);
            old.buffer.is_cancelled.store(true, Ordering::SeqCst);
        }
        cache.push(cache_item.clone());
    }

    let buffer_clone = shared_buffer.clone();
    let track_id_cloned = track_id.to_string();

    // 4. Start background download task on the GLOBAL runtime
    RUNTIME.spawn(async move {
        let client = reqwest::Client::new();
        if let Ok(res) = client.get(&media_url_str).send().await {
            use tokio_stream::StreamExt;
            let mut byte_stream = res.bytes_stream();
            let key = crypto::calc_blowfish_key(&track_id_cloned);
            let mut chunk_buffer = Vec::new();
            let mut block_index = 0;
            let mut batch_buffer = Vec::with_capacity(16384);

            while let Some(chunk_res) = byte_stream.next().await {
                if buffer_clone.is_cancelled.load(Ordering::Relaxed) {
                    break;
                }

                if let Ok(chunk) = chunk_res {
                    chunk_buffer.extend_from_slice(&chunk);
                    while chunk_buffer.len() >= 2048 {
                        let block: Vec<u8> = chunk_buffer.drain(..2048).collect();
                        let processed = if block_index % 3 == 0 {
                            crypto::decrypt_blowfish_chunk(&block, &key)
                        } else {
                            block
                        };

                        batch_buffer.extend_from_slice(&processed);
                        block_index += 1;

                        if batch_buffer.len() >= 16384 {
                            {
                                let mut data = buffer_clone.data.lock().unwrap();
                                data.extend_from_slice(&batch_buffer);
                            }
                            buffer_clone.notify.notify_waiters();
                            batch_buffer.clear();
                        }
                    }
                } else {
                    break;
                }
            }
            if !buffer_clone.is_cancelled.load(Ordering::Relaxed) {
                if !batch_buffer.is_empty() {
                    let mut data = buffer_clone.data.lock().unwrap();
                    data.extend_from_slice(&batch_buffer);
                }
                if !chunk_buffer.is_empty() {
                    let mut data = buffer_clone.data.lock().unwrap();
                    data.extend_from_slice(&chunk_buffer);
                }
            }
            buffer_clone.is_complete.store(true, Ordering::SeqCst);
            buffer_clone.notify.notify_waiters();
        }
    });

    // 5. WAIT until we have at least SOME data (headers) before returning
    // This prevents ExoPlayer from failing to recognize the format
    loop {
        {
            let data = shared_buffer.data.lock().unwrap();
            if data.len() >= 32768 || shared_buffer.is_complete.load(Ordering::SeqCst) {
                break;
            }
        }
        tokio::time::sleep(std::time::Duration::from_millis(50)).await;
    }

    Ok(())
}

pub async fn read_audio_chunk(track_id: &str, offset: u64, size: u32) -> Result<Vec<u8>> {
    let buffer_opt = {
        let cache = STREAM_CACHE.lock().unwrap();
        cache.iter().find(|c| c.track_id == track_id).map(|c| c.buffer.clone())
    };

    let buffer = match buffer_opt {
        Some(b) => b,
        None => return Err(DeezerError::ApiError(format!("Track {} not in cache. Call preload_track first.", track_id))),
    };

    let offset_usize = offset as usize;
    let size_usize = size as usize;

    loop {
        if buffer.is_cancelled.load(Ordering::Relaxed) {
            return Err(DeezerError::ApiError("Stream was cancelled".to_string()));
        }

        let data = buffer.data.lock().unwrap();
        let available_length = data.len();

        if offset_usize < available_length {
            let to_read = std::cmp::min(size_usize, available_length - offset_usize);
            let chunk = data[offset_usize..offset_usize + to_read].to_vec();
            return Ok(chunk);
        }

        if buffer.is_complete.load(Ordering::SeqCst) {
            // EOF reached
            return Ok(Vec::new());
        }

        // Wait for more data
        let notify = buffer.notify.clone();
        drop(data);
        notify.notified().await;
    }
}

pub fn cancel_preload(track_id: &str) {
    let mut cache = STREAM_CACHE.lock().unwrap();
    if let Some(pos) = cache.iter().position(|c| c.track_id == track_id) {
        let item = cache.remove(pos);
        item.buffer.is_cancelled.store(true, Ordering::SeqCst);
    }
}
