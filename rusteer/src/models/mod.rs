//! Data models for Deezer API responses.
//!
//! This module contains all the data structures used to represent
//! tracks, albums, playlists, artists, and related metadata.

pub mod album;
pub mod artist;
pub mod common;
pub mod playlist;
pub mod track;

// Re-exports for convenience
pub use album::{Album, AlbumArtist, TrackAlbum};
pub use artist::{AlbumArtist as ArtistAlbum, Artist};
pub use common::{IDs, Image, Quality, ReleaseDate};
pub use playlist::{Playlist, TrackPlaylist};
pub use track::{AlbumTrack, ArtistTrack, Track};
