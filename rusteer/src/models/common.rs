//! Common types shared across all models.

use serde::{Deserialize, Serialize};

/// Identifiers for Deezer content.
///
/// Different fields are populated depending on the type of content.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct IDs {
    /// Deezer ID.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub deezer: Option<String>,

    /// International Standard Recording Code (for tracks).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub isrc: Option<String>,

    /// Universal Product Code (for albums).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub upc: Option<String>,
}

impl IDs {
    /// Create new IDs with just a Deezer ID.
    pub fn with_deezer<S: Into<String>>(deezer_id: S) -> Self {
        Self {
            deezer: Some(deezer_id.into()),
            ..Default::default()
        }
    }

    /// Create new IDs with Deezer ID and ISRC.
    pub fn with_deezer_and_isrc<S1: Into<String>, S2: Into<String>>(
        deezer_id: S1,
        isrc: S2,
    ) -> Self {
        Self {
            deezer: Some(deezer_id.into()),
            isrc: Some(isrc.into()),
            ..Default::default()
        }
    }
}

/// Release date structure.
///
/// Not all fields may be available; year is always present when known,
/// but month and day may be unknown.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct ReleaseDate {
    /// Year of release.
    pub year: i32,

    /// Month of release (1-12), if known.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub month: Option<i32>,

    /// Day of release (1-31), if known.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub day: Option<i32>,
}

impl ReleaseDate {
    /// Parse a date string in "YYYY-MM-DD" format.
    pub fn parse(date_str: &str) -> Self {
        if date_str.is_empty() {
            return Self::default();
        }

        let parts: Vec<&str> = date_str.split('-').collect();

        Self {
            year: parts.first().and_then(|s| s.parse().ok()).unwrap_or(0),
            month: parts.get(1).and_then(|s| s.parse().ok()),
            day: parts.get(2).and_then(|s| s.parse().ok()),
        }
    }

    /// Format as "YYYY-MM-DD" string.
    pub fn to_string(&self) -> String {
        match (self.month, self.day) {
            (Some(m), Some(d)) => format!("{:04}-{:02}-{:02}", self.year, m, d),
            (Some(m), None) => format!("{:04}-{:02}", self.year, m),
            _ => format!("{:04}", self.year),
        }
    }
}

/// Image with URL and dimensions.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct Image {
    /// URL to the image.
    pub url: String,

    /// Height in pixels.
    pub height: u32,

    /// Width in pixels.
    pub width: u32,
}

impl Image {
    /// Create a new image.
    pub fn new<S: Into<String>>(url: S, height: u32, width: u32) -> Self {
        Self {
            url: url.into(),
            height,
            width,
        }
    }
}

/// User information (for playlist owners, etc.).
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct User {
    /// Display name of the user.
    pub name: String,

    /// User identifiers.
    pub ids: IDs,
}

/// Audio quality options.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum Quality {
    /// MP3 128 kbps.
    #[serde(rename = "MP3_128")]
    Mp3_128,
    /// MP3 320 kbps.
    #[serde(rename = "MP3_320")]
    Mp3_320,
    /// FLAC lossless.
    #[serde(rename = "FLAC")]
    Flac,
}

impl Quality {
    /// Get the numeric quality code for Deezer API.
    pub fn code(&self) -> &str {
        match self {
            Quality::Mp3_128 => "1",
            Quality::Mp3_320 => "3",
            Quality::Flac => "9",
        }
    }

    /// Get the file extension for this quality.
    pub fn extension(&self) -> &str {
        match self {
            Quality::Mp3_128 | Quality::Mp3_320 => ".mp3",
            Quality::Flac => ".flac",
        }
    }

    /// Get a human-readable label.
    pub fn label(&self) -> &str {
        match self {
            Quality::Mp3_128 => "128",
            Quality::Mp3_320 => "320",
            Quality::Flac => "FLAC",
        }
    }
}

impl Default for Quality {
    fn default() -> Self {
        Self::Mp3_320
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_release_date_full() {
        let date = ReleaseDate::parse("2023-05-15");
        assert_eq!(date.year, 2023);
        assert_eq!(date.month, Some(5));
        assert_eq!(date.day, Some(15));
    }

    #[test]
    fn test_parse_release_date_year_only() {
        let date = ReleaseDate::parse("2020");
        assert_eq!(date.year, 2020);
        assert_eq!(date.month, None);
        assert_eq!(date.day, None);
    }

    #[test]
    fn test_parse_release_date_empty() {
        let date = ReleaseDate::parse("");
        assert_eq!(date.year, 0);
    }

    #[test]
    fn test_ids_with_deezer() {
        let ids = IDs::with_deezer("12345");
        assert_eq!(ids.deezer, Some("12345".to_string()));
        assert_eq!(ids.isrc, None);
    }

    #[test]
    fn test_quality_code() {
        assert_eq!(Quality::Mp3_128.code(), "1");
        assert_eq!(Quality::Mp3_320.code(), "3");
        assert_eq!(Quality::Flac.code(), "9");
    }
}
