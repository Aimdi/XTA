package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.core.feed.FeedStrip
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.XtaPrefs
import com.aimdi.xtanative.ui.plugins.PluginSettings

/** [this] without the group memberships of plugin [id]'s accounts, whose member ids start "<id>:". */
internal fun Session.withoutMembersOf(id: String): Session =
    copy(groups = groups.map { group -> group.copy(memberIds = group.memberIds.filterNot { it.startsWith("$id:") }) })

/**
 * [this] without what Home keeps of plugin [id]: its place on the strip,
 * among the timelines opened last and on the bottom bar, its top bar's
 * pins, and Home's memory of having offered it a tab or a place on the
 * strip, so that installing it again offers them as a first install does
 * (XTA's uninstall); and without the network's own settings (`plugin.<id>.…`, which a
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
        .dropping(HomePrefs.seededNetworkTabs)
        .dropping(HomePrefs.seededStripNetworks)
        .let { if (it.isSet(pins)) it.without(pins) else it }
        .let { settings -> settings.values.keys.filter { it.startsWith(own) }.fold(settings) { kept, key -> kept.with(key, null) } }
}

/** [this] with plugin [id] on Home's strip, after the networks already there, or off it. */
internal fun Settings.withPluginOnStrip(id: String, pinned: Boolean): Settings {
    val pins = this[HomePrefs.feedStripNetworks]
    return with(HomePrefs.feedStripNetworks, if (pinned) FeedStrip.pinned(pins, id) else FeedStrip.forgotten(pins, id))
}

/** Whether plugin [id] has a page on Home's bottom bar to offer: a network with its own tab (XTA's homeTabPrefKey). */
internal fun hasTabOf(id: String): Boolean = XtaPrefs.networks.firstOrNull { it.id == id }?.showTab != null

/** Whether plugin [id] shows as a tab of Home's bottom bar in [this], as XTA's showsHomeTab says: on, and its tab not switched off. */
internal fun Settings.showsPluginAsTab(id: String): Boolean {
    val network = XtaPrefs.networks.firstOrNull { it.id == id } ?: return false
    val tab = network.showTab ?: return false
    return this[network.enabled] && this[tab]
}

/**
 * [this] with plugin [id]'s page on Home's bottom bar, or not, as XTA's "Show
 * as a tab" turns it (_plugin_row.dart): on, the network is on and offered to
 * the bar again, which puts it there once; off, it waits among Home's
 * timelines instead. A network without a tab of its own stays as it was.
 */
internal fun Settings.withPluginAsTab(id: String, on: Boolean): Settings {
    val network = XtaPrefs.networks.firstOrNull { it.id == id } ?: return this
    val tab = network.showTab ?: return this
    val shown = with(tab, on).with(network.enabled, true)
    return if (on) shown.with(HomePrefs.seededNetworkTabs, this[HomePrefs.seededNetworkTabs] - id) else shown.withPluginOnStrip(id, pinned = true)
}
