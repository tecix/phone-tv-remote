package io.github.tecix.richtvremote.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.tecix.richtvremote.MainActivity
import io.github.tecix.richtvremote.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.Inet4Address

sealed interface ShareState {
    data object Idle : ShareState
    data object Preparing : ShareState
    data class Serving(val url: String, val mime: String) : ShareState
    data class Failed(val message: String) : ShareState
}

/** A user-started foreground transfer to a connected TV; no cloud upload or TV installation. */
class MediaShareService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var preparation: Job? = null
    @Volatile private var preparingInput: java.io.InputStream? = null
    override fun onCreate() {
        super.onCreate()
        _running.value = true
        cacheDir.listFiles { f -> f.name.startsWith("tv-media-") && f.extension == "tmp" }?.forEach { it.delete() }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            MediaStreaming.controller(this).requestStop()
            return START_NOT_STICKY
        }
        if (intent?.data == null) { stopSelf(); return START_NOT_STICKY }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("tv-sharing", "TV media sharing", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "tv-sharing")
            .setSmallIcon(R.drawable.ic_stat_share).setContentTitle("Sharing media with your TV")
            .setContentText("Keep this phone on Wi-Fi. Stop streaming when finished.")
            .setContentIntent(open).setOngoing(true).addAction(0, "Stop streaming", stop).build()
        ServiceCompat.startForeground(this, 42, notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
        val previous = preparation
        previous?.cancel()
        preparation = scope.launch {
            // A replacement must finish closing the previous file/server before preparing its own.
            withContext(NonCancellable) { previous?.join() }
            var file: File? = null
            var stream: SingleMediaServer? = null
            var wake: PowerManager.WakeLock? = null
            var wifi: WifiManager.WifiLock? = null
            try {
                ensureActive()
                _state.value = ShareState.Preparing
                wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RichTVRemote:sharing")
                    .apply { acquire(4 * 60 * 60_000L) }
                val (address, caps) = localNetwork(this@MediaShareService)
                val mediaFile = File.createTempFile("tv-media-", ".tmp", cacheDir)
                file = mediaFile
                // Cache provider data first: Drive may need to download it, and VLC needs a seekable file.
                contentResolver.openInputStream(intent.data!!)?.use { input ->
                    preparingInput = input
                    mediaFile.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            check(cacheDir.usableSpace > n + 16 * 1024 * 1024L) { "Not enough phone storage to prepare this media" }
                            out.write(buffer, 0, n)
                        }
                    }
                } ?: error("The sharing app did not provide a readable file")
                preparingInput = null
                require(mediaFile.length() > 0) { "The shared file is empty" }
                val displayName = runCatching {
                    contentResolver.query(intent.data!!, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                }.getOrNull()
                val mime = sequenceOf(runCatching { contentResolver.getType(intent.data!!) }.getOrNull(), intent.type)
                    .mapNotNull { resolveMediaMime(it, displayName.orEmpty()) }
                    .firstOrNull { !it.endsWith("/*") }
                    ?: "application/octet-stream"
                ensureActive()
                val mediaServer = SingleMediaServer(mediaFile, mime, address)
                stream = mediaServer
                // Keep Wi-Fi out of power saving while the TV pulls the video, including with the phone locked.
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    @Suppress("DEPRECATION")
                    val lock = applicationContext.getSystemService(WifiManager::class.java)
                        .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RichTVRemote:streaming")
                    wifi = lock
                    lock.acquire()
                }
                ensureActive()
                _state.value = ShareState.Serving(mediaServer.url, mime)
                delay(4 * 60 * 60_000L)
                stopSelf(startId)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.value = ShareState.Failed(e.message ?: "Could not prepare the shared media")
                stopSelf(startId)
            } finally {
                preparingInput = null
                runCatching { stream?.close() }
                runCatching { file?.delete() }
                runCatching { if (wake?.isHeld == true) wake.release() }
                runCatching { if (wifi?.isHeld == true) wifi.release() }
            }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        scope.cancel()
        runCatching { preparingInput?.close() } // Unblock a provider read when a new file replaces it.
        scope.coroutineContext[Job]?.invokeOnCompletion {
            if (_state.value !is ShareState.Failed) _state.value = ShareState.Idle
            _running.value = false // Published only after the transfer's finally block released its resources.
        }
        super.onDestroy()
    }
    companion object {
        /** The phone's LAN IPv4 address, which the TV can reach; fails off Wi-Fi/Ethernet. */
        internal fun localNetwork(context: android.content.Context): Pair<Inet4Address, NetworkCapabilities?> {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val network = connectivity.activeNetwork ?: error("Connect the phone and TV to the same Wi-Fi")
            val caps = connectivity.getNetworkCapabilities(network)
            require(caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) { "Connect to local Wi-Fi to share a file" }
            val address = connectivity.getLinkProperties(network)?.linkAddresses
                ?.map { it.address }?.filterIsInstance<Inet4Address>()?.firstOrNull { !it.isLoopbackAddress }
                ?: error("No local IPv4 address available")
            return address to caps
        }

        const val STOP = "io.github.tecix.richtvremote.STOP_SHARING"
        private val _running = MutableStateFlow(false)
        val running = _running.asStateFlow()
        private val _state = MutableStateFlow<ShareState>(ShareState.Idle)
        val state = _state.asStateFlow()
        internal fun markPreparing() { _state.value = ShareState.Preparing }
        internal fun cancelPreparing() { if (!_running.value && _state.value is ShareState.Preparing) _state.value = ShareState.Idle }
    }
}
