package com.getwemap.example.common.map

import androidx.lifecycle.ViewModel
import com.getwemap.sdk.map.MapSession

/**
 * Activity-scoped shared ViewModel used to carry the [MapSession] from the initial fragment to the
 * view-owning fragments. A session is not serializable, so it cannot be passed through the navigation
 * [android.os.Bundle].
 *
 * The ViewModel owns the session's end-of-life: [onCleared] (fired when the hosting activity is finished)
 * calls [MapSession.deinit] to release the session's shared services. The session outlives each individual
 * view-owning fragment, so its services are reused as the user moves between screens; they are torn down only
 * here, when the whole flow ends.
 *
 * Non-map sample apps use the `CoreSession` variant in `:examples:common` instead.
 */
class SessionViewModel : ViewModel() {
    var session: MapSession? = null

    /**
     * Stores [newSession], tearing down whichever session it displaces.
     *
     * Use this instead of assigning [session] directly whenever a screen creates a session mid-flow — which the
     * initial screen does every time the user comes back to it and loads a map again. A bare assignment would drop
     * the old session's only reference without calling [MapSession.deinit], leaking its sensors and scopes for the
     * rest of the activity's life; [onCleared] would then only ever reach the last one.
     */
    fun replace(newSession: MapSession) {
        if (session === newSession) {
            return
        }
        session?.deinit()
        session = newSession
    }

    override fun onCleared() {
        session?.deinit()
        session = null
    }
}
