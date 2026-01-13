//! Unified Rusteer interface.
//!
//! This module provides a high-level, easy-to-use interface for
//! downloading music and fetching metadata from Deezer.

use std::fs;
use std::path::{Path, PathBuf};

use crate::api::{DeezerApi, GatewayApi};
use crate::crypto;
use crate::error::{DeezerError, Result};
use crate::models::{Album, Artist, Playlist, Track};
use crate::tagging::{self, AudioMetadata};

/// Audio quality options for downloads.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum DownloadQuality {
    /// FLAC lossless (requires premium).
    Flac,
    /// MP3 320 kbps (requires premium).
    Mp3_320,
    /// MP3 128 kbps (free accounts).
    #[default]
    Mp3_128,
}

impl DownloadQuality {
    /// Get the Deezer API format string.
    pub fn format(&self) -> &'static str {
        match self {
            DownloadQuality::Flac => "FLAC",
            DownloadQuality::Mp3_320 => "MP3_320",
            DownloadQuality::Mp3_128 => "MP3_128",
        }
    }

    /// Get file extension.
    pub fn extension(&self) -> &'static str {
        match self {
            DownloadQuality::Flac => ".flac",
            DownloadQuality::Mp3_320 | DownloadQuality::Mp3_128 => ".mp3",
        }
    }

    /// Get all qualities in order of preference.
    pub fn all() -> &'static [DownloadQuality] {
        &[
            DownloadQuality::Flac,
            DownloadQuality::Mp3_320,
            DownloadQuality::Mp3_128,
        ]
    }
}

/// Result of a single track download.
#[derive(Debug)]
pub struct DownloadResult {
    /// Path to the downloaded file.
    pub path: PathBuf,
    /// Quality that was actually used.
    pub quality: DownloadQuality,
    /// File size in bytes.
    pub size: u64,
    /// Track title.
    pub title: String,
    /// Artist name.
    pub artist: String,
}

/// Result of a batch download (album/playlist).
#[derive(Debug)]
pub struct BatchDownloadResult {
    /// Output directory.
    pub directory: PathBuf,
    /// Successfully downloaded tracks.
    pub successful: Vec<DownloadResult>,
    /// Failed track titles with error messages.
    pub failed: Vec<(String, String)>,
}

impl BatchDownloadResult {
    /// Total number of tracks attempted.
    pub fn total(&self) -> usize {
        self.successful.len() + self.failed.len()
    }

    /// Check if all tracks were downloaded successfully.
    pub fn all_successful(&self) -> bool {
        self.failed.is_empty()
    }
}

/// Main Rusteer interface.
///
/// Provides a unified API for downloading music and fetching metadata.
///
/// # Example
///
/// ```rust,no_run
/// use rusteer::Rusteer;
///
/// #[tokio::main]
/// async fn main() -> Result<(), Box<dyn std::error::Error>> {
///     // Create instance with ARL token
///     let dz = Rusteer::new("your_arl_token").await?;
///
///     // Download a track
///     let result = dz.download_track("3135556", ".").await?;
///     println!("Downloaded: {}", result.path.display());
///
///     // Get album metadata
///     let album = dz.get_album("302127").await?;
///     println!("Album: {} ({} tracks)", album.title, album.total_tracks);
///
///     Ok(())
/// }
/// ```
#[derive(Debug)]
pub struct Rusteer {
    public_api: DeezerApi,
    gateway_api: GatewayApi,
    preferred_quality: DownloadQuality,
    /// Whether to embed metadata tags in downloaded files.
    embed_tags: bool,
    /// Default output directory for downloads.
    output_dir: PathBuf,
}

impl Rusteer {
    /// Create a new Rusteer instance.
    ///
    /// Requires a valid ARL token from a logged-in Deezer session.
    ///
    /// # Arguments
    ///
    /// * `arl` - ARL authentication token
    ///
    /// # Errors
    ///
    /// Returns `BadCredentials` if the ARL token is invalid.
    pub async fn new(arl: &str) -> Result<Self> {
        let gateway_api = GatewayApi::new(arl).await?;
        let public_api = DeezerApi::new();

        Ok(Self {
            public_api,
            gateway_api,
            preferred_quality: DownloadQuality::default(),
            embed_tags: true,
            output_dir: PathBuf::from("downloads"),
        })
    }

    /// Set the preferred download quality.
    ///
    /// If the preferred quality is not available, will fall back to lower qualities.
    pub fn set_quality(&mut self, quality: DownloadQuality) {
        self.preferred_quality = quality;
    }

    /// Get the preferred download quality.
    pub fn quality(&self) -> DownloadQuality {
        self.preferred_quality
    }

    /// Enable or disable embedding metadata tags in downloaded files.
    ///
    /// When enabled (default), downloaded files will include:
    /// - Title, Artist, Album
    /// - Track/Disc numbers
    /// - Cover art
    /// - Genre, Year, ISRC
    pub fn set_embed_tags(&mut self, embed: bool) {
        self.embed_tags = embed;
    }

    /// Check if metadata tagging is enabled.
    pub fn embed_tags(&self) -> bool {
        self.embed_tags
    }

    /// Set the output directory for downloads.
    ///
    /// Default is "downloads" in the current working directory.
    pub fn set_output_dir<P: AsRef<Path>>(&mut self, path: P) {
        self.output_dir = path.as_ref().to_path_buf();
    }

    /// Get the current output directory.
    pub fn output_dir(&self) -> &Path {
        &self.output_dir
    }

    /// Check if the account has premium access.
    pub fn has_premium(&self) -> bool {
        self.gateway_api.has_license_token()
    }

    // ==================
    // METADATA FETCHING
    // ==================

    /// Get track metadata by ID or ISRC.
    pub async fn get_track(&self, track_id: &str) -> Result<Track> {
        self.public_api.get_track(track_id).await
    }

    /// Get album metadata by ID.
    pub async fn get_album(&self, album_id: &str) -> Result<Album> {
        self.public_api.get_album(album_id).await
    }

    /// Get playlist metadata by ID.
    pub async fn get_playlist(&self, playlist_id: &str) -> Result<Playlist> {
        self.public_api.get_playlist(playlist_id).await
    }

    /// Get artist metadata by ID.
    pub async fn get_artist(&self, artist_id: &str) -> Result<Artist> {
        self.public_api.get_artist(artist_id).await
    }

    /// Search for tracks.
    pub async fn search_tracks(&self, query: &str, limit: u32) -> Result<Vec<Track>> {
        self.public_api.search_tracks(query, limit).await
    }

    /// Search for albums.
    pub async fn search_albums(&self, query: &str, limit: u32) -> Result<Vec<Album>> {
        self.public_api.search_albums(query, limit).await
    }

    // ==================
    // DOWNLOADING
    // ==================

    /// Download a single track to the default output directory.
    ///
    /// Uses the configured output_dir (default: "downloads").
    pub async fn download_track(&self, track_id: &str) -> Result<DownloadResult> {
        self.download_track_to(track_id, &self.output_dir.clone())
            .await
    }

    /// Download an entire album to the default output directory.
    ///
    /// Uses the configured output_dir (default: "downloads").
    pub async fn download_album(&self, album_id: &str) -> Result<BatchDownloadResult> {
        self.download_album_to(album_id, &self.output_dir.clone())
            .await
    }

    /// Download an entire playlist to the default output directory.
    ///
    /// Uses the configured output_dir (default: "downloads").
    pub async fn download_playlist(&self, playlist_id: &str) -> Result<BatchDownloadResult> {
        self.download_playlist_to(playlist_id, &self.output_dir.clone())
            .await
    }

    /// Download a single track to a specific directory.
    ///
    /// # Arguments
    ///
    /// * `track_id` - Deezer track ID
    /// * `output_dir` - Directory to save the file
    ///
    /// # Returns
    ///
    /// Information about the downloaded file.
    pub async fn download_track_to<P: AsRef<Path>>(
        &self,
        track_id: &str,
        output_dir: P,
    ) -> Result<DownloadResult> {
        let output_dir = output_dir.as_ref();
        fs::create_dir_all(output_dir)?;

        // Get track metadata
        let track = self.public_api.get_track(track_id).await?;
        let artist = track.artists_string(", ");
        let title = track.title.clone();

        // Get song data from gateway
        let song_data = self.gateway_api.get_song_data(track_id).await?;

        if !song_data.readable {
            return Err(DeezerError::TrackNotFound(format!(
                "Track {} is not readable",
                track_id
            )));
        }

        let track_token = song_data
            .track_token
            .ok_or_else(|| DeezerError::NoDataApi("No track token".to_string()))?;

        // Find available quality
        let (media_url, quality) = self.find_media_url(&track_token).await?;

        // Download encrypted audio
        let client = reqwest::Client::new();
        let response = client.get(&media_url.url).send().await?;
        let encrypted_bytes = response.bytes().await?;

        // Build filename
        let safe_artist = sanitize_filename(&artist);
        let safe_title = sanitize_filename(&title);
        let filename = format!("{} - {}{}", safe_artist, safe_title, quality.extension());
        let output_path = output_dir.join(&filename);

        // Decrypt and save
        crypto::decrypt_track(&encrypted_bytes, track_id, &output_path)?;

        // Embed metadata tags
        if self.embed_tags {
            // Fetch cover art
            let cover_art = if !track.album.images.is_empty() {
                tagging::fetch_cover_art(&track.album.images[0].url).await
            } else {
                None
            };

            let metadata = AudioMetadata::new()
                .with_title(&track.title)
                .with_artist(&artist)
                .with_album(&track.album.title)
                .with_album_artist(&track.album.artists_string(", "))
                .with_track(track.track_number, Some(track.album.total_tracks))
                .with_disc(track.disc_number, Some(track.album.total_discs))
                .with_year(track.album.release_date.year);

            // Add ISRC if available
            let metadata = if let Some(isrc) = &track.ids.isrc {
                metadata.with_isrc(isrc)
            } else {
                metadata
            };

            // Add genre if available
            let metadata = if !track.album.genres.is_empty() {
                metadata.with_genre(track.album.genres.join(", "))
            } else {
                metadata
            };

            // Add cover art if fetched
            let metadata = if let Some(cover) = cover_art {
                metadata.with_cover_art(cover)
            } else {
                metadata
            };

            tagging::write_metadata(&output_path, &metadata)?;
        }

        let size = fs::metadata(&output_path)?.len();

        Ok(DownloadResult {
            path: output_path,
            quality,
            size,
            title,
            artist,
        })
    }

    /// Download an entire album to a specific directory.
    ///
    /// Creates a directory with the album name and downloads all tracks.
    ///
    /// # Arguments
    ///
    /// * `album_id` - Deezer album ID
    /// * `output_dir` - Base directory (album folder will be created inside)
    pub async fn download_album_to<P: AsRef<Path>>(
        &self,
        album_id: &str,
        output_dir: P,
    ) -> Result<BatchDownloadResult> {
        let output_dir = output_dir.as_ref();

        // Get album metadata
        let album = self.public_api.get_album(album_id).await?;

        // Create album directory
        let safe_artist = sanitize_filename(&album.artists_string(", "));
        let safe_title = sanitize_filename(&album.title);
        let album_dir = output_dir.join(format!("{} - {}", safe_artist, safe_title));
        fs::create_dir_all(&album_dir)?;

        let mut result = BatchDownloadResult {
            directory: album_dir.clone(),
            successful: Vec::new(),
            failed: Vec::new(),
        };

        // Download each track
        for track in &album.tracks {
            let track_id = match &track.ids.deezer {
                Some(id) => id.clone(),
                None => {
                    result
                        .failed
                        .push((track.title.clone(), "No track ID".to_string()));
                    continue;
                }
            };

            match self
                .download_album_track(&track_id, &track.title, track.track_number, &album_dir)
                .await
            {
                Ok(download_result) => {
                    result.successful.push(download_result);
                }
                Err(e) => {
                    result.failed.push((track.title.clone(), e.to_string()));
                }
            }
        }

        Ok(result)
    }

    /// Download an entire playlist to a specific directory.
    ///
    /// Creates a directory with the playlist name and downloads all tracks.
    ///
    /// # Arguments
    ///
    /// * `playlist_id` - Deezer playlist ID
    /// * `output_dir` - Base directory (playlist folder will be created inside)
    pub async fn download_playlist_to<P: AsRef<Path>>(
        &self,
        playlist_id: &str,
        output_dir: P,
    ) -> Result<BatchDownloadResult> {
        let output_dir = output_dir.as_ref();

        // Get playlist metadata
        let playlist = self.public_api.get_playlist(playlist_id).await?;

        // Create playlist directory
        let safe_title = sanitize_filename(&playlist.title);
        let playlist_dir = output_dir.join(format!("Playlist - {}", safe_title));
        fs::create_dir_all(&playlist_dir)?;

        let mut result = BatchDownloadResult {
            directory: playlist_dir.clone(),
            successful: Vec::new(),
            failed: Vec::new(),
        };

        // Download each track
        for (idx, track) in playlist.tracks.iter().enumerate() {
            let track_id = match &track.ids.deezer {
                Some(id) => id.clone(),
                None => {
                    result
                        .failed
                        .push((track.title.clone(), "No track ID".to_string()));
                    continue;
                }
            };

            let artist = track.artists_string(", ");
            let track_title = format!("{} - {}", artist, track.title);

            match self
                .download_playlist_track(&track_id, &artist, &track.title, idx + 1, &playlist_dir)
                .await
            {
                Ok(download_result) => {
                    result.successful.push(download_result);
                }
                Err(e) => {
                    result.failed.push((track_title, e.to_string()));
                }
            }
        }

        Ok(result)
    }

    // ==================
    // INTERNAL HELPERS
    // ==================

    /// Find an available media URL, trying different qualities.
    async fn find_media_url(
        &self,
        track_token: &str,
    ) -> Result<(crate::api::gateway::MediaUrl, DownloadQuality)> {
        // Build quality order starting from preferred
        let qualities = match self.preferred_quality {
            DownloadQuality::Flac => vec![
                DownloadQuality::Flac,
                DownloadQuality::Mp3_320,
                DownloadQuality::Mp3_128,
            ],
            DownloadQuality::Mp3_320 => vec![DownloadQuality::Mp3_320, DownloadQuality::Mp3_128],
            DownloadQuality::Mp3_128 => vec![DownloadQuality::Mp3_128],
        };

        for quality in qualities {
            if let Ok(urls) = self
                .gateway_api
                .get_media_url(&[track_token.to_string()], quality.format())
                .await
            {
                if let Some(url) = urls.into_iter().next() {
                    return Ok((url, quality));
                }
            }
        }

        Err(DeezerError::NoRightOnMedia(
            "No media URL available for any quality".to_string(),
        ))
    }

    /// Download a track from an album context.
    async fn download_album_track(
        &self,
        track_id: &str,
        title: &str,
        track_number: u32,
        output_dir: &Path,
    ) -> Result<DownloadResult> {
        let song_data = self.gateway_api.get_song_data(track_id).await?;

        if !song_data.readable {
            return Err(DeezerError::TrackNotFound("Not readable".to_string()));
        }

        let track_token = song_data
            .track_token
            .ok_or_else(|| DeezerError::NoDataApi("No track token".to_string()))?;

        let (media_url, quality) = self.find_media_url(&track_token).await?;

        let client = reqwest::Client::new();
        let response = client.get(&media_url.url).send().await?;
        let encrypted_bytes = response.bytes().await?;

        let safe_title = sanitize_filename(title);
        let filename = format!(
            "{:02} - {}{}",
            track_number,
            safe_title,
            quality.extension()
        );
        let output_path = output_dir.join(&filename);

        crypto::decrypt_track(&encrypted_bytes, track_id, &output_path)?;

        // Embed metadata tags
        if self.embed_tags {
            // Fetch full track info for metadata
            if let Ok(track) = self.public_api.get_track(track_id).await {
                // Fetch cover art
                let cover_art = if !track.album.images.is_empty() {
                    tagging::fetch_cover_art(&track.album.images[0].url).await
                } else {
                    None
                };

                let artist = track.artists_string(", ");

                let metadata = AudioMetadata::new()
                    .with_title(&track.title)
                    .with_artist(&artist)
                    .with_album(&track.album.title)
                    .with_album_artist(&track.album.artists_string(", "))
                    .with_track(track.track_number, Some(track.album.total_tracks))
                    .with_disc(track.disc_number, Some(track.album.total_discs))
                    .with_year(track.album.release_date.year);

                // Add ISRC if available
                let metadata = if let Some(isrc) = &track.ids.isrc {
                    metadata.with_isrc(isrc)
                } else {
                    metadata
                };

                // Add genre if available
                let metadata = if !track.album.genres.is_empty() {
                    metadata.with_genre(track.album.genres.join(", "))
                } else {
                    metadata
                };

                // Add cover art if fetched
                let metadata = if let Some(cover) = cover_art {
                    metadata.with_cover_art(cover)
                } else {
                    metadata
                };

                // Ignore tagging errors
                let _ = tagging::write_metadata(&output_path, &metadata);
            }
        }

        let size = fs::metadata(&output_path)?.len();

        Ok(DownloadResult {
            path: output_path,
            quality,
            size,
            title: title.to_string(),
            artist: String::new(), // We could fill this if we fetched the track
        })
    }

    /// Download a track from a playlist context.
    async fn download_playlist_track(
        &self,
        track_id: &str,
        artist: &str,
        title: &str,
        position: usize,
        output_dir: &Path,
    ) -> Result<DownloadResult> {
        let song_data = self.gateway_api.get_song_data(track_id).await?;

        if !song_data.readable {
            return Err(DeezerError::TrackNotFound("Not readable".to_string()));
        }

        let track_token = song_data
            .track_token
            .ok_or_else(|| DeezerError::NoDataApi("No track token".to_string()))?;

        let (media_url, quality) = self.find_media_url(&track_token).await?;

        let client = reqwest::Client::new();
        let response = client.get(&media_url.url).send().await?;
        let encrypted_bytes = response.bytes().await?;

        let safe_artist = sanitize_filename(artist);
        let safe_title = sanitize_filename(title);
        let filename = format!(
            "{:03} - {} - {}{}",
            position,
            safe_artist,
            safe_title,
            quality.extension()
        );
        let output_path = output_dir.join(&filename);

        crypto::decrypt_track(&encrypted_bytes, track_id, &output_path)?;

        // Embed metadata tags
        if self.embed_tags {
            // Fetch full track info for metadata
            if let Ok(track) = self.public_api.get_track(track_id).await {
                // Fetch cover art
                let cover_art = if !track.album.images.is_empty() {
                    tagging::fetch_cover_art(&track.album.images[0].url).await
                } else {
                    None
                };

                let artist = track.artists_string(", ");

                let metadata = AudioMetadata::new()
                    .with_title(&track.title)
                    .with_artist(&artist)
                    .with_album(&track.album.title)
                    .with_album_artist(&track.album.artists_string(", "))
                    .with_track(track.track_number, Some(track.album.total_tracks))
                    .with_disc(track.disc_number, Some(track.album.total_discs))
                    .with_year(track.album.release_date.year);

                // Add ISRC if available
                let metadata = if let Some(isrc) = &track.ids.isrc {
                    metadata.with_isrc(isrc)
                } else {
                    metadata
                };

                // Add genre if available
                let metadata = if !track.album.genres.is_empty() {
                    metadata.with_genre(track.album.genres.join(", "))
                } else {
                    metadata
                };

                // Add cover art if fetched
                let metadata = if let Some(cover) = cover_art {
                    metadata.with_cover_art(cover)
                } else {
                    metadata
                };

                // Ignore tagging errors
                let _ = tagging::write_metadata(&output_path, &metadata);
            }
        }

        let size = fs::metadata(&output_path)?.len();

        Ok(DownloadResult {
            path: output_path,
            quality,
            size,
            title: title.to_string(),
            artist: artist.to_string(),
        })
    }
}

/// Sanitize a string for use as a filename.
fn sanitize_filename(name: &str) -> String {
    name.replace(['/', '\\', ':', '*', '?', '"', '<', '>', '|'], "_")
        .trim()
        .to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sanitize_filename() {
        assert_eq!(sanitize_filename("Hello/World"), "Hello_World");
        assert_eq!(sanitize_filename("Test: File*Name"), "Test_ File_Name");
    }

    #[test]
    fn test_quality_format() {
        assert_eq!(DownloadQuality::Flac.format(), "FLAC");
        assert_eq!(DownloadQuality::Mp3_320.format(), "MP3_320");
        assert_eq!(DownloadQuality::Mp3_128.format(), "MP3_128");
    }

    #[test]
    fn test_quality_extension() {
        assert_eq!(DownloadQuality::Flac.extension(), ".flac");
        assert_eq!(DownloadQuality::Mp3_320.extension(), ".mp3");
    }
}
