//! Error types for the Deezer API.

use thiserror::Error;

/// Main error type for all Deezer operations.
#[derive(Debug, Error)]
pub enum DeezerError {
    /// Track was not found (possibly region-restricted or premium-only).
    #[error("Track not found: {0}")]
    TrackNotFound(String),

    /// Album was not found.
    #[error("Album not found: {0}")]
    AlbumNotFound(String),

    /// Playlist was not found.
    #[error("Playlist not found: {0}")]
    PlaylistNotFound(String),

    /// Artist was not found.
    #[error("Artist not found: {0}")]
    ArtistNotFound(String),

    /// Invalid or expired credentials (ARL token).
    #[error("Bad credentials: {0}")]
    BadCredentials(String),

    /// No rights to access the media (premium required).
    #[error("No rights on media: {0}")]
    NoRightOnMedia(String),

    /// Requested quality is not available.
    #[error("Quality not available: {0}")]
    QualityNotFound(String),

    /// Too many requests - rate limited.
    #[error("Quota exceeded: too many requests")]
    QuotaExceeded,

    /// Invalid link format.
    #[error("Invalid link: {0}")]
    InvalidLink(String),

    /// No data returned from API.
    #[error("No data from API: {0}")]
    NoDataApi(String),

    /// HTTP request failed.
    #[error("Request error: {0}")]
    RequestError(#[from] reqwest::Error),

    /// JSON parsing failed.
    #[error("Parse error: {0}")]
    ParseError(#[from] serde_json::Error),

    /// Cryptographic operation failed.
    #[error("Crypto error: {0}")]
    CryptoError(String),

    /// I/O operation failed.
    #[error("IO error: {0}")]
    IoError(#[from] std::io::Error),

    /// Generic API error with message.
    #[error("API error: {0}")]
    ApiError(String),
}

/// Result type alias for Deezer operations.
pub type Result<T> = std::result::Result<T, DeezerError>;
