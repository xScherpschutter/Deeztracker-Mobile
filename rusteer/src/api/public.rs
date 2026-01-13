//! Public Deezer API client.
//!
//! This module provides a client for the public Deezer API (api.deezer.com).
//! No authentication is required for most operations.

use reqwest::Client;
use serde_json::Value;
use tracing::{debug, error, warn};

use crate::converters;
use crate::error::{DeezerError, Result};
use crate::models::{Album, Artist, Playlist, Track};

/// Base URL for the Deezer public API.
const API_BASE_URL: &str = "https://api.deezer.com/";

/// Cover image URL template.
const COVER_URL_TEMPLATE: &str =
    "https://e-cdns-images.dzcdn.net/images/cover/{md5}/{size}-000000-80-0-0.jpg";

/// Public Deezer API client.
///
/// Provides methods to query tracks, albums, playlists, and artists
/// without requiring authentication.
///
/// # Example
///
/// ```rust,no_run
/// use Rusteer::DeezerApi;
///
/// #[tokio::main]
/// async fn main() -> Result<(), Box<dyn std::error::Error>> {
///     let api = DeezerApi::new();
///     let track = api.get_track("3135556").await?;
///     println!("Track: {} by {}", track.title, track.artists_string(", "));
///     Ok(())
/// }
/// ```
#[derive(Debug, Clone)]
pub struct DeezerApi {
    client: Client,
    /// Cache for album data to avoid redundant requests.
    album_cache: std::sync::Arc<tokio::sync::RwLock<std::collections::HashMap<String, Value>>>,
}

impl Default for DeezerApi {
    fn default() -> Self {
        Self::new()
    }
}

impl DeezerApi {
    /// Create a new Deezer API client.
    pub fn new() -> Self {
        let client = Client::builder()
            .user_agent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()
            .expect("Failed to create HTTP client");

        Self {
            client,
            album_cache: std::sync::Arc::new(tokio::sync::RwLock::new(
                std::collections::HashMap::new(),
            )),
        }
    }

    /// Make a GET request to the Deezer API.
    async fn get_api(&self, endpoint: &str) -> Result<Value> {
        let url = format!("{}{}", API_BASE_URL, endpoint);
        debug!("GET {}", url);

        let response = self.client.get(&url).send().await?;
        let data: Value = response.json().await?;

        // Check for API errors
        if let Some(error) = data.get("error") {
            let error_msg = error
                .get("message")
                .and_then(|m| m.as_str())
                .unwrap_or("Unknown error");
            error!("Deezer API error: {}", error_msg);
            return Err(DeezerError::ApiError(error_msg.to_string()));
        }

        Ok(data)
    }

    /// Make a GET request with query parameters.
    async fn get_api_with_params(&self, endpoint: &str, params: &[(&str, &str)]) -> Result<Value> {
        let url = format!("{}{}", API_BASE_URL, endpoint);
        debug!("GET {} with params: {:?}", url, params);

        let response = self.client.get(&url).query(params).send().await?;
        let data: Value = response.json().await?;

        if let Some(error) = data.get("error") {
            let error_msg = error
                .get("message")
                .and_then(|m| m.as_str())
                .unwrap_or("Unknown error");
            error!("Deezer API error: {}", error_msg);
            return Err(DeezerError::ApiError(error_msg.to_string()));
        }

        Ok(data)
    }

    /// Get a track by ID.
    ///
    /// Also fetches full album data to enrich the track metadata.
    pub async fn get_track(&self, track_id: &str) -> Result<Track> {
        let mut track_json = self.get_api(&format!("track/{}", track_id)).await?;

        // Enrich with album data if available
        if let Some(album_id) = track_json
            .get("album")
            .and_then(|a| a.get("id"))
            .and_then(|id| id.as_u64())
        {
            let album_id_str = album_id.to_string();

            // Check cache first
            let cached = {
                let cache = self.album_cache.read().await;
                cache.get(&album_id_str).cloned()
            };

            let full_album = match cached {
                Some(album) => album,
                None => {
                    match self.get_api(&format!("album/{}", album_id)).await {
                        Ok(album_json) => {
                            // Cache the album
                            let mut cache = self.album_cache.write().await;
                            cache.insert(album_id_str.clone(), album_json.clone());
                            album_json
                        }
                        Err(e) => {
                            warn!("Could not fetch album data for enrichment: {}", e);
                            Value::Null
                        }
                    }
                }
            };

            // Enrich track with album data
            if !full_album.is_null() {
                // Collect data to add to track level first
                let genres_to_add = full_album.get("genres").cloned();
                let contributors_to_add = if !track_json
                    .get("contributors")
                    .map(|c| !c.is_null())
                    .unwrap_or(false)
                {
                    full_album.get("contributors").cloned()
                } else {
                    None
                };

                // Update album object
                if let Some(album) = track_json.get_mut("album") {
                    if let Some(album_obj) = album.as_object_mut() {
                        // Copy genres if available
                        if let Some(genres) = full_album.get("genres") {
                            album_obj.insert("genres".to_string(), genres.clone());
                        }

                        // Copy nb_tracks
                        if let Some(nb_tracks) = full_album.get("nb_tracks") {
                            album_obj.insert("nb_tracks".to_string(), nb_tracks.clone());
                        }

                        // Copy record_type
                        if let Some(record_type) = full_album.get("record_type") {
                            album_obj.insert("record_type".to_string(), record_type.clone());
                        }

                        // Copy contributors
                        if let Some(contributors) = full_album.get("contributors") {
                            album_obj.insert("contributors".to_string(), contributors.clone());
                        }
                    }
                }

                // Now update track level
                if let Some(track_obj) = track_json.as_object_mut() {
                    if let Some(genres) = genres_to_add {
                        track_obj.insert("genres".to_string(), genres);
                    }
                    if let Some(contributors) = contributors_to_add {
                        track_obj.insert("contributors".to_string(), contributors);
                    }
                }
            }
        }

        converters::parse_track(&track_json)
    }

    /// Get raw track JSON by ID or ISRC.
    ///
    /// Accepts numeric ID or "isrc:CODE" format.
    pub async fn get_track_json(&self, track_id_or_isrc: &str) -> Result<Value> {
        self.get_api(&format!("track/{}", track_id_or_isrc)).await
    }

    /// Get an album by ID.
    ///
    /// Handles pagination for albums with more than 25 tracks.
    pub async fn get_album(&self, album_id: &str) -> Result<Album> {
        let mut album_json = self.get_api(&format!("album/{}", album_id)).await?;

        // Check for API errors
        if album_json.get("error").is_some() {
            let error_msg = album_json
                .get("error")
                .and_then(|e| e.get("message"))
                .and_then(|m| m.as_str())
                .unwrap_or("Unknown error");
            return Err(DeezerError::AlbumNotFound(format!(
                "{}: {}",
                album_id, error_msg
            )));
        }

        let numeric_album_id =
            album_json
                .get("id")
                .and_then(|id| id.as_u64())
                .ok_or_else(|| {
                    DeezerError::AlbumNotFound(format!("Could not get numeric ID for {}", album_id))
                })?;

        // Fetch detailed tracks from dedicated endpoint
        let tracks_url = format!("album/{}/tracks?limit=100", numeric_album_id);
        let mut all_tracks = Vec::new();

        match self.get_api(&tracks_url).await {
            Ok(tracks_response) => {
                if let Some(data) = tracks_response.get("data").and_then(|d| d.as_array()) {
                    all_tracks.extend(data.iter().cloned());
                }

                // Handle pagination
                let mut next_url = tracks_response
                    .get("next")
                    .and_then(|n| n.as_str())
                    .map(|s| s.to_string());

                while let Some(url) = next_url {
                    match self.client.get(&url).send().await {
                        Ok(response) => match response.json::<Value>().await {
                            Ok(next_data) => {
                                if let Some(data) = next_data.get("data").and_then(|d| d.as_array())
                                {
                                    all_tracks.extend(data.iter().cloned());
                                }
                                next_url = next_data
                                    .get("next")
                                    .and_then(|n| n.as_str())
                                    .map(|s| s.to_string());
                            }
                            Err(e) => {
                                error!("Error parsing pagination response: {}", e);
                                break;
                            }
                        },
                        Err(e) => {
                            error!("Error fetching next page: {}", e);
                            break;
                        }
                    }
                }

                // Replace tracks in album JSON
                if let Some(tracks) = album_json.get_mut("tracks") {
                    if let Some(tracks_obj) = tracks.as_object_mut() {
                        tracks_obj.insert("data".to_string(), Value::Array(all_tracks.clone()));
                    }
                }

                debug!(
                    "Fetched {} detailed tracks for album {}",
                    all_tracks.len(),
                    numeric_album_id
                );
            }
            Err(e) => {
                warn!("Failed to fetch detailed tracks: {}", e);
                // Fall back to regular album tracks and handle pagination there
                let nb_tracks = album_json
                    .get("nb_tracks")
                    .and_then(|n| n.as_u64())
                    .unwrap_or(0);

                if nb_tracks > 25 {
                    if let Some(tracks) = album_json.get("tracks") {
                        if let Some(next) = tracks.get("next").and_then(|n| n.as_str()) {
                            let mut all_tracks: Vec<Value> = tracks
                                .get("data")
                                .and_then(|d| d.as_array())
                                .cloned()
                                .unwrap_or_default();

                            let mut next_url = Some(next.to_string());

                            while let Some(url) = next_url {
                                match self.client.get(&url).send().await {
                                    Ok(response) => match response.json::<Value>().await {
                                        Ok(next_data) => {
                                            if let Some(data) =
                                                next_data.get("data").and_then(|d| d.as_array())
                                            {
                                                all_tracks.extend(data.iter().cloned());
                                            }
                                            next_url = next_data
                                                .get("next")
                                                .and_then(|n| n.as_str())
                                                .map(|s| s.to_string());
                                        }
                                        Err(e) => {
                                            error!("Error parsing pagination: {}", e);
                                            break;
                                        }
                                    },
                                    Err(e) => {
                                        error!("Error fetching next page: {}", e);
                                        break;
                                    }
                                }
                            }

                            if let Some(tracks) = album_json.get_mut("tracks") {
                                if let Some(tracks_obj) = tracks.as_object_mut() {
                                    tracks_obj.insert("data".to_string(), Value::Array(all_tracks));
                                }
                            }
                        }
                    }
                }
            }
        }

        converters::parse_album(&album_json)
    }

    /// Get raw album JSON by ID or UPC.
    ///
    /// Accepts numeric ID or "upc:CODE" format.
    pub async fn get_album_json(&self, album_id_or_upc: &str) -> Result<Value> {
        self.get_api(&format!("album/{}", album_id_or_upc)).await
    }

    /// Get a playlist by ID.
    ///
    /// Handles pagination for large playlists.
    pub async fn get_playlist(&self, playlist_id: &str) -> Result<Playlist> {
        let mut playlist_json = self.get_api(&format!("playlist/{}", playlist_id)).await?;

        // Handle pagination for tracks
        if let Some(tracks) = playlist_json.get_mut("tracks") {
            if let Some(next) = tracks.get("next").and_then(|n| n.as_str()) {
                let mut all_tracks: Vec<Value> = tracks
                    .get("data")
                    .and_then(|d| d.as_array())
                    .cloned()
                    .unwrap_or_default();

                let mut next_url = Some(next.to_string());

                while let Some(url) = next_url {
                    match self.client.get(&url).send().await {
                        Ok(response) => match response.json::<Value>().await {
                            Ok(next_data) => {
                                if let Some(data) = next_data.get("data").and_then(|d| d.as_array())
                                {
                                    all_tracks.extend(data.iter().cloned());
                                }
                                next_url = next_data
                                    .get("next")
                                    .and_then(|n| n.as_str())
                                    .map(|s| s.to_string());
                            }
                            Err(e) => {
                                error!("Error parsing pagination: {}", e);
                                break;
                            }
                        },
                        Err(e) => {
                            error!("Error fetching next page: {}", e);
                            break;
                        }
                    }
                }

                if let Some(tracks_obj) = tracks.as_object_mut() {
                    tracks_obj.insert("data".to_string(), Value::Array(all_tracks));
                }
            }
        }

        converters::parse_playlist(&playlist_json)
    }

    /// Get an artist by ID.
    pub async fn get_artist(&self, artist_id: &str) -> Result<Artist> {
        let artist_json = self.get_api(&format!("artist/{}", artist_id)).await?;
        converters::parse_artist(&artist_json)
    }

    /// Get an artist's top tracks.
    pub async fn get_artist_top_tracks(&self, artist_id: &str, limit: u32) -> Result<Vec<Track>> {
        let response = self
            .get_api(&format!("artist/{}/top?limit={}", artist_id, limit))
            .await?;

        let tracks_data = response
            .get("data")
            .and_then(|d| d.as_array())
            .ok_or_else(|| DeezerError::NoDataApi("No tracks data".to_string()))?;

        tracks_data
            .iter()
            .map(|t| converters::parse_track(t))
            .collect()
    }

    /// Search for tracks.
    pub async fn search_tracks(&self, query: &str, limit: u32) -> Result<Vec<Track>> {
        let response = self
            .get_api_with_params(
                "search/track",
                &[("q", query), ("limit", &limit.to_string())],
            )
            .await?;

        let total = response.get("total").and_then(|t| t.as_u64()).unwrap_or(0);
        if total == 0 {
            return Err(DeezerError::NoDataApi(query.to_string()));
        }

        let tracks_data = response
            .get("data")
            .and_then(|d| d.as_array())
            .ok_or_else(|| DeezerError::NoDataApi("No tracks data".to_string()))?;

        tracks_data
            .iter()
            .filter_map(|t| converters::parse_track(t).ok())
            .collect::<Vec<_>>()
            .pipe(Ok)
    }

    /// Search for albums.
    pub async fn search_albums(&self, query: &str, limit: u32) -> Result<Vec<Album>> {
        let response = self
            .get_api_with_params(
                "search/album",
                &[("q", query), ("limit", &limit.to_string())],
            )
            .await?;

        let total = response.get("total").and_then(|t| t.as_u64()).unwrap_or(0);
        if total == 0 {
            return Err(DeezerError::NoDataApi(query.to_string()));
        }

        let albums_data = response
            .get("data")
            .and_then(|d| d.as_array())
            .ok_or_else(|| DeezerError::NoDataApi("No albums data".to_string()))?;

        albums_data
            .iter()
            .filter_map(|a| converters::parse_album(a).ok())
            .collect::<Vec<_>>()
            .pipe(Ok)
    }

    /// Search for playlists.
    pub async fn search_playlists(&self, query: &str, limit: u32) -> Result<Vec<Playlist>> {
        let response = self
            .get_api_with_params(
                "search/playlist",
                &[("q", query), ("limit", &limit.to_string())],
            )
            .await?;

        let total = response.get("total").and_then(|t| t.as_u64()).unwrap_or(0);
        if total == 0 {
            return Err(DeezerError::NoDataApi(query.to_string()));
        }

        let playlists_data = response
            .get("data")
            .and_then(|d| d.as_array())
            .ok_or_else(|| DeezerError::NoDataApi("No playlists data".to_string()))?;

        playlists_data
            .iter()
            .filter_map(|p| converters::parse_playlist(p).ok())
            .collect::<Vec<_>>()
            .pipe(Ok)
    }

    /// Get raw search results for tracks.
    pub async fn search_tracks_raw(&self, query: &str, limit: u32) -> Result<Vec<Value>> {
        let response = self
            .get_api_with_params(
                "search/track",
                &[("q", query), ("limit", &limit.to_string())],
            )
            .await?;

        let total = response.get("total").and_then(|t| t.as_u64()).unwrap_or(0);
        if total == 0 {
            return Err(DeezerError::NoDataApi(query.to_string()));
        }

        Ok(response
            .get("data")
            .and_then(|d| d.as_array())
            .cloned()
            .unwrap_or_default())
    }

    /// Get raw search results for albums.
    pub async fn search_albums_raw(&self, query: &str, limit: u32) -> Result<Vec<Value>> {
        let response = self
            .get_api_with_params(
                "search/album",
                &[("q", query), ("limit", &limit.to_string())],
            )
            .await?;

        let total = response.get("total").and_then(|t| t.as_u64()).unwrap_or(0);
        if total == 0 {
            return Err(DeezerError::NoDataApi(query.to_string()));
        }

        Ok(response
            .get("data")
            .and_then(|d| d.as_array())
            .cloned()
            .unwrap_or_default())
    }

    /// Get an episode by ID (for podcasts).
    pub async fn get_episode(&self, episode_id: &str) -> Result<Value> {
        self.get_api(&format!("episode/{}", episode_id)).await
    }

    /// Get the cover image URL for a given MD5 hash.
    pub fn get_image_url(md5_image: &str, size: &str) -> String {
        COVER_URL_TEMPLATE
            .replace("{md5}", md5_image)
            .replace("{size}", size)
    }

    /// Fetch cover image bytes.
    pub async fn get_image(&self, md5_image: &str, size: &str) -> Result<Vec<u8>> {
        let url = Self::get_image_url(md5_image, size);
        let response = self.client.get(&url).send().await?;
        let bytes = response.bytes().await?;

        // Check for empty/placeholder image (Deezer returns 13 bytes for missing covers)
        if bytes.len() == 13 {
            // Try default empty cover
            let default_url = Self::get_image_url("", size);
            let default_response = self.client.get(&default_url).send().await?;
            Ok(default_response.bytes().await?.to_vec())
        } else {
            Ok(bytes.to_vec())
        }
    }
}

/// Extension trait for pipe operations.
trait Pipe: Sized {
    fn pipe<F, R>(self, f: F) -> R
    where
        F: FnOnce(Self) -> R,
    {
        f(self)
    }
}

impl<T> Pipe for T {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_image_url_generation() {
        let url = DeezerApi::get_image_url("abcd1234", "1200x1200");
        assert!(url.contains("abcd1234"));
        assert!(url.contains("1200x1200"));
    }
}
