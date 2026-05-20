package com.starnest.journalcanvaseditor.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.starnest.journalcanvaseditor.R
import com.starnest.journalcanvaseditor.domain.EditorObject

internal class SelectionRenderer(
    context: Context,
    private val hitTester: ObjectHitTester
) {
    private val handleVisualRadiusPx = 16f * context.resources.displayMetrics.density

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.canvas_selection)
        strokeWidth = 2f * context.resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.canvas_handle_fill)
        style = Paint.Style.FILL
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.canvas_selection)
        strokeWidth = 2f * context.resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val handleIcons: Map<CanvasHandle, Drawable?> = mapOf(
        CanvasHandle.Delete to AppCompatResources.getDrawable(context, R.drawable.ic_close),
        CanvasHandle.Flip to AppCompatResources.getDrawable(context, R.drawable.ic_flip),
        CanvasHandle.Rotate to AppCompatResources.getDrawable(context, R.drawable.ic_rotate),
        CanvasHandle.Resize to AppCompatResources.getDrawable(context, R.drawable.ic_resize)
    )

    fun draw(canvas: Canvas, obj: EditorObject, totalScale: Float) {
        canvas.save()
        canvas.concat(CanvasGeometry.objectMatrix(obj))
        canvas.drawRect(0f, 0f, obj.width, obj.height, selectionPaint)
        canvas.restore()

        if (obj.locked) return

        for ((handle, canvasPoint) in hitTester.handleCanvasPoints(obj)) {
            val radius = handleVisualRadiusPx / totalScale
            canvas.drawCircle(canvasPoint.x, canvasPoint.y, radius, handlePaint)
            canvas.drawCircle(canvasPoint.x, canvasPoint.y, radius, handleStrokePaint)
            drawHandleIcon(canvas, handle, canvasPoint, radius)
        }
    }

    private fun drawHandleIcon(canvas: Canvas, handle: CanvasHandle, center: PointF, radius: Float) {
        val icon = handleIcons[handle] ?: return
        val padding = radius * 0.34f
        val left = (center.x - radius + padding).toInt()
        val top = (center.y - radius + padding).toInt()
        val right = (center.x + radius - padding).toInt()
        val bottom = (center.y + radius - padding).toInt()
        icon.setBounds(left, top, right, bottom)
        icon.draw(canvas)
    }
}
