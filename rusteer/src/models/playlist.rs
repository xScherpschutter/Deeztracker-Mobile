//! Playlist-related models.
//!
//! This module contains models for representing playlists and their
//! nested tracks.

use serde::{Deserialize, Serialize};

use super::common::{IDs, Image, ReleaseDate, User};

/// Artist when nested inside a track in a playlist context.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct ArtistTrackPlaylist {
    /// Type marker for serialization.
    #[serde(rename = "type", default = "default_artist_track_playlist_type")]
    pub type_: String,

    /// Artist name.
    pub name: String,

    /// Artist identifiers.
    pub ids: IDs,
}

fn default_artist_track_playlist_type() -> String {
    "artistTrackPlaylist".to_string()
}

impl ArtistTrackPlaylist {
    /// Create a new artist with name and Deezer ID.
    pub fn new<S1: Into<String>, S2: Into<String>>(name: S1, deezer_id: S2) -> Self {
        Self {
            type_: "artistTrackPlaylist".to_string(),
            name: name.into(),
            ids: IDs::with_deezer(deezer_id),
        }
    }
}

/// Artist when nested inside an album in a track in a playlist context.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct ArtistAlbumTrackPlaylist {
    /// Type marker for serialization.
    #[serde(rename = "type", default = "default_artist_album_track_playlist_type")]
    pub type_: String,

    /// Artist name.
    pub name: String,

    /// Artist identifiers.
    pub ids: IDs,
}

fn default_artist_album_track_playlist_type() -> String {
    "artistAlbumTrackPlaylist".to_string()
}

impl ArtistAlbumTrackPlaylist {
    /// Create a new artist with name and Deezer ID.
    pub fn new<S1: Into<String>, S2: Into<String>>(name: S1, deezer_id: S2) -> Self {
        Self {
            type_: "artistAlbumTrackPlaylist".to_string(),
            name: name.into(),
            ids: IDs::with_deezer(deezer_id),
        }
    }
}

/// Album when nested inside a track in a playlist context.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct AlbumTrackPlaylist {
    /// Type marker for serialization.
    #[serde(rename = "type", default = "default_album_track_playlist_type")]
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

    /// Cover images in various sizes.
    #[serde(default)]
    pub images: Vec<Image>,

    /// Album identifiers.
    pub ids: IDs,

    /// Album artists.
    #[serde(default)]
    pub artists: Vec<ArtistAlbumTrackPlaylist>,
}

fn default_album_track_playlist_type() -> String {
    "albumTrackPlaylist".to_string()
}

fn default_one() -> u32 {
    1
}

/// Track when nested inside a playlist context.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct TrackPlaylist {
    /// Type marker for serialization.
    #[serde(rename = "type", default = "default_track_playlist_type")]
    pub type_: String,

    /// Track title.
    pub title: String,

    /// Position in the playlist (0-indexed).
    #[serde(default)]
    pub position: u32,

    /// Duration in milliseconds.
    pub duration_ms: u64,

    /// Artists who performed this track.
    #[serde(default)]
    pub artists: Vec<ArtistTrackPlaylist>,

    /// Album containing this track.
    pub album: AlbumTrackPlaylist,

    /// Track identifiers.
    pub ids: IDs,

    /// Disc number (1-indexed).
    #[serde(default = "default_one")]
    pub disc_number: u32,

    /// Track number on the disc (1-indexed).
    #[serde(default = "default_one")]
    pub track_number: u32,

    /// Whether the track has explicit content.
    #[serde(default)]
    pub explicit: bool,
}

fn default_track_playlist_type() -> String {
    "trackPlaylist".to_string()
}

impl TrackPlaylist {
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
}

/// A user-curated playlist.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct Playlist {
    /// Type marker for serialization.
    #[serde(rename = "type", default = "default_playlist_type")]
    pub type_: String,

    /// Playlist title.
    pub title: String,

    /// Playlist description.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub description: Option<String>,

    /// Playlist owner.
    pub owner: User,

    /// Tracks in the playlist.
    #[serde(default)]
    pub tracks: Vec<TrackPlaylist>,

    /// Playlist cover images.
    #[serde(default)]
    pub images: Vec<Image>,

    /// Playlist identifiers.
    pub ids: IDs,
}

fn default_playlist_type() -> String {
    "playlist".to_string()
}

impl Playlist {
    /// Get the Deezer playlist ID.
    pub fn deezer_id(&self) -> Option<&str> {
        self.ids.deezer.as_deref()
    }

    /// Get total duration of all tracks in milliseconds.
    pub fn total_duration_ms(&self) -> u64 {
        self.tracks.iter().map(|t| t.duration_ms).sum()
    }

    /// Get the number of tracks in the playlist.
    pub fn track_count(&self) -> usize {
        self.tracks.len()
    }

    /// Get the largest cover image available.
    pub fn largest_image(&self) -> Option<&Image> {
        self.images.iter().max_by_key(|img| img.width * img.height)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_playlist_total_duration() {
        let playlist = Playlist {
            tracks: vec![
                TrackPlaylist {
                    duration_ms: 200000,
                    ..Default::default()
                },
                TrackPlaylist {
                    duration_ms: 300000,
                    ..Default::default()
                },
            ],
            ..Default::default()
        };
        assert_eq!(playlist.total_duration_ms(), 500000);
    }

    #[test]
    fn test_playlist_track_count() {
        let playlist = Playlist {
            tracks: vec![
                TrackPlaylist::default(),
                TrackPlaylist::default(),
                TrackPlaylist::default(),
            ],
            ..Default::default()
        };
        assert_eq!(playlist.track_count(), 3);
    }

    #[test]
    fn test_track_playlist_artists_string() {
        let track = TrackPlaylist {
            artists: vec![
                ArtistTrackPlaylist::new("Artist 1", "1"),
                ArtistTrackPlaylist::new("Artist 2", "2"),
            ],
            ..Default::default()
        };
        assert_eq!(track.artists_string("; "), "Artist 1; Artist 2");
    }
}
