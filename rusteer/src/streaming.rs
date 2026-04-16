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
const MAX_CONCURRENT_DOWNLOADS: usize = 4; // Aumentado para mejor pre-carga

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
    // 1. Check LRU Cache
    let cached = {
        let mut cache = STREAM_CACHE.lock().unwrap();
        if let Some(pos) = cache.iter().position(|c| c.track_id == track_id && c.quality == preferred_quality) {
            let item = cache.remove(pos);
            let buffer = item.buffer.clone();
            let media_url = item.media_url.clone();
            
            if buffer.is_cancelled.load(Ordering::SeqCst) {
                buffer.is_cancelled.store(false, Ordering::SeqCst);
                start_background_download(buffer.clone(), track_id.to_string(), media_url);
            } else if !buffer.is_downloading.load(Ordering::SeqCst) && !buffer.is_complete.load(Ordering::SeqCst) {
                start_background_download(buffer.clone(), track_id.to_string(), media_url);
            }
            
            cache.push(item);
            Some(buffer)
        } else {
            None
        }
    };

    if let Some(buffer) = cached {
        wait_for_initial_data(&buffer).await;
        return Ok(buffer.total_size.load(Ordering::SeqCst));
    }

    // 2. Resolve URL
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

    // 3. Create Buffer
    let buffer = SharedBuffer::new();
    let buffer_clone = buffer.clone();
    
    {
        let mut cache = STREAM_CACHE.lock().unwrap();
        if cache.len() >= 10 {
            let old = cache.remove(0);
            old.buffer.is_cancelled.store(true, Ordering::SeqCst);
            old.buffer.notify.notify_waiters();
        }
        cache.push(StreamCache {
            track_id: track_id.to_string(),
            quality: final_quality,
            buffer: buffer.clone(),
            media_url: final_url.clone(),
        });
    }

    start_background_download(buffer, track_id.to_string(), final_url);
    wait_for_initial_data(&buffer_clone).await;
    Ok(buffer_clone.total_size.load(Ordering::SeqCst))
}

async fn wait_for_initial_data(buffer: &SharedBuffer) {
    let mut attempts = 0;
    while attempts < 60 { // Aumentado a 3 segundos
        {
            let blocks = buffer.blocks.lock().unwrap();
            let total = buffer.total_size.load(Ordering::SeqCst);
            // Si ya tenemos el tamaño total y al menos 2 bloques, es suficiente para ExoPlayer
            if (total > 0 && blocks.len() >= 2) || buffer.is_complete.load(Ordering::SeqCst) { break; }
        }
        tokio::time::sleep(std::time::Duration::from_millis(50)).await;
        attempts += 1;
    }
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

            debug!("[Stream:{}] Starting download from {}", track_id, aligned_start);

            let res = HTTP_CLIENT.get(&media_url)
                .header("Range", format!("bytes={}-", aligned_start))
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
                        let full_size = if status.as_u16() == 206 { aligned_start + len } else { len };
                        buffer.total_size.store(full_size, Ordering::SeqCst);
                    }

                    use tokio_stream::StreamExt;
                    let mut byte_stream = response.bytes_stream();
                    let mut chunk_buffer = Vec::with_capacity(16384);

                    loop {
                        tokio::select! {
                            // Si hay un seek, interrumpimos la descarga actual para saltar
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
                                        // Fin del stream o error
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
    let buffer = {
        let mut cache = STREAM_CACHE.lock().unwrap();
        if let Some(pos) = cache.iter().position(|c| c.track_id == track_id) {
            let item = cache.remove(pos);
            let b = item.buffer.clone();
            cache.push(item);
            Some(b)
        } else { None }
    };

    let buffer = buffer.ok_or_else(|| DeezerError::ApiError(format!("Track {} not in cache", track_id)))?;
    let mut current_offset = offset;
    let end_offset = offset + size as u64;
    let mut result = Vec::with_capacity(size as usize);

    while current_offset < end_offset {
        if buffer.is_cancelled.load(Ordering::Relaxed) { return Err(DeezerError::ApiError("Cancelled".to_string())); }

        let block_index = current_offset / BLOCK_SIZE;
        let block_offset = (current_offset % BLOCK_SIZE) as usize;

        if let Some(block) = buffer.get_block(block_index) {
            let available = block.len() - block_offset;
            let to_copy = std::cmp::min(available, (end_offset - current_offset) as usize);
            result.extend_from_slice(&block[block_offset..block_offset + to_copy]);
            current_offset += to_copy as u64;
        } else {
            // Si no tenemos el bloque, comprobamos si es un Seek (salto adelante o atrás)
            let d_pos = buffer.download_pos.load(Ordering::Relaxed);
            let is_far_ahead = current_offset > d_pos + (128 * 1024); // Salto de más de 128KB
            let is_behind = current_offset < d_pos;

            if is_far_ahead || is_behind {
                // Actualizamos la posición de descarga y disparamos el trigger para que el downloader salte
                buffer.download_pos.store(current_offset, Ordering::SeqCst);
                buffer.seek_trigger.notify_waiters();
                debug!("[Stream:{}] Triggered jump to {}", track_id, current_offset);
            }

            if buffer.is_complete.load(Ordering::SeqCst) { break; }
            
            // Esperamos a que el downloader nos de datos (máximo 5 segundos)
            if tokio::time::timeout(std::time::Duration::from_secs(5), buffer.notify.notified()).await.is_err() {
                break; // Timeout, probablemente error de red
            }
        }
    }
    Ok(result)
}

pub fn cancel_preload(track_id: &str) {
    let mut cache = STREAM_CACHE.lock().unwrap();
    if let Some(pos) = cache.iter().position(|c| c.track_id == track_id) {
        let item = cache.remove(pos);
        item.buffer.is_cancelled.store(true, Ordering::SeqCst);
        item.buffer.notify.notify_waiters();
    }
}
