package com.crowstar.deeztrackermobile.features.deezer

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeezerRepository @Inject constructor(
    private val api: DeezerApiService
) {
    suspend fun searchTracks(query: String, next: String? = null): TrackSearchResponse {
        return if (next != null) api.searchTracksByUrl(next) else api.searchTracks(query)
    }

    suspend fun searchAlbums(query: String, next: String? = null): AlbumSearchResponse {
        return if (next != null) api.searchAlbumsByUrl(next) else api.searchAlbums(query)
    }

    suspend fun searchArtists(query: String, next: String? = null): ArtistSearchResponse {
        return if (next != null) api.searchArtistsByUrl(next) else api.searchArtists(query)
    }

    suspend fun searchPlaylists(query: String, next: String? = null): PlaylistSearchResponse {
        return if (next != null) api.searchPlaylistsByUrl(next) else api.searchPlaylists(query)
    }

    suspend fun getArtist(id: Long) = api.getArtist(id)
    
    suspend fun getArtistAlbums(id: Long, next: String? = null): AlbumSearchResponse {
        return if (next != null) api.getArtistAlbumsByUrl(next) else api.getArtistAlbums(id)
    }

    suspend fun getArtistTopTracks(id: Long, limit: Int = 10): TrackListResponse {
        return api.getArtistTopTracks(id, limit)
    }

    suspend fun getAlbum(id: Long) = api.getAlbum(id)
    
    suspend fun getAlbumTracks(id: Long, next: String? = null): TrackListResponse {
        return if (next != null) api.getAlbumTracksByUrl(next) else api.getAlbumTracks(id)
    }

    suspend fun getPlaylist(id: Long) = api.getPlaylist(id)
    
    suspend fun getPlaylistTracks(id: Long, next: String? = null): TrackListResponse {
        return if (next != null) api.getPlaylistTracksByUrl(next) else api.getPlaylistTracks(id)
    }
}
