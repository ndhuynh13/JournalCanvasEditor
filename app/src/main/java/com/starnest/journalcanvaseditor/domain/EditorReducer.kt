package com.starnest.journalcanvaseditor.domain

class EditorReducer(
    private val defaultTextColor: Int,
    private val defaultText: String
) {
    fun reduce(document: EditorDocument, action: EditorAction): EditorDocument {
        return when (action) {
            is EditorAction.AddText -> document.addObject(
                EditorObject(
                    type = EditorObjectType.TEXT,
                    centerX = action.centerX ?: (document.canvasWidth / 2f),
                    centerY = action.centerY ?: (document.canvasHeight / 2f),
                    width = DEFAULT_TEXT_WIDTH,
                    height = DEFAULT_TEXT_HEIGHT,
                    text = action.text,
                    textColor = defaultTextColor,
                    zIndex = nextZIndex(document)
                )
            )

            is EditorAction.AddImage -> {
                val ratio = if (action.originalHeight > 0) {
                    action.originalWidth.toFloat() / action.originalHeight.toFloat()
                } else {
                    1f
                }
                val width = if (ratio >= 1f) MAX_IMAGE_SIDE else MAX_IMAGE_SIDE * ratio
                val height = if (ratio >= 1f) MAX_IMAGE_SIDE / ratio else MAX_IMAGE_SIDE
                document.addObject(
                    EditorObject(
                        type = EditorObjectType.IMAGE,
                        centerX = document.canvasWidth / 2f,
                        centerY = document.canvasHeight / 2f,
                        width = width.coerceAtLeast(MIN_IMAGE_SIDE),
                        height = height.coerceAtLeast(MIN_IMAGE_SIDE),
                        imagePath = action.imagePath,
                        imageOriginalWidth = action.originalWidth,
                        imageOriginalHeight = action.originalHeight,
                        zIndex = nextZIndex(document)
                    )
                )
            }

            is EditorAction.MoveObject -> document.updateObject(action.objectId) {
                it.copy(centerX = it.centerX + action.dx, centerY = it.centerY + action.dy)
            }

            is EditorAction.ResizeObject -> document.updateObject(action.objectId) {
                val resized = it.copy(
                    width = action.width.coerceAtLeast(MIN_OBJECT_SIZE),
                    height = action.height.coerceAtLeast(MIN_OBJECT_SIZE)
                )
                if (it.type == EditorObjectType.TEXT && action.textSize != null) {
                    resized.copy(textSize = action.textSize.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE))
                } else {
                    resized
                }
            }

            is EditorAction.RotateObject -> document.updateObject(action.objectId) {
                it.copy(rotation = action.rotation)
            }

            is EditorAction.UpdateText -> document.updateObject(action.objectId) {
                it.copy(text = action.text.ifBlank { defaultText })
            }

            is EditorAction.DeleteObject -> {
                val target = document.objects.firstOrNull { it.id == action.objectId }
                if (target?.locked == true) {
                    document
                } else {
                    document.copy(objects = document.objects.filterNot { it.id == action.objectId }.normalizeZByExistingLayer())
                }
            }
            is EditorAction.FlipObject -> document.updateObject(action.objectId) { it.copy(flipped = !it.flipped) }
            is EditorAction.SetViewport -> document.copy(viewport = action.viewport)
            is EditorAction.ToggleGrid -> document.copy(viewport = document.viewport.copy(showGrid = !document.viewport.showGrid))
            is EditorAction.ReorderObject -> reorder(document, action.objectId, action.direction)
            is EditorAction.SetObjectVisibility -> document.updateObject(action.objectId) { it.copy(visible = action.visible) }
            is EditorAction.SetObjectLocked -> document.setObjectLocked(action.objectId, action.locked)
            EditorAction.Reset -> EditorDocument()
            is EditorAction.SelectObject,
            is EditorAction.SetSnapGuides,
            EditorAction.Undo,
            EditorAction.Redo -> document
        }
    }

    private fun EditorDocument.addObject(editorObject: EditorObject): EditorDocument {
        return copy(objects = (objects + editorObject).normalizeZByExistingLayer())
    }

    private fun EditorDocument.updateObject(id: String, transform: (EditorObject) -> EditorObject): EditorDocument {
        return copy(objects = objects.map { if (it.id == id && !it.locked) transform(it) else it }.normalizeZByExistingLayer())
    }

    private fun EditorDocument.setObjectLocked(id: String, locked: Boolean): EditorDocument {
        return copy(objects = objects.map { if (it.id == id) it.copy(locked = locked) else it }.normalizeZByExistingLayer())
    }

    private fun reorder(document: EditorDocument, objectId: String, direction: LayerDirection): EditorDocument {
        val ordered = document.objects.sortedBy { it.zIndex }.toMutableList()
        val index = ordered.indexOfFirst { it.id == objectId }
        if (index == -1) return document
        if (ordered[index].locked) return document

        when (direction) {
            LayerDirection.BringForward -> if (index < ordered.lastIndex) {
                val item = ordered.removeAt(index)
                ordered.add(index + 1, item)
            }

            LayerDirection.SendBackward -> if (index > 0) {
                val item = ordered.removeAt(index)
                ordered.add(index - 1, item)
            }

            LayerDirection.BringToFront -> {
                val item = ordered.removeAt(index)
                ordered.add(item)
            }

            LayerDirection.SendToBack -> {
                val item = ordered.removeAt(index)
                ordered.add(0, item)
            }
        }
        return document.copy(objects = ordered.normalizeZByListOrder())
    }

    private fun nextZIndex(document: EditorDocument): Int {
        return (document.objects.maxOfOrNull { it.zIndex } ?: -1) + 1
    }

    private fun List<EditorObject>.normalizeZByExistingLayer(): List<EditorObject> {
        return sortedBy { it.zIndex }.mapIndexed { index, item -> item.copy(zIndex = index) }
    }

    private fun List<EditorObject>.normalizeZByListOrder(): List<EditorObject> {
        return mapIndexed { index, item -> item.copy(zIndex = index) }
    }

    private companion object {
        const val MIN_OBJECT_SIZE = 80f
        const val MIN_TEXT_SIZE = 12f
        const val MAX_TEXT_SIZE = 320f
        const val DEFAULT_TEXT_WIDTH = 520f
        const val DEFAULT_TEXT_HEIGHT = 180f
        const val MAX_IMAGE_SIDE = 420f
        const val MIN_IMAGE_SIDE = 160f
    }
}
