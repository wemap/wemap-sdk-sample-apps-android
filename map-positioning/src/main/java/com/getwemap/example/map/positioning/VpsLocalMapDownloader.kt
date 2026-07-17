package com.getwemap.example.map.positioning

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.getwemap.example.map.positioning.VpsLocalMapDownloader.DATASET_BASE_URL
import com.getwemap.example.map.positioning.VpsLocalMapDownloader.MAP_ID
import com.getwemap.example.map.positioning.VpsLocalMapDownloader.REQUIRED_FILES
import com.getwemap.example.map.positioning.VpsLocalMapDownloader.requireConfigured
import java.io.File

/**
 * Downloads the offline VPS map-database files consumed by
 * [com.getwemap.sdk.positioning.wemapvpslocal.VpsLocalLocationSource].
 *
 * These files are large and map-specific, so the SDK does not bundle them — the app fetches them
 * on demand and stores them under `getExternalFilesDir(null)/maps/`, then passes that directory to
 * the location source.
 *
 * ⚠️ [MAP_ID] and [DATASET_BASE_URL] below are placeholders. Wemap provides the values for your
 * venue — set both before running this sample. Until you do, [requireConfigured] throws a
 * descriptive error when a download is attempted.
 */
object VpsLocalMapDownloader {

    /** Wemap map id for your venue. Provided by Wemap. `-1` means not configured yet. */
    const val MAP_ID = -1

    /**
     * Base URL of the offline map-database files for your venue — the directory that contains the
     * files listed in [REQUIRED_FILES]. Provided by Wemap.
     */
    const val DATASET_BASE_URL = "REPLACE_WITH_DATASET_URL_FROM_WEMAP"

    /** Offline map-database files that make up the dataset. */
    private val REQUIRED_FILES = listOf(
        "descriptors.bq",
        "reloc-simplified.db",
        "georef.db",
    )

    /**
     * Fails fast when [MAP_ID] / [DATASET_BASE_URL] are still the shipped placeholders, so a
     * misconfigured sample surfaces a clear message instead of downloading from a bogus URL.
     */
    private fun requireConfigured() {
        check(MAP_ID >= 0 && DATASET_BASE_URL != "REPLACE_WITH_DATASET_URL_FROM_WEMAP") {
            "VPS Local offline dataset is not configured. Set VpsLocalMapDownloader.MAP_ID and " +
                "VpsLocalMapDownloader.DATASET_BASE_URL to the values Wemap sent you by email before running this sample."
        }
    }

    fun mapDir(context: Context): File =
        File(context.getExternalFilesDir(null), "maps/vps-offline")

    fun isAvailable(context: Context): Boolean {
        val dir = mapDir(context)
        return REQUIRED_FILES.all { File(dir, it).exists() }
    }

    fun missingFiles(context: Context): List<String> {
        val dir = mapDir(context)
        return REQUIRED_FILES.filter { !File(dir, it).exists() }
    }

    /** Enqueues downloads for the missing files (or all, when [forceAll]) and returns their ids. */
    fun enqueueDownloads(context: Context, forceAll: Boolean = false): List<Long> {
        requireConfigured()
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val dir = mapDir(context).also { it.mkdirs() }
        val filesToDownload = if (forceAll) {
            REQUIRED_FILES.onEach { File(dir, it).delete() }
        } else {
            missingFiles(context)
        }
        return filesToDownload.map { filename ->
            dm.enqueue(
                DownloadManager.Request(Uri.parse("$DATASET_BASE_URL/$filename"))
                    .setTitle("VPS Offline — $filename")
                    .setDestinationUri(Uri.fromFile(File(dir, filename)))
                    .setAllowedOverMetered(true)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE),
            )
        }
    }

    data class FileProgress(
        val filename: String,
        val downloaded: Long,
        val total: Long,
        val done: Boolean,
        val failed: Boolean,
    )

    fun queryProgress(context: Context, ids: List<Long>): List<FileProgress> {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val result = mutableListOf<FileProgress>()
        dm.query(DownloadManager.Query().setFilterById(*ids.toLongArray())).use { cursor ->
            while (cursor.moveToNext()) {
                val titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val dlIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIdx)
                val filename = (cursor.getString(titleIdx) ?: "").substringAfterLast("— ").trim()
                result.add(
                    FileProgress(
                        filename = filename,
                        downloaded = cursor.getLong(dlIdx).coerceAtLeast(0),
                        total = cursor.getLong(totalIdx).coerceAtLeast(0),
                        done = status == DownloadManager.STATUS_SUCCESSFUL,
                        failed = status == DownloadManager.STATUS_FAILED,
                    ),
                )
            }
        }
        return result
    }
}
