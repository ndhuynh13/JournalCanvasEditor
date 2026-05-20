package com.starnest.journalcanvaseditor.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.starnest.journalcanvaseditor.R
import com.starnest.journalcanvaseditor.databinding.ActivityJournalCanvasEditorBinding
import com.starnest.journalcanvaseditor.domain.EditorAction
import com.starnest.journalcanvaseditor.domain.EditorObject
import com.starnest.journalcanvaseditor.domain.EditorObjectType
import com.starnest.journalcanvaseditor.domain.ExportStatus
import com.starnest.journalcanvaseditor.extension.safeClick
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class JournalCanvasEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityJournalCanvasEditorBinding
    private val viewModel: JournalCanvasEditorViewModel by viewModels()
    private val dialogs: EditorDialogs by lazy { EditorDialogs(this) }
    private var pendingTextPlacement: String? = null

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@registerForActivityResult
        viewModel.importImage(uri) { showToast(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJournalCanvasEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCanvasCallbacks()
        setupToolbar()
        collectState()
    }

    override fun onStop() {
        viewModel.forceSave()
        super.onStop()
    }

    private fun setupCanvasCallbacks() = with(binding.journalCanvas) {
        onSelectObject = { viewModel.dispatch(EditorAction.SelectObject(it)) }
        onMoveObject = { id, dx, dy, commit -> viewModel.dispatch(EditorAction.MoveObject(id, dx, dy, commit)) }
        onResizeObject = { id, width, height, textSize, commit ->
            viewModel.dispatch(EditorAction.ResizeObject(id, width, height, textSize, commit))
        }
        onRotateObject = { id, rotation, commit -> viewModel.dispatch(EditorAction.RotateObject(id, rotation, commit)) }
        onViewportChanged = { viewport, commit -> viewModel.dispatch(EditorAction.SetViewport(viewport, commit)) }
        onGuidesChanged = { viewModel.dispatch(EditorAction.SetSnapGuides(it)) }
        onDeleteObject = { viewModel.dispatch(EditorAction.DeleteObject(it)) }
        onFlipObject = { viewModel.dispatch(EditorAction.FlipObject(it)) }
        onCanvasTap = { x, y -> placePendingText(x, y) }
        onEditText = { id ->
            viewModel.objectById(id)
                ?.takeIf { it.type == EditorObjectType.TEXT }
                ?.let { showTextDialog(it) }
        }
    }

    private fun setupToolbar() {
        binding.actionText.safeClick {
            showTextDialog(null)
        }
        binding.actionImage.safeClick {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.actionGrid.safeClick {
            viewModel.dispatch(EditorAction.ToggleGrid)
        }
        binding.actionReset.safeClick {
            viewModel.dispatch(EditorAction.Reset)
        }

        binding.undoButton.safeClick { viewModel.dispatch(EditorAction.Undo) }
        binding.redoButton.safeClick { viewModel.dispatch(EditorAction.Redo) }
        binding.layersButton.safeClick { showLayerDialog() }
        binding.exportButton.safeClick { viewModel.exportCanvas() }
        binding.saveButton.safeClick {
            viewModel.forceSave()
            showToast(getString(R.string.saved))
        }
    }

    private fun collectState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.journalCanvas.submitState(
                        document = state.document,
                        selectedObjectId = state.selectedObjectId,
                        guides = state.guides
                    )
                    binding.undoButton.isEnabled = state.canUndo
                    binding.redoButton.isEnabled = state.canRedo
                    binding.undoButton.alpha = if (state.canUndo) 1f else 0.35f
                    binding.redoButton.alpha = if (state.canRedo) 1f else 0.35f

                    when (val status = state.exportStatus) {
                        ExportStatus.Idle -> Unit
                        ExportStatus.Running -> showToast(getString(R.string.export))
                        is ExportStatus.Success -> {
                            showToast(getString(R.string.exported))
                            viewModel.clearExportStatus()
                        }
                        is ExportStatus.Error -> {
                            showToast(status.message.ifBlank { getString(R.string.export_failed) })
                            viewModel.clearExportStatus()
                        }
                    }
                }
            }
        }
    }

    private fun showTextDialog(objectToEdit: EditorObject?) {
        dialogs.showTextDialog(objectToEdit) { value ->
            if (objectToEdit == null) {
                pendingTextPlacement = value
                binding.journalCanvas.isPlacementMode = true
                showToast(getString(R.string.tap_canvas_to_place_text))
            } else {
                viewModel.dispatch(EditorAction.UpdateText(objectToEdit.id, value))
            }
        }
    }

    private fun placePendingText(x: Float, y: Float) {
        val text = pendingTextPlacement ?: return
        pendingTextPlacement = null
        binding.journalCanvas.isPlacementMode = false
        viewModel.dispatch(EditorAction.AddText(text, centerX = x, centerY = y))
    }

    private fun showLayerDialog() {
        val state = viewModel.state.value
        val selectedObject = state.selectedObjectId?.let { selectedId ->
            state.document.objects.firstOrNull { it.id == selectedId }
        }
        if (selectedObject != null) {
            showLayerActionDialog(selectedObject)
            return
        }

        val objects = state.document.objects.sortedByDescending { it.zIndex }
        if (objects.isEmpty()) {
            showToast(getString(R.string.layers))
            return
        }

        dialogs.showLayerPicker(objects, ::showLayerActionDialog)
    }

    private fun showLayerActionDialog(obj: EditorObject) {
        dialogs.showLayerActions(
            obj = obj,
            onReorder = { viewModel.dispatch(EditorAction.ReorderObject(obj.id, it)) },
            onToggleVisibility = { viewModel.dispatch(EditorAction.SetObjectVisibility(obj.id, !obj.visible)) },
            onToggleLock = { viewModel.dispatch(EditorAction.SetObjectLocked(obj.id, !obj.locked)) },
            onDelete = { viewModel.dispatch(EditorAction.DeleteObject(obj.id)) }
        )
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
