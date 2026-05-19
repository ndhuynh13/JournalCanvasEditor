package com.starnest.journalcanvaseditor.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.starnest.journalcanvaseditor.model.CanvasState
import com.starnest.journalcanvaseditor.persistence.CanvasStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class JournalCanvasEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val store = CanvasStateStore(application.applicationContext)

    private val _restoredState = MutableStateFlow<CanvasState?>(null)
    val restoredState: StateFlow<CanvasState?> = _restoredState.asStateFlow()

    init {
        viewModelScope.launch {
            _restoredState.value = store.load()
        }
    }

    fun saveCanvas(state: CanvasState) {
        viewModelScope.launch {
            store.save(state)
        }
    }

    fun resetCanvas(onDone: () -> Unit) {
        viewModelScope.launch {
            store.clear()
            _restoredState.value = CanvasState()
            onDone()
        }
    }

    fun copyImage(uri: Uri, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { store.copyImage(uri) })
        }
    }
}
