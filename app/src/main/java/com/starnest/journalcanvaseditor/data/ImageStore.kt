package com.starnest.journalcanvaseditor.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class StoredImage(
    val path: String,
    val width: Int,
    val height: Int
)

class ImageStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val imageDir = File(context.filesDir, IMAGE_DIR_NAME)

    suspend fun copy(uri: Uri): StoredImage = withContext(Dispatchers.IO) {
        imageDir.mkdirs()
        val extension = context.contentResolver.getType(uri)?.substringAfterLast('/') ?: "jpg"
        val file = File(imageDir, "image_${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open selected image" }
            file.outputStream().use { output -> input.copyTo(output) }
        }

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val rotation = readRotation(file)
        val swapped = rotation == 90 || rotation == 270
        StoredImage(
            path = file.absolutePath,
            width = if (swapped) options.outHeight else options.outWidth,
            height = if (swapped) options.outWidth else options.outHeight
        )
    }

    private fun readRotation(file: File): Int {
        return runCatching {
            when (ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
    }

    companion object {
        const val IMAGE_DIR_NAME = "images"
    }
}
