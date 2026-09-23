package com.getwemap.example.map.positioning

import android.content.Context
import android.os.Environment
import androidx.core.content.edit
import com.getwemap.example.common.AppPreferences
import com.getwemap.sdk.map.MapSession
import com.getwemap.sdk.map.offline.Packdata
import com.getwemap.sdk.map.offline.PackdataService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /** The packdata stored for [mapId], or `null` when this device has not downloaded one. */
    fun stored(context: Context, mapId: Int): Packdata? {
        val encoded = AppPreferences.get(context).getString(key(mapId), null)
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
        val downloaded = service(context, mapId).downloadPackdata()
        withContext(Dispatchers.IO) {
            val destination = File(documentsDir(context), downloaded.fileName)
            if (destination.exists()) {
                destination.delete()
            }
            File(downloaded.filePath).copyTo(destination)
            AppPreferences.get(context).edit(commit = true) {
                putString(key(mapId), Json.encodeToString(downloaded))
            }
        }
    }

    /**
     * Whether a packdata newer than the stored one is available for [mapId]. `false` when nothing is
     * stored yet — there is no version to compare against, so there is no update to report.
     */
    suspend fun isUpdateAvailable(context: Context, mapId: Int): Boolean {
        val eTag = stored(context, mapId)?.eTag
            ?: return false

        return service(context, mapId).isNewPackdataAvailable(eTag)
    }

    /**
     * Creates a session from the packdata stored for [mapId] — the offline counterpart of
     * `MapSession.create(context, mapId, token, config)`, and the reason a screen can show a venue with no
     * network.
     *
     * @throws IllegalStateException when no packdata is stored for [mapId].
     */
    suspend fun createSession(context: Context, mapId: Int): MapSession {
        val stored = stored(context, mapId)
            ?: throw IllegalStateException("Offline map of map $mapId is not downloaded")

        val zip = withContext(Dispatchers.IO) { File(documentsDir(context), stored.fileName) }
        return MapSession.create(context, zip, Config.makeSessionConfig(context))
    }

    private suspend fun service(context: Context, mapId: Int): PackdataService =
        MapSession.createPackdataService(mapId, Config.makeSessionConfig(context).environment)

    private fun documentsDir(context: Context): File? =
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)

    private fun key(mapId: Int) = "packdata-$mapId"
}
