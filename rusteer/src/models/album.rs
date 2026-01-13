//! Album-related models.
//!
//! This module contains models for representing albums and their
//! nested tracks and artist information.

use serde::{Deserialize, Serialize};

use super::common::{IDs, Image, ReleaseDate};

/// Artist when nested inside an album context.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct AlbumArtist {
    /// Type marker for serialization.
    #[serde(rename = "type", default = "default_artist_album_type")]
    pub type_: String,

    /// Artist name.
    pub name: String,

    /// Genres associated with the artist.
    #[serde(default)]
    pub genres: Vec<String>,

    /// Artist identifiers.
    pub ids: IDs,
}

fn default_artist_album_type() -> String {
    "artistAlbum".to_string()
}

impl AlbumArtist {
    /// Create a new album artist with name and Deezer ID.
    pub fn new<S1: Into<String>, S2: Into<String>>(name: S1, deezer_id: S2) -> Self {
        Self {
            type_: "artistAlbum".to_string(),
            name: name.into(),
            genres: Vec::new(),
            ids: IDs::with_deezer(deezer_id),
        }
    }
}

/// Artist when nested inside a track in an album context.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct ArtistTrackAlbum {
    /// Type marker for serialization.
    #[serde(rename = "type", default = "default_artist_track_album_type")]
    pub type_: String,

    /// Artist name.
    pub name: String,

    /// Artist identifiers.
    pub ids: IDs,
}

fn default_artist_track_album_type() -> String {
    "artistTrackAlbum".to_string()
}

impl ArtistTrackAlbum {
    /// Create a new artist with name and Deezer ID.
    pub fn new<S1: Into<String>, S2: Into<String>>(name: S1, deezer_id: S2) -> Self {
        Self {
            type_: "artistTrackAlbum".to_string(),
            name: name.into(),
            ids: IDs::with_deezer(deezer_id),
        }
    }
}

/// Track when nested inside an album context.
///
/// Contains track metadata without the full album info (since it's implicit).
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct TrackAlbum {
    /// Type marker for serialization.
    #[serde(rename = "type", default = "default_track_album_type")]
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

    /// Track identifiers.
    pub ids: IDs,

    /// Artists who performed this track.
    #[serde(default)]
    pub artists: Vec<ArtistTrackAlbum>,
}

fn default_track_album_type() -> String {
    "trackAlbum".to_string()
}

fn default_one() -> u32 {
    1
}

impl TrackAlbum {
    /// Get the primary artist name.
    pub fn primary_artist(&self) -> Option<&str> {
        self.artists.first().map(|a| a.name.as_str())
    }

    /// Get duration formatted as MM:SS.
    pub fn duration_formatted(&self) -> String {
        let total_seconds = self.duration_ms / 1000;
        let minutes = total_seconds / 60;
        let seconds = total_seconds % 60;
        format!("{:02}:{:02}", minutes, seconds)
    }
}

/// Copyright information for an album.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct Copyright {
    /// Copyright text.
    pub text: String,

    /// Copyright type (e.g., "C" for copyright, "P" for phonogram).
    #[serde(rename = "type")]
    pub type_: String,
}

/// A full album record.
///
/// Contains complete album information including nested tracks and artist data.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct Album {
    /// Type marker for serialization.
    #[serde(rename = "type", default = "default_album_type")]
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

    /// Copyright information.
    #[serde(default)]
    pub copyrights: Vec<Copyright>,

    /// Album identifiers.
    pub ids: IDs,

    /// Tracks in the album.
    #[serde(default)]
    pub tracks: Vec<TrackAlbum>,

    /// Album artists.
    #[serde(default)]
    pub artists: Vec<AlbumArtist>,
}

fn default_album_type() -> String {
    "album".to_string()
}

impl Album {
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

    /// Get total duration of all tracks in milliseconds.
    pub fn total_duration_ms(&self) -> u64 {
        self.tracks.iter().map(|t| t.duration_ms).sum()
    }

    /// Get the Deezer album ID.
    pub fn deezer_id(&self) -> Option<&str> {
        self.ids.deezer.as_deref()
    }

    /// Get the largest cover image available.
    pub fn largest_image(&self) -> Option<&Image> {
        self.images.iter().max_by_key(|img| img.width * img.height)
    }

    /// Get tracks for a specific disc.
    pub fn tracks_for_disc(&self, disc_number: u32) -> Vec<&TrackAlbum> {
        self.tracks
            .iter()
            .filter(|t| t.disc_number == disc_number)
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_album_total_duration() {
        let album = Album {
            tracks: vec![
                TrackAlbum {
                    duration_ms: 180000,
                    ..Default::default()
                },
                TrackAlbum {
                    duration_ms: 240000,
                    ..Default::default()
                },
            ],
            ..Default::default()
        };
        assert_eq!(album.total_duration_ms(), 420000);
    }

    #[test]
    fn test_album_artists_string() {
        let album = Album {
            artists: vec![
                AlbumArtist::new("Artist A", "1"),
                AlbumArtist::new("Artist B", "2"),
            ],
            ..Default::default()
        };
        assert_eq!(album.artists_string(" & "), "Artist A & Artist B");
    }

    #[test]
    fn test_tracks_for_disc() {
        let album = Album {
            tracks: vec![
                TrackAlbum {
                    title: "Track 1".to_string(),
                    disc_number: 1,
                    ..Default::default()
                },
                TrackAlbum {
                    title: "Track 2".to_string(),
                    disc_number: 2,
                    ..Default::default()
                },
                TrackAlbum {
                    title: "Track 3".to_string(),
                    disc_number: 1,
                    ..Default::default()
                },
            ],
            ..Default::default()
        };

        let disc1_tracks = album.tracks_for_disc(1);
        assert_eq!(disc1_tracks.len(), 2);
        assert_eq!(disc1_tracks[0].title, "Track 1");
        assert_eq!(disc1_tracks[1].title, "Track 3");
    }
}
