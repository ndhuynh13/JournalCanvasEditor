package com.starnest.journalcanvaseditor.domain

import java.util.UUID

const val DEFAULT_CANVAS_WIDTH = 1080f
const val DEFAULT_CANVAS_HEIGHT = 1440f

data class EditorState(
    val document: EditorDocument = EditorDocument(),
    val selectedObjectId: String? = null,
    val guides: List<SnapGuide> = emptyList(),
    val exportStatus: ExportStatus = ExportStatus.Idle,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

data class EditorDocument(
    val canvasWidth: Float = DEFAULT_CANVAS_WIDTH,
    val canvasHeight: Float = DEFAULT_CANVAS_HEIGHT,
    val viewport: ViewportState = ViewportState(),
    val objects: List<EditorObject> = emptyList()
)

data class ViewportState(
    val scale: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val showGrid: Boolean = false
)

data class EditorObject(
    val id: String = UUID.randomUUID().toString(),
    val type: EditorObjectType,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f,
    val zIndex: Int = 0,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val text: String = "",
    val textSize: Float = 64f,
    val textColor: Int? = null,
    val typefaceStyle: Int = 1,
    val imagePath: String = "",
    val imageOriginalWidth: Int = 0,
    val imageOriginalHeight: Int = 0,
    val flipped: Boolean = false
) {
    val left: Float get() = centerX - width / 2f
    val right: Float get() = centerX + width / 2f
    val top: Float get() = centerY - height / 2f
    val bottom: Float get() = centerY + height / 2f
}

enum class EditorObjectType {
    TEXT,
    IMAGE
}

data class SnapGuide(
    val orientation: GuideOrientation,
    val position: Float,
    val start: Float,
    val end: Float
)

enum class GuideOrientation {
    Vertical,
    Horizontal
}

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data object Running : ExportStatus
    data class Success(val uriString: String) : ExportStatus
    data class Error(val message: String) : ExportStatus
}
