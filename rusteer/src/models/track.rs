//! Track-related models.
//!
//! This module contains models for representing tracks and their
//! nested artist/album information.

use serde::{Deserialize, Serialize};

use super::common::{IDs, Image, ReleaseDate};

/// Artist when nested inside a track context.
///
/// Contains basic identifying information only.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct ArtistTrack {
    /// Type marker for serialization.
    #[serde(rename = "type", default = "default_artist_track_type")]
    pub type_: String,

    /// Artist name.
    pub name: String,

    /// Artist identifiers.
    pub ids: IDs,
}

fn default_artist_track_type() -> String {
    "artistTrack".to_string()
}

impl ArtistTrack {
    /// Create a new artist with name and Deezer ID.
    pub fn new<S1: Into<String>, S2: Into<String>>(name: S1, deezer_id: S2) -> Self {
        Self {
            type_: "artistTrack".to_string(),
            name: name.into(),
            ids: IDs::with_deezer(deezer_id),
        }
    }
}

/// Artist when nested inside a track in an album context.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct ArtistAlbumTrack {
    /// Type marker for serialization.
    #[serde(rename = "type", default = "default_artist_album_track_type")]
    pub type_: String,

    /// Artist name.
    pub name: String,

    /// Artist identifiers.
    pub ids: IDs,
}

fn default_artist_album_track_type() -> String {
    "artistAlbumTrack".to_string()
}

impl ArtistAlbumTrack {
    /// Create a new artist with name and Deezer ID.
    pub fn new<S1: Into<String>, S2: Into<String>>(name: S1, deezer_id: S2) -> Self {
        Self {
            type_: "artistAlbumTrack".to_string(),
            name: name.into(),
            ids: IDs::with_deezer(deezer_id),
        }
    }
}

/// Album when nested inside a track context.
///
/// Contains album metadata relevant to the track.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct AlbumTrack {
    /// Type marker for serialization.
    #[serde(rename = "type", default = "default_album_track_type")]
    pub type_: String,

    /// Album type: "album", "single", or "compilation".
    pub album_type: String,

    /// Album title.
    pub title: String,

    /// Release date.
    pub release_date: ReleaseDate,

    /// Total number of tracks in the album.
    pub total_tracks: u32,

    /// Total number of discs in the album.
    #[serde(default = "default_one")]
    pub total_discs: u32,

    /// Genres associated with the album.
    #[serde(default)]
    pub genres: Vec<String>,

    /// Cover images in various sizes.
    #[serde(default)]
    pub images: Vec<Image>,

    /// Album identifiers.
    pub ids: IDs,

    /// Album artists.
    #[serde(default)]
    pub artists: Vec<ArtistAlbumTrack>,
}

fn default_album_track_type() -> String {
    "albumTrack".to_string()
}

fn default_one() -> u32 {
    1
}

impl AlbumTrack {
    /// Get all artist names joined by a separator.
    pub fn artists_string(&self, separator: &str) -> String {
        self.artists
            .iter()
            .map(|a| a.name.as_str())
            .collect::<Vec<_>>()
            .join(separator)
    }
}

/// A full track record.
///
/// Contains complete track information including nested album and artist data.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct Track {
    /// Type marker for serialization.
    #[serde(rename = "type", default = "default_track_type")]
    pub type_: String,

    /// Track title.
    pub title: String,

    /// Disc number (1-indexed).
    #[serde(default = "default_one")]
    pub disc_number: u32,

    /// Track number on the disc (1-indexed).
    #[serde(default = "default_one")]
    pub track_number: u32,

    /// Duration in milliseconds.
    pub duration_ms: u64,

    /// Whether the track has explicit content.
    #[serde(default)]
    pub explicit: bool,

    /// Genres associated with the track.
    #[serde(default)]
    pub genres: Vec<String>,

    /// Album containing this track.
    pub album: AlbumTrack,

    /// Artists who performed this track.
    #[serde(default)]
    pub artists: Vec<ArtistTrack>,

    /// Track identifiers.
    pub ids: IDs,
}

fn default_track_type() -> String {
    "track".to_string()
}

impl Track {
    /// Get the primary artist name.
    pub fn primary_artist(&self) -> Option<&str> {
        self.artists.first().map(|a| a.name.as_str())
    }

    /// Get all artist names joined by a separator.
    pub fn artists_string(&self, separator: &str) -> String {
        self.artists
            .iter()
            .map(|a| a.name.as_str())
            .collect::<Vec<_>>()
            .join(separator)
    }

    /// Get duration formatted as MM:SS.
    pub fn duration_formatted(&self) -> String {
        let total_seconds = self.duration_ms / 1000;
        let minutes = total_seconds / 60;
        let seconds = total_seconds % 60;
        format!("{:02}:{:02}", minutes, seconds)
    }

    /// Get the Deezer track ID.
    pub fn deezer_id(&self) -> Option<&str> {
        self.ids.deezer.as_deref()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_track_duration_formatted() {
        let track = Track {
            duration_ms: 215000, // 3:35
            ..Default::default()
        };
        assert_eq!(track.duration_formatted(), "03:35");
    }

    #[test]
    fn test_track_artists_string() {
        let track = Track {
            artists: vec![
                ArtistTrack::new("Artist One", "1"),
                ArtistTrack::new("Artist Two", "2"),
            ],
            ..Default::default()
        };
        assert_eq!(track.artists_string(", "), "Artist One, Artist Two");
    }

    #[test]
    fn test_primary_artist() {
        let track = Track {
            artists: vec![ArtistTrack::new("Main Artist", "1")],
            ..Default::default()
        };
        assert_eq!(track.primary_artist(), Some("Main Artist"));
    }
}
