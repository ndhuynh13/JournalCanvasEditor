package com.starnest.journalcanvaseditor.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import androidx.appcompat.content.res.AppCompatResources
import com.starnest.journalcanvaseditor.R
import com.starnest.journalcanvaseditor.domain.EditorDocument
import com.starnest.journalcanvaseditor.domain.EditorObject
import com.starnest.journalcanvaseditor.domain.EditorObjectType
import com.starnest.journalcanvaseditor.domain.GuideOrientation
import com.starnest.journalcanvaseditor.domain.SnapGuide
import com.starnest.journalcanvaseditor.domain.ViewportState
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

class JournalCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val bitmapCache = BitmapCache()
    private val renderer = CanvasRenderer(bitmapCache)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handleVisualRadiusPx = 16f * resources.displayMetrics.density
    private val handleTouchRadiusPx = 34f * resources.displayMetrics.density

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(46, 107, 255)
        strokeWidth = 2f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(46, 107, 255)
        strokeWidth = 2f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(46, 107, 255)
        strokeWidth = 1.5f * resources.displayMetrics.density
    }
    private val handleIcons: Map<Handle, Drawable?> = mapOf(
        Handle.Delete to AppCompatResources.getDrawable(context, R.drawable.ic_close),
        Handle.Flip to AppCompatResources.getDrawable(context, R.drawable.ic_flip),
        Handle.Rotate to AppCompatResources.getDrawable(context, R.drawable.ic_rotate),
        Handle.Resize to AppCompatResources.getDrawable(context, R.drawable.ic_resize)
    )

    private var document = EditorDocument()
    private var selectedObjectId: String? = null
    private var guides: List<SnapGuide> = emptyList()

    private var mode: GestureMode = GestureMode.Idle
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var activeObjectStart: EditorObject? = null
    private var activeObjectLatest: EditorObject? = null
    private var startDistance = 1f
    private var startAngleOffset = 0f
    private var pinchResizeFactor = 1f
    private var selectedIdAtDown: String? = null

    var onSelectObject: ((String?) -> Unit)? = null
    var onMoveObject: ((String, Float, Float, Boolean) -> Unit)? = null
    var onResizeObject: ((String, Float, Float, Float?, Boolean) -> Unit)? = null
    var onRotateObject: ((String, Float, Boolean) -> Unit)? = null
    var onViewportChanged: ((ViewportState, Boolean) -> Unit)? = null
    var onGuidesChanged: ((List<SnapGuide>) -> Unit)? = null
    var onDeleteObject: ((String) -> Unit)? = null
    var onFlipObject: ((String) -> Unit)? = null
    var onEditText: ((String) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                val focus = viewToCanvas(detector.focusX, detector.focusY)
                val selected = selectedObject()
                if (selected != null && !selected.locked && hitObject(focus.x, focus.y)?.id == selected.id) {
                    activeObjectStart = selected
                    activeObjectLatest = selected
                    pinchResizeFactor = 1f
                    mode = GestureMode.PinchResize(selected.id)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                mode = GestureMode.CanvasTransform
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                when (val current = mode) {
                    is GestureMode.PinchResize -> {
                        val start = activeObjectStart ?: return true
                        pinchResizeFactor = (pinchResizeFactor * detector.scaleFactor).coerceIn(MIN_RESIZE_FACTOR, MAX_RESIZE_FACTOR)
                        val textSize = scaledTextSize(start, pinchResizeFactor)
                        onResizeObject?.invoke(
                            current.objectId,
                            start.width * pinchResizeFactor,
                            start.height * pinchResizeFactor,
                            textSize,
                            false
                        )
                    }
                    else -> {
                        val viewport = document.viewport
                        val nextScale = (viewport.scale * detector.scaleFactor).coerceIn(MIN_VIEWPORT_SCALE, MAX_VIEWPORT_SCALE)
                        updateViewport(viewport.zoomAt(nextScale, detector.focusX, detector.focusY), false)
                    }
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                when (val current = mode) {
                    is GestureMode.PinchResize -> {
                        val resized = document.objects.firstOrNull { it.id == current.objectId } ?: activeObjectLatest
                        resized?.let {
                            onResizeObject?.invoke(current.objectId, it.width, it.height, it.textSize.takeIf { _ -> it.type == EditorObjectType.TEXT }, true)
                        }
                    }
                    GestureMode.CanvasTransform -> onViewportChanged?.invoke(document.viewport, true)
                    else -> Unit
                }
                clearGesture()
            }
        }
    )

    fun submitState(document: EditorDocument, selectedObjectId: String?, guides: List<SnapGuide>) {
        this.document = document
        this.selectedObjectId = selectedObjectId
        this.guides = guides
        activeObjectLatest = selectedObjectId?.let { id -> document.objects.firstOrNull { it.id == id } }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.concat(viewportMatrix())
        renderer.renderDocument(
            canvas = canvas,
            document = document,
            selectedObjectId = selectedObjectId,
            drawGrid = document.viewport.showGrid,
            drawSelection = true,
            selectionRenderer = ::drawSelection
        )
        drawGuides(canvas)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount > 1) return true

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP -> handleUp(event)
            MotionEvent.ACTION_CANCEL -> {
                clearGesture()
                true
            }
            else -> true
        }
    }

    private fun handleDown(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        downRawX = event.rawX
        downRawY = event.rawY
        lastRawX = event.rawX
        lastRawY = event.rawY
        selectedIdAtDown = selectedObjectId

        val selected = selectedObject()
        val handle = selected?.takeIf { !it.locked }?.let { hitHandle(event.rawX, event.rawY, it) }
        if (selected != null && handle != null) {
            activeObjectStart = selected
            activeObjectLatest = selected
            mode = when (handle) {
                Handle.Delete -> GestureMode.Delete(selected.id)
                Handle.Flip -> GestureMode.Flip(selected.id)
                Handle.Resize -> {
                    val center = canvasToScreen(selected.centerX, selected.centerY)
                    startDistance = distance(center.x, center.y, event.rawX, event.rawY).coerceAtLeast(1f)
                    GestureMode.Resize(selected.id)
                }
                Handle.Rotate -> {
                    val center = canvasToScreen(selected.centerX, selected.centerY)
                    startAngleOffset = angle(center.x, center.y, event.rawX, event.rawY) - selected.rotation
                    GestureMode.Rotate(selected.id)
                }
            }
            return true
        }

        val canvasPoint = screenToCanvas(event.rawX, event.rawY)
        val hitObject = hitObject(canvasPoint.x, canvasPoint.y)
        if (hitObject != null && !hitObject.locked) {
            onSelectObject?.invoke(hitObject.id)
            activeObjectStart = hitObject
            activeObjectLatest = hitObject
            mode = GestureMode.Move(hitObject.id)
            return true
        }

        onSelectObject?.invoke(null)
        mode = GestureMode.CanvasTransform
        return true
    }

    private fun handleMove(event: MotionEvent): Boolean {
        if (scaleDetector.isInProgress) return true
        val dxScreen = event.rawX - lastRawX
        val dyScreen = event.rawY - lastRawY

        when (val current = mode) {
            is GestureMode.Move -> onMoveObject?.invoke(current.objectId, dxScreen / totalScale(), dyScreen / totalScale(), false)
            is GestureMode.Resize -> {
                val start = activeObjectStart ?: return true
                val center = canvasToScreen(start.centerX, start.centerY)
                val factor = (distance(center.x, center.y, event.rawX, event.rawY) / startDistance).coerceIn(0.12f, 8f)
                onResizeObject?.invoke(current.objectId, start.width * factor, start.height * factor, scaledTextSize(start, factor), false)
            }
            is GestureMode.Rotate -> {
                val start = activeObjectStart ?: return true
                val center = canvasToScreen(start.centerX, start.centerY)
                onRotateObject?.invoke(current.objectId, normalizeDegrees(angle(center.x, center.y, event.rawX, event.rawY) - startAngleOffset), false)
            }
            GestureMode.CanvasTransform -> Unit
            else -> Unit
        }

        lastRawX = event.rawX
        lastRawY = event.rawY
        return true
    }

    private fun handleUp(event: MotionEvent): Boolean {
        val endedMode = mode
        when (val current = endedMode) {
            is GestureMode.Move -> onMoveObject?.invoke(current.objectId, 0f, 0f, true)
            is GestureMode.Resize -> activeObjectLatest?.let {
                onResizeObject?.invoke(current.objectId, it.width, it.height, it.textSize.takeIf { _ -> it.type == EditorObjectType.TEXT }, true)
            }
            is GestureMode.Rotate -> activeObjectLatest?.let { onRotateObject?.invoke(current.objectId, it.rotation, true) }
            is GestureMode.Delete -> onDeleteObject?.invoke(current.objectId)
            is GestureMode.Flip -> onFlipObject?.invoke(current.objectId)
            GestureMode.CanvasTransform -> onViewportChanged?.invoke(document.viewport, true)
            else -> Unit
        }

        if (endedMode is GestureMode.Move && distance(downRawX, downRawY, event.rawX, event.rawY) < touchSlop) {
            val point = screenToCanvas(event.rawX, event.rawY)
            val hit = hitObject(point.x, point.y)
            val hitId = hit?.id
            if (hitId != null && hitId == selectedObjectId && hitId == selectedIdAtDown) {
                onEditText?.invoke(hitId)
            }
        }

        clearGesture()
        return true
    }

    private fun updateViewport(viewport: ViewportState, commit: Boolean) {
        document = document.copy(viewport = viewport)
        onViewportChanged?.invoke(viewport, commit)
        invalidate()
    }

    private fun clearGesture() {
        parent?.requestDisallowInterceptTouchEvent(false)
        mode = GestureMode.Idle
        activeObjectStart = null
        activeObjectLatest = null
        selectedIdAtDown = null
        onGuidesChanged?.invoke(emptyList())
    }

    private fun selectedObject(): EditorObject? = document.objects.firstOrNull { it.id == selectedObjectId }

    private fun hitObject(x: Float, y: Float): EditorObject? {
        return document.objects
            .asSequence()
            .filter { it.visible }
            .sortedByDescending { it.zIndex }
            .firstOrNull { obj ->
                val inverse = Matrix()
                objectMatrix(obj).invert(inverse)
                val pts = floatArrayOf(x, y)
                inverse.mapPoints(pts)
                pts[0] in 0f..obj.width && pts[1] in 0f..obj.height
            }
    }

    private fun hitHandle(rawX: Float, rawY: Float, obj: EditorObject): Handle? {
        return handleScreenPoints(obj).entries.firstOrNull { (_, point) ->
            distance(point.x, point.y, rawX, rawY) <= handleTouchRadiusPx
        }?.key
    }

    private fun drawSelection(canvas: Canvas, obj: EditorObject) {
        canvas.save()
        canvas.concat(objectMatrix(obj))
        canvas.drawRect(0f, 0f, obj.width, obj.height, selectionPaint)
        canvas.restore()

        if (!obj.locked) {
            handleCanvasPoints(obj).forEach { (handle, canvasPoint) ->
                val radius = handleVisualRadiusPx / totalScale()
                canvas.drawCircle(canvasPoint.x, canvasPoint.y, radius, handlePaint)
                canvas.drawCircle(canvasPoint.x, canvasPoint.y, radius, handleStrokePaint)
                drawHandleIcon(canvas, handle, canvasPoint, radius)
            }
        }
    }

    private fun drawGuides(canvas: Canvas) {
        guides.forEach { guide ->
            when (guide.orientation) {
                GuideOrientation.Vertical -> canvas.drawLine(guide.position, guide.start, guide.position, guide.end, guidePaint)
                GuideOrientation.Horizontal -> canvas.drawLine(guide.start, guide.position, guide.end, guide.position, guidePaint)
            }
        }
    }

    private fun handleCanvasPoints(obj: EditorObject): Map<Handle, PointF> {
        val matrix = objectMatrix(obj)
        fun map(x: Float, y: Float): PointF {
            val pts = floatArrayOf(x, y)
            matrix.mapPoints(pts)
            return PointF(pts[0], pts[1])
        }
        return mapOf(
            Handle.Delete to map(0f, 0f),
            Handle.Flip to map(obj.width, 0f),
            Handle.Rotate to map(0f, obj.height),
            Handle.Resize to map(obj.width, obj.height)
        )
    }

    private fun handleScreenPoints(obj: EditorObject): Map<Handle, PointF> {
        val matrix = Matrix(objectMatrix(obj)).apply { postConcat(viewportMatrix()) }
        fun map(x: Float, y: Float): PointF {
            val pts = floatArrayOf(x, y)
            matrix.mapPoints(pts)
            val location = IntArray(2)
            getLocationOnScreen(location)
            return PointF(pts[0] + location[0], pts[1] + location[1])
        }
        return mapOf(
            Handle.Delete to map(0f, 0f),
            Handle.Flip to map(obj.width, 0f),
            Handle.Rotate to map(0f, obj.height),
            Handle.Resize to map(obj.width, obj.height)
        )
    }

    private fun drawHandleIcon(canvas: Canvas, handle: Handle, center: PointF, radius: Float) {
        val icon = handleIcons[handle] ?: return
        val padding = radius * 0.34f
        val left = (center.x - radius + padding).toInt()
        val top = (center.y - radius + padding).toInt()
        val right = (center.x + radius - padding).toInt()
        val bottom = (center.y + radius - padding).toInt()
        icon.setBounds(left, top, right, bottom)
        icon.draw(canvas)
    }

    private fun objectMatrix(obj: EditorObject): Matrix {
        return Matrix().apply {
            postTranslate(-obj.width / 2f, -obj.height / 2f)
            if (obj.flipped) postScale(-1f, 1f)
            postRotate(obj.rotation)
            postTranslate(obj.centerX, obj.centerY)
        }
    }

    private fun viewportMatrix(): Matrix {
        return Matrix().apply {
            val base = baseScale()
            val scale = base * document.viewport.scale
            postScale(scale, scale)
            postTranslate(
                baseTranslateX() + document.viewport.panX * base,
                baseTranslateY() + document.viewport.panY * base
            )
        }
    }

    private fun screenToCanvas(rawX: Float, rawY: Float): PointF {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return viewToCanvas(rawX - location[0], rawY - location[1])
    }

    private fun viewToCanvas(x: Float, y: Float): PointF {
        val pts = floatArrayOf(x, y)
        val inverse = Matrix()
        viewportMatrix().invert(inverse)
        inverse.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    private fun canvasToScreen(x: Float, y: Float): PointF {
        val pts = floatArrayOf(x, y)
        viewportMatrix().mapPoints(pts)
        val location = IntArray(2)
        getLocationOnScreen(location)
        return PointF(pts[0] + location[0], pts[1] + location[1])
    }

    private fun baseScale(): Float {
        val horizontal = width / document.canvasWidth
        val vertical = height / document.canvasHeight
        return min(horizontal, vertical).coerceAtLeast(0.1f) * 0.9f
    }

    private fun totalScale(): Float = baseScale() * document.viewport.scale
    private fun baseTranslateX(): Float = (width - document.canvasWidth * baseScale()) / 2f
    private fun baseTranslateY(): Float = (height - document.canvasHeight * baseScale()) / 2f
    private fun ViewportState.zoomAt(nextScale: Float, focusX: Float, focusY: Float): ViewportState {
        val focusCanvas = viewToCanvas(focusX, focusY)
        val base = baseScale()
        val nextPanX = (focusX - baseTranslateX() - focusCanvas.x * base * nextScale) / base
        val nextPanY = (focusY - baseTranslateY() - focusCanvas.y * base * nextScale) / base
        return copy(scale = nextScale, panX = nextPanX, panY = nextPanY)
    }

    private fun scaledTextSize(obj: EditorObject, factor: Float): Float? {
        return if (obj.type == EditorObjectType.TEXT) obj.textSize * factor else null
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(x2 - x1, y2 - y1)
    private fun angle(cx: Float, cy: Float, x: Float, y: Float): Float = Math.toDegrees(atan2(y - cy, x - cx).toDouble()).toFloat()
    private fun normalizeDegrees(value: Float): Float = ((value % 360f) + 360f) % 360f

    private enum class Handle { Delete, Flip, Rotate, Resize }

    private sealed interface GestureMode {
        data object Idle : GestureMode
        data object CanvasTransform : GestureMode
        data class Move(val objectId: String) : GestureMode
        data class Resize(val objectId: String) : GestureMode
        data class PinchResize(val objectId: String) : GestureMode
        data class Rotate(val objectId: String) : GestureMode
        data class Delete(val objectId: String) : GestureMode
        data class Flip(val objectId: String) : GestureMode
    }

    private companion object {
        const val MIN_VIEWPORT_SCALE = 0.25f
        const val MAX_VIEWPORT_SCALE = 5f
        const val MIN_RESIZE_FACTOR = 0.12f
        const val MAX_RESIZE_FACTOR = 8f
    }
}
