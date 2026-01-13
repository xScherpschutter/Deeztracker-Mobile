use std::path::PathBuf;
use std::sync::Arc;
use tokio::sync::Mutex;

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

#[derive(uniffi::Object)]
pub struct RusteerService {
    inner: Mutex<Rusteer>,
}

#[uniffi::export]
impl RusteerService {
    #[uniffi::constructor]
    pub async fn new(arl: String) -> Result<Arc<Self>, RusteerError> {
        let rusteer = Rusteer::new(&arl).await?;
        Ok(Arc::new(Self {
            inner: Mutex::new(rusteer),
        }))
    }

    pub async fn set_quality(&self, quality: DownloadQuality) {
        let mut inner = self.inner.lock().await;
        inner.set_quality(quality.into());
    }

    pub async fn download_track(
        &self,
        track_id: String,
        output_dir: String,
    ) -> Result<DownloadResult, RusteerError> {
        let inner = self.inner.lock().await;
        let result = inner
            .download_track_to(&track_id, PathBuf::from(output_dir))
            .await?;
        Ok(result.into())
    }

    pub async fn download_album(
        &self,
        album_id: String,
        output_dir: String,
    ) -> Result<BatchDownloadResult, RusteerError> {
        let inner = self.inner.lock().await;
        let result = inner
            .download_album_to(&album_id, PathBuf::from(output_dir))
            .await?;
        Ok(result.into())
    }

    pub async fn download_playlist(
        &self,
        playlist_id: String,
        output_dir: String,
    ) -> Result<BatchDownloadResult, RusteerError> {
        let inner = self.inner.lock().await;
        let result = inner
            .download_playlist_to(&playlist_id, PathBuf::from(output_dir))
            .await?;
        Ok(result.into())
    }
}
