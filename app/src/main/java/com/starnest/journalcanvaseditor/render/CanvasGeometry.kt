package com.starnest.journalcanvaseditor.render

import android.graphics.Matrix
import android.graphics.RectF
import com.starnest.journalcanvaseditor.domain.EditorObject
import kotlin.math.max
import kotlin.math.min

internal enum class CanvasHandle {
    Delete,
    Flip,
    Rotate,
    Resize
}

internal object CanvasGeometry {
    fun objectMatrix(obj: EditorObject): Matrix {
        return Matrix().apply {
            postTranslate(-obj.width / 2f, -obj.height / 2f)
            if (obj.flipped) postScale(-1f, 1f)
            postRotate(obj.rotation)
            postTranslate(obj.centerX, obj.centerY)
        }
    }

    fun objectBounds(obj: EditorObject): RectF {
        val points = floatArrayOf(
            0f,
            0f,
            obj.width,
            0f,
            obj.width,
            obj.height,
            0f,
            obj.height
        )
        objectMatrix(obj).mapPoints(points)

        var left = points[0]
        var top = points[1]
        var right = points[0]
        var bottom = points[1]

        for (index in 2 until points.size step 2) {
            left = min(left, points[index])
            top = min(top, points[index + 1])
            right = max(right, points[index])
            bottom = max(bottom, points[index + 1])
        }

        return RectF(left, top, right, bottom)
    }
}
