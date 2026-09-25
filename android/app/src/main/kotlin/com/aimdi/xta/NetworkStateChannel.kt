package com.aimdi.xta

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel

/** Reports validated default-network changes without probing external hosts. */
class NetworkStateChannel(context: Context, messenger: BinaryMessenger) : EventChannel.StreamHandler {
    private val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val channel = EventChannel(messenger, "com.aimdi.xta/network_state")
    private val main = Handler(Looper.getMainLooper())
    private var sink: EventChannel.EventSink? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var lastNetwork: Network? = null
    private var lastValidated: Boolean? = null

    init { channel.setStreamHandler(this) }

    private fun report() {
        val network = manager.activeNetwork
        val caps = manager.getNetworkCapabilities(network)
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (network == lastNetwork && validated == lastValidated) return
        lastNetwork = network
        lastValidated = validated
        sink?.success(mapOf("online" to validated, "networkId" to network?.toString()))
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        onCancel(null)
        sink = events
        lastNetwork = null
        lastValidated = null
        val listener = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { main.post { report() } }
            override fun onLost(network: Network) { main.post { report() } }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                main.post { report() }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) manager.registerDefaultNetworkCallback(listener)
            else manager.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), listener)
            callback = listener
            report()
        } catch (_: RuntimeException) { events.success(false) }
    }

    override fun onCancel(arguments: Any?) {
        callback?.let { try { manager.unregisterNetworkCallback(it) } catch (_: RuntimeException) {} }
        callback = null
        sink = null
        main.removeCallbacksAndMessages(null)
    }
    fun dispose() { onCancel(null); channel.setStreamHandler(null) }
}
