package com.getwemap.example.common

import androidx.lifecycle.ViewModel
import com.getwemap.sdk.core.CoreSession

/**
 * Activity-scoped shared ViewModel used to carry the [CoreSession] from the initial fragment to the
 * view-owning fragments. A session is not serializable, so it cannot be passed through the navigation
 * [android.os.Bundle].
 *
 * The ViewModel owns the session's end-of-life: [onCleared] (fired when the hosting activity is finished)
 * calls [CoreSession.deinit] to release the session's shared services. The session outlives each individual
 * view-owning fragment, so its services are reused as the user moves between screens; they are torn down only
 * here, when the whole flow ends.
 *
 * Map-based sample apps use the `MapSession` variant in `:examples:common-map` instead.
 */
class SessionViewModel : ViewModel() {
    var session: CoreSession? = null

    /**
     * Stores [newSession], tearing down whichever session it displaces.
     *
     * Use this instead of assigning [session] directly whenever a screen creates a session mid-flow — which the
     * initial screen does every time the user comes back to it and loads a map again. A bare assignment would drop
     * the old session's only reference without calling [CoreSession.deinit], leaking its sensors and scopes for the
     * rest of the activity's life; [onCleared] would then only ever reach the last one.
     */
    fun replace(newSession: CoreSession) {
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
