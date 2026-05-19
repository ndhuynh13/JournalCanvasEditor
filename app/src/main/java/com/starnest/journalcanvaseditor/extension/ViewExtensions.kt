package com.starnest.journalcanvaseditor.extension

import android.content.res.Resources
import android.graphics.Matrix
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import com.starnest.journalcanvaseditor.view.StickerView

val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

fun View.safeClick(onClick: (View) -> Unit) {
    setOnClickListener { view ->
        view.isClickable = false
        onClick(view)
        view.postDelayed({ view.isClickable = true }, 400L)
    }
}

fun View.isTouchInsideRaw(rawX: Float, rawY: Float): Boolean {
    if (!isShown) return false

    val location = IntArray(2)
    getLocationOnScreen(location)
    val localX = rawX - location[0]
    val localY = rawY - location[1]

    val inverse = Matrix()
    return if (matrix.invert(inverse)) {
        val points = floatArrayOf(localX, localY)
        inverse.mapPoints(points)
        points[0] >= 0f && points[1] >= 0f && points[0] <= width && points[1] <= height
    } else {
        localX >= 0f && localY >= 0f && localX <= width && localY <= height
    }
}

fun ViewGroup.descendantStickerViews(): List<StickerView> {
    val result = mutableListOf<StickerView>()
    fun walk(group: ViewGroup) {
        group.children.forEach { child ->
            if (child is StickerView) result += child
            if (child is ViewGroup) walk(child)
        }
    }
    walk(this)
    return result
}
