package com.starnest.journalcanvaseditor.persistence

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.starnest.journalcanvaseditor.model.CanvasState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class CanvasStateStore(private val context: Context) {
    private val gson = Gson()
    private val stateFile = File(context.filesDir, STATE_FILE_NAME)
    private val imageDir = File(context.filesDir, IMAGE_DIR_NAME)

    suspend fun load(): CanvasState? = withContext(Dispatchers.IO) {
        if (!stateFile.exists()) return@withContext null
        runCatching {
            gson.fromJson(stateFile.readText(), CanvasState::class.java)
        }.getOrNull()
    }

    suspend fun save(state: CanvasState) = withContext(Dispatchers.IO) {
        stateFile.writeText(gson.toJson(state))
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        if (stateFile.exists()) stateFile.delete()
    }

    suspend fun copyImage(uri: Uri): String = withContext(Dispatchers.IO) {
        imageDir.mkdirs()
        val extension = context.contentResolver.getType(uri)?.substringAfterLast('/') ?: "jpg"
        val file = File(imageDir, "image_${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open selected image" }
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file.absolutePath
    }

    private companion object {
        const val STATE_FILE_NAME = "canvas_state.json"
        const val IMAGE_DIR_NAME = "images"
    }
}
