package com.starnest.journalcanvaseditor.di

import android.content.Context
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

@Module
@InstallIn(SingletonComponent::class)
object EditorModule {
    @Provides
    fun provideEditorReducer(): EditorReducer = EditorReducer()

    @Provides
    fun provideSnapEngine(): SnapEngine = SnapEngine()

    @Provides
    fun provideHistoryStore(): HistoryStore = HistoryStore()

    @Provides
    fun provideCanvasStateStore(@ApplicationContext context: Context): CanvasStateStore =
        CanvasStateStore(context)

    @Provides
    fun provideImageStore(@ApplicationContext context: Context): ImageStore =
        ImageStore(context)

    @Provides
    fun provideCanvasExporter(@ApplicationContext context: Context): CanvasExporter =
        CanvasExporter(context)
}
