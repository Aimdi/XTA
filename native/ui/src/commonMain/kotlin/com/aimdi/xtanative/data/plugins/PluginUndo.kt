package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.store.settings.PrefValue
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.PluginSettings

/**
 * What a plugin keeps at one moment, as an unfollow's undo compares it
 * (subscription_unfollow.dart's snapshots of a source's table or its
 * preference): the text of each of its backup sections, and its own settings.
 */
data class PluginKept(val sections: Map<String, String?>, val settings: Map<String, PrefValue>)

/** Taking an unfollow in a plugin back, as XTA's undo does: only while nothing changed since. */
object PluginUndo {
    /** Plugin [id]'s own settings in [settings]: those under its prefix and the keys its network declares. */
    fun ownSettings(settings: Settings, id: String): Map<String, PrefValue> {
        val prefix = PluginSettings.prefixOf(id)
        val declared = PluginSettings.keysOf(id)
        return settings.values.filterKeys { it.startsWith(prefix) || it in declared }
    }

    /** [settings] with plugin [id]'s own as [kept] holds them, the rest as it is. */
    fun withOwn(settings: Settings, id: String, kept: Map<String, PrefValue>): Settings =
        (ownSettings(settings, id).keys + kept.keys).fold(settings) { next, key -> next.with(key, kept[key]) }

    /** The sections whose text the unfollow changed, with what to give each back. */
    fun changedSections(before: PluginKept, after: PluginKept): Map<String, String> =
        before.sections.mapNotNull { (name, text) -> text?.takeIf { it != after.sections[name] }?.let { name to it } }.toMap()
}
