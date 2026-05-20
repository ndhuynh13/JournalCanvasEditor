package com.starnest.journalcanvaseditor.render

import android.graphics.Matrix
import android.graphics.PointF
import android.view.View
import com.starnest.journalcanvaseditor.domain.EditorDocument
import com.starnest.journalcanvaseditor.domain.EditorObject
import kotlin.math.hypot

internal class ObjectHitTester(
    private val viewportTransformer: ViewportTransformer
) {
    fun hitObject(canvasX: Float, canvasY: Float, candidates: List<EditorObject>): EditorObject? {
        var topmost: EditorObject? = null
        for (obj in candidates) {
            val inverse = Matrix()
            CanvasGeometry.objectMatrix(obj).invert(inverse)
            val pts = floatArrayOf(canvasX, canvasY)
            inverse.mapPoints(pts)
            val containsPoint = pts[0] in 0f..obj.width && pts[1] in 0f..obj.height
            if (containsPoint && obj.zIndex > (topmost?.zIndex ?: Int.MIN_VALUE)) {
                topmost = obj
            }
        }
        return topmost
    }

    fun hitHandle(
        rawX: Float,
        rawY: Float,
        obj: EditorObject,
        view: View,
        document: EditorDocument,
        touchRadiusPx: Float
    ): CanvasHandle? {
        return handleScreenPoints(obj, view, document).entries.firstOrNull { (_, point) ->
            distance(point.x, point.y, rawX, rawY) <= touchRadiusPx
        }?.key
    }

    fun handleCanvasPoints(obj: EditorObject): Map<CanvasHandle, PointF> {
        val matrix = CanvasGeometry.objectMatrix(obj)
        fun map(x: Float, y: Float): PointF {
            val pts = floatArrayOf(x, y)
            matrix.mapPoints(pts)
            return PointF(pts[0], pts[1])
        }
        return mapOf(
            CanvasHandle.Delete to map(0f, 0f),
            CanvasHandle.Flip to map(obj.width, 0f),
            CanvasHandle.Rotate to map(0f, obj.height),
            CanvasHandle.Resize to map(obj.width, obj.height)
        )
    }

    private fun handleScreenPoints(obj: EditorObject, view: View, document: EditorDocument): Map<CanvasHandle, PointF> {
        val matrix = Matrix(CanvasGeometry.objectMatrix(obj)).apply {
            postConcat(viewportTransformer.viewportMatrix(document, view.width, view.height))
        }
        fun map(x: Float, y: Float): PointF {
            val pts = floatArrayOf(x, y)
            matrix.mapPoints(pts)
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            return PointF(pts[0] + location[0], pts[1] + location[1])
        }
        return mapOf(
            CanvasHandle.Delete to map(0f, 0f),
            CanvasHandle.Flip to map(obj.width, 0f),
            CanvasHandle.Rotate to map(0f, obj.height),
            CanvasHandle.Resize to map(obj.width, obj.height)
        )
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return hypot(x2 - x1, y2 - y1)
    }
}
