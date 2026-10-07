package com.getwemap.example.map.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * A failure a sample is showing the user, and the action that answers it.
 *
 * The SDK's `ItineraryFormFailure` is not kept as-is: a dialog shows a *message* rather than an error, and the
 * retry it offers has to be the one that came with the failure being shown — pairing the two here is what stops
 * the button outliving the failure it belongs to.
 */
data class SampleFailure(val message: String, val retry: (() -> Unit)?)

/**
 * Asks the user whether to try the failed thing again.
 *
 * **A failure carrying a retry is a question, and a question belongs in a dialog**: it cannot be missed, and it
 * is answered before the map or the form can be touched — which matters here, because a widget's retry is only
 * good while the question is unchanged, so a fading snackbar would carry the way back off screen and leave a
 * form with both ends filled, no line, and nothing to press. A failure with nothing to re-ask is only news, and
 * news goes on the snackbar.
 *
 * Neither may be a `Log.e` or a `println`: the widgets draw no error of their own, so a sample that only logs
 * one shows nothing whatsoever to the person holding the phone.
 *
 * @param failure the failure to ask about. Its `retry` is what the confirm button calls.
 * @param onDismiss invoked once the user has answered, either way.
 */
@Composable
fun SampleFailureDialog(failure: SampleFailure, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Itinerary") },
        text = { Text(failure.message) },
        confirmButton = {
            TextButton(
                onClick = {
                    failure.retry?.invoke()
                    onDismiss()
                }
            ) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
