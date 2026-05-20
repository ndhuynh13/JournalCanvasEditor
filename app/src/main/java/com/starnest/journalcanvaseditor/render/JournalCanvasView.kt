package com.starnest.journalcanvaseditor.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import com.starnest.journalcanvaseditor.R
import com.starnest.journalcanvaseditor.domain.EditorDocument
import com.starnest.journalcanvaseditor.domain.EditorObject
import com.starnest.journalcanvaseditor.domain.EditorObjectType
import com.starnest.journalcanvaseditor.domain.GuideOrientation
import com.starnest.journalcanvaseditor.domain.SnapGuide
import com.starnest.journalcanvaseditor.domain.ViewportState
import kotlin.math.atan2
import kotlin.math.hypot

class JournalCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val bitmapCache = BitmapCache()
    private val renderer = CanvasRenderer(context, bitmapCache)
    private val viewportTransformer = ViewportTransformer()
    private val hitTester = ObjectHitTester(viewportTransformer)
    private val spatialIndex = CanvasObjectSpatialIndex()
    private val selectionRenderer = SelectionRenderer(context, hitTester)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handleTouchRadiusPx = 16f * resources.displayMetrics.density

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.canvas_selection)
        strokeWidth = 1.5f * resources.displayMetrics.density
    }

    private var document = EditorDocument()
    private var selectedObjectId: String? = null
    private var guides: List<SnapGuide> = emptyList()
    private var visibleObjectsForDraw: List<EditorObject> = emptyList()
    private var indexedObjectsSource: List<EditorObject>? = null
    private var indexedCanvasWidth = Float.NaN
    private var indexedCanvasHeight = Float.NaN

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
    var onCanvasTap: ((Float, Float) -> Unit)? = null
    var isPlacementMode: Boolean = false

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
                startCanvasPan()
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
        rebuildRenderCachesIfNeeded(document)
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
            objectsToRender = visibleObjectsForDraw,
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
            MotionEvent.ACTION_UP -> {
                performClick()
                handleUp(event)
            }
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

        if (isPlacementMode) {
            mode = GestureMode.PlaceObject
            return true
        }

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
        if (hitObject != null) {
            onSelectObject?.invoke(hitObject.id)
            if (hitObject.locked) {
                mode = GestureMode.Idle
                return true
            }
            activeObjectStart = hitObject
            activeObjectLatest = hitObject
            mode = GestureMode.Move(hitObject.id)
            return true
        }

        onSelectObject?.invoke(null)
        startCanvasPan()
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
            GestureMode.CanvasTransform -> panCanvasBy(dxScreen, dyScreen)
            else -> Unit
        }

        lastRawX = event.rawX
        lastRawY = event.rawY
        return true
    }

    private fun handleUp(event: MotionEvent): Boolean {
        val endedMode = mode
        val isTap = distance(downRawX, downRawY, event.rawX, event.rawY) < touchSlop
        when (val current = endedMode) {
            is GestureMode.Move -> onMoveObject?.invoke(current.objectId, 0f, 0f, true)
            is GestureMode.Resize -> activeObjectLatest?.let {
                onResizeObject?.invoke(current.objectId, it.width, it.height, it.textSize.takeIf { _ -> it.type == EditorObjectType.TEXT }, true)
            }
            is GestureMode.Rotate -> activeObjectLatest?.let { onRotateObject?.invoke(current.objectId, it.rotation, true) }
            is GestureMode.Delete -> onDeleteObject?.invoke(current.objectId)
            is GestureMode.Flip -> onFlipObject?.invoke(current.objectId)
            GestureMode.CanvasTransform -> commitCanvasTransform()
            GestureMode.PlaceObject -> handleCanvasPlacementTap(event)
            else -> Unit
        }

        if (endedMode is GestureMode.Move && isTap) {
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

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleCanvasPlacementTap(event: MotionEvent) {
        if (distance(downRawX, downRawY, event.rawX, event.rawY) >= touchSlop) return
        val point = screenToCanvas(event.rawX, event.rawY)
        if (point.x in 0f..document.canvasWidth && point.y in 0f..document.canvasHeight) {
            onCanvasTap?.invoke(point.x, point.y)
        }
    }

    private fun updateViewport(viewport: ViewportState, commit: Boolean) {
        document = document.copy(viewport = viewport)
        onViewportChanged?.invoke(viewport, commit)
        invalidate()
    }

    private fun startCanvasPan() {
        mode = GestureMode.CanvasTransform
    }

    private fun panCanvasBy(dxScreen: Float, dyScreen: Float) {
        if (dxScreen == 0f && dyScreen == 0f) return
        updateViewport(viewportTransformer.panByScreenDelta(document, width, height, dxScreen, dyScreen), commit = false)
    }

    private fun commitCanvasTransform() {
        onViewportChanged?.invoke(document.viewport, true)
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
        return hitTester.hitObject(x, y, spatialIndex.query(x, y))
    }

    private fun rebuildRenderCachesIfNeeded(document: EditorDocument) {
        if (
            indexedObjectsSource === document.objects &&
            indexedCanvasWidth == document.canvasWidth &&
            indexedCanvasHeight == document.canvasHeight
        ) {
            return
        }

        visibleObjectsForDraw = document.objects
            .asSequence()
            .filter { it.visible }
            .sortedBy { it.zIndex }
            .toList()
        spatialIndex.rebuild(document, visibleObjectsForDraw)
        indexedObjectsSource = document.objects
        indexedCanvasWidth = document.canvasWidth
        indexedCanvasHeight = document.canvasHeight
    }

    private fun hitHandle(rawX: Float, rawY: Float, obj: EditorObject): Handle? {
        return hitTester.hitHandle(rawX, rawY, obj, this, document, handleTouchRadiusPx)?.toHandle()
    }

    private fun drawSelection(canvas: Canvas, obj: EditorObject) {
        selectionRenderer.draw(canvas, obj, totalScale())
    }

    private fun drawGuides(canvas: Canvas) {
        for (guide in guides) {
            when (guide.orientation) {
                GuideOrientation.Vertical -> canvas.drawLine(guide.position, guide.start, guide.position, guide.end, guidePaint)
                GuideOrientation.Horizontal -> canvas.drawLine(guide.start, guide.position, guide.end, guide.position, guidePaint)
            }
        }
    }

    private fun viewportMatrix(): android.graphics.Matrix {
        return viewportTransformer.viewportMatrix(document, width, height)
    }

    private fun screenToCanvas(rawX: Float, rawY: Float): PointF {
        return viewportTransformer.rawToCanvas(document, this, rawX, rawY)
    }

    private fun viewToCanvas(x: Float, y: Float): PointF {
        return viewportTransformer.viewToCanvas(document, width, height, x, y)
    }

    private fun canvasToScreen(x: Float, y: Float): PointF {
        return viewportTransformer.canvasToRaw(document, this, x, y)
    }

    private fun totalScale(): Float = viewportTransformer.totalScale(document, width, height)
    private fun ViewportState.zoomAt(nextScale: Float, focusX: Float, focusY: Float): ViewportState {
        return viewportTransformer.zoomAt(document, width, height, nextScale, focusX, focusY)
    }

    private fun scaledTextSize(obj: EditorObject, factor: Float): Float? {
        return if (obj.type == EditorObjectType.TEXT) obj.textSize * factor else null
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(x2 - x1, y2 - y1)
    private fun angle(cx: Float, cy: Float, x: Float, y: Float): Float = Math.toDegrees(atan2(y - cy, x - cx).toDouble()).toFloat()
    private fun normalizeDegrees(value: Float): Float = ((value % 360f) + 360f) % 360f

    private enum class Handle { Delete, Flip, Rotate, Resize }

    private fun CanvasHandle.toHandle(): Handle {
        return when (this) {
            CanvasHandle.Delete -> Handle.Delete
            CanvasHandle.Flip -> Handle.Flip
            CanvasHandle.Rotate -> Handle.Rotate
            CanvasHandle.Resize -> Handle.Resize
        }
    }

    private sealed interface GestureMode {
        data object Idle : GestureMode
        data object CanvasTransform : GestureMode
        data class Move(val objectId: String) : GestureMode
        data class Resize(val objectId: String) : GestureMode
        data class PinchResize(val objectId: String) : GestureMode
        data class Rotate(val objectId: String) : GestureMode
        data class Delete(val objectId: String) : GestureMode
        data class Flip(val objectId: String) : GestureMode
        data object PlaceObject : GestureMode
    }

    private companion object {
        const val MIN_VIEWPORT_SCALE = 0.25f
        const val MAX_VIEWPORT_SCALE = 5f
        const val MIN_RESIZE_FACTOR = 0.12f
        const val MAX_RESIZE_FACTOR = 8f
    }
}
