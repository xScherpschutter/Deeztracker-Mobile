use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use once_cell::sync::Lazy;
use tokio::sync::{Notify, Semaphore, RwLock};
use tokio::runtime::Runtime;
use std::collections::{BTreeMap, HashMap};
use log::{debug};
use reqwest::Client;

use crate::api::GatewayApi;
use crate::crypto;
use crate::error::{DeezerError, Result};
use crate::rusteer::DownloadQuality;

const BLOCK_SIZE: u64 = 2048; 
const MAX_CONCURRENT_DOWNLOADS: usize = 4;
const SPOT_DOWNLOAD_BATCH: u64 = 64;

#[derive(Debug, Clone)]
pub struct SharedBuffer {
    pub blocks: Arc<Mutex<BTreeMap<u64, Vec<u8>>>>,
    pub is_complete: Arc<AtomicBool>,
    pub is_cancelled: Arc<AtomicBool>,
    pub is_downloading: Arc<AtomicBool>,
    pub notify: Arc<Notify>,
    pub total_size: Arc<AtomicU64>,
    pub download_pos: Arc<AtomicU64>, 
    pub seek_trigger: Arc<Notify>,
}

impl SharedBuffer {
    pub fn new() -> Self {
        Self {
            blocks: Arc::new(Mutex::new(BTreeMap::new())),
            is_complete: Arc::new(AtomicBool::new(false)),
            is_cancelled: Arc::new(AtomicBool::new(false)),
            is_downloading: Arc::new(AtomicBool::new(false)),
            notify: Arc::new(Notify::new()),
            total_size: Arc::new(AtomicU64::new(0)),
            download_pos: Arc::new(AtomicU64::new(0)),
            seek_trigger: Arc::new(Notify::new()),
        }
    }

    pub fn has_block(&self, block_index: u64) -> bool {
        self.blocks.lock().unwrap().contains_key(&block_index)
    }

    pub fn get_block(&self, block_index: u64) -> Option<Vec<u8>> {
        self.blocks.lock().unwrap().get(&block_index).cloned()
    }
}

#[derive(Debug, Clone)]
pub struct StreamCache {
    pub track_id: String,
    pub quality: DownloadQuality,
    pub buffer: SharedBuffer,
    pub media_url: String,
}

pub static STREAM_CACHE: Lazy<Mutex<Vec<StreamCache>>> = Lazy::new(|| Mutex::new(Vec::with_capacity(10)));

static METADATA_CACHE: Lazy<Mutex<HashMap<String, (String, DownloadQuality, u64)>>> = Lazy::new(|| Mutex::new(HashMap::new()));

static GLOBAL_API: Lazy<RwLock<Option<GatewayApi>>> = Lazy::new(|| RwLock::new(None));

static DOWNLOAD_SEMAPHORE: Lazy<Arc<Semaphore>> = Lazy::new(|| Arc::new(Semaphore::new(MAX_CONCURRENT_DOWNLOADS)));

pub static RUNTIME: Lazy<Runtime> = Lazy::new(|| {
    Runtime::new().expect("Failed to create Tokio runtime")
});

static HTTP_CLIENT: Lazy<Client> = Lazy::new(|| {
    Client::builder()
        .pool_max_idle_per_host(10)
        .tcp_keepalive(Some(std::time::Duration::from_secs(60)))
        .timeout(std::time::Duration::from_secs(30))
        .build()
        .expect("Failed to create HTTP client")
});

async fn get_gateway(arl: &str) -> Result<GatewayApi> {
    {
        let cache = GLOBAL_API.read().await;
        if let Some(api) = &*cache {
            if api.arl() == arl {
                return Ok(api.clone());
            }
        }
    }
    
    let mut cache = GLOBAL_API.write().await;
    if let Some(api) = &*cache {
        if api.arl() == arl {
            return Ok(api.clone());
        }
    }

    debug!("[Stream] Authenticating with Deezer...");
    let api = GatewayApi::new(arl).await?;
    *cache = Some(api.clone());
    Ok(api)
}

pub async fn preload_track(arl: &str, track_id: &str, preferred_quality: DownloadQuality) -> Result<u64> {
    let cached = {
        let mut cache = STREAM_CACHE.lock().unwrap();
        debug!("[Cache:{}] LRU lookup — cache has {} entries: [{}]",
            track_id,
            cache.len(),
            cache.iter().map(|c| c.track_id.as_str()).collect::<Vec<_>>().join(", ")
        );
        if let Some(pos) = cache.iter().position(|c| c.track_id == track_id) {
            let item = cache.remove(pos);
            let buffer = item.buffer.clone();
            let media_url = item.media_url.clone();
            let block_count = buffer.blocks.lock().unwrap().len();

            debug!("[Cache:{}] HIT (quality_stored={:?}, quality_req={:?}) — blocks={}, download_pos={}, total_size={}, complete={}, cancelled={}, downloading={}",
                track_id,
                item.quality,
                preferred_quality,
                block_count,
                buffer.download_pos.load(Ordering::SeqCst),
                buffer.total_size.load(Ordering::SeqCst),
                buffer.is_complete.load(Ordering::SeqCst),
                buffer.is_cancelled.load(Ordering::SeqCst),
                buffer.is_downloading.load(Ordering::SeqCst),
            );

            if item.quality != preferred_quality && block_count == 0 && !buffer.is_downloading.load(Ordering::SeqCst) {
                debug!("[Cache:{}] Quality mismatch and empty buffer → discarding entry to re-download with quality {:?}", track_id, preferred_quality);
                buffer.is_cancelled.store(true, Ordering::SeqCst);
                buffer.notify.notify_waiters();
                None
            } else {
                if buffer.is_cancelled.load(Ordering::SeqCst) {
                    debug!("[Cache:{}] Buffer cancelled — restarting download", track_id);
                    buffer.is_cancelled.store(false, Ordering::SeqCst);
                    start_background_download(buffer.clone(), track_id.to_string(), media_url);
                } else if !buffer.is_downloading.load(Ordering::SeqCst) && !buffer.is_complete.load(Ordering::SeqCst) {
                    debug!("[Cache:{}] Buffer stopped (not downloading, not complete) — restarting download", track_id);
                    start_background_download(buffer.clone(), track_id.to_string(), media_url);
                }
                cache.push(item);
                Some(buffer)
            }
        } else {
            debug!("[Cache:{}] MISS — track not found in LRU", track_id);
            None
        }
    };

    if let Some(buffer) = cached {
        wait_for_initial_data(&buffer).await;
        return Ok(buffer.total_size.load(Ordering::SeqCst));
    }

    let (url, quality, size) = {
        let meta = METADATA_CACHE.lock().unwrap();
        meta.get(track_id).cloned().unwrap_or_default()
    };

    let (final_url, final_quality, _final_size) = if url.is_empty() || quality != preferred_quality {
        let gateway_api = get_gateway(arl).await?;
        let song_data = gateway_api.get_song_data(track_id).await?;
        if !song_data.readable {
            return Err(DeezerError::TrackNotFound(format!("Track {} not readable", track_id)));
        }
        let track_token = song_data.track_token.ok_or_else(|| DeezerError::NoDataApi("No track token".to_string()))?;
        let mut resolved_url = String::new();
        let mut resolved_q = preferred_quality;
        
        let qualities = match preferred_quality {
            DownloadQuality::Flac => vec![DownloadQuality::Flac, DownloadQuality::Mp3_320, DownloadQuality::Mp3_128],
            DownloadQuality::Mp3_320 => vec![DownloadQuality::Mp3_320, DownloadQuality::Mp3_128],
            _ => vec![DownloadQuality::Mp3_128],
        };

        for q in qualities {
            if let Ok(urls) = gateway_api.get_media_url(&[track_token.clone()], q.format()).await {
                if let Some(murl) = urls.into_iter().next() {
                    resolved_url = murl.url;
                    resolved_q = q;
                    break;
                }
            }
        }
        if resolved_url.is_empty() { return Err(DeezerError::NoRightOnMedia("No media URL".to_string())); }
        
        METADATA_CACHE.lock().unwrap().insert(track_id.to_string(), (resolved_url.clone(), resolved_q, 0));
        (resolved_url, resolved_q, 0)
    } else {
        (url, quality, size)
    };

    let buffer = SharedBuffer::new();
    let buffer_clone = buffer.clone();

    let buffer_to_use = {
        let mut cache = STREAM_CACHE.lock().unwrap();

        if let Some(pos) = cache.iter().position(|c| c.track_id == track_id) {
            let item = cache.remove(pos);
            let existing_buffer = item.buffer.clone();
            let block_count = existing_buffer.blocks.lock().unwrap().len();
            debug!("[Cache:{}] Double-check HIT (race won by another thread) — blocks={}, reusing existing buffer",
                track_id, block_count);
            cache.push(item);
            if existing_buffer.is_cancelled.load(Ordering::SeqCst) {
                existing_buffer.is_cancelled.store(false, Ordering::SeqCst);
                start_background_download(existing_buffer.clone(), track_id.to_string(), final_url.clone());
            } else if !existing_buffer.is_downloading.load(Ordering::SeqCst) && !existing_buffer.is_complete.load(Ordering::SeqCst) {
                start_background_download(existing_buffer.clone(), track_id.to_string(), final_url.clone());
            }
            existing_buffer
        } else {
            if cache.len() >= 10 {
                let old = cache.remove(0);
                let old_blocks = old.buffer.blocks.lock().unwrap().len();
                debug!("[Cache] EVICT '{}' (memory blocks: {}) to make room for '{}'",
                    old.track_id, old_blocks, track_id);
                old.buffer.is_cancelled.store(true, Ordering::SeqCst);
                old.buffer.notify.notify_waiters();
            }
            cache.push(StreamCache {
                track_id: track_id.to_string(),
                quality: final_quality,
                buffer: buffer.clone(),
                media_url: final_url.clone(),
            });
            debug!("[Cache:{}] New buffer created — cache now has {} entries",
                track_id, cache.len());
            buffer_clone.clone()
        }
    };

    if Arc::ptr_eq(&buffer_to_use.blocks, &buffer.blocks) {
        start_background_download(buffer, track_id.to_string(), final_url);
    }
    wait_for_initial_data(&buffer_to_use).await;
    Ok(buffer_to_use.total_size.load(Ordering::SeqCst))
}

async fn wait_for_initial_data(buffer: &SharedBuffer) {
    if buffer.is_complete.load(Ordering::Acquire) {
        return;
    }
    {
        let total = buffer.total_size.load(Ordering::Acquire);
        if total > 0 {
            let block_count = buffer.blocks.lock().unwrap().len();
            if block_count >= 2 { return; }
        }
    }
    let _ = tokio::time::timeout(
        std::time::Duration::from_secs(3),
        async {
            loop {
                buffer.notify.notified().await;
                let total = buffer.total_size.load(Ordering::SeqCst);
                let block_count = buffer.blocks.lock().unwrap().len();
                if (total > 0 && block_count >= 2) || buffer.is_complete.load(Ordering::SeqCst) {
                    break;
                }
            }
        },
    )
    .await;
}

fn start_background_download(buffer: SharedBuffer, track_id: String, media_url: String) {
    if buffer.is_downloading.swap(true, Ordering::SeqCst) { return; }

    RUNTIME.spawn(async move {
        let _permit = match DOWNLOAD_SEMAPHORE.acquire().await {
            Ok(p) => p,
            Err(_) => {
                buffer.is_downloading.store(false, Ordering::SeqCst);
                return;
            }
        };

        let key = crypto::calc_blowfish_key(&track_id);
        
        'download_loop: loop {
            if buffer.is_cancelled.load(Ordering::Relaxed) { break; }

            let start_pos = buffer.download_pos.load(Ordering::SeqCst);
            let total_size = buffer.total_size.load(Ordering::SeqCst);
            
            if total_size > 0 && start_pos >= total_size {
                buffer.is_complete.store(true, Ordering::SeqCst);
                buffer.notify.notify_waiters();
                break;
            }

            let aligned_start = (start_pos / BLOCK_SIZE) * BLOCK_SIZE;
            let mut block_index = aligned_start / BLOCK_SIZE;
            {
                let blocks = buffer.blocks.lock().unwrap();
                while blocks.contains_key(&block_index) {
                    block_index += 1;
                }
            }
            let actual_http_start = block_index * BLOCK_SIZE;

            if total_size > 0 && actual_http_start >= total_size {
                buffer.is_complete.store(true, Ordering::SeqCst);
                buffer.notify.notify_waiters();
                break;
            }

            debug!("[Stream:{}] Starting download from {} (aligned={}, skip_cached={})",
                track_id, actual_http_start, aligned_start, block_index - (aligned_start / BLOCK_SIZE));

            let res = HTTP_CLIENT.get(&media_url)
                .header("Range", format!("bytes={}-", actual_http_start))
                .send().await;

            match res {
                Ok(response) => {
                    let status = response.status();
                    if status.as_u16() == 416 { 
                        buffer.is_complete.store(true, Ordering::SeqCst);
                        buffer.notify.notify_waiters();
                        break;
                    }

                    if !status.is_success() {
                        tokio::time::sleep(std::time::Duration::from_millis(1000)).await;
                        continue;
                    }

                    if let Some(len) = response.content_length() {
                        let full_size = if status.as_u16() == 206 { actual_http_start + len } else { len };
                        buffer.total_size.store(full_size, Ordering::SeqCst);
                    }

                    use tokio_stream::StreamExt;
                    let mut byte_stream = response.bytes_stream();
                    let mut chunk_buffer = Vec::with_capacity(16384);

                    loop {
                        tokio::select! {
                            _ = buffer.seek_trigger.notified() => {
                                debug!("[Stream:{}] Seek detected, restarting stream", track_id);
                                continue 'download_loop;
                            }
                            
                            chunk_res = byte_stream.next() => {
                                match chunk_res {
                                    Some(Ok(chunk)) => {
                                        chunk_buffer.extend_from_slice(&chunk);
                                        while chunk_buffer.len() >= BLOCK_SIZE as usize {
                                            let block: Vec<u8> = chunk_buffer.drain(..BLOCK_SIZE as usize).collect();
                                            if !buffer.has_block(block_index) {
                                                let processed = if block_index % 3 == 0 {
                                                    crypto::decrypt_blowfish_chunk(&block, &key)
                                                } else {
                                                    block
                                                };
                                                buffer.blocks.lock().unwrap().insert(block_index, processed);
                                            }
                                            block_index += 1;
                                            buffer.download_pos.store(block_index * BLOCK_SIZE, Ordering::SeqCst);
                                            buffer.notify.notify_waiters();
                                        }
                                    }
                                    Some(Err(_)) | None => {
                                        if !chunk_buffer.is_empty() {
                                            let mut blocks = buffer.blocks.lock().unwrap();
                                            if !blocks.contains_key(&block_index) {
                                                blocks.insert(block_index, chunk_buffer.clone());
                                            }
                                            let final_pos = block_index * BLOCK_SIZE + chunk_buffer.len() as u64;
                                            buffer.download_pos.store(final_pos, Ordering::SeqCst);
                                            buffer.notify.notify_waiters();
                                        }
                                        break; 
                                    }
                                }
                            }
                        }
                        
                        if buffer.is_cancelled.load(Ordering::Relaxed) { break 'download_loop; }
                    }
                },
                Err(_) => { 
                    tokio::time::sleep(std::time::Duration::from_millis(1000)).await; 
                }
            }
            
            let current_pos = buffer.download_pos.load(Ordering::SeqCst);
            let total = buffer.total_size.load(Ordering::SeqCst);
            if total > 0 && current_pos >= total {
                buffer.is_complete.store(true, Ordering::SeqCst);
                buffer.notify.notify_waiters();
                break;
            }
        }
        buffer.is_downloading.store(false, Ordering::SeqCst);
    });
}

pub async fn read_audio_chunk(track_id: &str, offset: u64, size: u32) -> Result<Vec<u8>> {
    let (buffer, media_url) = {
        let mut cache = STREAM_CACHE.lock().unwrap();
        if let Some(pos) = cache.iter().position(|c| c.track_id == track_id) {
            let item = cache.remove(pos);
            let b = item.buffer.clone();
            let url = item.media_url.clone();
            cache.push(item);
            Some((b, url))
        } else {
            debug!("[Cache:{}] read_audio_chunk — track NOT in cache. Current entries: [{}]",
                track_id,
                cache.iter().map(|c| format!("{}(b={})",
                    c.track_id,
                    c.buffer.blocks.lock().unwrap().len()
                )).collect::<Vec<_>>().join(", ")
            );
            None
        }
    }.ok_or_else(|| DeezerError::ApiError(format!("Track {} not in cache", track_id)))?;

    let key = crypto::calc_blowfish_key(track_id);
    let mut current_offset = offset;
    let end_offset = offset + size as u64;
    let mut result = Vec::with_capacity(size as usize);

    while current_offset < end_offset {
        if buffer.is_cancelled.load(Ordering::Relaxed) {
            return Err(DeezerError::ApiError("Cancelled".to_string()));
        }

        let block_index = current_offset / BLOCK_SIZE;
        let block_offset = (current_offset % BLOCK_SIZE) as usize;

        if let Some(block) = buffer.get_block(block_index) {
            let available = block.len() - block_offset;
            let to_copy = std::cmp::min(available, (end_offset - current_offset) as usize);
            result.extend_from_slice(&block[block_offset..block_offset + to_copy]);
            current_offset += to_copy as u64;
        } else {
            let d_pos = buffer.download_pos.load(Ordering::Relaxed);
            let is_far_ahead = current_offset > d_pos + (128 * 1024);
            let is_behind = current_offset < d_pos.saturating_sub(BLOCK_SIZE);

            if is_behind {
                debug!("[Stream:{}] Spot download needed at block {} (d_pos={})", track_id, block_index, d_pos);
                spot_download_blocks(&buffer, &media_url, track_id, block_index, &key).await?;
            } else if is_far_ahead {
                buffer.is_complete.store(false, Ordering::SeqCst);
                buffer.download_pos.store(current_offset, Ordering::SeqCst);
                buffer.seek_trigger.notify_waiters();
                debug!("[Stream:{}] Forward seek → {} (redirecting downloader)", track_id, current_offset);

                if buffer.is_complete.load(Ordering::SeqCst) {
                    if buffer.has_block(block_index) { continue; }
                    break; 
                }
                if tokio::time::timeout(std::time::Duration::from_secs(5), buffer.notify.notified()).await.is_err() {
                    return Err(DeezerError::ApiError(
                        format!("[Stream:{}] Forward seek timeout at offset {}", track_id, current_offset)
                    ));
                }
            } else {
                if buffer.is_complete.load(Ordering::SeqCst) {
                    if buffer.has_block(block_index) { continue; }
                    break; 
                }
                if tokio::time::timeout(std::time::Duration::from_secs(5), buffer.notify.notified()).await.is_err() {
                    return Err(DeezerError::ApiError(
                        format!("[Stream:{}] Read timeout at offset {}: downloader stalled", track_id, current_offset)
                    ));
                }
            }
        }
    }
    Ok(result)
}

async fn spot_download_blocks(
    buffer: &SharedBuffer,
    media_url: &str,
    track_id: &str,
    start_block: u64,
    key: &[u8],
) -> Result<()> {
    let total_size = buffer.total_size.load(Ordering::SeqCst);
    let max_block = if total_size > 0 {
        (total_size + BLOCK_SIZE - 1) / BLOCK_SIZE
    } else {
        start_block + SPOT_DOWNLOAD_BATCH
    };
    let end_block = (start_block + SPOT_DOWNLOAD_BATCH).min(max_block);

    let http_start = start_block * BLOCK_SIZE;
    let http_end   = end_block * BLOCK_SIZE - 1; 

    debug!("[Stream:{}] Spot download blocks {}-{} (bytes {}-{})",
        track_id, start_block, end_block, http_start, http_end);

    let response = HTTP_CLIENT
        .get(media_url)
        .header("Range", format!("bytes={}-{}", http_start, http_end))
        .send()
        .await
        .map_err(|e| DeezerError::ApiError(format!("[Spot] HTTP request error: {}", e)))?;

    let status = response.status();
    if status.as_u16() == 416 {
        return Ok(());
    }
    if !status.is_success() {
        return Err(DeezerError::ApiError(format!("[Spot] HTTP error {}", status)));
    }

    let data = response
        .bytes()
        .await
        .map_err(|e| DeezerError::ApiError(format!("[Spot] Read error: {}", e)))?;

    let data_slice = data.as_ref();
    let mut block_index = start_block;
    let mut consumed = 0usize;

    while consumed + BLOCK_SIZE as usize <= data_slice.len() {
        if !buffer.has_block(block_index) {
            let raw = &data_slice[consumed..consumed + BLOCK_SIZE as usize];
            let processed = if block_index % 3 == 0 {
                crypto::decrypt_blowfish_chunk(raw, key)
            } else {
                raw.to_vec()
            };
            buffer.blocks.lock().unwrap().insert(block_index, processed);
        }
        block_index += 1;
        consumed += BLOCK_SIZE as usize;
    }

    if consumed < data_slice.len() && !buffer.has_block(block_index) {
        let raw = &data_slice[consumed..];
        let processed = if block_index % 3 == 0 {
            crypto::decrypt_blowfish_chunk(raw, key)
        } else {
            raw.to_vec()
        };
        buffer.blocks.lock().unwrap().insert(block_index, processed);
    }

    buffer.notify.notify_waiters();
    Ok(())
}

pub fn cancel_preload(track_id: &str) {
    let mut cache = STREAM_CACHE.lock().unwrap();
    if let Some(pos) = cache.iter().position(|c| c.track_id == track_id) {
        let item = cache.remove(pos);
        item.buffer.is_cancelled.store(true, Ordering::SeqCst);
        item.buffer.notify.notify_waiters();
    }
}

/// Returns the known total size of a cached track.
pub fn get_cached_track_size(track_id: &str) -> Option<u64> {
    let cache = STREAM_CACHE.lock().unwrap();
    cache.iter().find(|c| c.track_id == track_id).and_then(|entry| {
        let size = entry.buffer.total_size.load(Ordering::Acquire);
        if size > 0 { Some(size) } else { None }
    })
}
