# Rusteer

**Rusteer** is a high-performance Rust library and CLI for downloading music from Deezer. It supports downloading tracks, albums, and playlists with full metadata tagging and cover art.

## Features

- 🎵 **High Quality**: Download in MP3 (128/320kbps) or FLAC (Lossless).
- 🏷️ **Metadata**: Automatically embeds ID3 tags (Title, Artist, Album, Year, Genre, ISRC, etc.).
- 🖼️ **Cover Art**: Embeds high-resolution album covers.
- 📦 **Batch Downloads**: Download full albums and playlists with a single command.
- 🚀 **Fast**: Built with Rust for optimal performance and concurrency.
- 🔒 **Secure**: Handles Deezer encryption transparently.

## Installation

### From Source

1. Clone the repository:
   ```bash
   git clone https://github.com/xScherpschutter/Rusteer.git
   cd Rusteer
   ```

2. Build the project:
   ```bash
   cargo build --release
   ```

3. The CLI binary will be available at `target/release/rusteer-cli`.

## CLI Usage

The `rusteer-cli` tool allows you to interact with Rusteer from the terminal.

### Authentication (ARL Token)

You need a valid Deezer ARL token to use Rusteer. You can find this in your browser cookies (named `arl`) when logged into Deezer.

You can provide the token in two ways:
1. **Flag**: `--arl "YOUR_TOKEN"`
2. **Environment Variable**: `export DEEZER_ARL="YOUR_TOKEN"`

### Commands

#### Download

Download a track, album, or playlist.

```bash
# Download a track by URL
rusteer-cli download "https://www.deezer.com/track/3135556"

# Download an album by ID
rusteer-cli download "302127" --type album

# Download a playlist
rusteer-cli download "908622995" --type playlist

# Specify output directory and quality
rusteer-cli --output "My Music" --quality flac download "3135556"
```

#### Search

Search for tracks or albums.

```bash
# Search for tracks
rusteer-cli search "Daft Punk"

# Search for albums
rusteer-cli search "Discovery" --type album
```

## Library Usage

Add Rusteer to your `Cargo.toml`:

```toml
[dependencies]
rusteer = { path = "." } # Or git repository
tokio = { version = "1", features = ["full"] }
```

### Example

```rust
use rusteer::{Rusteer, DownloadQuality};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Initialize with ARL token
    let dz = Rusteer::new("YOUR_ARL_TOKEN").await?;

    // Configure options
    dz.set_output_dir("downloads");
    dz.set_quality(DownloadQuality::Mp3_320);

    // Download a track
    let result = dz.download_track("3135556").await?;
    println!("Downloaded: {}", result.title);

    Ok(())
}
```

## Disclaimer

This tool is for recreational purposes only. Downloading copyrighted material without permission may be illegal in your country. Use responsibly and support the artists by streaming on official platforms.

## License

MIT
