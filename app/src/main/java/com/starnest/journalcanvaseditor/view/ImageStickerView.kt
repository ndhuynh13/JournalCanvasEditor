package com.starnest.journalcanvaseditor.view

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.starnest.journalcanvaseditor.R

class ImageStickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : StickerView(context, attrs) {
    init {
        textView.visibility = GONE
        imageView.visibility = VISIBLE
    }

    override fun showDashedBorder(isShow: Boolean) {
        background = if (isShow) {
            ContextCompat.getDrawable(context, R.drawable.bg_dashed_border_image)
        } else {
            null
        }
    }
}
