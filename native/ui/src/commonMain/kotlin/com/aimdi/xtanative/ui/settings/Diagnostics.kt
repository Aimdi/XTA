package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.core.x.XEndpoints
import com.aimdi.xtanative.core.x.XRateLimits
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.settings.DataPrefs
import com.aimdi.xtanative.store.settings.Settings
import java.time.Instant

/** An account's state as the transport sees it: the endpoints X is rate-limiting it on, until when. */
data class AccountDiagnostics(val screenName: String?, val rateLimited: Map<String, Instant>) {
    val isHealthy: Boolean get() = rateLimited.isEmpty()
}

/** One of X's operations: the query id asked with, and whether it came from the published list rather than this build. */
data class EndpointDiagnostics(val name: String, val host: String, val queryId: String, val overridden: Boolean)

/**
 * Why the app is or is not talking to X (diagnostics_report.dart): the
 * accounts, the endpoints and their query ids, the endpoint list, and the
 * crash reports kept.
 */
data class DiagnosticsReport(
    val appVersion: String,
    val generatedAt: Instant,
    val accounts: List<AccountDiagnostics>,
    val endpoints: List<EndpointDiagnostics>,
    val registryEnabled: Boolean,
    val registryFetchedAt: Instant?,
    val crashReports: Int,
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
            val state = account.rateLimited.map { (endpoint, until) -> "429 $endpoint until $until" }
            add("  ${account.screenName?.let { "@$it" } ?: "unnamed account"}: ${if (state.isEmpty()) "ok" else state.joinToString("; ")}")
        }
        add("")
        add("endpoint registry: ${if (registryEnabled) "on" else "off"}, last checked ${registryFetchedAt ?: "never"}, $overriddenCount override(s)")
        add("")
        add("endpoints:")
        endpoints.forEach { add("  ${it.name.padEnd(22)} ${it.host.padEnd(12)} ${it.queryId}${if (it.overridden) " (overridden)" else ""}") }
        add("")
        add("crash reports kept: $crashReports")
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
        ): DiagnosticsReport {
            val paths = XEndpoints.shipped.keys.associateWith { endpoints.url(it, emptyMap()).encodedPath }
            return DiagnosticsReport(
                appVersion = appVersion,
                generatedAt = now,
                accounts = accounts.map { account ->
                    val limited = paths.mapNotNull { (name, path) ->
                        limits.resetAt(account.id, path, now.toEpochMilli())?.let { name to Instant.ofEpochMilli(it) }
                    }.toMap()
                    AccountDiagnostics(account.screenName, limited)
                },
                endpoints = XEndpoints.shipped.values.map { EndpointDiagnostics(it.name, it.host, endpoints.queryId(it.name), endpoints.isOverridden(it.name)) },
                registryEnabled = settings[DataPrefs.endpointRegistryEnabled],
                registryFetchedAt = XtaTime.parse(settings[DataPrefs.endpointRegistryFetchedAt]),
                crashReports = crashReports,
            )
        }
    }
}
