package com.starnest.journalcanvaseditor.view

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.starnest.journalcanvaseditor.R

class TextStickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : StickerView(context, attrs) {
    var text: String
        get() = textView.text.toString()
        set(value) {
            textView.text = value
        }

    init {
        textView.visibility = VISIBLE
        imageView.visibility = GONE
        textView.textSize = 28f
        textView.typeface = ResourcesCompat.getFont(context, R.font.montserrat_bold)
    }

    override fun visibleAction(isVisible: Boolean) {
        super.visibleAction(isVisible)
    }

    override fun showDashedBorder(isShow: Boolean) {
        background = if (isShow) {
            ContextCompat.getDrawable(context, R.drawable.bg_dashed_border_text)
        } else {
            null
        }
    }
}
