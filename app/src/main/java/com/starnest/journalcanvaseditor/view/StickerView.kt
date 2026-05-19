package com.starnest.journalcanvaseditor.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.setPadding
import com.starnest.journalcanvaseditor.R
import com.starnest.journalcanvaseditor.extension.dp
import kotlin.math.abs
import kotlin.math.atan2

@SuppressLint("ClickableViewAccessibility")
open class StickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var listener: StickerEventListener? = null
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var initialWidth = 0
    private var initialHeight = 0
    private var initialCenterX = 0f
    private var initialCenterY = 0f
    private var hasMoved = false

    val objectId: String = java.util.UUID.randomUUID().toString()

    val imageView = AppCompatImageView(context).apply {
        visibility = GONE
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = true
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    val textView = TightTextView(context).apply {
        visibility = GONE
        gravity = Gravity.CENTER
        setTextColor(Color.BLACK)
        includeFontPadding = false
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private val deleteButton = handleButton(R.drawable.ic_close, Gravity.TOP or Gravity.START)
    private val flipButton = handleButton(R.drawable.ic_flip, Gravity.TOP or Gravity.END)
    private val rotateButton = handleButton(R.drawable.ic_rotate, Gravity.BOTTOM or Gravity.START)
    private val resizeButton = handleButton(R.drawable.ic_resize, Gravity.BOTTOM or Gravity.END)

    init {
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(false)
        setPadding(10.dp)

        addView(textView)
        addView(imageView)
        addView(deleteButton)
        addView(flipButton)
        addView(rotateButton)
        addView(resizeButton)

        deleteButton.setOnClickListener {
            (parent as? ViewGroup)?.removeView(this)
            listener?.onStickerDeleted(this)
        }

        flipButton.setOnClickListener {
            imageView.scaleX *= -1f
            listener?.onStickerFlipped(this)
            listener?.onStickerReleased(this)
        }

        rotateButton.setOnTouchListener { _, event -> handleRotate(event) }
        resizeButton.setOnTouchListener { _, event -> handleResize(event) }

        setOnTouchListener { _, event -> handleMove(event) }
        visibleAction(false)
    }

    fun setStickerEventListener(listener: StickerEventListener) {
        this.listener = listener
    }

    open fun visibleAction(isVisible: Boolean) {
        val visibility = if (isVisible) View.VISIBLE else View.GONE
        deleteButton.visibility = visibility
        rotateButton.visibility = visibility
        resizeButton.visibility = visibility
        flipButton.visibility = visibility
    }

    open fun showDashedBorder(isShow: Boolean) {
        background = if (isShow) {
            context.getDrawable(R.drawable.bg_dashed_border_image)
        } else {
            null
        }
    }

    private fun handleButton(drawableRes: Int, gravity: Int): ImageButton {
        val size = context.resources.getDimensionPixelSize(R.dimen.sticker_handle_size)
        val half = size / 2
        return ImageButton(context).apply {
            setImageResource(drawableRes)
            background = context.getDrawable(R.drawable.bg_handle)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(6.dp)
            elevation = 8f
            layoutParams = LayoutParams(size, size, gravity).apply {
                setMargins(-half, -half, -half, -half)
            }
        }
    }

    private fun handleMove(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                bringToFront()
                lastRawX = event.rawX
                lastRawY = event.rawY
                hasMoved = false
                listener?.onStickerSelected(this)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastRawX
                val dy = event.rawY - lastRawY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) hasMoved = true
                translationX += dx
                translationY += dy
                lastRawX = event.rawX
                lastRawY = event.rawY
                listener?.onStickerMoved(this, dx, dy)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                listener?.onStickerReleased(this)
                return true
            }
        }
        return false
    }

    private fun handleResize(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastRawX = event.rawX
                lastRawY = event.rawY
                initialWidth = width
                initialHeight = height
                listener?.onStickerSelected(this)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastRawX
                val dy = event.rawY - lastRawY
                val newWidth = (initialWidth + dx).toInt().coerceIn(72.dp, 1400.dp)
                val newHeight = (initialHeight + dy).toInt().coerceIn(72.dp, 1400.dp)
                layoutParams = layoutParams.apply {
                    width = newWidth
                    height = newHeight
                }
                listener?.onStickerScaled(this, newWidth / initialWidth.toFloat())
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                listener?.onStickerReleased(this)
                return true
            }
        }
        return false
    }

    private fun handleRotate(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val location = IntArray(2)
                getLocationOnScreen(location)
                initialCenterX = location[0] + width / 2f
                initialCenterY = location[1] + height / 2f
                lastRawX = event.rawX
                lastRawY = event.rawY
                listener?.onStickerSelected(this)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val currentAngle = atan2(event.rawY - initialCenterY, event.rawX - initialCenterX)
                val lastAngle = atan2(lastRawY - initialCenterY, lastRawX - initialCenterX)
                rotation += Math.toDegrees((currentAngle - lastAngle).toDouble()).toFloat()
                lastRawX = event.rawX
                lastRawY = event.rawY
                listener?.onStickerRotated(this, rotation)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                listener?.onStickerReleased(this)
                return true
            }
        }
        return false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (parent as? ViewGroup)?.clipChildren = false
        (parent as? ViewGroup)?.clipToPadding = false
    }

    interface StickerEventListener {
        fun onStickerSelected(sticker: StickerView)
        fun onStickerMoved(sticker: StickerView, dx: Float, dy: Float)
        fun onStickerScaled(sticker: StickerView, scaleFactor: Float)
        fun onStickerRotated(sticker: StickerView, angle: Float)
        fun onStickerDeleted(sticker: StickerView)
        fun onStickerFlipped(sticker: StickerView)
        fun onStickerReleased(sticker: StickerView)
    }
}

class TightTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {
    override fun onDraw(canvas: Canvas) {
        val offset = (paint.fontMetrics.descent + paint.fontMetrics.ascent) / 8f
        canvas.save()
        canvas.translate(0f, -offset)
        super.onDraw(canvas)
        canvas.restore()
    }
}
