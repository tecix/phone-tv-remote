package io.github.tecix.richtvremote.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address
import java.util.ArrayDeque

data class DiscoveredTv(val name: String, val host: String, val port: Int = 0)

private const val SERVICE_TYPE = "_androidtvremote2._tcp."
private const val TAG = "TvDiscovery"

/**
 * Finds TVs that run the Android TV Remote service via mDNS.
 * Emits the full list each time a TV appears or goes away.
 */
fun discoverTvs(context: Context): Flow<List<DiscoveredTv>> = discoverServices(context, SERVICE_TYPE)

/** Any DNS-SD service type, e.g. "_airplay._tcp." for AirPlay receivers. */
fun discoverServices(context: Context, serviceType: String): Flow<List<DiscoveredTv>> = callbackFlow {
    val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    // Some devices drop multicast replies unless an app holds this lock.
    val lock = wifi.createMulticastLock("rich-tv-remote-discovery").apply {
        setReferenceCounted(false)
        runCatching { acquire() }
    }

    val found = LinkedHashMap<String, DiscoveredTv>()
    val pending = ArrayDeque<NsdServiceInfo>()
    var resolving = false

    fun publish() {
        trySend(found.values.sortedBy { it.name.lowercase() })
    }

    // Older Android versions resolve one service at a time.
    fun resolveNext() {
        if (resolving) return
        val info = pending.pollFirst() ?: return
        resolving = true
        @Suppress("DEPRECATION")
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.d(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                synchronized(found) {
                    resolving = false
                    resolveNext()
                }
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val address = if (Build.VERSION.SDK_INT >= 34) {
                    serviceInfo.hostAddresses.firstOrNull { it is Inet4Address } ?: serviceInfo.hostAddresses.firstOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    serviceInfo.host
                }
                synchronized(found) {
                    address?.hostAddress?.let { host ->
                        found[serviceInfo.serviceName] = DiscoveredTv(serviceInfo.serviceName, host, serviceInfo.port)
                        publish()
                    }
                    resolving = false
                    resolveNext()
                }
            }
        })
    }

    val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "Discovery failed: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            synchronized(found) {
                pending.addLast(serviceInfo)
                resolveNext()
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            synchronized(found) {
                if (found.remove(serviceInfo.serviceName) != null) publish()
            }
        }
    }

    publish()
    nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
    awaitClose {
        runCatching { nsd.stopServiceDiscovery(listener) }
        runCatching { lock.release() }
    }
}
