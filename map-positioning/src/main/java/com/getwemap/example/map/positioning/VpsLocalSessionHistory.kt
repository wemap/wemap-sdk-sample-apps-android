package com.getwemap.example.map.positioning

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Reads back what [VpsLocalSessionRecorder] wrote: the list of past scanning sessions for a venue, each
 * summarised, with its fixes in order so they can be drawn as a trace.
 *
 * Parsing is deliberately forgiving. A session file is appended live during a walk, so the last line can be
 * half-written when the process dies, and an older file may lack fields a later build added. Every line is
 * parsed independently and anything unreadable is skipped — a truncated session is still worth reviewing,
 * and refusing to open it would lose exactly the sessions that ended badly, which are the interesting ones.
 */
object VpsLocalSessionHistory {

    /** One accepted fix. */
    class Fix(
        val atMs: Long,
        val latitude: Double,
        val longitude: Double,
        val level: Float?,
        val accuracy: Float,
        val headingDegrees: Double?,
    )

    /** One recorded session, summarised. */
    class Session(
        val file: File,
        val mapId: Int,
        val device: String,
        val startedAtMs: Long,
        val lastEventAtMs: Long,
        val fixes: List<Fix>,
        /** Failed-scan outcomes by [com.getwemap.sdk.positioning.wemapvpslocal.VpsLocalLocationSource.ScanOutcome] name. */
        val failedOutcomes: Map<String, Int>,
        val errors: Int,
    ) {
        val durationMs: Long get() = (lastEventAtMs - startedAtMs).coerceAtLeast(0)

        /** Scan attempts that reported *something*. Silent skips (cadence hold-off, flat pause) are absent. */
        val attempts: Int get() = fixes.size + failedOutcomes.values.sum()

        val acceptedPercent: Int
            get() = if (attempts == 0) 0 else (100.0 * fixes.size / attempts).toInt()

        val fixesPerMinute: Double
            get() {
                val minutes = durationMs / 60_000.0
                return if (minutes <= 0.0) 0.0 else fixes.size / minutes
            }

        /**
         * Largest gap between consecutive fixes. The headline number for "how did the walk go": a session
         * can average well and still have left the user unlocated for half a minute.
         */
        val longestFixGapMs: Long
            get() = fixes.zipWithNext { a, b -> b.atMs - a.atMs }.maxOrNull() ?: 0

        val title: String get() = TITLE_STAMP.format(Date(startedAtMs))

        val summary: String
            get() = buildString {
                append("${fixes.size} fixes · ")
                append("%.1f/min · ".format(fixesPerMinute))
                append("$acceptedPercent% of $attempts scans · ")
                append(formatDuration(durationMs))
                if (longestFixGapMs > 0) {
                    append(" · worst gap ${longestFixGapMs / 1000}s")
                }
                if (errors > 0) {
                    append(" · $errors errors")
                }
            }

        /** Failure mix, commonest first — usually the answer to "why were there so few fixes". */
        val outcomeBreakdown: String
            get() = failedOutcomes.entries
                .sortedByDescending { it.value }
                .joinToString(", ") { "${it.key.lowercase().replace('_', ' ')} ${it.value}" }
                .ifEmpty { "no failed scans" }
    }

    /** `…/maps/vps-offline/<map id>/history/` — history lives with the venue's map database. */
    fun sessionsDir(context: Context, mapId: Int): File =
        File(VpsLocalMapDownloader.mapDir(context, mapId), "history")

    /** Recorded sessions for [mapId], newest first. Sessions with no events at all are omitted. */
    fun listSessions(context: Context, mapId: Int): List<Session> =
        (sessionsDir(context, mapId).listFiles { f -> f.isFile && f.name.endsWith(".jsonl") } ?: emptyArray())
            .mapNotNull { read(it) }
            .sortedByDescending { it.startedAtMs }

    fun read(file: File): Session? {
        var mapId = 0
        var device = ""
        var startedAtMs = 0L
        var lastEventAtMs = 0L
        val fixes = mutableListOf<Fix>()
        val failed = mutableMapOf<String, Int>()
        var errors = 0

        try {
            file.forEachLine { line ->
                val event = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
                val at = event.optLong("at", 0L)
                if (at > lastEventAtMs) {
                    lastEventAtMs = at
                }
                when (event.optString("event")) {
                    "session" -> {
                        mapId = event.optInt("mapId", 0)
                        device = event.optString("device")
                        startedAtMs = at
                    }
                    "fix" -> fixes += Fix(
                        atMs = at,
                        latitude = event.optDouble("lat"),
                        longitude = event.optDouble("lon"),
                        level = if (event.isNull("level")) null else event.optDouble("level").toFloat(),
                        accuracy = event.optDouble("accuracy", 0.0).toFloat(),
                        headingDegrees = if (event.isNull("heading")) null else event.optDouble("heading"),
                    )
                    "scan" -> {
                        val outcome = event.optString("outcome").ifEmpty { "UNKNOWN" }
                        failed[outcome] = (failed[outcome] ?: 0) + 1
                    }
                    "error" -> errors++
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not read ${file.name}", e)
            return null
        }

        if (startedAtMs == 0L && fixes.isEmpty() && failed.isEmpty()) {
            return null
        }
        return Session(
            file = file,
            mapId = mapId,
            device = device,
            // A file whose header line was lost still has usable events; fall back to the first of them.
            startedAtMs = if (startedAtMs > 0) startedAtMs else fixes.firstOrNull()?.atMs ?: lastEventAtMs,
            lastEventAtMs = lastEventAtMs,
            fixes = fixes,
            failedOutcomes = failed,
            errors = errors,
        )
    }

    fun deleteAll(context: Context, mapId: Int) {
        sessionsDir(context, mapId).listFiles()?.forEach { it.delete() }
    }

    private fun formatDuration(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }

    private const val TAG = "VpsLocalSessionHistory"
    private val TITLE_STAMP = SimpleDateFormat("d MMM, HH:mm:ss", Locale.getDefault())
}
