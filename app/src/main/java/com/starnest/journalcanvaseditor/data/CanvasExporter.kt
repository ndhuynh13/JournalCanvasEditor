package com.starnest.journalcanvaseditor.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.provider.MediaStore
import com.starnest.journalcanvaseditor.domain.EditorDocument
import com.starnest.journalcanvaseditor.render.BitmapCache
import com.starnest.journalcanvaseditor.render.CanvasRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.core.graphics.createBitmap

class CanvasExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun export(document: EditorDocument): String = withContext(Dispatchers.IO) {
        val bitmap = createBitmap(document.canvasWidth.toInt(), document.canvasHeight.toInt())
        val canvas = Canvas(bitmap)
        CanvasRenderer(BitmapCache()).renderDocument(
            canvas = canvas,
            document = document,
            selectedObjectId = null,
            drawGrid = false,
            drawSelection = false
        )

        val resolver = context.contentResolver
        val name = "journal_canvas_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/JournalCanvasEditor")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri).use { output ->
            requireNotNull(output) { "Cannot open export output stream" }
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        bitmap.recycle()
        uri.toString()
    }
}
