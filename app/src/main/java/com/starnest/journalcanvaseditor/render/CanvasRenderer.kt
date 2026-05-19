package com.starnest.journalcanvaseditor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.starnest.journalcanvaseditor.domain.EditorDocument
import com.starnest.journalcanvaseditor.domain.EditorObject
import com.starnest.journalcanvaseditor.domain.EditorObjectType

class CanvasRenderer(
    private val bitmapCache: BitmapCache,
    private val textLayoutCache: TextLayoutCache = TextLayoutCache()
) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 46, 107, 255)
        strokeWidth = 1f
    }
    private val missingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 235, 244) }

    fun renderDocument(
        canvas: Canvas,
        document: EditorDocument,
        selectedObjectId: String?,
        drawGrid: Boolean,
        drawSelection: Boolean,
        selectionRenderer: ((Canvas, EditorObject) -> Unit)? = null
    ) {
        canvas.drawRect(0f, 0f, document.canvasWidth, document.canvasHeight, backgroundPaint)
        if (drawGrid) drawGrid(canvas, document)

        document.objects
            .asSequence()
            .filter { it.visible }
            .sortedBy { it.zIndex }
            .forEach { obj ->
                drawObject(canvas, obj)
                if (drawSelection && obj.id == selectedObjectId) {
                    selectionRenderer?.invoke(canvas, obj)
                }
            }
    }

    fun drawObject(canvas: Canvas, obj: EditorObject) {
        canvas.save()
        canvas.translate(obj.centerX, obj.centerY)
        canvas.rotate(obj.rotation)
        if (obj.flipped) canvas.scale(-1f, 1f)
        canvas.translate(-obj.width / 2f, -obj.height / 2f)

        when (obj.type) {
            EditorObjectType.TEXT -> drawText(canvas, obj)
            EditorObjectType.IMAGE -> drawImage(canvas, obj)
        }
        canvas.restore()
    }

    private fun drawText(canvas: Canvas, obj: EditorObject) {
        val layout = textLayoutCache.get(obj)
        val y = ((obj.height - layout.height) / 2f).coerceAtLeast(0f)
        canvas.save()
        canvas.translate(0f, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawImage(canvas: Canvas, obj: EditorObject) {
        val bitmap = bitmapCache.get(obj.imagePath)
        if (bitmap == null) {
            canvas.drawRoundRect(RectF(0f, 0f, obj.width, obj.height), 18f, 18f, missingPaint)
            return
        }
        val src = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val dst = RectF(0f, 0f, obj.width, obj.height)
        val matrix = android.graphics.Matrix().apply { setRectToRect(src, dst, android.graphics.Matrix.ScaleToFit.CENTER) }
        canvas.drawBitmap(bitmap, matrix, null)
    }

    private fun drawGrid(canvas: Canvas, document: EditorDocument) {
        var x = 0f
        while (x <= document.canvasWidth) {
            canvas.drawLine(x, 0f, x, document.canvasHeight, gridPaint)
            x += 120f
        }
        var y = 0f
        while (y <= document.canvasHeight) {
            canvas.drawLine(0f, y, document.canvasWidth, y, gridPaint)
            y += 120f
        }
    }
}
