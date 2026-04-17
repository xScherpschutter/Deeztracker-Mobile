use std::path::PathBuf;

use crate::rusteer::{self, Rusteer};

#[derive(uniffi::Enum)]
pub enum DownloadQuality {
    Flac,
    Mp3_320,
    Mp3_128,
}

impl From<DownloadQuality> for rusteer::DownloadQuality {
    fn from(q: DownloadQuality) -> Self {
        match q {
            DownloadQuality::Flac => rusteer::DownloadQuality::Flac,
            DownloadQuality::Mp3_320 => rusteer::DownloadQuality::Mp3_320,
            DownloadQuality::Mp3_128 => rusteer::DownloadQuality::Mp3_128,
        }
    }
}

impl From<rusteer::DownloadQuality> for DownloadQuality {
    fn from(q: rusteer::DownloadQuality) -> Self {
        match q {
            rusteer::DownloadQuality::Flac => DownloadQuality::Flac,
            rusteer::DownloadQuality::Mp3_320 => DownloadQuality::Mp3_320,
            rusteer::DownloadQuality::Mp3_128 => DownloadQuality::Mp3_128,
        }
    }
}

#[derive(uniffi::Record)]
pub struct DownloadResult {
    pub path: String,
    pub quality: DownloadQuality,
    pub size: u64,
    pub title: String,
    pub artist: String,
}

impl From<rusteer::DownloadResult> for DownloadResult {
    fn from(r: rusteer::DownloadResult) -> Self {
        Self {
            path: r.path.to_string_lossy().to_string(),
            quality: r.quality.into(),
            size: r.size,
            title: r.title,
            artist: r.artist,
        }
    }
}

#[derive(uniffi::Record)]
pub struct BatchDownloadResult {
    pub directory: String,
    pub successful: Vec<DownloadResult>,
    pub failed: Vec<FailedDownload>,
}

#[derive(uniffi::Record)]
pub struct FailedDownload {
    pub title: String,
    pub error: String,
}

impl From<rusteer::BatchDownloadResult> for BatchDownloadResult {
    fn from(r: rusteer::BatchDownloadResult) -> Self {
        Self {
            directory: r.directory.to_string_lossy().to_string(),
            successful: r.successful.into_iter().map(Into::into).collect(),
            failed: r
                .failed
                .into_iter()
                .map(|(title, error)| FailedDownload { title, error })
                .collect(),
        }
    }
}

#[derive(uniffi::Error, Debug, thiserror::Error)]
pub enum RusteerError {
    #[error("Deezer error: {0}")]
    DeezerError(String),
}

impl From<crate::error::DeezerError> for RusteerError {
    fn from(e: crate::error::DeezerError) -> Self {
        RusteerError::DeezerError(e.to_string())
    }
}

#[derive(uniffi::Record)]
pub struct Track {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub cover_url: Option<String>,
}

impl From<crate::models::Track> for Track {
    fn from(t: crate::models::Track) -> Self {
        let artist = t.artists_string(", ");
        Self {
            id: t.ids.deezer.clone().unwrap_or_default(),
            title: t.title,
            artist,
            album: t.album.title,
            cover_url: t.album.images.first().map(|i| i.url.clone()),
        }
    }
}

/// Stateless Rusteer service - each method receives ARL as parameter
#[derive(uniffi::Object)]
pub struct RusteerService {}

#[uniffi::export]
impl RusteerService {
    #[uniffi::constructor]
    pub fn new() -> Self {
        #[cfg(target_os = "android")]
        {
            use android_logger::Config;
            use log::LevelFilter;
            let _ = android_logger::init_once(
                Config::default()
                    .with_max_level(LevelFilter::Debug)
                    .with_tag("RusteerRust"),
            );
        }
        Self {}
    }

    /// Search for tracks
    pub fn search_tracks(&self, arl: String, query: String) -> Result<Vec<Track>, RusteerError> {
        let runtime = tokio::runtime::Runtime::new()
            .map_err(|e| RusteerError::DeezerError(format!("Failed to create runtime: {}", e)))?;

        runtime.block_on(async {
            let rusteer = Rusteer::new(&arl).await?;
            let results = rusteer.search_tracks(&query, 20).await?;
            Ok(results.into_iter().map(Into::into).collect())
        })
    }

    /// Verify if an ARL token is valid (for login)
    /// This is a blocking call that internally uses Tokio runtime
    pub fn verify_arl(&self, arl: String) -> Result<bool, RusteerError> {
        let runtime = tokio::runtime::Runtime::new()
            .map_err(|e| RusteerError::DeezerError(format!("Failed to create runtime: {}", e)))?;

        runtime.block_on(async {
            match Rusteer::new(&arl).await {
                Ok(_) => Ok(true),
                Err(e) => {
                    // Check if it's a bad credentials error
                    if e.to_string().contains("Bad credentials") || e.to_string().contains("401") {
                        Ok(false)
                    } else {
                        Err(e.into())
                    }
                }
            }
        })
    }

    pub fn download_track(
        &self,
        arl: String,
        track_id: String,
        output_dir: String,
        quality: DownloadQuality,
    ) -> Result<DownloadResult, RusteerError> {
        let runtime = tokio::runtime::Runtime::new()
            .map_err(|e| RusteerError::DeezerError(format!("Failed to create runtime: {}", e)))?;

        runtime.block_on(async {
            let mut rusteer = Rusteer::new(&arl).await?;
            rusteer.set_quality(quality.into());
            let result = rusteer
                .download_track_to(&track_id, PathBuf::from(output_dir))
                .await?;
            Ok(result.into())
        })
    }

    pub fn download_album(
        &self,
        arl: String,
        album_id: String,
        output_dir: String,
        quality: DownloadQuality,
    ) -> Result<BatchDownloadResult, RusteerError> {
        let runtime = tokio::runtime::Runtime::new()
            .map_err(|e| RusteerError::DeezerError(format!("Failed to create runtime: {}", e)))?;

        runtime.block_on(async {
            let mut rusteer = Rusteer::new(&arl).await?;
            rusteer.set_quality(quality.into());
            let result = rusteer
                .download_album_to(&album_id, PathBuf::from(output_dir))
                .await?;
            Ok(result.into())
        })
    }

    pub fn download_playlist(
        &self,
        arl: String,
        playlist_id: String,
        output_dir: String,
        quality: DownloadQuality,
    ) -> Result<BatchDownloadResult, RusteerError> {
        let runtime = tokio::runtime::Runtime::new()
            .map_err(|e| RusteerError::DeezerError(format!("Failed to create runtime: {}", e)))?;

        runtime.block_on(async {
            let mut rusteer = Rusteer::new(&arl).await?;
            rusteer.set_quality(quality.into());
            let result = rusteer
                .download_playlist_to(&playlist_id, PathBuf::from(output_dir))
                .await?;
            Ok(result.into())
        })
    }

    // =====================================
    // STREAMING
    // =====================================

    pub fn preload_track(
        &self,
        arl: String,
        track_id: String,
        quality: DownloadQuality,
    ) -> Result<u64, RusteerError> {
        crate::streaming::RUNTIME.block_on(async {
            let size = crate::streaming::preload_track(&arl, &track_id, quality.into()).await?;
            Ok(size)
        })
    }

    pub fn read_audio_chunk(
        &self,
        track_id: String,
        offset: u64,
        size: u32,
    ) -> Result<Vec<u8>, RusteerError> {
        crate::streaming::RUNTIME.block_on(async {
            let chunk = crate::streaming::read_audio_chunk(&track_id, offset, size).await?;
            Ok(chunk)
        })
    }

    pub fn cancel_preload(&self, track_id: String) {
        crate::streaming::cancel_preload(&track_id);
    }

    pub fn get_cached_track_size(&self, track_id: String) -> Option<u64> {
        crate::streaming::get_cached_track_size(&track_id)
    }
}
