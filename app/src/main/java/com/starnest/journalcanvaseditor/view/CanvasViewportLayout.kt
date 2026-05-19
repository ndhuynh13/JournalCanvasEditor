package com.starnest.journalcanvaseditor.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.starnest.journalcanvaseditor.extension.descendantStickerViews
import com.starnest.journalcanvaseditor.extension.isTouchInsideRaw
import kotlin.math.abs

class CanvasViewportLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var canvasView: View? = null
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    var canvasScaleValue: Float = 1f
        private set
    var canvasTranslationXValue: Float = 0f
        private set
    var canvasTranslationYValue: Float = 0f
        private set

    var onTransformChanged: (() -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = (canvasScaleValue * detector.scaleFactor).coerceIn(0.35f, 3.5f)
                canvasScaleValue = next
                applyTransform()
                onTransformChanged?.invoke()
                return true
            }
        }
    )

    fun bindCanvas(view: View) {
        canvasView = view
        view.pivotX = 0f
        view.pivotY = 0f
        applyTransform()
    }

    fun setCanvasTransform(scale: Float, translationX: Float, translationY: Float) {
        canvasScaleValue = scale.coerceIn(0.35f, 3.5f)
        canvasTranslationXValue = translationX
        canvasTranslationYValue = translationY
        applyTransform()
    }

    fun resetTransform() {
        canvasScaleValue = 1f
        canvasTranslationXValue = 0f
        canvasTranslationYValue = 0f
        applyTransform()
        onTransformChanged?.invoke()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (isTouchOnSticker(event)) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX
                lastY = event.rawY
                dragging = false
                return false
            }

            MotionEvent.ACTION_POINTER_DOWN -> return true

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.rawX - lastX)
                val dy = abs(event.rawY - lastY)
                if (dx > touchSlop || dy > touchSlop) {
                    dragging = true
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX
                lastY = event.rawY
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY
                    canvasTranslationXValue += dx
                    canvasTranslationYValue += dy
                    applyTransform()
                    onTransformChanged?.invoke()
                }
                lastX = event.rawX
                lastY = event.rawY
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return true
    }

    private fun isTouchOnSticker(event: MotionEvent): Boolean {
        return descendantStickerViews().any { it.isTouchInsideRaw(event.rawX, event.rawY) }
    }

    private fun applyTransform() {
        canvasView?.apply {
            scaleX = canvasScaleValue
            scaleY = canvasScaleValue
            translationX = canvasTranslationXValue
            translationY = canvasTranslationYValue
        }
    }
}
