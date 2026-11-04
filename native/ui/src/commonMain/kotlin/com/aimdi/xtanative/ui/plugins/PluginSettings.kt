package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.store.settings.PrefValue
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.XtaPrefs
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's settings as a plugin sees them. It reads all of them (its
 * network's preferences, `:store`'s NetworkPrefs, beside the reader's theme
 * and language) and changes only its own: the keys under `plugin.<id>.`,
 * and the few its network's NetworkPrefs declares where XTA kept them
 * elsewhere ([keysOf]). They are the app's settings, so XTA's backup
 * restores them where XTA kept them and the app's backup carries them, with
 * nothing for the plugin to do.
 */
class PluginSettings(
    private val id: String,
    val state: StateFlow<Settings?>,
    private val write: suspend ((Settings) -> Settings) -> Unit,
) {
    /** Applies [change] to the app's settings, keeping only what it does to this plugin's keys. */
    suspend fun update(change: (Settings) -> Settings) = write { current -> ownChanges(current, change(current), prefixOf(id), keysOf(id)) }

    /** [update], answering whether it was written: one that fails (the disk would not keep it) leaves them as they were. */
    suspend fun tryUpdate(change: (Settings) -> Settings): Boolean = try {
        update(change)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    /** The settings now: the defaults until the app has read them. */
    fun current(): Settings = state.value ?: Settings()

    /** The plugin whose settings these are: the id its keys sit under. */
    val pluginId: String get() = id

    /** Whether [settings] have the plugin's followed accounts in Home's Following and For you: its "show in the home timeline" switch. */
    fun inHomeFeed(settings: Settings? = state.value): Boolean = inHomeFeed(settings, id)

    /** That switch turned on or off. */
    suspend fun setInHomeFeed(on: Boolean) = update { it.with(homeFeedKey(id), PrefValue.Bool(on)) }

    companion object {
        /** Where a plugin's preferences are kept, as XTA keeps a network's. */
        fun prefixOf(id: String) = "plugin.$id."

        /** Where plugin [id]'s "show in the home timeline" switch is kept, as XTA keeps a network's (`NetworkPrefs.inHomeFeed`). */
        fun homeFeedKey(id: String) = prefixOf(id) + "in_home_feed"

        /** Whether plugin [id]'s followed accounts join Home's timelines in [settings]: off until the reader turns it on. */
        fun inHomeFeed(settings: Settings?, id: String): Boolean = (settings?.values?.get(homeFeedKey(id)) as? PrefValue.Bool)?.value == true

        /**
         * The keys [id]'s network declares outside its prefix, which are its
         * all the same: Stocks' `other.ticker_chart` and `other.ticker_instruments`;
         * and those of another network it writes where XTA's does
         * (NetworkPrefs.shared): the web sources' read marks among RSS's.
         */
        fun keysOf(id: String): Set<String> {
            val network = XtaPrefs.networks.firstOrNull { it.id == id } ?: return emptySet()
            return (network.prefs.map { it.key }.filterNot { it.startsWith(prefixOf(id)) } + network.shared.map { it.key }).toSet()
        }

        /** [before] with what [after] holds under [prefix] and at [keys], and nothing else of it. */
        internal fun ownChanges(before: Settings, after: Settings, prefix: String, keys: Set<String> = emptySet()): Settings =
            (before.values.keys + after.values.keys)
                .filter { it.startsWith(prefix) || it in keys }
                .fold(before) { settings, key -> settings.with(key, after.values[key]) }
    }
}
