package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XAccountHealth
import com.aimdi.xtanative.core.x.XAccountHealthStore
import com.aimdi.xtanative.core.x.XAccountPolicy
import com.aimdi.xtanative.core.x.XEndpoints
import com.aimdi.xtanative.core.x.XRateLimits
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.settings.DataPrefs
import com.aimdi.xtanative.store.settings.Settings
import java.time.Instant

/**
 * An account's state as the transport sees it: the endpoints X is
 * rate-limiting it on, until when, and until when its sign-in rests once it
 * looks broken (XTA's notFoundUntil).
 */
data class AccountDiagnostics(val screenName: String?, val rateLimited: Map<String, Instant>, val signInBrokenUntil: Instant? = null) {
    val isHealthy: Boolean get() = rateLimited.isEmpty() && signInBrokenUntil == null
}

/** One of X's operations: the query id asked with, and whether it came from the published list rather than this build. */
data class EndpointDiagnostics(val name: String, val host: String, val queryId: String, val overridden: Boolean)

/**
 * Why the app is or is not talking to X (diagnostics_report.dart): the
 * accounts, the endpoints and their query ids, the endpoint list, the crash
 * reports kept, what Android's main-thread watchdog wrote down
 * ([mainThreadStalls], [MainThreadStalls.summary]; null leaves the local
 * checks out), and the reads asked of X lately ([operations], one line each).
 */
data class DiagnosticsReport(
    val appVersion: String,
    val generatedAt: Instant,
    val accounts: List<AccountDiagnostics>,
    val endpoints: List<EndpointDiagnostics>,
    val registryEnabled: Boolean,
    val registryFetchedAt: Instant?,
    val crashReports: Int,
    val operations: List<String> = emptyList(),
    val mainThreadStalls: String? = null,
) {
    val overriddenCount: Int get() = endpoints.count { it.overridden }

    /**
     * What the copy button puts on the clipboard. Not translated, as in XTA:
     * it is pasted into issues read by whoever maintains the app. Accounts go
     * by their handle only: an account's id is its session token.
     */
    fun toPlainText(): String = buildList {
        add("XTA Native diagnostics")
        add("app: $appVersion")
        add("generated: $generatedAt")
        add("")
        add("accounts (${accounts.size}):")
        if (accounts.isEmpty()) add("  none")
        accounts.forEach { account ->
            val state = listOfNotNull(account.signInBrokenUntil?.let { "auth broken until $it" }) +
                account.rateLimited.map { (endpoint, until) -> "429 $endpoint until $until" }
            add("  ${account.screenName?.let { "@$it" } ?: "unnamed account"}: ${if (state.isEmpty()) "ok" else state.joinToString("; ")}")
        }
        add("")
        add("endpoint registry: ${if (registryEnabled) "on" else "off"}, last checked ${registryFetchedAt ?: "never"}, $overriddenCount override(s)")
        add("")
        add("endpoints:")
        endpoints.forEach { add("  ${it.name.padEnd(22)} ${it.host.padEnd(12)} ${it.queryId}${if (it.overridden) " (overridden)" else ""}") }
        add("")
        add("crash reports kept: $crashReports")
        if (mainThreadStalls != null) {
            add("")
            add("local checks:")
            add("  android main thread: $mainThreadStalls")
        }
        add("")
        add("recent reads (local timing, no request content):")
        addAll(operations)
    }.joinToString("\n")

    companion object {
        fun of(
            appVersion: String,
            accounts: List<XAccount>,
            endpoints: XEndpoints,
            limits: XRateLimits,
            settings: Settings,
            crashReports: Int,
            now: Instant,
            health: XAccountHealthStore = XAccountHealthStore.inMemory(),
            operations: List<String> = emptyList(),
            mainThreadStalls: String? = null,
        ): DiagnosticsReport {
            val paths = XEndpoints.shipped.keys.associateWith { endpoints.url(it, emptyMap()).encodedPath }
            return DiagnosticsReport(
                appVersion = appVersion,
                generatedAt = now,
                accounts = accounts.map { account ->
                    val limited = paths.mapNotNull { (name, path) ->
                        limits.resetAt(account.id, path, now.toEpochMilli())?.let { name to Instant.ofEpochMilli(it) }
                    }.toMap()
                    AccountDiagnostics(account.screenName, limited, signInBrokenUntil(health.read(account.id), now))
                },
                endpoints = XEndpoints.shipped.values.map { EndpointDiagnostics(it.name, it.host, endpoints.queryId(it.name), endpoints.isOverridden(it.name)) },
                registryEnabled = settings[DataPrefs.endpointRegistryEnabled],
                registryFetchedAt = XtaTime.parse(settings[DataPrefs.endpointRegistryFetchedAt]),
                crashReports = crashReports,
                operations = operations,
                mainThreadStalls = mainThreadStalls,
            )
        }

        /** When a flagged account's rest ends, while it lasts (XTA's lastNotFoundAt plus notFoundCooldown). */
        private fun signInBrokenUntil(health: XAccountHealth, now: Instant): Instant? =
            health.notFoundAt?.let { Instant.ofEpochMilli(it + XAccountPolicy.NOT_FOUND_COOLDOWN_MILLIS) }?.takeIf { it.isAfter(now) }
    }
}
