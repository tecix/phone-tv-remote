package io.github.tecix.richtvremote.media

import android.content.Context
import android.content.Intent
import io.github.tecix.richtvremote.R
import io.github.tecix.richtvremote.TvKey
import io.github.tecix.richtvremote.data.Route
import io.github.tecix.richtvremote.data.Settings
import io.github.tecix.richtvremote.direct.AndroidTvRemote
import io.github.tecix.richtvremote.direct.ClientIdentity
import io.github.tecix.richtvremote.ha.HaHttp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject

internal data class SharedPlayback(val destination: MediaDestination, val renderer: DlnaRenderer?, val settings: Settings)

/** Keeps the original destination available when the activity is backgrounded or recreated. */
internal object MediaStreaming {
    val dlnaSession = DlnaReceiverSession()
    private var instance: StreamingController<SharedPlayback>? = null

    @Synchronized fun controller(context: Context): StreamingController<SharedPlayback> = instance ?: run {
        val app = context.applicationContext
        StreamingController<SharedPlayback>(
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            stopLocal = {
                app.stopService(Intent(app, MediaShareService::class.java))
                MediaShareService.cancelPreparing()
                withTimeout(15_000) { MediaShareService.running.first { !it } }
            },
            stopTv = { target ->
                if (target.destination == MediaDestination.DLNA) {
                    DlnaController.stop(requireNotNull(target.renderer) { "No DLNA receiver selected" })
                } else withContext(Dispatchers.IO) {
                    // Use the saved destination, not whatever route is currently selected on screen.
                    if (target.settings.route == Route.HOME_ASSISTANT) {
                        HaHttp(target.settings.ha).callService("remote", "send_command", JSONObject()
                            .put("entity_id", target.settings.ha.entityId)
                            .put("command", "input keyevent ${TvKey.MEDIA_STOP.code}"))
                    } else {
                        val host = requireNotNull(target.settings.selectedHost) { "No TV selected for VLC" }
                        val remote = AndroidTvRemote(ClientIdentity.get(app), host, app.getString(R.string.app_name))
                        try { remote.connect(); remote.pressAndWait(TvKey.MEDIA_STOP) }
                        finally { remote.disconnect() }
                    }
                }
            },
        ).also { instance = it }
    }
}
