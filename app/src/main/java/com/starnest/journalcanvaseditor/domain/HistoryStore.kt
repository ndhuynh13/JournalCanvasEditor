package com.starnest.journalcanvaseditor.domain

import javax.inject.Inject

class HistoryStore @Inject constructor() {
    private val capacity: Int = 50
    private val undoStack = ArrayDeque<EditorHistoryCommand>()
    private val redoStack = ArrayDeque<EditorHistoryCommand>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun record(before: EditorDocument, after: EditorDocument) {
        val command = EditorHistoryCommandFactory.create(before, after) ?: return
        undoStack.addLast(command)
        while (undoStack.size > capacity) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(current: EditorDocument): EditorDocument? {
        val command = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(command)
        return command.undo(current)
    }

    fun redo(current: EditorDocument): EditorDocument? {
        val command = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(command)
        return command.redo(current)
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
