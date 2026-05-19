package com.starnest.journalcanvaseditor.ui

import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.starnest.journalcanvaseditor.R
import com.starnest.journalcanvaseditor.databinding.ActivityJournalCanvasEditorBinding
import com.starnest.journalcanvaseditor.databinding.ItemEditorActionBinding
import com.starnest.journalcanvaseditor.domain.EditorAction
import com.starnest.journalcanvaseditor.domain.EditorObject
import com.starnest.journalcanvaseditor.domain.EditorObjectType
import com.starnest.journalcanvaseditor.domain.ExportStatus
import com.starnest.journalcanvaseditor.domain.LayerDirection
import com.starnest.journalcanvaseditor.extension.dp
import com.starnest.journalcanvaseditor.extension.safeClick
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class JournalCanvasEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityJournalCanvasEditorBinding
    private val viewModel: JournalCanvasEditorViewModel by viewModels()

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
        onEditText = { id ->
            viewModel.objectById(id)
                ?.takeIf { it.type == EditorObjectType.TEXT }
                ?.let { showTextDialog(it) }
        }
    }

    private fun setupToolbar() {
        setupAction(binding.actionText, R.drawable.ic_text, R.string.text) {
            showTextDialog(null)
        }
        setupAction(binding.actionImage, R.drawable.ic_image, R.string.image) {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        setupAction(binding.actionGrid, R.drawable.ic_grid, R.string.grid) {
            viewModel.dispatch(EditorAction.ToggleGrid)
        }
        setupAction(binding.actionReset, R.drawable.ic_reset, R.string.reset) {
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

    private fun setupAction(
        action: ItemEditorActionBinding,
        iconRes: Int,
        labelRes: Int,
        onClick: () -> Unit
    ) {
        action.actionIcon.setImageResource(iconRes)
        action.actionLabel.setText(labelRes)
        action.root.safeClick { onClick() }
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
        val input = EditText(this).apply {
            setText(objectToEdit?.text ?: getString(R.string.journal_text))
            setSelection(text.length)
            textSize = 18f
            setSingleLine(false)
            minLines = 2
            setPadding(16.dp, 10.dp, 16.dp, 10.dp)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (objectToEdit == null) R.string.add_text else R.string.edit_text)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.done) { _, _ ->
                val value = input.text.toString().ifBlank { getString(R.string.journal_text) }
                if (objectToEdit == null) {
                    viewModel.dispatch(EditorAction.AddText(value))
                } else {
                    viewModel.dispatch(EditorAction.UpdateText(objectToEdit.id, value))
                }
            }
            .create()

        dialog.setOnShowListener {
            input.requestFocus()
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.show()
    }

    private fun showLayerDialog() {
        val state = viewModel.state.value
        val objects = state.document.objects.sortedByDescending { it.zIndex }
        if (objects.isEmpty()) {
            showToast(getString(R.string.layers))
            return
        }

        val labels = objects.map { obj ->
            val type = if (obj.type == EditorObjectType.TEXT) obj.text.take(18).ifBlank { "Text" } else "Image"
            val stateText = buildString {
                if (!obj.visible) append(" hidden")
                if (obj.locked) append(" locked")
            }
            "$type$stateText"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.layers)
            .setItems(labels) { _, which ->
                showLayerActionDialog(objects[which])
            }
            .show()
    }

    private fun showLayerActionDialog(obj: EditorObject) {
        val actions = arrayOf(
            getString(R.string.bring_forward),
            getString(R.string.send_backward),
            getString(R.string.bring_to_front),
            getString(R.string.send_to_back),
            getString(R.string.hide_show),
            getString(R.string.lock_unlock),
            getString(R.string.delete)
        )
        AlertDialog.Builder(this)
            .setTitle(if (obj.type == EditorObjectType.TEXT) obj.text else "Image")
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> viewModel.dispatch(EditorAction.ReorderObject(obj.id, LayerDirection.BringForward))
                    1 -> viewModel.dispatch(EditorAction.ReorderObject(obj.id, LayerDirection.SendBackward))
                    2 -> viewModel.dispatch(EditorAction.ReorderObject(obj.id, LayerDirection.BringToFront))
                    3 -> viewModel.dispatch(EditorAction.ReorderObject(obj.id, LayerDirection.SendToBack))
                    4 -> viewModel.dispatch(EditorAction.SetObjectVisibility(obj.id, !obj.visible))
                    5 -> viewModel.dispatch(EditorAction.SetObjectLocked(obj.id, !obj.locked))
                    6 -> viewModel.dispatch(EditorAction.DeleteObject(obj.id))
                }
            }
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
