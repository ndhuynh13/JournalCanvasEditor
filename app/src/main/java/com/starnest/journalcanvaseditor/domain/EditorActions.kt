package com.starnest.journalcanvaseditor.domain

sealed interface EditorAction {
    data class AddText(val text: String) : EditorAction
    data class AddImage(
        val imagePath: String,
        val originalWidth: Int,
        val originalHeight: Int
    ) : EditorAction

    data class SelectObject(val objectId: String?) : EditorAction
    data class MoveObject(val objectId: String, val dx: Float, val dy: Float, val commit: Boolean) : EditorAction
    data class ResizeObject(
        val objectId: String,
        val width: Float,
        val height: Float,
        val textSize: Float?,
        val commit: Boolean
    ) : EditorAction
    data class RotateObject(val objectId: String, val rotation: Float, val commit: Boolean) : EditorAction
    data class UpdateText(val objectId: String, val text: String) : EditorAction
    data class DeleteObject(val objectId: String) : EditorAction
    data class FlipObject(val objectId: String) : EditorAction
    data class SetViewport(val viewport: ViewportState, val commit: Boolean = false) : EditorAction
    data class SetSnapGuides(val guides: List<SnapGuide>) : EditorAction
    data class ReorderObject(val objectId: String, val direction: LayerDirection) : EditorAction
    data class SetObjectVisibility(val objectId: String, val visible: Boolean) : EditorAction
    data class SetObjectLocked(val objectId: String, val locked: Boolean) : EditorAction
    data object ToggleGrid : EditorAction
    data object Undo : EditorAction
    data object Redo : EditorAction
    data object Reset : EditorAction
}

enum class LayerDirection {
    BringForward,
    SendBackward,
    BringToFront,
    SendToBack
}
