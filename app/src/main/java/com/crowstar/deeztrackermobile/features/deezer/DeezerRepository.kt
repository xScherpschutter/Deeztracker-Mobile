package com.crowstar.deeztrackermobile.features.deezer

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

object DeezerNetwork {
    private const val BASE_URL = "https://api.deezer.com/"

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val api: DeezerApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DeezerApiService::class.java)
    }
}

class DeezerRepository {
    private val api = DeezerNetwork.api

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

    suspend fun getAlbum(id: Long) = api.getAlbum(id)
    
    suspend fun getAlbumTracks(id: Long, next: String? = null): TrackListResponse {
        return if (next != null) api.getAlbumTracksByUrl(next) else api.getAlbumTracks(id)
    }

    suspend fun getPlaylist(id: Long) = api.getPlaylist(id)
    
    suspend fun getPlaylistTracks(id: Long, next: String? = null): TrackListResponse {
        return if (next != null) api.getPlaylistTracksByUrl(next) else api.getPlaylistTracks(id)
    }
}
