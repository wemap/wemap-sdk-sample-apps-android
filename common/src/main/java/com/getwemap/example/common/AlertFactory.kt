package com.getwemap.example.common

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AlertFactory {

    suspend fun showSimpleAlert(
        context: Context,
        message: String,
        errorMessage: String,
        positiveText: String = "OK",
        negativeText: String = "Cancel"
    ) = suspendCancellableCoroutine { continuation ->
        val dialog = MaterialAlertDialogBuilder(context)
            .setMessage(message)
            .setPositiveButton(positiveText) { _, _ -> continuation.resume(Unit) }
            .setNegativeButton(negativeText) { _, _ -> continuation.resumeWithException(Throwable(errorMessage)) }
            .show()

        continuation.invokeOnCancellation { dialog.dismiss() }
    }
}
