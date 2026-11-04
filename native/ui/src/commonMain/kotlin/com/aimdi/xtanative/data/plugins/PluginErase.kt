package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.core.feed.FeedStrip
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.PluginSettings

/** [this] without the group memberships of plugin [id]'s accounts, whose member ids start "<id>:". */
internal fun Session.withoutMembersOf(id: String): Session =
    copy(groups = groups.map { group -> group.copy(memberIds = group.memberIds.filterNot { it.startsWith("$id:") }) })

/**
 * [this] without what Home keeps of plugin [id]: its place on the strip,
 * among the timelines opened last and on the bottom bar, and its top bar's
 * pins; and without the network's own settings (`plugin.<id>.…`, which a
 * plugin keeps through [PluginSettings]), as XTA's uninstall resets them.
 * A setting that never named it stays as it was, unset or not.
 */
internal fun Settings.withoutPlugin(id: String): Settings {
    fun Settings.dropping(pref: Pref<List<String>>): Settings = if (id in this[pref]) with(pref, FeedStrip.forgotten(this[pref], id)) else this
    val pins = HomePrefs.topBarPins[id]
    val own = PluginSettings.prefixOf(id)
    return dropping(HomePrefs.feedStripNetworks)
        .dropping(HomePrefs.recentNetworks)
        .dropping(HomePrefs.pages)
        .let { if (it.isSet(pins)) it.without(pins) else it }
        .let { settings -> settings.values.keys.filter { it.startsWith(own) }.fold(settings) { kept, key -> kept.with(key, null) } }
}

/** [this] with plugin [id] on Home's strip, after the networks already there, or off it. */
internal fun Settings.withPluginOnStrip(id: String, pinned: Boolean): Settings {
    val pins = this[HomePrefs.feedStripNetworks]
    return with(HomePrefs.feedStripNetworks, if (pinned) FeedStrip.pinned(pins, id) else FeedStrip.forgotten(pins, id))
}
