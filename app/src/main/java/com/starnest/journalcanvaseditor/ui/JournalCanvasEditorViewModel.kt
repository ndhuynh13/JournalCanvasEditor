package com.starnest.journalcanvaseditor.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starnest.journalcanvaseditor.data.CanvasExporter
import com.starnest.journalcanvaseditor.data.CanvasStateStore
import com.starnest.journalcanvaseditor.data.ImageStore
import com.starnest.journalcanvaseditor.domain.EditorAction
import com.starnest.journalcanvaseditor.domain.EditorDocument
import com.starnest.journalcanvaseditor.domain.EditorObject
import com.starnest.journalcanvaseditor.domain.EditorReducer
import com.starnest.journalcanvaseditor.domain.EditorState
import com.starnest.journalcanvaseditor.domain.ExportStatus
import com.starnest.journalcanvaseditor.domain.HistoryStore
import com.starnest.journalcanvaseditor.domain.SnapEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JournalCanvasEditorViewModel @Inject constructor(
    private val reducer: EditorReducer,
    private val history: HistoryStore,
    private val snapEngine: SnapEngine,
    private val stateStore: CanvasStateStore,
    private val imageStore: ImageStore,
    private val exporter: CanvasExporter
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var pendingTransformBefore: EditorDocument? = null
    private var autosaveJob: Job? = null

    init {
        viewModelScope.launch {
            stateStore.load()?.let { loaded ->
                _state.value = _state.value.copy(document = loaded)
            }
        }
    }

    fun dispatch(action: EditorAction) {
        when (action) {
            EditorAction.Undo -> undo()
            EditorAction.Redo -> redo()
            is EditorAction.SelectObject -> updateState { it.copy(selectedObjectId = action.objectId) }
            is EditorAction.SetSnapGuides -> updateState { it.copy(guides = action.guides) }
            is EditorAction.MoveObject -> moveObject(action)
            is EditorAction.ResizeObject -> transformObject(action.commit) {
                reducer.reduce(it, action)
            }
            is EditorAction.RotateObject -> transformObject(action.commit) {
                reducer.reduce(it, action)
            }
            is EditorAction.SetViewport -> {
                val before = _state.value.document
                val after = reducer.reduce(before, action)
                updateDocument(after, recordHistory = action.commit, before = before)
            }
            EditorAction.Reset -> {
                val before = _state.value.document
                val after = reducer.reduce(before, action)
                pendingTransformBefore = null
                snapEngine.reset()
                updateDocument(
                    document = after,
                    recordHistory = true,
                    before = before,
                    selectedObjectId = null
                )
                updateState { it.copy(guides = emptyList()) }
            }
            is EditorAction.AddImage,
            is EditorAction.AddText,
            is EditorAction.DeleteObject,
            is EditorAction.FlipObject,
            is EditorAction.ReorderObject,
            is EditorAction.SetObjectLocked,
            is EditorAction.SetObjectVisibility,
            EditorAction.ToggleGrid,
            is EditorAction.UpdateText -> applyImmediate(action)
        }
    }

    fun importImage(uri: Uri, onError: (String) -> Unit) {
        viewModelScope.launch {
            imageStore.copy(uri)
                .onSuccess {
                    dispatch(
                        EditorAction.AddImage(
                            imagePath = it.path,
                            originalWidth = it.width,
                            originalHeight = it.height
                        )
                    )
                }
                .onFailure { onError(it.message ?: "Could not load image") }
        }
    }

    fun exportCanvas() {
        val document = _state.value.document
        viewModelScope.launch {
            updateState { it.copy(exportStatus = ExportStatus.Running) }
            exporter.export(document)
                .onSuccess { uri -> updateState { it.copy(exportStatus = ExportStatus.Success(uri)) } }
                .onFailure { e -> updateState { it.copy(exportStatus = ExportStatus.Error(e.message ?: "Export failed")) } }
        }
    }

    fun clearExportStatus() {
        updateState { it.copy(exportStatus = ExportStatus.Idle) }
    }

    fun forceSave() {
        autosaveJob?.cancel()
        viewModelScope.launch { stateStore.save(_state.value.document) }
    }

    fun objectById(id: String): EditorObject? {
        return _state.value.document.objects.firstOrNull { it.id == id }
    }

    private fun moveObject(action: EditorAction.MoveObject) {
        if (!action.commit && pendingTransformBefore == null) {
            pendingTransformBefore = _state.value.document
        }

        if (action.commit) {
            pendingTransformBefore?.let { before ->
                updateDocument(_state.value.document, recordHistory = true, before = before)
            }
            pendingTransformBefore = null
            snapEngine.reset()
            updateState { it.copy(guides = emptyList()) }
            return
        }

        val beforeMove = _state.value.document
        val raw = reducer.reduce(beforeMove, action)
        val moving = raw.objects.firstOrNull { it.id == action.objectId }
        if (moving == null) {
            updateDocument(raw, recordHistory = false)
            return
        }

        val snap = snapEngine.snap(raw, moving)
        val snapped = if (snap.dx != 0f || snap.dy != 0f) {
            reducer.reduce(raw, EditorAction.MoveObject(action.objectId, snap.dx, snap.dy, commit = false))
        } else {
            raw
        }
        updateState {
            it.copy(
                document = snapped,
                selectedObjectId = action.objectId,
                guides = snap.guides,
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
        scheduleSave()
    }

    private fun transformObject(commit: Boolean, transform: (EditorDocument) -> EditorDocument) {
        if (!commit && pendingTransformBefore == null) {
            pendingTransformBefore = _state.value.document
        }
        val after = transform(_state.value.document)
        if (commit) {
            val before = pendingTransformBefore ?: _state.value.document
            pendingTransformBefore = null
            updateDocument(after, recordHistory = true, before = before)
        } else {
            updateDocument(after, recordHistory = false)
        }
    }

    private fun applyImmediate(action: EditorAction) {
        val before = _state.value.document
        val after = reducer.reduce(before, action)
        val selected = when (action) {
            is EditorAction.AddText,
            is EditorAction.AddImage -> after.objects.maxByOrNull { it.zIndex }?.id
            is EditorAction.DeleteObject -> null
            else -> _state.value.selectedObjectId
        }
        updateDocument(after, recordHistory = true, before = before, selectedObjectId = selected)
    }

    private fun updateDocument(
        document: EditorDocument,
        recordHistory: Boolean,
        before: EditorDocument = _state.value.document,
        selectedObjectId: String? = _state.value.selectedObjectId
    ) {
        if (recordHistory) history.record(before, document)
        updateState {
            it.copy(
                document = document,
                selectedObjectId = selectedObjectId,
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
        scheduleSave()
    }

    private fun undo() {
        val current = _state.value.document
        history.undo(current)?.let { previous ->
            updateState {
                it.copy(
                    document = previous,
                    selectedObjectId = null,
                    guides = emptyList(),
                    canUndo = history.canUndo,
                    canRedo = history.canRedo
                )
            }
            scheduleSave()
        }
    }

    private fun redo() {
        val current = _state.value.document
        history.redo(current)?.let { next ->
            updateState {
                it.copy(
                    document = next,
                    selectedObjectId = null,
                    guides = emptyList(),
                    canUndo = history.canUndo,
                    canRedo = history.canRedo
                )
            }
            scheduleSave()
        }
    }

    private fun updateState(transform: (EditorState) -> EditorState) {
        _state.value = transform(_state.value)
    }

    private fun scheduleSave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            stateStore.save(_state.value.document)
        }
    }

    private companion object {
        const val AUTOSAVE_DELAY_MS = 400L
    }
}
