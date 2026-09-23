package com.getwemap.example.common

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Base [Application] shared by the example apps.
 *
 * Warms the default SharedPreferences off-main (see [AppPreferences]) so the `Config` factories
 * don't hit disk on the main thread. Referenced by each example app via `android:name` in its
 * manifest.
 */
open class WemapExampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Preload the default SharedPreferences off-main so the first Config build reads from the
        // already-loaded, cached instance instead of triggering a main-thread disk read.
        CoroutineScope(Dispatchers.IO).launch { AppPreferences.warmUp(this@WemapExampleApplication) }
    }
}
