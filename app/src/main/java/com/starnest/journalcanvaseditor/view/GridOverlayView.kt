package com.starnest.journalcanvaseditor.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

class GridOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#5F8DFF".toColorInt()
        alpha = 95
        strokeWidth = 1f
    }
    private val cellSize = 64f

    override fun onDraw(canvas: Canvas) {
        var x = 0f
        while (x <= width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), paint)
            x += cellSize
        }

        var y = 0f
        while (y <= height) {
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
            y += cellSize
        }
    }
}
