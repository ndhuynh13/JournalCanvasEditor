package com.starnest.journalcanvaseditor.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.MotionEvent
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import coil3.load
import com.starnest.journalcanvaseditor.R
import com.starnest.journalcanvaseditor.databinding.ActivityJournalCanvasEditorBinding
import com.starnest.journalcanvaseditor.databinding.ItemEditorActionBinding
import com.starnest.journalcanvaseditor.extension.dp
import com.starnest.journalcanvaseditor.extension.isTouchInsideRaw
import com.starnest.journalcanvaseditor.extension.safeClick
import com.starnest.journalcanvaseditor.model.CanvasObjectState
import com.starnest.journalcanvaseditor.model.CanvasObjectType
import com.starnest.journalcanvaseditor.model.CanvasState
import com.starnest.journalcanvaseditor.view.ImageStickerView
import com.starnest.journalcanvaseditor.view.StickerView
import com.starnest.journalcanvaseditor.view.TextStickerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class JournalCanvasEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityJournalCanvasEditorBinding
    private val viewModel: JournalCanvasEditorViewModel by viewModels()

    private var selectedSticker: StickerView? = null
    private var hasAppliedRestoredState = false

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@registerForActivityResult
        viewModel.copyImage(uri) { result ->
            result
                .onSuccess { addImageSticker(it, shouldSave = true) }
                .onFailure { showToast(getString(R.string.image_not_loaded)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJournalCanvasEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCanvas()
        setupToolbar()
        observeState()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val selected = selectedSticker
            val touchesSelected = selected?.isTouchInsideRaw(event.rawX, event.rawY) == true
            val touchesToolbar = binding.bottomBar.isTouchInsideRaw(event.rawX, event.rawY) ||
                binding.topBar.isTouchInsideRaw(event.rawX, event.rawY)
            if (selected != null && !touchesSelected && !touchesToolbar) {
                selectSticker(null)
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun setupCanvas() {
        binding.canvasViewport.bindCanvas(binding.canvasSurface)
        binding.canvasViewport.onTransformChanged = { saveCurrentState() }
    }

    private fun setupToolbar() {
        setupAction(binding.actionText, R.drawable.ic_text, R.string.text) {
            val selectedText = selectedSticker as? TextStickerView
            showTextDialog(selectedText)
        }
        setupAction(binding.actionImage, R.drawable.ic_image, R.string.image) {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        setupAction(binding.actionGrid, R.drawable.ic_grid, R.string.grid) {
            binding.gridOverlay.isVisible = !binding.gridOverlay.isVisible
        }
        setupAction(binding.actionReset, R.drawable.ic_reset, R.string.reset) {
            resetCanvas()
        }

        binding.saveButton.safeClick {
            saveCurrentState()
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

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.restoredState.collectLatest { state ->
                if (state != null && !hasAppliedRestoredState) {
                    hasAppliedRestoredState = true
                    restoreState(state)
                }
            }
        }
    }

    private fun showTextDialog(stickerToEdit: TextStickerView?) {
        val input = EditText(this).apply {
            setText(stickerToEdit?.text ?: getString(R.string.journal_text))
            setSelection(text.length)
            textSize = 18f
            setSingleLine(false)
            minLines = 2
            setPadding(16.dp, 10.dp, 16.dp, 10.dp)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (stickerToEdit == null) R.string.add_text else R.string.edit_text)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.done) { _, _ ->
                val value = input.text.toString().ifBlank { getString(R.string.journal_text) }
                if (stickerToEdit == null) {
                    addTextSticker(value, shouldSave = true)
                } else {
                    stickerToEdit.text = value
                    saveCurrentState()
                }
            }
            .create()

        dialog.setOnShowListener {
            input.requestFocus()
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.show()
    }

    private fun addTextSticker(text: String, state: CanvasObjectState? = null, shouldSave: Boolean = false) {
        val sticker = TextStickerView(this).apply {
            this.text = text
            layoutParams = stickerLayoutParams(state, 220.dp, 104.dp)
            rotation = state?.rotation ?: 0f
        }
        addSticker(sticker)
        selectSticker(sticker)
        if (shouldSave) saveCurrentState()
    }

    private fun addImageSticker(path: String, state: CanvasObjectState? = null, shouldSave: Boolean = false) {
        val file = File(path)
        if (!file.exists()) return

        val sticker = ImageStickerView(this).apply {
            layoutParams = stickerLayoutParams(state, 220.dp, 220.dp)
            rotation = state?.rotation ?: 0f
            imageView.scaleX = if (state?.flipped == true) -1f else 1f
            imageView.tag = path
            imageView.load(file)
        }
        addSticker(sticker)
        selectSticker(sticker)
        if (shouldSave) saveCurrentState()
    }

    private fun stickerLayoutParams(
        state: CanvasObjectState?,
        defaultWidth: Int,
        defaultHeight: Int
    ): FrameLayout.LayoutParams {
        val width = state?.width?.coerceAtLeast(72.dp) ?: defaultWidth
        val height = state?.height?.coerceAtLeast(72.dp) ?: defaultHeight
        val left = state?.x?.toInt() ?: ((binding.canvasSurface.width - width) / 2).coerceAtLeast(24.dp)
        val top = state?.y?.toInt() ?: ((binding.canvasSurface.height - height) / 2).coerceAtLeast(24.dp)

        return FrameLayout.LayoutParams(width, height).apply {
            leftMargin = left
            topMargin = top
        }
    }

    private fun addSticker(sticker: StickerView) {
        binding.canvasSurface.addView(sticker)
        sticker.setStickerEventListener(object : StickerView.StickerEventListener {
            override fun onStickerSelected(sticker: StickerView) = selectSticker(sticker)
            override fun onStickerMoved(sticker: StickerView, dx: Float, dy: Float) = Unit
            override fun onStickerScaled(sticker: StickerView, scaleFactor: Float) = Unit
            override fun onStickerRotated(sticker: StickerView, angle: Float) = Unit
            override fun onStickerDeleted(sticker: StickerView) {
                if (selectedSticker == sticker) selectedSticker = null
                saveCurrentState()
            }
            override fun onStickerFlipped(sticker: StickerView) = Unit
            override fun onStickerReleased(sticker: StickerView) = saveCurrentState()
        })
    }

    private fun selectSticker(sticker: StickerView?) {
        selectedSticker = sticker
        binding.canvasSurface.children.filterIsInstance<StickerView>().forEach {
            val selected = it == sticker
            it.showDashedBorder(selected)
            it.visibleAction(selected)
        }
    }

    private fun saveCurrentState() {
        val objects = binding.canvasSurface.children
            .filterIsInstance<StickerView>()
            .mapNotNull { sticker ->
                val params = sticker.layoutParams as? FrameLayout.LayoutParams ?: return@mapNotNull null
                val x = params.leftMargin + sticker.translationX
                val y = params.topMargin + sticker.translationY
                when (sticker) {
                    is TextStickerView -> CanvasObjectState(
                        id = sticker.objectId,
                        type = CanvasObjectType.TEXT,
                        x = x,
                        y = y,
                        width = params.width,
                        height = params.height,
                        rotation = sticker.rotation,
                        text = sticker.text
                    )

                    is ImageStickerView -> CanvasObjectState(
                        id = sticker.objectId,
                        type = CanvasObjectType.IMAGE,
                        x = x,
                        y = y,
                        width = params.width,
                        height = params.height,
                        rotation = sticker.rotation,
                        imagePath = (sticker.imageView.tag as? String).orEmpty(),
                        flipped = sticker.imageView.scaleX < 0f
                    )

                    else -> null
                }
            }
            .toList()

        viewModel.saveCanvas(
            CanvasState(
                canvasScale = binding.canvasViewport.canvasScaleValue,
                canvasTranslationX = binding.canvasViewport.canvasTranslationXValue,
                canvasTranslationY = binding.canvasViewport.canvasTranslationYValue,
                objects = objects
            )
        )
    }

    private fun restoreState(state: CanvasState) {
        clearStickerViews()
        binding.canvasViewport.setCanvasTransform(
            state.canvasScale,
            state.canvasTranslationX,
            state.canvasTranslationY
        )
        binding.canvasSurface.post {
            state.objects.forEach { item ->
                when (item.type) {
                    CanvasObjectType.TEXT -> addTextSticker(item.text, item)
                    CanvasObjectType.IMAGE -> addImageSticker(item.imagePath, item)
                }
            }
            selectSticker(null)
        }
    }

    private fun resetCanvas() {
        clearStickerViews()
        binding.canvasViewport.resetTransform()
        binding.gridOverlay.isVisible = false
        hasAppliedRestoredState = false
        viewModel.resetCanvas {
            hasAppliedRestoredState = true
        }
    }

    private fun clearStickerViews() {
        binding.canvasSurface.children.filterIsInstance<StickerView>().toList().forEach {
            binding.canvasSurface.removeView(it)
        }
        selectedSticker = null
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
