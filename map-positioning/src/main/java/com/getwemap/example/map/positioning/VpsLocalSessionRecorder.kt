@file:OptIn(AlphaVpsLocalApi::class)

package com.getwemap.example.map.positioning

import android.content.Context
import android.os.Build
import android.util.Log
import com.getwemap.sdk.core.model.entities.Attitude
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.positioning.wemapvpslocal.AlphaVpsLocalApi
import com.getwemap.sdk.positioning.wemapvpslocal.VpsLocalLocationSource
import com.getwemap.sdk.positioning.wemapvpslocal.constants.VpsLocalConstants
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Records one offline-VPS scanning session to a file, so a walk can be reviewed **afterwards** — the
 * screen is unwatchable while the phone is on a lanyard or in a pocket, which is exactly when the SDK is
 * doing its most interesting work.
 *
 * Written as **JSON Lines**, one event per line, appended and flushed immediately. That format is chosen
 * for the failure modes this app actually has: a scanning session can be killed by a crash or by the user
 * walking out of range of a charger, and an append-only file loses only the partially written last line
 * (which [VpsLocalSessionHistory] skips). A database would need a clean close to be readable.
 *
 * Files live **inside the map database directory** (`…/maps/vps-offline/<map id>/history/`), mirroring the
 * research bench's choice to keep history next to the venue it belongs to: pick up a dataset, get its
 * history with it. They are also plain text under `getExternalFilesDir`, so a session can be pulled off the
 * device with `adb pull` and analysed on a laptop — a more reliable channel than logcat, which caps at
 * 5 MiB, pools several processes into one buffer, and needs `StageTimer` debug logging enabled.
 *
 * Callbacks arrive on the SDK's scan threads, so every write is handed to a single-threaded executor: the
 * scan loop must never block on file IO.
 */
class VpsLocalSessionRecorder(context: Context, private val mapId: Int) : Closeable {

    private val writerThread = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "VpsLocalSessionRecorder")
    }

    private val file: File =
        File(VpsLocalSessionHistory.sessionsDir(context, mapId), "${FILE_STAMP.format(Date())}.jsonl")

    init {
        file.parentFile?.mkdirs()
        // The header records the settings in force, because a session's fix rate cannot be read without
        // them: the same walk looks very different at a 4 m rescan distance and a 10 s max interval.
        append(
            JSONObject()
                .put("event", "session")
                .put("mapId", mapId)
                .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("androidSdk", Build.VERSION.SDK_INT)
                .put("minScanIntervalMs", VpsLocalConstants.MIN_SCAN_INTERVAL_MS)
                .put("maxScanIntervalMs", VpsLocalConstants.MAX_SCAN_INTERVAL_MS)
                .put("rescanDistanceMeters", VpsLocalConstants.RESCAN_DISTANCE_METERS)
                .put("stepLengthMeters", VpsLocalConstants.STEP_LENGTH_METERS)
                .put("cameraAutoSwitch", VpsLocalConstants.CAMERA_AUTO_SWITCH_ENABLED)
        )
        Log.i(TAG, "recording session to ${file.absolutePath}")
    }

    /** An accepted fix: what the trace is drawn from. */
    fun recordFix(coordinate: Coordinate, attitude: Attitude) {
        append(
            JSONObject()
                .put("event", "fix")
                .put("lat", coordinate.location.latitude)
                .put("lon", coordinate.location.longitude)
                // Coordinate.levels is a list (a level *range* for a coordinate spanning floors); the
                // offline source always reports a single level, so the first entry is the one to keep.
                .put("level", coordinate.levels.firstOrNull() ?: JSONObject.NULL)
                .put("accuracy", coordinate.location.accuracy)
                .put("heading", attitude.headingDegrees)
        )
    }

    /** A scan attempt that produced no fix — the denominator behind any "accepted %" figure. */
    fun recordOutcome(outcome: VpsLocalLocationSource.ScanOutcome) {
        append(JSONObject().put("event", "scan").put("outcome", outcome.name))
    }

    fun recordError(error: Throwable) {
        append(
            JSONObject()
                .put("event", "error")
                .put("message", error.message ?: error::class.java.simpleName)
        )
    }

    private fun append(event: JSONObject) {
        val line = event.put("at", System.currentTimeMillis()).toString()
        // The executor may already be shut down if a late callback races close(); dropping that event is
        // strictly better than crashing the app to record it.
        runCatching {
            writerThread.execute {
                runCatching { file.appendText("$line\n") }
                    .onFailure { Log.w(TAG, "failed to record event", it) }
            }
        }
    }

    override fun close() {
        writerThread.shutdown()
        // Drain briefly so the last events of a session are not lost on teardown; the queue holds only
        // single-line appends, so this returns almost immediately.
        runCatching { writerThread.awaitTermination(CLOSE_DRAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        Log.i(TAG, "session recorded: ${file.name}")
    }

    private companion object {
        const val TAG = "VpsLocalSessionRecorder"
        const val CLOSE_DRAIN_TIMEOUT_MS = 1_000L

        /** Sortable and human-readable, so the file list is chronological without parsing. */
        val FILE_STAMP = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}
