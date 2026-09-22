package com.getwemap.example.map.positioning

import android.content.Context
import android.os.Environment
import androidx.core.content.edit
import com.getwemap.sdk.core.model.entities.MapData
import com.getwemap.sdk.map.internal.MapDependencyManager
import com.getwemap.sdk.map.offline.Packdata
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The offline map (packdata) of a venue: tiles, style, fonts, sprites, buildings, POIs and routing graph
 * bundled into one zip, so the map renders with no network at all.
 *
 * Mirrors [VpsLocalMapDownloader] — that object holds a venue's offline VPS database, this one holds the
 * venue's offline map. Both are keyed by map id, so several venues stay side by side on the device and
 * every screen resolves the same venue the same way.
 */
object PackdataStore {

    private const val PREFERENCES = "wemap_prefs"

    /** The packdata stored for [mapId], or `null` when this device has not downloaded one. */
    fun stored(context: Context, mapId: Int): Packdata? {
        val encoded = preferences(context).getString(key(mapId), null)
            ?: return null

        return try {
            Json.decodeFromString<Packdata>(encoded)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Downloads the current packdata of [mapId] and stores it, replacing any earlier one of that venue.
     *
     * @throws Exception when the download or the copy onto the device fails.
     */
    suspend fun download(context: Context, mapId: Int) {
        val downloaded = manager(context).downloadPackdata(mapId)
        val destination = File(documentsDir(context), downloaded.fileName)
        if (destination.exists()) {
            destination.delete()
        }
        File(downloaded.filePath).copyTo(destination)
        preferences(context).edit(commit = true) {
            putString(key(mapId), Json.encodeToString(downloaded))
        }
    }

    /**
     * Whether a packdata newer than the stored one is available for [mapId]. `false` when nothing is
     * stored yet — there is no version to compare against, so there is no update to report.
     */
    suspend fun isUpdateAvailable(context: Context, mapId: Int): Boolean {
        val eTag = stored(context, mapId)?.eTag
            ?: return false

        return manager(context).isNewPackdataAvailable(mapId, eTag)
    }

    /**
     * Map data read out of the packdata stored for [mapId] — the offline counterpart of
     * [com.getwemap.sdk.map.WemapMapSDK.mapData], and the reason a screen can show a venue with no network.
     *
     * @throws IllegalStateException when no packdata is stored for [mapId].
     */
    suspend fun loadMapData(context: Context, mapId: Int): MapData {
        val stored = stored(context, mapId)
            ?: throw IllegalStateException("Offline map of map $mapId is not downloaded")

        return manager(context).loadMapData(File(documentsDir(context), stored.fileName))
    }

    private fun manager(context: Context) = MapDependencyManager.getPackdataManager(context)

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun documentsDir(context: Context): File? =
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)

    private fun key(mapId: Int) = "packdata-$mapId"
}
