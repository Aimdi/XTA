package com.aimdi.xtanative.ui.media

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the default network is metered, as Android says it is: read once
 * (`isActiveNetworkMetered`), then followed with a default-network callback
 * until the caller leaves. Both need only ACCESS_NETWORK_STATE; where the
 * system refuses either, the last answer stays.
 */
@Composable
internal actual fun rememberMeteredNetwork(): State<Boolean> {
    val context = LocalContext.current.applicationContext
    val connectivity: ConnectivityManager? = remember(context) { context.getSystemService(ConnectivityManager::class.java) }
    val metered = remember(connectivity) {
        mutableStateOf(connectivity != null && runCatching { connectivity.isActiveNetworkMetered }.getOrDefault(false))
    }
    DisposableEffect(connectivity, metered) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                metered.value = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
        }
        val registered = connectivity != null && runCatching { connectivity.registerDefaultNetworkCallback(callback) }.isSuccess
        onDispose {
            if (registered) runCatching { connectivity?.unregisterNetworkCallback(callback) }
        }
    }
    return metered
}
