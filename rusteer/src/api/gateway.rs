//! Gateway API client for authenticated Deezer operations.
//!
//! This module provides a client for the Deezer Gateway API
//! (deezer.com/ajax/gw-light.php), which requires authentication
//! and provides access to additional endpoints.

use reqwest::{cookie::Jar, Client, Url};
use serde_json::{json, Value};
use std::sync::Arc;
use tracing::{error, info, warn};

use crate::error::{DeezerError, Result};

/// Gateway API private endpoint.
const GATEWAY_URL: &str = "https://www.deezer.com/ajax/gw-light.php";

/// Auth token endpoint (reserved for future use).
#[allow(dead_code)]
const AUTH_TOKEN_URL: &str = "https://api.deezer.com/auth/token";

/// Media URL endpoint.
const MEDIA_URL: &str = "https://media.deezer.com/v1/get_url";

/// Song server URL template.
const SONG_SERVER_URL: &str = "https://e-cdns-proxy-{n}.dzcdn.net/mobile/1/{hash}";

/// Default client ID for Deezer API (reserved for future use).
#[allow(dead_code)]
const CLIENT_ID: u32 = 172365;

/// Default client secret for Deezer API (reserved for future use).
#[allow(dead_code)]
const CLIENT_SECRET: &str = "fb0bec7ccc063dab0417eb7b0d847f34";

/// Gateway API client with authentication.
///
/// Provides authenticated access to Deezer's internal API for:
/// - Getting detailed song data
/// - Fetching lyrics
/// - Getting media URLs for downloading
///
/// # Authentication
///
/// Requires an ARL (Authentication Request Locator) token, which can be
/// obtained from a logged-in browser session.
///
/// # Example
///
/// ```rust,no_run
/// use Rusteer::GatewayApi;
///
/// #[tokio::main]
/// async fn main() -> Result<(), Box<dyn std::error::Error>> {
///     let gw = GatewayApi::new("your_arl_token_here").await?;
///     let song_data = gw.get_song_data("3135556").await?;
///     println!("Song: {:?}", song_data);
///     Ok(())
/// }
/// ```
#[derive(Debug)]
pub struct GatewayApi {
    client: Client,
    arl: String,
    api_token: String,
    license_token: String,
}

/// Song data from the Gateway API.
#[derive(Debug, Clone)]
pub struct SongData {
    /// Song ID.
    pub id: String,
    /// Song title.
    pub title: String,
    /// MD5 hash of the audio file origin.
    pub md5_origin: String,
    /// Media version.
    pub media_version: String,
    /// Track token for media URL requests.
    pub track_token: Option<String>,
    /// Whether the track is readable/available.
    pub readable: bool,
    /// Raw JSON data for additional fields.
    pub raw: Value,
}

/// Lyrics data from the Gateway API.
#[derive(Debug, Clone)]
pub struct Lyrics {
    /// Lyrics ID.
    pub id: String,
    /// Unsynced lyrics text.
    pub lyrics_text: Option<String>,
    /// Synced lyrics (with timestamps).
    pub lyrics_sync: Vec<SyncedLyric>,
    /// Copyright information.
    pub lyrics_copyrights: Option<String>,
    /// Raw JSON data.
    pub raw: Value,
}

/// A synced lyric line with timestamp.
#[derive(Debug, Clone)]
pub struct SyncedLyric {
    /// Line text.
    pub line: String,
    /// Timestamp in milliseconds.
    pub timestamp_ms: u64,
}

/// Media URL data.
#[derive(Debug, Clone)]
pub struct MediaUrl {
    /// The actual media URL.
    pub url: String,
    /// Format (e.g., "FLAC", "MP3_320").
    pub format: String,
    /// Cipher type (e.g., "BF_CBC_STRIPE").
    pub cipher: String,
}

impl GatewayApi {
    /// Create a new Gateway API client with an ARL token.
    ///
    /// This will:
    /// 1. Set the ARL cookie
    /// 2. Fetch user data to get the API token
    /// 3. Fetch the license token for media access
    ///
    /// # Errors
    ///
    /// Returns `BadCredentials` if the ARL token is invalid.
    pub async fn new(arl: &str) -> Result<Self> {
        // Create cookie jar and set ARL
        let jar = Arc::new(Jar::default());
        let url = "https://www.deezer.com".parse::<Url>().unwrap();
        jar.add_cookie_str(&format!("arl={}", arl), &url);

        let client = Client::builder()
            .user_agent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .cookie_provider(jar)
            .build()
            .map_err(|e| DeezerError::ApiError(format!("Failed to create client: {}", e)))?;

        let mut api = Self {
            client,
            arl: arl.to_string(),
            api_token: "null".to_string(),
            license_token: String::new(),
        };

        // Refresh tokens
        api.refresh_token().await?;

        Ok(api)
    }

    /// Refresh the API and license tokens.
    async fn refresh_token(&mut self) -> Result<()> {
        // First check if we're logged in
        let user_data = self.get_user_data().await?;

        let user_id = user_data
            .get("USER")
            .and_then(|u| u.get("USER_ID"))
            .and_then(|id| id.as_u64())
            .unwrap_or(0);

        if user_id == 0 {
            return Err(DeezerError::BadCredentials(
                "ARL token is invalid or expired".to_string(),
            ));
        }

        // Get API token
        self.api_token = user_data
            .get("checkForm")
            .and_then(|t| t.as_str())
            .unwrap_or("null")
            .to_string();

        // Get license token
        self.license_token = user_data
            .get("USER")
            .and_then(|u| u.get("OPTIONS"))
            .and_then(|o| o.get("license_token"))
            .and_then(|t| t.as_str())
            .unwrap_or("")
            .to_string();

        info!(
            "Gateway API authenticated. User ID: {}, has license token: {}",
            user_id,
            !self.license_token.is_empty()
        );

        Ok(())
    }

    /// Make a request to the Gateway API.
    async fn call_api(&self, method: &str, json_data: Option<Value>) -> Result<Value> {
        let params = [
            ("api_version", "1.0"),
            ("api_token", &self.api_token),
            ("input", "3"),
            ("method", method),
        ];

        // Always send a JSON body - empty object if no data
        // This is required because Deezer returns 411 Length Required without Content-Length
        let body = json_data.unwrap_or_else(|| json!({}));

        let response = self
            .client
            .post(GATEWAY_URL)
            .query(&params)
            .json(&body)
            .send()
            .await?;
        let status = response.status();
        let text = response.text().await?;

        // Try to parse as JSON
        let result: Value = match serde_json::from_str(&text) {
            Ok(v) => v,
            Err(e) => {
                // Log the first 500 chars of response for debugging
                let preview = if text.len() > 500 {
                    format!("{}...", &text[..500])
                } else {
                    text.clone()
                };
                error!(
                    "Failed to parse Gateway response (status {}): {}",
                    status, preview
                );
                return Err(DeezerError::ApiError(format!(
                    "Invalid JSON response (status {}): {}",
                    status, e
                )));
            }
        };

        // Extract results
        let results = result.get("results").cloned().unwrap_or(Value::Null);

        if results.is_null() {
            // Check for errors
            if let Some(error) = result.get("error") {
                let error_msg = error.to_string();
                error!("Gateway API error: {}", error_msg);
                return Err(DeezerError::ApiError(error_msg));
            }
        }

        Ok(results)
    }

    /// Get user data (includes checkForm token and license token).
    async fn get_user_data(&self) -> Result<Value> {
        self.call_api("deezer.getUserData", None).await
    }

    /// Get detailed song data.
    pub async fn get_song_data(&self, song_id: &str) -> Result<SongData> {
        let json_data = json!({
            "sng_id": song_id
        });

        let result = self.call_api("song.getData", Some(json_data)).await?;

        if result.is_null() {
            return Err(DeezerError::TrackNotFound(song_id.to_string()));
        }

        Ok(SongData {
            id: result
                .get("SNG_ID")
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string(),
            title: result
                .get("SNG_TITLE")
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string(),
            md5_origin: result
                .get("MD5_ORIGIN")
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string(),
            media_version: result
                .get("MEDIA_VERSION")
                .and_then(|v| v.as_str())
                .unwrap_or("1")
                .to_string(),
            track_token: result
                .get("TRACK_TOKEN")
                .and_then(|v| v.as_str())
                .map(|s| s.to_string()),
            readable: !result
                .get("MD5_ORIGIN")
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .is_empty(),
            raw: result,
        })
    }

    /// Get album data (list of songs).
    pub async fn get_album_data(&self, album_id: &str) -> Result<Value> {
        let json_data = json!({
            "alb_id": album_id,
            "nb": -1
        });

        self.call_api("song.getListByAlbum", Some(json_data)).await
    }

    /// Get playlist data (list of songs).
    pub async fn get_playlist_data(&self, playlist_id: &str) -> Result<Value> {
        let json_data = json!({
            "playlist_id": playlist_id,
            "nb": -1
        });

        self.call_api("playlist.getSongs", Some(json_data)).await
    }

    /// Get lyrics for a song.
    pub async fn get_lyrics(&self, song_id: &str) -> Result<Lyrics> {
        let json_data = json!({
            "sng_id": song_id
        });

        let result = self.call_api("song.getLyrics", Some(json_data)).await?;

        if result.is_null() {
            return Err(DeezerError::NoDataApi(format!(
                "No lyrics for song {}",
                song_id
            )));
        }

        let synced = result
            .get("LYRICS_SYNC_JSON")
            .and_then(|s| s.as_array())
            .map(|arr| {
                arr.iter()
                    .filter_map(|line| {
                        let text = line.get("line")?.as_str()?;
                        let timestamp = line
                            .get("milliseconds")
                            .and_then(|m| m.as_str())
                            .and_then(|s| s.parse().ok())
                            .unwrap_or(0);
                        Some(SyncedLyric {
                            line: text.to_string(),
                            timestamp_ms: timestamp,
                        })
                    })
                    .collect()
            })
            .unwrap_or_default();

        Ok(Lyrics {
            id: result
                .get("LYRICS_ID")
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string(),
            lyrics_text: result
                .get("LYRICS_TEXT")
                .and_then(|v| v.as_str())
                .map(|s| s.to_string()),
            lyrics_sync: synced,
            lyrics_copyrights: result
                .get("LYRICS_COPYRIGHTS")
                .and_then(|v| v.as_str())
                .map(|s| s.to_string()),
            raw: result,
        })
    }

    /// Get page track data (additional track metadata).
    pub async fn get_page_track(&self, song_id: &str) -> Result<Value> {
        let json_data = json!({
            "sng_id": song_id
        });

        self.call_api("deezer.pageTrack", Some(json_data)).await
    }

    /// Get episode data (for podcasts).
    pub async fn get_episode_data(&self, episode_id: &str) -> Result<Value> {
        let json_data = json!({
            "episode_id": episode_id
        });

        let mut result = self.call_api("episode.getData", Some(json_data)).await?;

        // Add compatibility fields for download
        if let Some(obj) = result.as_object_mut() {
            obj.insert("MEDIA_VERSION".to_string(), json!("1"));

            if let Some(episode_id) = obj.get("EPISODE_ID").cloned() {
                obj.insert("SNG_ID".to_string(), episode_id);
            }

            if obj.contains_key("EPISODE_DIRECT_STREAM_URL") {
                obj.insert("MD5_ORIGIN".to_string(), json!("episode"));
            }
        }

        Ok(result)
    }

    /// Get media URLs for downloading.
    ///
    /// # Arguments
    ///
    /// * `track_tokens` - List of track tokens from song data
    /// * `quality` - Quality format (e.g., "FLAC", "MP3_320", "MP3_128")
    ///
    /// # Errors
    ///
    /// Returns `NoRightOnMedia` if the user doesn't have access to the requested quality.
    pub async fn get_media_url(
        &self,
        track_tokens: &[String],
        quality: &str,
    ) -> Result<Vec<MediaUrl>> {
        let json_data = json!({
            "license_token": self.license_token,
            "media": [
                {
                    "type": "FULL",
                    "formats": [
                        {
                            "cipher": "BF_CBC_STRIPE",
                            "format": quality
                        }
                    ]
                }
            ],
            "track_tokens": track_tokens
        });

        let response = self.client.post(MEDIA_URL).json(&json_data).send().await?;

        let result: Value = response.json().await?;

        // Check for errors
        if let Some(errors) = result.get("errors").and_then(|e| e.as_array()) {
            if let Some(first_error) = errors.first() {
                let msg = first_error
                    .get("message")
                    .and_then(|m| m.as_str())
                    .unwrap_or("Unknown error");
                return Err(DeezerError::NoRightOnMedia(msg.to_string()));
            }
        }

        let media_data = result
            .get("data")
            .and_then(|d| d.as_array())
            .ok_or_else(|| DeezerError::NoDataApi("No media data".to_string()))?;

        let mut urls = Vec::new();

        for item in media_data {
            if let Some(media_arr) = item.get("media").and_then(|m| m.as_array()) {
                for media in media_arr {
                    if let Some(sources) = media.get("sources").and_then(|s| s.as_array()) {
                        for source in sources {
                            if let Some(url) = source.get("url").and_then(|u| u.as_str()) {
                                urls.push(MediaUrl {
                                    url: url.to_string(),
                                    format: media
                                        .get("format")
                                        .and_then(|f| f.as_str())
                                        .unwrap_or("")
                                        .to_string(),
                                    cipher: media
                                        .get("cipher")
                                        .and_then(|c| c.get("type"))
                                        .and_then(|t| t.as_str())
                                        .unwrap_or("BF_CBC_STRIPE")
                                        .to_string(),
                                });
                            }
                        }
                    }
                }
            }
        }

        Ok(urls)
    }

    /// Generate a legacy song URL.
    ///
    /// # Arguments
    ///
    /// * `n` - Server number (0-7)
    /// * `song_hash` - Song hash generated from MD5 and song data
    pub fn get_song_url(n: u8, song_hash: &str) -> String {
        SONG_SERVER_URL
            .replace("{n}", &n.to_string())
            .replace("{hash}", song_hash)
    }

    /// Check if a song URL is accessible.
    pub async fn song_exists(&self, song_url: &str) -> Result<bool> {
        // Special handling for Spreaker URLs
        if song_url.contains("spreaker.com") {
            let response = self.client.get(song_url).send().await?;
            return Ok(response.status().is_success());
        }

        match self.client.get(song_url).send().await {
            Ok(response) => {
                let bytes = response.bytes().await?;
                if bytes.is_empty() {
                    return Err(DeezerError::TrackNotFound(song_url.to_string()));
                }
                Ok(true)
            }
            Err(e) => {
                warn!("Failed to check song URL {}: {}", song_url, e);

                // Try fallback DNS across dzcdn proxy hosts
                if song_url.contains("e-cdns-proxy-") {
                    for i in 0..8 {
                        let fallback_url = song_url.replacen(
                            &format!("e-cdns-proxy-{}", i),
                            &format!("e-cdns-proxy-{}", (i + 1) % 8),
                            1,
                        );
                        if let Ok(response) = self.client.get(&fallback_url).send().await {
                            if let Ok(bytes) = response.bytes().await {
                                if !bytes.is_empty() {
                                    return Ok(true);
                                }
                            }
                        }
                    }
                }

                Err(DeezerError::TrackNotFound(song_url.to_string()))
            }
        }
    }

    /// Check if the client is authenticated.
    pub async fn is_logged_in(&self) -> bool {
        match self.get_user_data().await {
            Ok(data) => {
                data.get("USER")
                    .and_then(|u| u.get("USER_ID"))
                    .and_then(|id| id.as_u64())
                    .unwrap_or(0)
                    != 0
            }
            Err(_) => false,
        }
    }

    /// Get the current user's ARL token.
    pub fn arl(&self) -> &str {
        &self.arl
    }

    /// Check if we have a license token (premium access).
    pub fn has_license_token(&self) -> bool {
        !self.license_token.is_empty()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_song_url_generation() {
        let url = GatewayApi::get_song_url(2, "abc123");
        assert!(url.contains("e-cdns-proxy-2"));
        assert!(url.contains("abc123"));
    }
}
