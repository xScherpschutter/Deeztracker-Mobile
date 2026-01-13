//! # Rusteer
//!
//! A Rust library for downloading music and fetching metadata from Deezer.
//!
//! ## Quick Start
//!
//! The easiest way to use this library is through the [`Rusteer`] struct:
//!
//! ```rust,no_run
//! use rusteer::Rusteer;
//!
//! #[tokio::main]
//! async fn main() -> Result<(), Box<dyn std::error::Error>> {
//!     // Create instance with ARL token
//!     let dz = Rusteer::new("your_arl_token").await?;
//!
//!     // Download a single track
//!     let result = dz.download_track("3135556").await?;
//!     println!("Downloaded: {}", result.path.display());
//!
//!     // Download an entire album
//!     let album_result = dz.download_album("302127").await?;
//!     println!("Downloaded {} tracks", album_result.successful.len());
//!
//!     // Get metadata only
//!     let album = dz.get_album("302127").await?;
//!     println!("Album: {} ({} tracks)", album.title, album.total_tracks);
//!
//!     Ok(())
//! }
//! ```
//!
//! ## Features
//!
//! - **Download tracks, albums, and playlists** with automatic decryption
//! - **Multiple quality options**: FLAC, MP3 320, MP3 128
//! - **Metadata fetching** for tracks, albums, playlists, and artists
//! - **Search** for tracks and albums
//!
//! ## Low-Level APIs
//!
//! For more control, you can use the lower-level APIs directly:
//!
//! - [`DeezerApi`] - Public API for metadata (no auth required)
//! - [`GatewayApi`] - Private API for downloads (requires ARL token)
//! - [`crypto`] - Decryption utilities

pub mod api;
pub mod converters;
pub mod crypto;
pub mod error;
pub mod models;
mod rusteer;
pub mod tagging;

// Main interface (recommended)
pub use rusteer::{BatchDownloadResult, DownloadQuality, DownloadResult, Rusteer};

// Low-level APIs
pub use api::{DeezerApi, GatewayApi};
pub use error::DeezerError;
pub use models::{Album, Artist, Playlist, Track};

pub mod bindings;
uniffi::setup_scaffolding!();
