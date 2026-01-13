package com.crowstar.deeztrackermobile.features.deezer

import com.google.gson.annotations.SerializedName

// --- Common / Mini Models ---

data class ArtistMini(
    val id: Long,
    val name: String,
    val link: String? = null,
    val picture: String? = null,
    @SerializedName("picture_small") val pictureSmall: String? = null,
    @SerializedName("picture_medium") val pictureMedium: String? = null,
    @SerializedName("picture_big") val pictureBig: String? = null,
    @SerializedName("picture_xl") val pictureXl: String? = null,
    val tracklist: String? = null,
    val type: String? = null
)

data class AlbumMini(
    val id: Long,
    val title: String,
    val cover: String? = null,
    @SerializedName("cover_small") val coverSmall: String? = null,
    @SerializedName("cover_medium") val coverMedium: String? = null,
    @SerializedName("cover_big") val coverBig: String? = null,
    @SerializedName("cover_xl") val coverXl: String? = null,
    @SerializedName("md5_image") val md5Image: String? = null,
    val tracklist: String? = null,
    val type: String? = null
)

data class UserMini(
    val id: Long,
    val name: String,
    val tracklist: String? = null,
    val type: String? = null
)

// --- Main Entities ---

data class Artist(
    val id: Long,
    val name: String,
    val link: String? = null,
    val share: String? = null,
    val picture: String? = null,
    @SerializedName("picture_small") val pictureSmall: String? = null,
    @SerializedName("picture_medium") val pictureMedium: String? = null,
    @SerializedName("picture_big") val pictureBig: String? = null,
    @SerializedName("picture_xl") val pictureXl: String? = null,
    @SerializedName("nb_album") val nbAlbum: Int? = null,
    @SerializedName("nb_fan") val nbFan: Int? = null,
    val radio: Boolean? = null,
    val tracklist: String? = null,
    val type: String? = null
)

data class Album(
    val id: Long,
    val title: String,
    val upc: String? = null,
    val link: String? = null,
    val share: String? = null,
    val cover: String? = null,
    @SerializedName("cover_small") val coverSmall: String? = null,
    @SerializedName("cover_medium") val coverMedium: String? = null,
    @SerializedName("cover_big") val coverBig: String? = null,
    @SerializedName("cover_xl") val coverXl: String? = null,
    @SerializedName("md5_image") val md5Image: String? = null,
    @SerializedName("genre_id") val genreId: Int? = null,
    val genres: GenreList? = null,
    val label: String? = null,
    @SerializedName("nb_tracks") val nbTracks: Int? = null,
    val duration: Int? = null,
    val fans: Int? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("record_type") val recordType: String? = null,
    val available: Boolean? = null,
    @SerializedName("tracklist") val tracklist: String? = null,
    @SerializedName("explicit_lyrics") val explicitLyrics: Boolean? = null,
    @SerializedName("explicit_content_lyrics") val explicitContentLyrics: Int? = null,
    @SerializedName("explicit_content_cover") val explicitContentCover: Int? = null,
    val contributors: List<Contributor>? = null,
    val artist: ArtistMini? = null,
    val type: String? = null,
    val tracks: TrackListResponse? = null
)

data class Track(
    val id: Long,
    val readable: Boolean? = null,
    val title: String,
    @SerializedName("title_short") val titleShort: String? = null,
    @SerializedName("title_version") val titleVersion: String? = null,
    val link: String? = null,
    val duration: Int? = null,
    val rank: Int? = null,
    @SerializedName("explicit_lyrics") val explicitLyrics: Boolean? = null,
    @SerializedName("explicit_content_lyrics") val explicitContentLyrics: Int? = null,
    @SerializedName("explicit_content_cover") val explicitContentCover: Int? = null,
    val preview: String? = null,
    @SerializedName("md5_image") val md5Image: String? = null,
    val artist: ArtistMini? = null,
    val album: AlbumMini? = null,
    val type: String? = null
)

data class Playlist(
    val id: Long,
    val title: String,
    val description: String? = null,
    val duration: Int? = null,
    val public: Boolean? = null,
    @SerializedName("is_loved_track") val isLovedTrack: Boolean? = null,
    val collaborative: Boolean? = null,
    @SerializedName("nb_tracks") val nbTracks: Int? = null,
    val fans: Int? = null,
    val link: String? = null,
    val share: String? = null,
    val picture: String? = null,
    @SerializedName("picture_small") val pictureSmall: String? = null,
    @SerializedName("picture_medium") val pictureMedium: String? = null,
    @SerializedName("picture_big") val pictureBig: String? = null,
    @SerializedName("picture_xl") val pictureXl: String? = null,
    @SerializedName("checksum") val checksum: String? = null,
    val tracklist: String? = null,
    @SerializedName("creation_date") val creationDate: String? = null,
    @SerializedName("creator") val creator: UserMini? = null,
    val type: String? = null,
    val tracks: TrackListResponse? = null
)

data class GenreList(
    val data: List<Genre>
)

data class Genre(
    val id: Long,
    val name: String,
    val picture: String? = null,
    val type: String? = null
)

data class Contributor(
    val id: Long,
    val name: String,
    val link: String? = null,
    val share: String? = null,
    val picture: String? = null,
    @SerializedName("picture_small") val pictureSmall: String? = null,
    @SerializedName("picture_medium") val pictureMedium: String? = null,
    @SerializedName("picture_big") val pictureBig: String? = null,
    @SerializedName("picture_xl") val pictureXl: String? = null,
    val radio: Boolean? = null,
    val tracklist: String? = null,
    val type: String? = null,
    val role: String? = null
)


// --- Search Responses ---

data class TrackSearchResponse(
    val data: List<Track>,
    val total: Int? = null,
    val next: String? = null
)

data class AlbumSearchResponse(
    val data: List<Album>,
    val total: Int? = null,
    val next: String? = null
)

data class ArtistSearchResponse(
    val data: List<Artist>,
    val total: Int? = null,
    val next: String? = null
)

data class PlaylistSearchResponse(
    val data: List<Playlist>,
    val total: Int? = null,
    val next: String? = null
)

data class TrackListResponse(
    val data: List<Track>,
    val total: Int? = null,
    val next: String? = null
)
