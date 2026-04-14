package com.crowstar.deeztrackermobile.di

import android.content.ContentResolver
import android.content.Context
import com.crowstar.deeztrackermobile.features.rusteer.RustDeezerService
import com.crowstar.deeztrackermobile.features.localmusic.MetadataEditor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver {
        return context.contentResolver
    }

    @Provides
    @Singleton
    fun provideRustDeezerService(@ApplicationContext context: Context): RustDeezerService {
        return RustDeezerService(context)
    }

    @Provides
    @Singleton
    fun provideMetadataEditor(@ApplicationContext context: Context): MetadataEditor {
        return MetadataEditor(context)
    }
}
