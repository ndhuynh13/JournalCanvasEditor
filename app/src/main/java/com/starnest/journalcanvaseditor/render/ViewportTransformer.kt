package com.starnest.journalcanvaseditor.render

import android.graphics.Matrix
import android.graphics.PointF
import android.view.View
import com.starnest.journalcanvaseditor.domain.EditorDocument
import com.starnest.journalcanvaseditor.domain.ViewportState
import kotlin.math.min

internal class ViewportTransformer {
    fun viewportMatrix(document: EditorDocument, viewWidth: Int, viewHeight: Int): Matrix {
        return Matrix().apply {
            val base = baseScale(document, viewWidth, viewHeight)
            val scale = base * document.viewport.scale
            postScale(scale, scale)
            postTranslate(
                baseTranslateX(document, viewWidth, viewHeight) + document.viewport.panX * base,
                baseTranslateY(document, viewWidth, viewHeight) + document.viewport.panY * base
            )
        }
    }

    fun viewToCanvas(document: EditorDocument, viewWidth: Int, viewHeight: Int, x: Float, y: Float): PointF {
        val pts = floatArrayOf(x, y)
        val inverse = Matrix()
        viewportMatrix(document, viewWidth, viewHeight).invert(inverse)
        inverse.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    fun rawToCanvas(document: EditorDocument, view: View, rawX: Float, rawY: Float): PointF {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return viewToCanvas(document, view.width, view.height, rawX - location[0], rawY - location[1])
    }

    fun canvasToRaw(document: EditorDocument, view: View, x: Float, y: Float): PointF {
        val pts = floatArrayOf(x, y)
        viewportMatrix(document, view.width, view.height).mapPoints(pts)
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return PointF(pts[0] + location[0], pts[1] + location[1])
    }

    fun totalScale(document: EditorDocument, viewWidth: Int, viewHeight: Int): Float {
        return baseScale(document, viewWidth, viewHeight) * document.viewport.scale
    }

    fun panByScreenDelta(document: EditorDocument, viewWidth: Int, viewHeight: Int, dxScreen: Float, dyScreen: Float): ViewportState {
        val base = baseScale(document, viewWidth, viewHeight)
        return document.viewport.copy(
            panX = document.viewport.panX + dxScreen / base,
            panY = document.viewport.panY + dyScreen / base
        )
    }

    fun zoomAt(document: EditorDocument, viewWidth: Int, viewHeight: Int, nextScale: Float, focusX: Float, focusY: Float): ViewportState {
        val focusCanvas = viewToCanvas(document, viewWidth, viewHeight, focusX, focusY)
        val base = baseScale(document, viewWidth, viewHeight)
        val nextPanX = (focusX - baseTranslateX(document, viewWidth, viewHeight) - focusCanvas.x * base * nextScale) / base
        val nextPanY = (focusY - baseTranslateY(document, viewWidth, viewHeight) - focusCanvas.y * base * nextScale) / base
        return document.viewport.copy(scale = nextScale, panX = nextPanX, panY = nextPanY)
    }

    private fun baseScale(document: EditorDocument, viewWidth: Int, viewHeight: Int): Float {
        val horizontal = viewWidth / document.canvasWidth
        val vertical = viewHeight / document.canvasHeight
        return min(horizontal, vertical).coerceAtLeast(0.1f) * CANVAS_FIT_RATIO
    }

    private fun baseTranslateX(document: EditorDocument, viewWidth: Int, viewHeight: Int): Float {
        return (viewWidth - document.canvasWidth * baseScale(document, viewWidth, viewHeight)) / 2f
    }

    private fun baseTranslateY(document: EditorDocument, viewWidth: Int, viewHeight: Int): Float {
        return (viewHeight - document.canvasHeight * baseScale(document, viewWidth, viewHeight)) / 2f
    }

    private companion object {
        const val CANVAS_FIT_RATIO = 0.9f
    }
}
