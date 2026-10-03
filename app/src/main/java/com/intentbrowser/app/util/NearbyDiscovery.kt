package com.intentbrowser.app.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Nearby device discovery over mDNS (DNS-SD).
 *
 * When the Local Hub starts, this phone advertises itself as `_intentdrop._tcp`
 * and simultaneously discovers other Intent Browsers on the same Wi-Fi/hotspot.
 * No IP typing, no QR scanning — nearby hubs just appear by name.
 *
 * Self-filtering is done two ways: by the registered service name AND by our
 * own IP+port (registration is async, so discovery can spot us first).
 */
data class NearbyDevice(
    val name: String,
    val host: String,
    val port: Int
) {
    val url: String get() = "http://$host:$port"
}

object NearbyDiscovery {

    private const val SERVICE_TYPE = "_intentdrop._tcp."
    private const val APP_ID = "intentbrowser"

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var selfName: String? = null
    private var ownIp: String? = null
    private var ownPort: Int = -1

    private val resolved = mutableMapOf<String, NearbyDevice>()

    private val _devices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val devices: StateFlow<List<NearbyDevice>> = _devices.asStateFlow()

    /** Friendly device name, e.g. "Ritu's Pixel" or "Xiaomi 23127PN0CG". */
    fun deviceName(context: Context): String {
        val systemName = try {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        } catch (_: Exception) {
            null
        }
        if (!systemName.isNullOrBlank()) return systemName
        val fallback = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        return fallback.replaceFirstChar { it.uppercase() }
    }

    @Synchronized
    fun start(context: Context, port: Int) {
        if (nsdManager != null) return // already running
        val appContext = context.applicationContext
        val mgr: NsdManager
        try {
            mgr = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        } catch (_: Exception) {
            return // no NSD on this device; hub still works via IP
        }
        nsdManager = mgr
        ownIp = NetworkUtils.getLocalIpAddress()
        ownPort = port
        registerService(mgr, appContext, deviceName(appContext), port)
        startDiscovery(mgr)
    }

    @Synchronized
    fun stop() {
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
        } catch (_: Exception) {
        }
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (_: Exception) {
        }
        registrationListener = null
        discoveryListener = null
        nsdManager = null
        selfName = null
        ownIp = null
        ownPort = -1
        synchronized(resolved) { resolved.clear() }
        _devices.value = emptyList()
    }

    private fun registerService(mgr: NsdManager, context: Context, name: String, port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = name // NsdManager appends (2), (3)… on conflict
            serviceType = SERVICE_TYPE
            this.port = port
            // TXT record: only talk to our own app, and allow version gating later.
            setAttribute("app", APP_ID)
            setAttribute("v", "2")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                selfName = info.serviceName
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        try {
            mgr.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
        }
    }

    private fun startDiscovery(mgr: NsdManager) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType != SERVICE_TYPE) return
                if (service.serviceName == selfName) return
                resolveService(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                synchronized(resolved) { resolved.remove(service.serviceName) }
                publish()
            }
        }
        discoveryListener = listener
        try {
            mgr.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
        }
    }

    private fun resolveService(service: NsdServiceInfo) {
        val mgr = nsdManager ?: return
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}

            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress ?: return
                // Only our own app…
                val appAttr = info.attributes["app"]?.toString(Charsets.UTF_8)
                if (appAttr != APP_ID) return
                // …and not ourselves (IP+port check covers the registration race).
                if (host == ownIp && info.port == ownPort) return
                if (info.serviceName == selfName) return
                synchronized(resolved) {
                    resolved[info.serviceName] =
                        NearbyDevice(info.serviceName, host, info.port)
                }
                publish()
            }
        }
        try {
            mgr.resolveService(service, listener)
        } catch (_: Exception) {
        }
    }

    private fun publish() {
        val list = synchronized(resolved) { resolved.values.toList() }
        _devices.value = list.sortedBy { it.name.lowercase() }
    }
}
