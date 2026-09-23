package com.getwemap.example.common

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Caches the default [SharedPreferences] instance so the underlying `getSharedPreferences()`
 * dir-check — a disk read, and one that would otherwise land on the main thread — happens only once.
 *
 * The instance is warmed off the main thread at startup (see [WemapExampleApplication]); the
 * `Config` factories then read from the cached, already-loaded instance, so their `getString` /
 * `getBoolean` calls are pure in-memory lookups with no disk I/O on the main thread.
 *
 * If [get] is reached before warm-up completes, it obtains the instance synchronously as a fallback
 * (a one-time cost) and caches it for everyone else.
 */
object AppPreferences {

    @Volatile
    private var cached: SharedPreferences? = null

    fun get(context: Context): SharedPreferences =
        cached ?: synchronized(this) {
            cached ?: PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
                .also { cached = it }
        }

    /** Preloads the instance and forces its backing file to load. Call off the main thread. */
    fun warmUp(context: Context) {
        get(context).all
    }
}
