package com.getwemap.example.common

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.core.Single

object AlertFactory {

    fun showSimpleAlert(
        context: Context,
        message: String,
        errorMessage: String,
        positiveText: String = "OK",
        negativeText: String = "Cancel"
    ): Single<Unit> {
        return Single.create { emitter ->
            val dialog = MaterialAlertDialogBuilder(context)
                .setMessage(message)
                .setPositiveButton(positiveText) { _, _ -> emitter.onSuccess(Unit) }
                .setNegativeButton(negativeText) { _, _ -> emitter.onError(Throwable(errorMessage)) }
                .show()

            emitter.setCancellable { dialog.dismiss() }
        }
    }
}