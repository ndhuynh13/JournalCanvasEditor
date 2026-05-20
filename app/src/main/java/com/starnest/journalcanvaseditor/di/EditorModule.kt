package com.starnest.journalcanvaseditor.di

import android.content.Context
import androidx.core.content.ContextCompat
import com.starnest.journalcanvaseditor.R
import com.starnest.journalcanvaseditor.data.CanvasExporter
import com.starnest.journalcanvaseditor.data.CanvasStateStore
import com.starnest.journalcanvaseditor.data.ImageStore
import com.starnest.journalcanvaseditor.domain.EditorReducer
import com.starnest.journalcanvaseditor.domain.HistoryStore
import com.starnest.journalcanvaseditor.domain.SnapEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EditorModule {
    @Provides
    @Singleton
    fun provideEditorReducer(@ApplicationContext context: Context): EditorReducer =
        EditorReducer(
            defaultTextColor = ContextCompat.getColor(context, R.color.canvas_text_default),
            defaultText = context.getString(R.string.journal_text)
        )

    @Provides
    @Singleton
    fun provideSnapEngine(): SnapEngine = SnapEngine()

    @Provides
    @Singleton
    fun provideHistoryStore(): HistoryStore = HistoryStore()

    @Provides
    @Singleton
    fun provideCanvasStateStore(@ApplicationContext context: Context): CanvasStateStore =
        CanvasStateStore(context)

    @Provides
    @Singleton
    fun provideImageStore(@ApplicationContext context: Context): ImageStore =
        ImageStore(context)

    @Provides
    @Singleton
    fun provideCanvasExporter(@ApplicationContext context: Context): CanvasExporter =
        CanvasExporter(context)
}
