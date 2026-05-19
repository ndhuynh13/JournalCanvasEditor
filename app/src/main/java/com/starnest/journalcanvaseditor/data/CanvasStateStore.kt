package com.starnest.journalcanvaseditor.data

import android.content.Context
import com.google.gson.Gson
import com.starnest.journalcanvaseditor.domain.EditorDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class CanvasStateStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val gson = Gson()
    private val stateFile = File(context.filesDir, STATE_FILE_NAME)
    private val tempFile = File(context.filesDir, "$STATE_FILE_NAME.tmp")

    suspend fun load(): EditorDocument? = withContext(Dispatchers.IO) {
        if (!stateFile.exists()) return@withContext null
        runCatching {
            gson.fromJson(stateFile.readText(), EditorDocument::class.java)
        }.getOrNull()
    }

    suspend fun save(document: EditorDocument) = withContext(Dispatchers.IO) {
        tempFile.writeText(gson.toJson(document))
        if (stateFile.exists()) stateFile.delete()
        tempFile.renameTo(stateFile)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        if (stateFile.exists()) stateFile.delete()
        if (tempFile.exists()) tempFile.delete()
    }

    private companion object {
        const val STATE_FILE_NAME = "canvas_state.json"
    }
}
