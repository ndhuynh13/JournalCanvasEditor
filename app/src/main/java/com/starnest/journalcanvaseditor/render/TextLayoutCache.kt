package com.starnest.journalcanvaseditor.render

import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.collection.LruCache
import com.starnest.journalcanvaseditor.domain.EditorObject

class TextLayoutCache {
    private val cache = LruCache<TextLayoutKey, StaticLayout>(MAX_CACHE_SIZE)

    fun get(obj: EditorObject): StaticLayout {
        val key = TextLayoutKey(
            objectId = obj.id,
            text = obj.text,
            width = obj.width.toInt().coerceAtLeast(1),
            textSize = obj.textSize,
            textColor = obj.textColor,
            typefaceStyle = obj.typefaceStyle
        )
        cache[key]?.let { return it }

        val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = obj.textColor
            textSize = obj.textSize
            typeface = Typeface.create(Typeface.DEFAULT, obj.typefaceStyle)
        }
        val layout = StaticLayout.Builder
            .obtain(obj.text, 0, obj.text.length, textPaint, key.width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()

        cache.put(key, layout)
        return layout
    }

    fun clear() {
        cache.evictAll()
    }

    private data class TextLayoutKey(
        val objectId: String,
        val text: String,
        val width: Int,
        val textSize: Float,
        val textColor: Int,
        val typefaceStyle: Int
    )

    private companion object {
        const val MAX_CACHE_SIZE = 80
    }
}
