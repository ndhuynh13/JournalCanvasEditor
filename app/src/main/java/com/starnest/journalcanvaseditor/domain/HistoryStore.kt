package com.starnest.journalcanvaseditor.domain

import javax.inject.Inject

class HistoryStore @Inject constructor() {
    private val capacity: Int = 50
    private val undoStack = ArrayDeque<EditorDocument>()
    private val redoStack = ArrayDeque<EditorDocument>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun record(before: EditorDocument, after: EditorDocument) {
        if (before == after) return
        undoStack.addLast(before)
        while (undoStack.size > capacity) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(current: EditorDocument): EditorDocument? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        return previous
    }

    fun redo(current: EditorDocument): EditorDocument? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
