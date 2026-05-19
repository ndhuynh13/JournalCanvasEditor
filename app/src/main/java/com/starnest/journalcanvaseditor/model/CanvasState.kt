package com.starnest.journalcanvaseditor.model

data class CanvasState(
    val canvasScale: Float = 1f,
    val canvasTranslationX: Float = 0f,
    val canvasTranslationY: Float = 0f,
    val objects: List<CanvasObjectState> = emptyList()
)

data class CanvasObjectState(
    val id: String,
    val type: CanvasObjectType,
    val x: Float,
    val y: Float,
    val width: Int,
    val height: Int,
    val rotation: Float,
    val text: String = "",
    val imagePath: String = "",
    val flipped: Boolean = false
)

enum class CanvasObjectType {
    TEXT,
    IMAGE
}
