package com.starnest.journalcanvaseditor.ui

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import com.starnest.journalcanvaseditor.R
import com.starnest.journalcanvaseditor.databinding.DialogEditorListBinding
import com.starnest.journalcanvaseditor.databinding.DialogEditorTextBinding
import com.starnest.journalcanvaseditor.databinding.ItemDialogActionBinding
import com.starnest.journalcanvaseditor.domain.EditorObject
import com.starnest.journalcanvaseditor.domain.EditorObjectType
import com.starnest.journalcanvaseditor.domain.LayerDirection

internal class EditorDialogs(
    private val context: Context
) {
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    fun showTextDialog(objectToEdit: EditorObject?, onDone: (String) -> Unit) {
        val binding = DialogEditorTextBinding.inflate(inflater)
        val dialog = createDialog(binding.root)

        binding.dialogTitle.setText(if (objectToEdit == null) R.string.add_text else R.string.edit_text)
        binding.textInput.setText(objectToEdit?.text ?: context.getString(R.string.journal_text))
        binding.textInput.setSelection(binding.textInput.text.length)
        binding.cancelButton.setOnClickListener { dialog.dismiss() }
        binding.doneButton.setOnClickListener {
            onDone(binding.textInput.text.toString().ifBlank { context.getString(R.string.journal_text) })
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            binding.textInput.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        showDialog(dialog)
    }

    fun showLayerPicker(objects: List<EditorObject>, onObjectSelected: (EditorObject) -> Unit) {
        val binding = DialogEditorListBinding.inflate(inflater)
        val dialog = createDialog(binding.root)

        binding.dialogTitle.setText(R.string.layers)
        binding.closeButton.setOnClickListener { dialog.dismiss() }
        objects.forEachIndexed { index, obj ->
            binding.itemContainer.addView(
                createActionItem(
                    label = obj.layerLabel(),
                    isLast = index == objects.lastIndex,
                    onClick = {
                        onObjectSelected(obj)
                        dialog.dismiss()
                    }
                )
            )
        }

        showDialog(dialog)
    }

    fun showLayerActions(
        obj: EditorObject,
        onReorder: (LayerDirection) -> Unit,
        onToggleVisibility: () -> Unit,
        onToggleLock: () -> Unit,
        onDelete: () -> Unit
    ) {
        val binding = DialogEditorListBinding.inflate(inflater)
        val dialog = createDialog(binding.root)

        binding.dialogTitle.text = obj.dialogTitle()
        binding.closeButton.setOnClickListener { dialog.dismiss() }
        if (obj.locked) {
            binding.itemContainer.addView(
                createActionItem(context.getString(R.string.unlock), isLast = true) {
                    onToggleLock()
                    dialog.dismiss()
                }
            )
        } else {
            val actions = listOf(
                LayerAction(context.getString(R.string.bring_forward)) { onReorder(LayerDirection.BringForward) },
                LayerAction(context.getString(R.string.send_backward)) { onReorder(LayerDirection.SendBackward) },
                LayerAction(context.getString(R.string.bring_to_front)) { onReorder(LayerDirection.BringToFront) },
                LayerAction(context.getString(R.string.send_to_back)) { onReorder(LayerDirection.SendToBack) },
                LayerAction(context.getString(R.string.hide_show)) { onToggleVisibility() },
                LayerAction(context.getString(R.string.lock_unlock)) { onToggleLock() },
                LayerAction(context.getString(R.string.delete), isDanger = true) { onDelete() }
            )
            actions.forEachIndexed { index, action ->
                binding.itemContainer.addView(
                    createActionItem(
                        label = action.label,
                        isLast = index == actions.lastIndex,
                        isDanger = action.isDanger
                    ) {
                        action.invoke()
                        dialog.dismiss()
                    }
                )
            }
        }

        showDialog(dialog)
    }

    private fun createDialog(contentView: android.view.View): Dialog {
        return Dialog(context).apply {
            setContentView(contentView)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    private fun showDialog(dialog: Dialog) {
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout((context.resources.displayMetrics.widthPixels * DIALOG_WIDTH_RATIO).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun createActionItem(
        label: String,
        isLast: Boolean = false,
        isDanger: Boolean = false,
        onClick: () -> Unit
    ): android.view.View {
        val itemBinding = ItemDialogActionBinding.inflate(inflater)
        itemBinding.actionLabel.text = label
        if (isLast) {
            itemBinding.separator.visibility = android.view.View.GONE
        }
        if (isDanger) {
            itemBinding.actionLabel.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.danger))
            itemBinding.actionLabel.setBackgroundResource(R.drawable.bg_dialog_action_danger_ripple)
        }
        itemBinding.actionLabel.setOnClickListener { onClick() }
        return itemBinding.root
    }

    private fun EditorObject.layerLabel(): String {
        val type = if (type == EditorObjectType.TEXT) text.take(MAX_LAYER_TEXT_LENGTH).ifBlank { "Text" } else "Image"
        val stateText = buildString {
            if (!visible) append(" hidden")
            if (locked) append(" locked")
        }
        return "$type$stateText"
    }

    private fun EditorObject.dialogTitle(): String {
        return if (type == EditorObjectType.TEXT) text else "Image"
    }

    private data class LayerAction(
        val label: String,
        val isDanger: Boolean = false,
        val invoke: () -> Unit
    )

    private companion object {
        const val DIALOG_WIDTH_RATIO = 0.9f
        const val MAX_LAYER_TEXT_LENGTH = 18
    }
}
