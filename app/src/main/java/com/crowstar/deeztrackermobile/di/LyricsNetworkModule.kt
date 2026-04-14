package com.crowstar.deeztrackermobile.di

import com.crowstar.deeztrackermobile.features.lyrics.LrcLibApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LyricsNetworkModule {

    private const val BASE_URL = "https://lrclib.net/"

    @Provides
    @Singleton
    @Named("LyricsOkHttp")
    fun provideLyricsOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("LyricsRetrofit")
    fun provideLyricsRetrofit(@Named("LyricsOkHttp") okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideLrcLibApi(@Named("LyricsRetrofit") retrofit: Retrofit): LrcLibApi {
        return retrofit.create(LrcLibApi::class.java)
    }
}
