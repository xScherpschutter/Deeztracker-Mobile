//! Cryptographic utilities for Deezer audio decryption.
//!
//! This module provides functions for decrypting Deezer audio files,
//! which use a combination of Blowfish and AES encryption.
//!
//! # Encryption Scheme
//!
//! Deezer uses a stripe encryption scheme:
//! - Audio is divided into 2048-byte blocks
//! - Every 3rd block (0, 3, 6, 9...) is encrypted with Blowfish CBC
//! - Other blocks are left unencrypted
//! - The encryption key is derived from the song ID

use md5::{Digest, Md5};
use std::fs::File;
use std::io::{Read, Write};
use std::path::Path;
use tracing::{debug, warn};

use crate::error::{DeezerError, Result};

/// Deezer's secret key for Blowfish key derivation.
const SECRET_KEY: &[u8] = b"g4el58wc0zvf9na1";

/// Blowfish initialization vector.
const BLOWFISH_IV: [u8; 8] = [0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07];

/// Block size for audio encryption.
const BLOCK_SIZE: usize = 2048;

/// Blowfish cipher block size.
const BF_BLOCK_SIZE: usize = 8;

/// Compute MD5 hash of a string and return as hex string.
pub fn md5_hex(data: &str) -> String {
    let mut hasher = Md5::new();
    hasher.update(data.as_bytes());
    let result = hasher.finalize();
    hex::encode(result)
}

/// Compute MD5 hash of bytes and return as hex string.
pub fn md5_hex_bytes(data: &[u8]) -> String {
    let mut hasher = Md5::new();
    hasher.update(data);
    let result = hasher.finalize();
    hex::encode(result)
}

/// Generate a song hash using song ID, MD5, and media version.
///
/// This is used for legacy URL generation.
pub fn gen_song_hash(song_id: &str, song_md5: &str, media_version: &str) -> String {
    use sha1::{Digest as Sha1Digest, Sha1};

    let data = format!("{}{}{}", song_md5, media_version, song_id);
    let mut hasher = Sha1::new();
    hasher.update(data.as_bytes());
    let result = hasher.finalize();
    hex::encode(result)
}

/// Calculate the Blowfish decryption key for a given song ID.
///
/// The key is derived by XORing:
/// - First 16 chars of MD5(song_id)
/// - Second 16 chars of MD5(song_id)
/// - Deezer's secret key
pub fn calc_blowfish_key(song_id: &str) -> Vec<u8> {
    let hash = md5_hex(song_id);
    let hash_bytes = hash.as_bytes();

    debug!("MD5 hash of song ID '{}': {}", song_id, hash);

    let mut key = Vec::with_capacity(16);
    for i in 0..16 {
        let byte = hash_bytes[i] ^ hash_bytes[i + 16] ^ SECRET_KEY[i];
        key.push(byte);
    }

    debug!("Generated Blowfish key: {}", hex::encode(&key));
    key
}

/// Decrypt a chunk using Blowfish CBC mode.
///
/// This is a simplified implementation using the blowfish crate.
fn decrypt_blowfish_cbc(data: &[u8], key: &[u8]) -> Vec<u8> {
    use blowfish::Blowfish;
    use cipher::generic_array::GenericArray;
    use cipher::BlockDecrypt;
    use cipher::KeyInit;

    if data.len() % BF_BLOCK_SIZE != 0 {
        warn!(
            "Data length {} is not a multiple of {} bytes",
            data.len(),
            BF_BLOCK_SIZE
        );
    }

    // Create cipher with the key (using BigEndian byte order)
    let cipher: Blowfish<byteorder::BE> =
        Blowfish::new_from_slice(key).expect("Invalid key length for Blowfish");

    let mut result = data.to_vec();
    let mut prev_block = BLOWFISH_IV.to_vec();

    // Decrypt each 8-byte block with CBC mode
    for chunk in result.chunks_mut(BF_BLOCK_SIZE) {
        if chunk.len() < BF_BLOCK_SIZE {
            break;
        }

        // Save ciphertext for next CBC iteration
        let ciphertext = chunk.to_vec();

        // Decrypt block
        let block = GenericArray::from_mut_slice(chunk);
        cipher.decrypt_block(block);

        // XOR with previous ciphertext (CBC mode)
        for (byte, prev) in chunk.iter_mut().zip(prev_block.iter()) {
            *byte ^= prev;
        }

        prev_block = ciphertext;
    }

    result
}

/// Decrypt a 2048-byte audio block using Blowfish.
///
/// This function:
/// 1. Creates a fresh Blowfish cipher with the IV
/// 2. Decrypts the entire block in CBC mode
pub fn decrypt_blowfish_chunk(data: &[u8], key: &[u8]) -> Vec<u8> {
    decrypt_blowfish_cbc(data, key)
}

/// Decrypt a Deezer audio track.
///
/// This implements Deezer's stripe encryption scheme:
/// - Process data in 2048-byte blocks
/// - Only every 3rd block is encrypted
/// - Encrypted blocks use Blowfish CBC
///
/// # Arguments
///
/// * `encrypted_data` - The encrypted audio bytes
/// * `song_id` - The song ID for key derivation
/// * `output_path` - Path to write the decrypted file
pub fn decrypt_track(encrypted_data: &[u8], song_id: &str, output_path: &Path) -> Result<()> {
    let key = calc_blowfish_key(song_id);

    debug!(
        "Decrypting track {} ({} bytes) to {:?}",
        song_id,
        encrypted_data.len(),
        output_path
    );

    let mut output = File::create(output_path)?;
    let mut block_count = 0;

    for chunk in encrypted_data.chunks(BLOCK_SIZE) {
        let processed = if block_count % 3 == 0 && chunk.len() == BLOCK_SIZE {
            // Decrypt this block
            debug!("Decrypting block {} (size: {})", block_count, chunk.len());
            decrypt_blowfish_chunk(chunk, &key)
        } else {
            // Pass through unencrypted
            chunk.to_vec()
        };

        output.write_all(&processed)?;
        block_count += 1;
    }

    debug!(
        "Successfully decrypted {} blocks to {:?}",
        block_count, output_path
    );

    Ok(())
}

/// Decrypt a Deezer audio track from a reader (streaming).
///
/// This is useful for processing data as it's downloaded.
///
/// # Arguments
///
/// * `reader` - Source of encrypted data
/// * `song_id` - The song ID for key derivation
/// * `output_path` - Path to write the decrypted file
pub fn decrypt_track_streaming<R: Read>(
    reader: &mut R,
    song_id: &str,
    output_path: &Path,
) -> Result<()> {
    let key = calc_blowfish_key(song_id);

    let mut output = File::create(output_path)?;
    let mut buffer = [0u8; BLOCK_SIZE];
    let mut block_count = 0;
    let mut accumulated = Vec::new();

    loop {
        let bytes_read = reader.read(&mut buffer)?;
        if bytes_read == 0 {
            break;
        }

        accumulated.extend_from_slice(&buffer[..bytes_read]);

        // Process complete blocks
        while accumulated.len() >= BLOCK_SIZE {
            let block: Vec<u8> = accumulated.drain(..BLOCK_SIZE).collect();

            let processed = if block_count % 3 == 0 {
                decrypt_blowfish_chunk(&block, &key)
            } else {
                block
            };

            output.write_all(&processed)?;
            block_count += 1;
        }
    }

    // Write any remaining data (partial block, not encrypted)
    if !accumulated.is_empty() {
        debug!("Writing final partial block of {} bytes", accumulated.len());
        output.write_all(&accumulated)?;
    }

    Ok(())
}

/// Decrypt using AES-CTR mode.
///
/// This is used for newer Deezer content.
pub fn decrypt_aes_ctr(data: &[u8], key: &[u8], nonce: &[u8]) -> Result<Vec<u8>> {
    use aes::Aes128;
    use ctr::cipher::{KeyIvInit, StreamCipher};
    use ctr::Ctr128BE;

    if key.len() != 16 {
        return Err(DeezerError::CryptoError(format!(
            "Invalid AES key length: {} (expected 16)",
            key.len()
        )));
    }

    if nonce.len() != 16 {
        return Err(DeezerError::CryptoError(format!(
            "Invalid nonce length: {} (expected 16)",
            nonce.len()
        )));
    }

    let mut cipher = Ctr128BE::<Aes128>::new_from_slices(key, nonce)
        .map_err(|e| DeezerError::CryptoError(format!("Failed to create AES cipher: {}", e)))?;

    let mut result = data.to_vec();
    cipher.apply_keystream(&mut result);

    Ok(result)
}

/// Encryption parameters for a track.
#[derive(Debug, Clone)]
pub struct EncryptionParams {
    /// Type of encryption: "blowfish" or "aes"
    pub encryption_type: String,
    /// Track ID (for Blowfish key derivation)
    pub track_id: String,
    /// MD5 origin (for Blowfish)
    pub md5_origin: Option<String>,
    /// AES key (hex encoded)
    pub key: Option<String>,
    /// AES nonce (hex encoded)
    pub nonce: Option<String>,
}

/// Decrypt a file using the appropriate method based on parameters.
pub fn decrypt_file(
    encrypted_data: &[u8],
    params: &EncryptionParams,
    output_path: &Path,
) -> Result<()> {
    match params.encryption_type.as_str() {
        "aes" => {
            let key = params
                .key
                .as_ref()
                .ok_or_else(|| DeezerError::CryptoError("Missing AES key".to_string()))?;
            let nonce = params
                .nonce
                .as_ref()
                .ok_or_else(|| DeezerError::CryptoError("Missing AES nonce".to_string()))?;

            let key_bytes = hex::decode(key)
                .map_err(|e| DeezerError::CryptoError(format!("Invalid key hex: {}", e)))?;
            let nonce_bytes = hex::decode(nonce)
                .map_err(|e| DeezerError::CryptoError(format!("Invalid nonce hex: {}", e)))?;

            let decrypted = decrypt_aes_ctr(encrypted_data, &key_bytes, &nonce_bytes)?;

            let mut output = File::create(output_path)?;
            output.write_all(&decrypted)?;

            debug!("Successfully decrypted AES file to {:?}", output_path);
            Ok(())
        }
        "blowfish" | _ => decrypt_track(encrypted_data, &params.track_id, output_path),
    }
}

/// Analyze a FLAC file for structure validation.
///
/// This is useful for debugging decryption issues.
pub fn analyze_flac_file(file_path: &Path) -> Result<FlacAnalysis> {
    let mut file = File::open(file_path)?;
    let file_size = file.metadata()?.len();

    let mut analysis = FlacAnalysis {
        file_size,
        has_flac_signature: false,
        metadata_blocks: Vec::new(),
        potential_issues: Vec::new(),
    };

    if file_size < 8 {
        analysis
            .potential_issues
            .push("File too small to be a valid FLAC".to_string());
        return Ok(analysis);
    }

    // Check FLAC signature
    let mut header = [0u8; 4];
    file.read_exact(&mut header)?;
    analysis.has_flac_signature = &header == b"fLaC";

    if !analysis.has_flac_signature {
        analysis
            .potential_issues
            .push(format!("Missing FLAC signature. Found: {:?}", header));
        return Ok(analysis);
    }

    // Parse metadata blocks
    loop {
        let mut block_header = [0u8; 4];
        if file.read_exact(&mut block_header).is_err() {
            break;
        }

        let is_last = (block_header[0] & 0x80) != 0;
        let block_type = block_header[0] & 0x7F;
        let block_length = ((block_header[1] as u32) << 16)
            | ((block_header[2] as u32) << 8)
            | (block_header[3] as u32);

        analysis.metadata_blocks.push(MetadataBlock {
            block_type,
            length: block_length,
            is_last,
        });

        // Skip block data - read and discard
        let mut skip_buf = vec![0u8; block_length as usize];
        if file.read_exact(&mut skip_buf).is_err() {
            break;
        }

        if is_last {
            break;
        }

        if analysis.metadata_blocks.len() > 100 {
            analysis
                .potential_issues
                .push("Too many metadata blocks (possible corruption)".to_string());
            break;
        }
    }

    // Validate structure
    if analysis.metadata_blocks.is_empty() {
        analysis
            .potential_issues
            .push("No metadata blocks found".to_string());
    } else if !analysis.metadata_blocks.iter().any(|b| b.block_type == 0) {
        analysis
            .potential_issues
            .push("Missing STREAMINFO block".to_string());
    }

    Ok(analysis)
}

/// FLAC file analysis result.
#[derive(Debug)]
pub struct FlacAnalysis {
    /// Total file size in bytes.
    pub file_size: u64,
    /// Whether the file has a valid FLAC signature.
    pub has_flac_signature: bool,
    /// Metadata blocks found.
    pub metadata_blocks: Vec<MetadataBlock>,
    /// Potential issues detected.
    pub potential_issues: Vec<String>,
}

/// FLAC metadata block info.
#[derive(Debug)]
pub struct MetadataBlock {
    /// Block type (0 = STREAMINFO, 1 = PADDING, etc.).
    pub block_type: u8,
    /// Block length in bytes.
    pub length: u32,
    /// Whether this is the last metadata block.
    pub is_last: bool,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_md5_hex() {
        let result = md5_hex("test");
        assert_eq!(result, "098f6bcd4621d373cade4e832627b4f6");
    }

    #[test]
    fn test_calc_blowfish_key() {
        // Test with a known song ID
        let key = calc_blowfish_key("3135556");
        assert_eq!(key.len(), 16);
        // The key should be deterministic
        let key2 = calc_blowfish_key("3135556");
        assert_eq!(key, key2);
    }

    #[test]
    fn test_gen_song_hash() {
        let hash = gen_song_hash("12345", "abc123", "1");
        assert!(!hash.is_empty());
        assert_eq!(hash.len(), 40); // SHA1 produces 40 hex chars
    }

    #[test]
    fn test_encryption_decryption_roundtrip() {
        // This is a simplified test - real Deezer audio has specific structure
        let original = vec![0u8; BLOCK_SIZE * 4];

        // Create a temp file for testing
        let temp_dir = std::env::temp_dir();
        let output_path = temp_dir.join("test_decrypt.bin");

        let result = decrypt_track(&original, "test_song_id", &output_path);
        assert!(result.is_ok());

        // Cleanup
        let _ = std::fs::remove_file(&output_path);
    }
}
