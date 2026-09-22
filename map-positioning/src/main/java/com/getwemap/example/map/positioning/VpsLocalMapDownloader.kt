package com.getwemap.example.map.positioning

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.getwemap.example.map.positioning.VpsLocalMapDownloader.DATASETS
import com.getwemap.example.map.positioning.VpsLocalMapDownloader.REQUIRED_FILES
import java.io.File

/**
 * Downloads the offline VPS map-database files consumed by
 * [com.getwemap.sdk.positioning.wemapvpslocal.VpsLocalLocationSource].
 *
 * These files are large and map-specific, so the SDK does not bundle them — the app fetches them
 * on demand and stores them under `getExternalFilesDir(null)/maps/vps-offline/<map id>/`, then
 * passes that directory to the location source.
 *
 * Several venues can be kept side by side: every dataset lives in its own directory named after its
 * map id, so downloading a second venue never overwrites the first one, and an already-downloaded
 * venue is detected and reused instead of being fetched again.
 *
 * ⚠️ The entry in [DATASETS] below is a placeholder. Wemap sends you the map id and the dataset URL
 * of your venue by email — fill them in there before running this sample.
 */
object VpsLocalMapDownloader {

    /** Placeholder map id of a dataset that has not been configured yet. */
    private const val UNCONFIGURED_MAP_ID = -1

    /** Placeholder base URL of a dataset that has not been configured yet. */
    private const val UNCONFIGURED_BASE_URL = "REPLACE_WITH_DATASET_URL_FROM_WEMAP"

    /** Parent directory (relative to `getExternalFilesDir(null)`) holding every downloaded dataset. */
    private const val MAPS_SUBDIRECTORY = "maps/vps-offline"

    /**
     * One venue's offline map database: the Wemap [mapId] it positions against, a [name] to show in
     * the UI, and the [baseUrl] of the directory that contains the files listed in [REQUIRED_FILES].
     * All three are provided by Wemap.
     */
    data class Dataset(
        val mapId: Int,
        val name: String,
        val baseUrl: String,
    ) {
        /** Whether this entry still carries the shipped placeholders instead of the values from Wemap. */
        val isConfigured: Boolean get() = mapId != UNCONFIGURED_MAP_ID && baseUrl != UNCONFIGURED_BASE_URL
    }

    /**
     * Datasets this sample can download — one entry per venue. Replace the placeholder below with the
     * map id and dataset URL Wemap sent you, and append one entry per additional venue; the offline VPS
     * section of the initial screen lets the user pick between them.
     */
    val DATASETS = listOf(
        Dataset(
            mapId = UNCONFIGURED_MAP_ID,
            name = "Your venue",
            baseUrl = UNCONFIGURED_BASE_URL,
        ),
    )

    /** Offline map-database files that make up a dataset. */
    private val REQUIRED_FILES = listOf(
        "descriptors.bq",
        "reloc-simplified.db",
        "georef.db",
    )

    /** The dataset registered for [mapId], or `null` when this sample knows no dataset for it. */
    fun datasetFor(mapId: Int): Dataset? = DATASETS.firstOrNull { it.mapId == mapId }

    /**
     * Directory holding the map database of [mapId]. Named after the map id so datasets of different
     * venues coexist on the device.
     */
    fun mapDir(context: Context, mapId: Int): File =
        File(context.getExternalFilesDir(null), "$MAPS_SUBDIRECTORY/$mapId")

    /** Whether every file of [mapId]'s dataset is already on the device. */
    fun isAvailable(context: Context, mapId: Int): Boolean = missingFiles(context, mapId).isEmpty()

    /** Files of [mapId]'s dataset that still have to be downloaded. */
    fun missingFiles(context: Context, mapId: Int): List<String> {
        val dir = mapDir(context, mapId)
        return REQUIRED_FILES.filter { !File(dir, it).exists() }
    }

    /** Map ids whose dataset is fully downloaded, in [DATASETS] order. */
    fun downloadedMapIds(context: Context): List<Int> =
        DATASETS.map { it.mapId }.filter { isAvailable(context, it) }

    /**
     * Enqueues downloads for the files of [mapId] that are missing (or for all of them, when
     * [forceAll]) and returns their ids. Already-downloaded files are left untouched, so switching
     * back to a venue that was fetched earlier re-downloads nothing.
     *
     * @throws IllegalStateException when no dataset is configured for [mapId].
     */
    fun enqueueDownloads(context: Context, mapId: Int, forceAll: Boolean = false): List<Long> {
        val dataset = requireDataset(mapId)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val dir = mapDir(context, mapId).also { it.mkdirs() }
        val filesToDownload = if (forceAll) REQUIRED_FILES else missingFiles(context, mapId)
        return filesToDownload.map { filename ->
            // DownloadManager refuses to write over an existing file.
            val destination = File(dir, filename).also { it.delete() }
            dm.enqueue(
                DownloadManager.Request(Uri.parse("${dataset.baseUrl}/$filename"))
                    .setTitle("VPS Offline ${dataset.name} — $filename")
                    .setDestinationUri(Uri.fromFile(destination))
                    .setAllowedOverMetered(true)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE),
            )
        }
    }

    /**
     * Fails fast when [mapId] has no dataset — or still carries the shipped placeholder URL — so a
     * misconfigured sample surfaces a clear message instead of downloading from a bogus URL.
     */
    private fun requireDataset(mapId: Int): Dataset {
        val dataset = datasetFor(mapId)
        checkNotNull(dataset) {
            "No offline VPS dataset is configured for map id $mapId. Add a VpsLocalMapDownloader.Dataset " +
                    "with the map id and dataset URL Wemap sent you by email before running this sample."
        }
        check(dataset.isConfigured) {
            "The offline VPS dataset \"${dataset.name}\" still carries the shipped placeholders. Set its " +
                    "mapId and baseUrl to the values Wemap sent you by email before running this sample."
        }
        return dataset
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
