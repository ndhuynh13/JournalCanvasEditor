package com.starnest.journalcanvaseditor.domain

internal sealed interface EditorHistoryCommand {
    fun undo(document: EditorDocument): EditorDocument
    fun redo(document: EditorDocument): EditorDocument
}

internal object EditorHistoryCommandFactory {
    fun create(before: EditorDocument, after: EditorDocument): EditorHistoryCommand? {
        if (before == after) return null

        val commands = buildList {
            createObjectCommand(before, after)?.let(::add)
            if (before.viewport != after.viewport) {
                add(ViewportCommand(before.viewport, after.viewport))
            }
        }

        return when (commands.size) {
            0 -> ReplaceDocumentCommand(before, after)
            1 -> commands.first()
            else -> CompositeCommand(commands)
        }
    }

    private fun createObjectCommand(before: EditorDocument, after: EditorDocument): EditorHistoryCommand? {
        val beforeById = before.objects.associateBy { it.id }
        val afterById = after.objects.associateBy { it.id }
        val addedObjects = after.objects.filter { it.id !in beforeById }
        val deletedObjects = before.objects.filter { it.id !in afterById }
        val beforeOrder = before.objects.sortedBy { it.zIndex }.map { it.id }
        val afterOrder = after.objects.sortedBy { it.zIndex }.map { it.id }

        if (addedObjects.isNotEmpty() || deletedObjects.isNotEmpty()) {
            val commands = buildList {
                if (deletedObjects.isNotEmpty()) {
                    add(DeleteObjectsCommand(deletedObjects, beforeOrder, afterOrder))
                }
                if (addedObjects.isNotEmpty()) {
                    add(AddObjectsCommand(addedObjects, beforeOrder, afterOrder))
                }
            }
            return if (commands.size == 1) commands.first() else CompositeCommand(commands)
        }

        val isLayerOnlyChange = beforeOrder != afterOrder &&
            beforeOrder.size == afterOrder.size &&
            beforeOrder.toSet() == afterOrder.toSet() &&
            before.objects.all { beforeObject ->
                val afterObject = afterById[beforeObject.id] ?: return@all false
                beforeObject.withoutLayer() == afterObject.withoutLayer()
            }
        if (isLayerOnlyChange) {
            return ReorderObjectsCommand(beforeOrder, afterOrder)
        }

        val changes = after.objects.mapNotNull { afterObject ->
            val beforeObject = beforeById[afterObject.id] ?: return@mapNotNull null
            if (beforeObject == afterObject) null else ObjectChange(beforeObject, afterObject)
        }
        return changes.takeIf { it.isNotEmpty() }?.let(::UpdateObjectsCommand)
    }

    private fun EditorObject.withoutLayer(): EditorObject = copy(zIndex = 0)
}

private data class AddObjectsCommand(
    private val objects: List<EditorObject>,
    private val beforeOrder: List<String>,
    private val afterOrder: List<String>
) : EditorHistoryCommand {
    override fun undo(document: EditorDocument): EditorDocument {
        val ids = objects.mapTo(mutableSetOf()) { it.id }
        return document
            .copy(objects = document.objects.filterNot { it.id in ids })
            .reorderByIds(beforeOrder)
    }

    override fun redo(document: EditorDocument): EditorDocument {
        val existingIds = document.objects.mapTo(mutableSetOf()) { it.id }
        return document
            .copy(objects = document.objects + objects.filterNot { it.id in existingIds })
            .reorderByIds(afterOrder)
    }
}

private data class DeleteObjectsCommand(
    private val objects: List<EditorObject>,
    private val beforeOrder: List<String>,
    private val afterOrder: List<String>
) : EditorHistoryCommand {
    override fun undo(document: EditorDocument): EditorDocument {
        val existingIds = document.objects.mapTo(mutableSetOf()) { it.id }
        return document
            .copy(objects = document.objects + objects.filterNot { it.id in existingIds })
            .reorderByIds(beforeOrder)
    }

    override fun redo(document: EditorDocument): EditorDocument {
        val ids = objects.mapTo(mutableSetOf()) { it.id }
        return document
            .copy(objects = document.objects.filterNot { it.id in ids })
            .reorderByIds(afterOrder)
    }
}

private data class UpdateObjectsCommand(
    private val changes: List<ObjectChange>
) : EditorHistoryCommand {
    override fun undo(document: EditorDocument): EditorDocument = document.replaceObjects(changes.associate { it.before.id to it.before })
    override fun redo(document: EditorDocument): EditorDocument = document.replaceObjects(changes.associate { it.after.id to it.after })
}

private data class ReorderObjectsCommand(
    private val beforeOrder: List<String>,
    private val afterOrder: List<String>
) : EditorHistoryCommand {
    override fun undo(document: EditorDocument): EditorDocument = document.reorderByIds(beforeOrder)
    override fun redo(document: EditorDocument): EditorDocument = document.reorderByIds(afterOrder)
}

private data class ViewportCommand(
    private val before: ViewportState,
    private val after: ViewportState
) : EditorHistoryCommand {
    override fun undo(document: EditorDocument): EditorDocument = document.copy(viewport = before)
    override fun redo(document: EditorDocument): EditorDocument = document.copy(viewport = after)
}

private data class ReplaceDocumentCommand(
    private val before: EditorDocument,
    private val after: EditorDocument
) : EditorHistoryCommand {
    override fun undo(document: EditorDocument): EditorDocument = before
    override fun redo(document: EditorDocument): EditorDocument = after
}

private data class CompositeCommand(
    private val commands: List<EditorHistoryCommand>
) : EditorHistoryCommand {
    override fun undo(document: EditorDocument): EditorDocument {
        return commands.asReversed().fold(document) { current, command -> command.undo(current) }
    }

    override fun redo(document: EditorDocument): EditorDocument {
        return commands.fold(document) { current, command -> command.redo(current) }
    }
}

private data class ObjectChange(
    val before: EditorObject,
    val after: EditorObject
)

private fun EditorDocument.replaceObjects(replacements: Map<String, EditorObject>): EditorDocument {
    return copy(objects = objects.map { replacements[it.id] ?: it }.normalizeByLayer())
}

private fun EditorDocument.reorderByIds(order: List<String>): EditorDocument {
    val objectsById = objects.associateBy { it.id }
    val ordered = order.mapNotNull(objectsById::get)
    val remaining = objects
        .filterNot { it.id in order }
        .sortedBy { it.zIndex }
    return copy(objects = (ordered + remaining).normalizeByListOrder())
}

private fun List<EditorObject>.normalizeByLayer(): List<EditorObject> {
    return sortedBy { it.zIndex }.normalizeByListOrder()
}

private fun List<EditorObject>.normalizeByListOrder(): List<EditorObject> {
    return mapIndexed { index, item -> item.copy(zIndex = index) }
}
