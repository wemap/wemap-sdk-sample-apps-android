package com.getwemap.example.map

import android.widget.TextView
import com.google.android.material.snackbar.Snackbar

internal fun Snackbar.multiline(): Snackbar {
    val textView = view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
    textView.maxLines = 20
    return this
}

internal fun Snackbar.onDismissed(onDismissed: () -> Unit): Snackbar {
    return addCallback(object : Snackbar.Callback() {
        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
            onDismissed()
            super.onDismissed(transientBottomBar, event)
        }
    })
}