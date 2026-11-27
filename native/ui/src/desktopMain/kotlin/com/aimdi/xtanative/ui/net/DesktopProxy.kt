package com.aimdi.xtanative.ui.net

import com.aimdi.xtanative.core.net.AppProxy
import com.aimdi.xtanative.core.net.EnvProxyPolicy
import com.aimdi.xtanative.core.net.ManualProxyPolicy
import com.aimdi.xtanative.core.net.NoProxyRules
import com.aimdi.xtanative.core.net.ProxyCredentials
import com.aimdi.xtanative.core.net.ProxyKind
import com.aimdi.xtanative.core.net.ProxyPolicy
import com.aimdi.xtanative.core.net.ProxyRoute
import com.aimdi.xtanative.store.settings.DesktopPrefs
import com.aimdi.xtanative.store.settings.ProxyMode
import com.aimdi.xtanative.store.settings.ProxyType
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.SettingsStore
import java.net.Authenticator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

/**
 * The desktop's proxy settings (Settings › General › Network) put into
 * [AppProxy]: in System, the proxy environment variables when any is set
 * (as curl reads them), else the desktop's proxy portal; None goes straight
 * whatever either says; Manual goes through the HTTP or SOCKS5 proxy set by
 * hand, except the hosts its bypass list names. Loopback always goes
 * straight. Videos follow along through [MpvProxy].
 */
object DesktopProxy {
    /** What the proxy settings are, compared to tell a change from another setting's. */
    private data class Choice(val mode: ProxyMode, val manual: ProxyRoute, val bypass: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Routes by [settings] before anything is requested, then follows each
     * change; also answers SOCKS5 proxies' logins (and gives others an empty
     * one, never this user's account name).
     */
    fun follow(settings: SettingsStore, env: Map<String, String> = System.getenv(), portal: ProxyPolicy? = PortalProxyPolicy.desktop) {
        Authenticator.setDefault(AppProxy.socksAuthenticator)
        // Settings that cannot be read may name a proxy: nothing goes out around it, while the start says why.
        val loaded = try {
            runBlocking { settings.load() }
        } catch (_: Exception) {
            null
        }
        AppProxy.use(loaded?.let { policy(it, env, portal).warmed() } ?: UNREAD)
        settings.settings.map(::choice).distinctUntilChanged().drop(1)
            .onEach { AppProxy.use(policy(it, env, portal).warmed()) }
            .launchIn(scope)
    }

    /** Every request fails: the settings that would say the route could not be read. */
    private val UNREAD = ProxyPolicy { _, _, _ -> ProxyRoute.Unusable }

    /** A portal's first answer asked for off the window's thread now, so requests wait for it rather than for a miss. */
    private fun ProxyPolicy.warmed(): ProxyPolicy = also { (it as? PortalProxyPolicy)?.warm() }

    /** The policy [settings] ask for, the environment being [env] and the desktop's portal [portal]. */
    fun policy(settings: Settings, env: Map<String, String>, portal: ProxyPolicy?): ProxyPolicy = policy(choice(settings), env, portal)

    private fun policy(choice: Choice, env: Map<String, String>, portal: ProxyPolicy?): ProxyPolicy = when (choice.mode) {
        ProxyMode.NONE -> ProxyPolicy.DIRECT
        ProxyMode.MANUAL -> ManualProxyPolicy(choice.manual, NoProxyRules.parse(choice.bypass))
        ProxyMode.SYSTEM -> EnvProxyPolicy(env).takeIf { it.isSet } ?: portal ?: ProxyPolicy.DIRECT
    }

    private fun choice(settings: Settings) = Choice(settings[DesktopPrefs.proxyMode], manualRoute(settings), settings[DesktopPrefs.proxyBypass])

    /** The proxy set by hand; Unusable when its host or port is missing, so nothing leaves without it. */
    fun manualRoute(settings: Settings): ProxyRoute {
        val host = NoProxyRules.normalized(settings[DesktopPrefs.proxyHost])
        val port = settings[DesktopPrefs.proxyPort]
        if (host.isEmpty() || host.any { it.isWhitespace() || it == '/' } || port !in 1..65535) return ProxyRoute.Unusable
        val user = settings[DesktopPrefs.proxyUser]
        val credentials = if (user.isEmpty()) null else ProxyCredentials(user, settings[DesktopPrefs.proxyPassword])
        val kind = if (settings[DesktopPrefs.proxyType] == ProxyType.SOCKS5) ProxyKind.SOCKS else ProxyKind.HTTP
        return ProxyRoute.Via(kind, host, port, credentials)
    }
}
