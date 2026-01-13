//! Artist-related models.
//!
//! This module contains models for representing artists and their
//! discography.

use serde::{Deserialize, Serialize};

use super::common::{IDs, Image, ReleaseDate};

/// Album when nested inside an artist context.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct AlbumArtist {
    /// Type marker for serialization.
    #[serde(rename = "type", default = "default_album_artist_type")]
    pub type_: String,

    /// Album type: "album", "single", or "compilation".
    pub album_type: String,

    /// Album title.
    pub title: String,

    /// Release date.
    pub release_date: ReleaseDate,

    /// Total number of tracks in the album.
    pub total_tracks: u32,

    /// Album identifiers.
    pub ids: IDs,
}

fn default_album_artist_type() -> String {
    "albumArtist".to_string()
}

impl AlbumArtist {
    /// Create a new album with basic info.
    pub fn new<S1: Into<String>, S2: Into<String>>(title: S1, deezer_id: S2) -> Self {
        Self {
            type_: "albumArtist".to_string(),
            title: title.into(),
            ids: IDs::with_deezer(deezer_id),
            ..Default::default()
        }
    }
}

/// A full artist record.
///
/// Contains complete artist information including discography.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct Artist {
    /// Type marker for serialization.
    #[serde(rename = "type", default = "default_artist_type")]
    pub type_: String,

    /// Artist name.
    pub name: String,

    /// Genres associated with the artist.
    #[serde(default)]
    pub genres: Vec<String>,

    /// Artist images in various sizes.
    #[serde(default)]
    pub images: Vec<Image>,

    /// Artist identifiers.
    pub ids: IDs,

    /// Albums by this artist.
    #[serde(default)]
    pub albums: Vec<AlbumArtist>,
}

fn default_artist_type() -> String {
    "artist".to_string()
}

impl Artist {
    /// Create a new artist with name and Deezer ID.
    pub fn new<S1: Into<String>, S2: Into<String>>(name: S1, deezer_id: S2) -> Self {
        Self {
            type_: "artist".to_string(),
            name: name.into(),
            ids: IDs::with_deezer(deezer_id),
            ..Default::default()
        }
    }

    /// Get the Deezer artist ID.
    pub fn deezer_id(&self) -> Option<&str> {
        self.ids.deezer.as_deref()
    }

    /// Get the largest image available.
    pub fn largest_image(&self) -> Option<&Image> {
        self.images.iter().max_by_key(|img| img.width * img.height)
    }

    /// Get all albums sorted by release date (newest first).
    pub fn albums_by_date(&self) -> Vec<&AlbumArtist> {
        let mut albums: Vec<_> = self.albums.iter().collect();
        albums.sort_by(|a, b| b.release_date.year.cmp(&a.release_date.year));
        albums
    }

    /// Get only albums (excluding singles and compilations).
    pub fn albums_only(&self) -> Vec<&AlbumArtist> {
        self.albums
            .iter()
            .filter(|a| a.album_type == "album")
            .collect()
    }

    /// Get only singles.
    pub fn singles_only(&self) -> Vec<&AlbumArtist> {
        self.albums
            .iter()
            .filter(|a| a.album_type == "single")
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_artist_new() {
        let artist = Artist::new("Test Artist", "12345");
        assert_eq!(artist.name, "Test Artist");
        assert_eq!(artist.deezer_id(), Some("12345"));
    }

    #[test]
    fn test_albums_only() {
        let artist = Artist {
            albums: vec![
                AlbumArtist {
                    title: "Album 1".to_string(),
                    album_type: "album".to_string(),
                    ..Default::default()
                },
                AlbumArtist {
                    title: "Single 1".to_string(),
                    album_type: "single".to_string(),
                    ..Default::default()
                },
                AlbumArtist {
                    title: "Album 2".to_string(),
                    album_type: "album".to_string(),
                    ..Default::default()
                },
            ],
            ..Default::default()
        };

        let albums = artist.albums_only();
        assert_eq!(albums.len(), 2);
        assert!(albums.iter().all(|a| a.album_type == "album"));
    }

    #[test]
    fn test_albums_by_date() {
        let artist = Artist {
            albums: vec![
                AlbumArtist {
                    title: "Old Album".to_string(),
                    release_date: ReleaseDate {
                        year: 2010,
                        ..Default::default()
                    },
                    ..Default::default()
                },
                AlbumArtist {
                    title: "New Album".to_string(),
                    release_date: ReleaseDate {
                        year: 2023,
                        ..Default::default()
                    },
                    ..Default::default()
                },
            ],
            ..Default::default()
        };

        let sorted = artist.albums_by_date();
        assert_eq!(sorted[0].title, "New Album");
        assert_eq!(sorted[1].title, "Old Album");
    }
}
