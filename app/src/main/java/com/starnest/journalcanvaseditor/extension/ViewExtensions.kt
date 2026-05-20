package com.starnest.journalcanvaseditor.extension

import android.content.res.Resources
import android.view.View

val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

fun View.safeClick(onClick: (View) -> Unit) {
    setOnClickListener { view ->
        view.isClickable = false
        onClick(view)
        view.postDelayed({ view.isClickable = true }, CLICK_DEBOUNCE_MS)
    }
}

private const val CLICK_DEBOUNCE_MS = 400L
