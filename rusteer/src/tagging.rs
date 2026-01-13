//! Audio metadata tagging utilities.
//!
//! This module provides functions for embedding metadata (artist, album, cover art, etc.)
//! into downloaded audio files (MP3 and FLAC).

use lofty::config::WriteOptions;
use lofty::file::TaggedFileExt;
use lofty::picture::{MimeType, Picture, PictureType};
use lofty::tag::{Accessor, TagExt};
use std::path::Path;
use tracing::{debug, warn};

use crate::error::Result;

/// Metadata to embed in audio files.
#[derive(Debug, Clone, Default)]
pub struct AudioMetadata {
    /// Track title.
    pub title: Option<String>,
    /// Track artist(s).
    pub artist: Option<String>,
    /// Album title.
    pub album: Option<String>,
    /// Album artist(s).
    pub album_artist: Option<String>,
    /// Track number.
    pub track_number: Option<u32>,
    /// Total tracks in album.
    pub total_tracks: Option<u32>,
    /// Disc number.
    pub disc_number: Option<u32>,
    /// Total discs.
    pub total_discs: Option<u32>,
    /// Release year.
    pub year: Option<i32>,
    /// Genre(s).
    pub genre: Option<String>,
    /// ISRC code.
    pub isrc: Option<String>,
    /// Cover art as JPEG bytes.
    pub cover_art: Option<Vec<u8>>,
}

impl AudioMetadata {
    /// Create new empty metadata.
    pub fn new() -> Self {
        Self::default()
    }

    /// Set title.
    pub fn with_title<S: Into<String>>(mut self, title: S) -> Self {
        self.title = Some(title.into());
        self
    }

    /// Set artist.
    pub fn with_artist<S: Into<String>>(mut self, artist: S) -> Self {
        self.artist = Some(artist.into());
        self
    }

    /// Set album.
    pub fn with_album<S: Into<String>>(mut self, album: S) -> Self {
        self.album = Some(album.into());
        self
    }

    /// Set album artist.
    pub fn with_album_artist<S: Into<String>>(mut self, album_artist: S) -> Self {
        self.album_artist = Some(album_artist.into());
        self
    }

    /// Set track number and total.
    pub fn with_track(mut self, number: u32, total: Option<u32>) -> Self {
        self.track_number = Some(number);
        self.total_tracks = total;
        self
    }

    /// Set disc number and total.
    pub fn with_disc(mut self, number: u32, total: Option<u32>) -> Self {
        self.disc_number = Some(number);
        self.total_discs = total;
        self
    }

    /// Set year.
    pub fn with_year(mut self, year: i32) -> Self {
        self.year = Some(year);
        self
    }

    /// Set genre.
    pub fn with_genre<S: Into<String>>(mut self, genre: S) -> Self {
        self.genre = Some(genre.into());
        self
    }

    /// Set ISRC.
    pub fn with_isrc<S: Into<String>>(mut self, isrc: S) -> Self {
        self.isrc = Some(isrc.into());
        self
    }

    /// Set cover art from JPEG bytes.
    pub fn with_cover_art(mut self, cover: Vec<u8>) -> Self {
        self.cover_art = Some(cover);
        self
    }
}

/// Write metadata to an audio file.
///
/// Supports MP3 (ID3v2.4) and FLAC (Vorbis Comments).
///
/// # Arguments
///
/// * `path` - Path to the audio file
/// * `metadata` - Metadata to embed
///
/// # Errors
///
/// Returns an error if the file cannot be read or written.
pub fn write_metadata<P: AsRef<Path>>(path: P, metadata: &AudioMetadata) -> Result<()> {
    let path = path.as_ref();
    debug!("Writing metadata to: {}", path.display());

    // Read the file
    let mut tagged_file = match lofty::read_from_path(path) {
        Ok(f) => f,
        Err(e) => {
            warn!("Could not read file for tagging: {}", e);
            return Ok(()); // Don't fail the download if tagging fails
        }
    };

    // Get or create the primary tag
    let tag = match tagged_file.primary_tag_mut() {
        Some(t) => t,
        None => {
            // Create appropriate tag type based on file
            let tag_type = tagged_file.primary_tag_type();
            tagged_file.insert_tag(lofty::tag::Tag::new(tag_type));
            tagged_file.primary_tag_mut().unwrap()
        }
    };

    // Set basic metadata
    if let Some(title) = &metadata.title {
        tag.set_title(title.clone());
    }

    if let Some(artist) = &metadata.artist {
        tag.set_artist(artist.clone());
    }

    if let Some(album) = &metadata.album {
        tag.set_album(album.clone());
    }

    if let Some(track) = metadata.track_number {
        tag.set_track(track);
    }

    if let Some(total) = metadata.total_tracks {
        tag.set_track_total(total);
    }

    if let Some(disc) = metadata.disc_number {
        tag.set_disk(disc);
    }

    if let Some(total) = metadata.total_discs {
        tag.set_disk_total(total);
    }

    if let Some(year) = metadata.year {
        if year > 0 {
            tag.set_year(year as u32);
        }
    }

    if let Some(genre) = &metadata.genre {
        tag.set_genre(genre.clone());
    }

    // Add cover art
    if let Some(cover_data) = &metadata.cover_art {
        // Detect MIME type from magic bytes
        let mime_type = if cover_data.starts_with(&[0xFF, 0xD8, 0xFF]) {
            MimeType::Jpeg
        } else if cover_data.starts_with(&[0x89, 0x50, 0x4E, 0x47]) {
            MimeType::Png
        } else {
            MimeType::Jpeg // Assume JPEG
        };

        let picture = Picture::new_unchecked(
            PictureType::CoverFront,
            Some(mime_type),
            None,
            cover_data.clone(),
        );

        tag.push_picture(picture);
    }

    // Save the file
    if let Err(e) = tag.save_to_path(path, WriteOptions::default()) {
        warn!("Failed to save tags to {}: {}", path.display(), e);
        // Don't fail the download
    } else {
        debug!("Successfully wrote metadata to {}", path.display());
    }

    Ok(())
}

/// Fetch cover art from Deezer.
pub async fn fetch_cover_art(cover_url: &str) -> Option<Vec<u8>> {
    if cover_url.is_empty() {
        return None;
    }

    // Get highest resolution cover
    let high_res_url = cover_url
        .replace("/56x56", "/1200x1200")
        .replace("/250x250", "/1200x1200")
        .replace("/500x500", "/1200x1200")
        .replace("/1000x1000", "/1200x1200");

    let client = reqwest::Client::new();
    match client.get(&high_res_url).send().await {
        Ok(response) => match response.bytes().await {
            Ok(bytes) => {
                // Check if it's a valid image (not a placeholder)
                if bytes.len() > 1000 {
                    Some(bytes.to_vec())
                } else {
                    None
                }
            }
            Err(_) => None,
        },
        Err(_) => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_metadata_builder() {
        let meta = AudioMetadata::new()
            .with_title("Test Song")
            .with_artist("Test Artist")
            .with_album("Test Album")
            .with_track(1, Some(10))
            .with_year(2024);

        assert_eq!(meta.title, Some("Test Song".to_string()));
        assert_eq!(meta.artist, Some("Test Artist".to_string()));
        assert_eq!(meta.album, Some("Test Album".to_string()));
        assert_eq!(meta.track_number, Some(1));
        assert_eq!(meta.total_tracks, Some(10));
        assert_eq!(meta.year, Some(2024));
    }
}
