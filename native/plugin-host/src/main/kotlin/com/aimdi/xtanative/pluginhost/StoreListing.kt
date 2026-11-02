package com.aimdi.xtanative.pluginhost

import java.util.Locale

/** Where one plugin stands, as its row in the store shows it. */
sealed interface PluginStatus {
    /** On offer, not installed: Install. */
    data object Available : PluginStatus

    /** Built for plugin API [apiVersion], newer than this app's: "needs a newer XTA Native". [installed] is the version on the device, if any. */
    data class NeedsNewerApp(val apiVersion: Int, val installed: String? = null) : PluginStatus

    /** Being fetched: [bytes] of [total] have arrived. */
    data class Downloading(val bytes: Long, val total: Long) : PluginStatus {
        val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    /** Fetched; being checked and put in place. */
    data object Installing : PluginStatus

    data class Installed(val version: String) : PluginStatus

    /** [installed] is on the device; the catalogue offers [available]. */
    data class UpdateAvailable(val installed: String, val available: String) : PluginStatus

    /** The last install, update or uninstall failed for [reason]; [installed] is the version still on the device, if any. */
    data class Failed(val reason: PluginFailure, val installed: String?) : PluginStatus
}

/** One plugin in the store: what the catalogue says of it, what is installed, and where it stands. */
data class PluginState(
    val id: String,
    /** The catalogue's entry when it names this plugin, even one it holds back. */
    val entry: CatalogueEntry?,
    val installed: InstalledPlugin?,
    val status: PluginStatus,
) {
    val category: PluginCategory get() = entry?.category ?: installed?.category ?: PluginCategory.OTHER

    fun name(locale: Locale): String = entry?.name?.resolve(locale) ?: installed?.name?.resolve(locale) ?: id

    fun description(locale: Locale): String = entry?.description?.resolve(locale) ?: installed?.description?.resolve(locale).orEmpty()
}

/**
 * What the plugin store lists, by the rules of XTA's store: every installed
 * plugin first, whatever the catalogue says of it or when there is no
 * catalogue at all, then, by category, what the catalogue offers on
 * [platform]. The catalogue only offers: an entry it holds back
 * (`available: false`), one without a file for [platform], one built for an
 * API older than this app loads, and a private one while [showPrivate] is off,
 * are not on offer. One built for a newer API is listed as needing a newer app.
 */
fun storeListing(
    catalogue: PluginCatalogue?,
    installed: Map<String, InstalledPlugin>,
    platform: PluginPlatform,
    api: PluginApi,
    showPrivate: Boolean,
): List<PluginState> {
    val entries = catalogue?.entries.orEmpty()
    val position = entries.withIndex().associate { (index, entry) -> entry.id to index }
    val mine = installed.values
        .sortedWith(compareBy({ position[it.id] ?: Int.MAX_VALUE }, { it.id }))
        .map { plugin -> catalogue?.entry(plugin.id).let { PluginState(plugin.id, it, plugin, installedStatus(plugin, it, platform, api)) } }
    val onOffer = entries
        .filter { it.id !in installed && isOffered(it, platform, api, showPrivate) }
        .sortedBy { it.category.ordinal }
        .map { PluginState(it.id, it, null, offeredStatus(it, api)) }
    return mine + onOffer
}

/** Whether the store offers [entry] to someone who does not have it. */
internal fun isOffered(entry: CatalogueEntry, platform: PluginPlatform, api: PluginApi, showPrivate: Boolean): Boolean =
    entry.available && entry.artifact(platform) != null && (showPrivate || !entry.isPrivate) &&
        api.refusal(entry.apiVersion) !is PluginFailure.NeedsNewerPlugin

/** Whether [entry] is a version [plugin] can move to: offered on [platform], newer, and for an API this app loads. */
internal fun isUpdate(entry: CatalogueEntry, plugin: InstalledPlugin, platform: PluginPlatform, api: PluginApi): Boolean =
    entry.available && entry.artifact(platform) != null && api.supports(entry.apiVersion) &&
        PluginVersions.compare(entry.version, plugin.version) > 0

private fun installedStatus(plugin: InstalledPlugin, entry: CatalogueEntry?, platform: PluginPlatform, api: PluginApi): PluginStatus = when {
    api.refusal(plugin.apiVersion) is PluginFailure.NeedsNewerApp -> PluginStatus.NeedsNewerApp(plugin.apiVersion, plugin.version)
    entry != null && isUpdate(entry, plugin, platform, api) -> PluginStatus.UpdateAvailable(plugin.version, entry.version)
    else -> PluginStatus.Installed(plugin.version)
}

private fun offeredStatus(entry: CatalogueEntry, api: PluginApi): PluginStatus =
    when (api.refusal(entry.apiVersion)) {
        is PluginFailure.NeedsNewerApp -> PluginStatus.NeedsNewerApp(entry.apiVersion)
        else -> PluginStatus.Available
    }
