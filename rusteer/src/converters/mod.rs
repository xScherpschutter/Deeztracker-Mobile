//! JSON to model converters.
//!
//! This module provides functions to convert raw Deezer API JSON responses
//! into typed model structures.
//! 
use serde_json::Value;

use crate::error::{DeezerError, Result};
use crate::models::{
    album::{Album, AlbumArtist, ArtistTrackAlbum, TrackAlbum},
    artist::Artist,
    common::{IDs, Image, ReleaseDate, User},
    playlist::{
        AlbumTrackPlaylist, ArtistAlbumTrackPlaylist, ArtistTrackPlaylist, Playlist, TrackPlaylist,
    },
    track::{AlbumTrack, ArtistAlbumTrack, ArtistTrack, Track},
};

/// Parse a release date string into a ReleaseDate struct.
pub fn parse_release_date(date_str: &str) -> ReleaseDate {
    ReleaseDate::parse(date_str)
}

/// Extract images from cover/picture URLs in JSON.
pub fn extract_images(json: &Value) -> Vec<Image> {
    let mut images = Vec::new();

    // Cover images
    if let Some(url) = json.get("cover_small").and_then(|v| v.as_str()) {
        images.push(Image::new(url, 56, 56));
    }
    if let Some(url) = json.get("cover_medium").and_then(|v| v.as_str()) {
        images.push(Image::new(url, 250, 250));
    }
    if let Some(url) = json.get("cover_big").and_then(|v| v.as_str()) {
        images.push(Image::new(url, 500, 500));
    }
    if let Some(url) = json.get("cover_xl").and_then(|v| v.as_str()) {
        images.push(Image::new(url, 1000, 1000));
    }

    // Picture images (for artists, playlists)
    if let Some(url) = json.get("picture_small").and_then(|v| v.as_str()) {
        images.push(Image::new(url, 56, 56));
    }
    if let Some(url) = json.get("picture_medium").and_then(|v| v.as_str()) {
        images.push(Image::new(url, 250, 250));
    }
    if let Some(url) = json.get("picture_big").and_then(|v| v.as_str()) {
        images.push(Image::new(url, 500, 500));
    }
    if let Some(url) = json.get("picture_xl").and_then(|v| v.as_str()) {
        images.push(Image::new(url, 1000, 1000));
    }

    images
}

/// Extract genres from JSON.
fn extract_genres(json: &Value) -> Vec<String> {
    json.get("genres")
        .and_then(|g| g.get("data"))
        .and_then(|d| d.as_array())
        .map(|arr| {
            arr.iter()
                .filter_map(|g| g.get("name").and_then(|n| n.as_str()))
                .map(|s| s.to_string())
                .collect()
        })
        .unwrap_or_default()
}

/// Get string from JSON, returning empty string if not found.
fn get_str(json: &Value, key: &str) -> String {
    json.get(key)
        .and_then(|v| v.as_str())
        .unwrap_or("")
        .to_string()
}

/// Get string ID from JSON (handles both string and numeric IDs).
fn get_id(json: &Value, key: &str) -> Option<String> {
    json.get(key).map(|v| {
        if let Some(s) = v.as_str() {
            s.to_string()
        } else if let Some(n) = v.as_u64() {
            n.to_string()
        } else if let Some(n) = v.as_i64() {
            n.to_string()
        } else {
            v.to_string()
        }
    })
}

/// Get u32 from JSON.
fn get_u32(json: &Value, key: &str) -> u32 {
    json.get(key).and_then(|v| v.as_u64()).unwrap_or(0) as u32
}

/// Get u64 from JSON.
fn get_u64(json: &Value, key: &str) -> u64 {
    json.get(key).and_then(|v| v.as_u64()).unwrap_or(0)
}

/// Get bool from JSON.
fn get_bool(json: &Value, key: &str) -> bool {
    json.get(key).and_then(|v| v.as_bool()).unwrap_or(false)
}

/// Parse an artist in track context.
fn parse_artist_track(json: &Value) -> ArtistTrack {
    ArtistTrack {
        type_: "artistTrack".to_string(),
        name: get_str(json, "name"),
        ids: IDs::with_deezer(get_id(json, "id").unwrap_or_default()),
    }
}

/// Parse an artist in album-track context.
fn parse_artist_album_track(json: &Value) -> ArtistAlbumTrack {
    ArtistAlbumTrack {
        type_: "artistAlbumTrack".to_string(),
        name: get_str(json, "name"),
        ids: IDs::with_deezer(get_id(json, "id").unwrap_or_default()),
    }
}

/// Parse album data for track context.
fn parse_album_track(json: &Value) -> AlbumTrack {
    let mut artists = Vec::new();

    // Check for contributors first
    if let Some(contributors) = json.get("contributors").and_then(|c| c.as_array()) {
        // Look for main artists
        let main_artists: Vec<_> = contributors
            .iter()
            .filter(|c| c.get("role").and_then(|r| r.as_str()) == Some("Main"))
            .collect();

        let artists_to_use = if main_artists.is_empty() {
            contributors.iter().collect()
        } else {
            main_artists
        };

        for artist in artists_to_use {
            artists.push(parse_artist_album_track(artist));
        }
    }

    // If no contributors found, use artist field
    if artists.is_empty() {
        if let Some(artist) = json.get("artist") {
            artists.push(parse_artist_album_track(artist));
        }
    }

    AlbumTrack {
        type_: "albumTrack".to_string(),
        album_type: get_str(json, "record_type"),
        title: get_str(json, "title"),
        ids: IDs::with_deezer(get_id(json, "id").unwrap_or_default()),
        images: extract_images(json),
        release_date: parse_release_date(
            json.get("release_date")
                .and_then(|d| d.as_str())
                .unwrap_or(""),
        ),
        artists,
        total_tracks: get_u32(json, "nb_tracks"),
        total_discs: 1, // Will be calculated from tracks if needed
        genres: extract_genres(json),
    }
}

/// Parse a track from raw JSON.
pub fn parse_track(json: &Value) -> Result<Track> {
    let id = get_id(json, "id");
    if id.is_none() {
        return Err(DeezerError::ApiError("Missing track ID".to_string()));
    }

    // Parse artists
    let mut artists = Vec::new();
    if let Some(artist) = json.get("artist") {
        artists.push(parse_artist_track(artist));
    }

    // Add contributors
    if let Some(contributors) = json.get("contributors").and_then(|c| c.as_array()) {
        for contributor in contributors {
            let name = get_str(contributor, "name");
            // Skip duplicates
            if !artists.iter().any(|a| a.name == name) {
                artists.push(parse_artist_track(contributor));
            }
        }
    }

    // Parse album
    let album = json
        .get("album")
        .map(|a| parse_album_track(a))
        .unwrap_or_default();

    // Get track/disc position
    let track_number = json
        .get("track_position")
        .or_else(|| json.get("track_number"))
        .and_then(|v| v.as_u64())
        .unwrap_or(0) as u32;

    let disc_number = json
        .get("disk_number")
        .or_else(|| json.get("disc_number"))
        .and_then(|v| v.as_u64())
        .unwrap_or(1) as u32;

    Ok(Track {
        type_: "track".to_string(),
        title: get_str(json, "title"),
        disc_number,
        track_number,
        duration_ms: get_u64(json, "duration") * 1000,
        explicit: get_bool(json, "explicit_lyrics"),
        genres: extract_genres(json),
        album,
        artists,
        ids: IDs {
            deezer: id,
            isrc: json
                .get("isrc")
                .and_then(|v| v.as_str())
                .map(|s| s.to_string()),
            ..Default::default()
        },
    })
}

/// Parse an album from raw JSON.
pub fn parse_album(json: &Value) -> Result<Album> {
    let id = get_id(json, "id");
    if id.is_none() {
        return Err(DeezerError::ApiError("Missing album ID".to_string()));
    }

    // Parse album artists
    let mut artists = Vec::new();
    if let Some(contributors) = json.get("contributors").and_then(|c| c.as_array()) {
        let main_artists: Vec<_> = contributors
            .iter()
            .filter(|c| c.get("role").and_then(|r| r.as_str()) == Some("Main"))
            .collect();

        let artists_to_use = if main_artists.is_empty() {
            contributors.iter().collect()
        } else {
            main_artists
        };

        for artist in artists_to_use {
            artists.push(AlbumArtist {
                type_: "artistAlbum".to_string(),
                name: get_str(artist, "name"),
                genres: Vec::new(),
                ids: IDs::with_deezer(get_id(artist, "id").unwrap_or_default()),
            });
        }
    }

    if artists.is_empty() {
        if let Some(artist) = json.get("artist") {
            artists.push(AlbumArtist {
                type_: "artistAlbum".to_string(),
                name: get_str(artist, "name"),
                genres: Vec::new(),
                ids: IDs::with_deezer(get_id(artist, "id").unwrap_or_default()),
            });
        }
    }

    // Parse tracks
    let mut tracks = Vec::new();
    if let Some(tracks_data) = json
        .get("tracks")
        .and_then(|t| t.get("data"))
        .and_then(|d| d.as_array())
    {
        for track_data in tracks_data {
            // Parse track artists
            let mut track_artists = Vec::new();
            if let Some(artist) = track_data.get("artist") {
                track_artists.push(ArtistTrackAlbum {
                    type_: "artistTrackAlbum".to_string(),
                    name: get_str(artist, "name"),
                    ids: IDs::with_deezer(get_id(artist, "id").unwrap_or_default()),
                });
            }

            let track_number = track_data
                .get("track_position")
                .or_else(|| track_data.get("track_number"))
                .and_then(|v| v.as_u64())
                .unwrap_or(0) as u32;

            let disc_number = track_data
                .get("disk_number")
                .or_else(|| track_data.get("disc_number"))
                .and_then(|v| v.as_u64())
                .unwrap_or(1) as u32;

            tracks.push(TrackAlbum {
                type_: "trackAlbum".to_string(),
                title: get_str(track_data, "title"),
                duration_ms: get_u64(track_data, "duration") * 1000,
                explicit: get_bool(track_data, "explicit_lyrics"),
                track_number,
                disc_number,
                ids: IDs {
                    deezer: get_id(track_data, "id"),
                    isrc: track_data
                        .get("isrc")
                        .and_then(|v| v.as_str())
                        .map(|s| s.to_string()),
                    ..Default::default()
                },
                artists: track_artists,
                genres: Vec::new(),
            });
        }
    }

    // Calculate total discs
    let total_discs = tracks.iter().map(|t| t.disc_number).max().unwrap_or(1);

    Ok(Album {
        type_: "album".to_string(),
        album_type: get_str(json, "record_type"),
        title: get_str(json, "title"),
        release_date: parse_release_date(
            json.get("release_date")
                .and_then(|d| d.as_str())
                .unwrap_or(""),
        ),
        total_tracks: get_u32(json, "nb_tracks"),
        total_discs,
        genres: extract_genres(json),
        images: extract_images(json),
        copyrights: Vec::new(), // Deezer API doesn't provide this in the same way
        ids: IDs {
            deezer: id,
            upc: json
                .get("upc")
                .and_then(|v| v.as_str())
                .map(|s| s.to_string()),
            ..Default::default()
        },
        tracks,
        artists,
    })
}

/// Parse track for playlist context.
fn parse_track_playlist(json: &Value) -> Option<TrackPlaylist> {
    let id = get_id(json, "id")?;

    // Parse artists
    let mut artists = Vec::new();
    if let Some(artist) = json.get("artist") {
        artists.push(ArtistTrackPlaylist {
            type_: "artistTrackPlaylist".to_string(),
            name: get_str(artist, "name"),
            ids: IDs::with_deezer(get_id(artist, "id").unwrap_or_default()),
        });
    }

    // Add contributors
    if let Some(contributors) = json.get("contributors").and_then(|c| c.as_array()) {
        for contributor in contributors {
            let name = get_str(contributor, "name");
            if !artists.iter().any(|a| a.name == name) {
                artists.push(ArtistTrackPlaylist {
                    type_: "artistTrackPlaylist".to_string(),
                    name,
                    ids: IDs::with_deezer(get_id(contributor, "id").unwrap_or_default()),
                });
            }
        }
    }

    // Parse album
    let album_data = json.get("album").unwrap_or(&Value::Null);

    let mut album_artists = Vec::new();
    if let Some(artist) = album_data.get("artist") {
        album_artists.push(ArtistAlbumTrackPlaylist {
            type_: "artistAlbumTrackPlaylist".to_string(),
            name: get_str(artist, "name"),
            ids: IDs::with_deezer(get_id(artist, "id").unwrap_or_default()),
        });
    }

    let album = AlbumTrackPlaylist {
        type_: "albumTrackPlaylist".to_string(),
        title: get_str(album_data, "title"),
        ids: IDs::with_deezer(get_id(album_data, "id").unwrap_or_default()),
        images: extract_images(album_data),
        artists: album_artists,
        album_type: get_str(album_data, "record_type"),
        release_date: parse_release_date(
            album_data
                .get("release_date")
                .and_then(|d| d.as_str())
                .unwrap_or(""),
        ),
        total_tracks: get_u32(album_data, "nb_tracks"),
        total_discs: 1,
    };

    let disc_number = json
        .get("disk_number")
        .or_else(|| json.get("disc_number"))
        .and_then(|v| v.as_u64())
        .unwrap_or(1) as u32;

    let track_number = json
        .get("track_position")
        .or_else(|| json.get("track_number"))
        .and_then(|v| v.as_u64())
        .unwrap_or(0) as u32;

    Some(TrackPlaylist {
        type_: "trackPlaylist".to_string(),
        title: get_str(json, "title"),
        position: 0, // Position is set by the playlist
        duration_ms: get_u64(json, "duration") * 1000,
        artists,
        album,
        ids: IDs {
            deezer: Some(id),
            isrc: json
                .get("isrc")
                .and_then(|v| v.as_str())
                .map(|s| s.to_string()),
            ..Default::default()
        },
        disc_number,
        track_number,
        explicit: get_bool(json, "explicit_lyrics"),
    })
}

/// Parse a playlist from raw JSON.
pub fn parse_playlist(json: &Value) -> Result<Playlist> {
    let id = get_id(json, "id");
    if id.is_none() {
        return Err(DeezerError::ApiError("Missing playlist ID".to_string()));
    }

    // Parse owner/creator
    let creator = json.get("creator").unwrap_or(&Value::Null);
    let owner = User {
        name: get_str(creator, "name"),
        ids: IDs::with_deezer(get_id(creator, "id").unwrap_or_default()),
    };

    // Parse tracks
    let mut tracks = Vec::new();
    if let Some(tracks_data) = json
        .get("tracks")
        .and_then(|t| t.get("data"))
        .and_then(|d| d.as_array())
    {
        for (idx, track_data) in tracks_data.iter().enumerate() {
            if let Some(mut track) = parse_track_playlist(track_data) {
                track.position = idx as u32;
                tracks.push(track);
            }
        }
    }

    // Extract images
    let mut images = extract_images(json);

    // Use first track's album image if no playlist images
    if images.is_empty() {
        if let Some(first_track) = tracks.first() {
            images = first_track.album.images.clone();
        }
    }

    Ok(Playlist {
        type_: "playlist".to_string(),
        title: get_str(json, "title"),
        description: json
            .get("description")
            .and_then(|d| d.as_str())
            .map(|s| s.to_string()),
        owner,
        tracks,
        images,
        ids: IDs::with_deezer(id.unwrap_or_default()),
    })
}

/// Parse an artist from raw JSON.
pub fn parse_artist(json: &Value) -> Result<Artist> {
    let id = get_id(json, "id");
    if id.is_none() {
        return Err(DeezerError::ApiError("Missing artist ID".to_string()));
    }

    Ok(Artist {
        type_: "artist".to_string(),
        name: get_str(json, "name"),
        genres: Vec::new(), // Artist genres aren't typically available from basic endpoint
        images: extract_images(json),
        ids: IDs::with_deezer(id.unwrap_or_default()),
        albums: Vec::new(), // Would need separate API call for discography
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn test_parse_release_date() {
        let date = parse_release_date("2023-05-15");
        assert_eq!(date.year, 2023);
        assert_eq!(date.month, Some(5));
        assert_eq!(date.day, Some(15));
    }

    #[test]
    fn test_extract_images() {
        let json = json!({
            "cover_small": "http://example.com/small.jpg",
            "cover_xl": "http://example.com/xl.jpg"
        });

        let images = extract_images(&json);
        assert_eq!(images.len(), 2);
        assert_eq!(images[0].height, 56);
        assert_eq!(images[1].height, 1000);
    }

    #[test]
    fn test_parse_track() {
        let json = json!({
            "id": 12345,
            "title": "Test Track",
            "duration": 215,
            "explicit_lyrics": false,
            "track_position": 1,
            "disk_number": 1,
            "artist": {
                "id": 1,
                "name": "Test Artist"
            },
            "album": {
                "id": 100,
                "title": "Test Album",
                "record_type": "album"
            }
        });

        let track = parse_track(&json).unwrap();
        assert_eq!(track.title, "Test Track");
        assert_eq!(track.duration_ms, 215000);
        assert_eq!(track.track_number, 1);
        assert_eq!(track.artists[0].name, "Test Artist");
        assert_eq!(track.album.title, "Test Album");
    }

    #[test]
    fn test_parse_album() {
        let json = json!({
            "id": 100,
            "title": "Test Album",
            "record_type": "album",
            "release_date": "2023-01-01",
            "nb_tracks": 10,
            "artist": {
                "id": 1,
                "name": "Test Artist"
            },
            "tracks": {
                "data": [
                    {
                        "id": 1,
                        "title": "Track 1",
                        "duration": 180,
                        "track_position": 1,
                        "disk_number": 1,
                        "artist": {
                            "id": 1,
                            "name": "Test Artist"
                        }
                    }
                ]
            }
        });

        let album = parse_album(&json).unwrap();
        assert_eq!(album.title, "Test Album");
        assert_eq!(album.total_tracks, 10);
        assert_eq!(album.tracks.len(), 1);
        assert_eq!(album.tracks[0].title, "Track 1");
    }
}
