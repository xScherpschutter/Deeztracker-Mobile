package com.crowstar.deeztrackermobile.features.deezer

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface DeezerApiService {

    // --- Search ---

    @GET("search/track")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("index") index: Int? = null,
        @Query("limit") limit: Int? = null
    ): TrackSearchResponse

    @GET("search/album")
    suspend fun searchAlbums(
        @Query("q") query: String,
        @Query("index") index: Int? = null,
        @Query("limit") limit: Int? = null
    ): AlbumSearchResponse

    @GET("search/artist")
    suspend fun searchArtists(
        @Query("q") query: String,
        @Query("index") index: Int? = null,
        @Query("limit") limit: Int? = null
    ): ArtistSearchResponse

    @GET("search/playlist")
    suspend fun searchPlaylists(
        @Query("q") query: String,
        @Query("index") index: Int? = null,
        @Query("limit") limit: Int? = null
    ): PlaylistSearchResponse

    // --- Artist ---

    @GET("artist/{id}")
    suspend fun getArtist(
        @Path("id") id: Long
    ): Artist

    @GET("artist/{id}/albums")
    suspend fun getArtistAlbums(
        @Path("id") id: Long,
        @Query("index") index: Int? = null,
        @Query("limit") limit: Int? = null
    ): AlbumSearchResponse // The response structure for artist albums is similar to search results

    @GET("artist/{id}/top")
    suspend fun getArtistTopTracks(
        @Path("id") id: Long,
        @Query("limit") limit: Int? = 10
    ): TrackListResponse

    // --- Album ---

    @GET("album/{id}")
    suspend fun getAlbum(
        @Path("id") id: Long
    ): Album

    @GET("album/{id}/tracks")
    suspend fun getAlbumTracks(
        @Path("id") id: Long,
        @Query("index") index: Int? = null,
        @Query("limit") limit: Int? = null
    ): TrackListResponse

    // --- Playlist ---

    @GET("playlist/{id}")
    suspend fun getPlaylist(
        @Path("id") id: Long
    ): Playlist

    @GET("playlist/{id}/tracks")
    suspend fun getPlaylistTracks(
        @Path("id") id: Long,
        @Query("index") index: Int? = null,
        @Query("limit") limit: Int? = null
    ): TrackListResponse

    // --- Pagination / Direct URL ---

    @GET
    suspend fun searchTracksByUrl(@Url url: String): TrackSearchResponse

    @GET
    suspend fun searchAlbumsByUrl(@Url url: String): AlbumSearchResponse

    @GET
    suspend fun searchArtistsByUrl(@Url url: String): ArtistSearchResponse

    @GET
    suspend fun searchPlaylistsByUrl(@Url url: String): PlaylistSearchResponse

    @GET
    suspend fun getAlbumTracksByUrl(@Url url: String): TrackListResponse

    @GET
    suspend fun getArtistAlbumsByUrl(@Url url: String): AlbumSearchResponse
    
    @GET
    suspend fun getPlaylistTracksByUrl(@Url url: String): TrackListResponse
}
