package com.aimdi.xtanative.plugins.mastodon

import com.aimdi.xtanative.networks.mastodon.MastodonInstances
import com.aimdi.xtanative.networks.mastodon.MastodonTimeline
import com.aimdi.xtanative.networks.mastodon.MastodonTimelineFilter
import com.aimdi.xtanative.networks.mastodon.MastodonTimelineOptions
import com.aimdi.xtanative.store.settings.MastodonPrefs
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings

/** What the plugin reads of the app's settings: its own, under XTA's keys (`:store`'s MastodonPrefs), and how posts read. */
internal data class MastodonPrefsView(
    val home: String = "",
    val extras: List<String> = emptyList(),
    val timelines: Map<Int, MastodonTimelineFilter> = emptyMap(),
    /** Zen or calm mode is on: no engagement counts. */
    val hideCounts: Boolean = false,
    /** Settings › Posts › Remember reading position. */
    val keepPlaces: Boolean = true,
    val places: String = "",
) {
    /** The reader's own instances, home first. */
    val configured: List<String> get() = MastodonInstances.configured(home, extras)

    fun filter(timeline: MastodonTimeline): MastodonTimelineFilter = timelines[timeline.ordinal] ?: MastodonTimelineFilter()
}

/** The plugin's settings, read from and written into the app's, as XTA's Mastodon settings screen and timeline controls keep them. */
internal object MastodonSettings {
    fun view(settings: Settings?): MastodonPrefsView = if (settings == null) MastodonPrefsView() else MastodonPrefsView(
        home = settings[MastodonPrefs.instance],
        extras = settings[MastodonPrefs.instances],
        timelines = MastodonTimelineOptions.parse(settings[MastodonPrefs.timeline]),
        hideCounts = settings[PostsPrefs.zenMode] || settings[PostsPrefs.calmMode],
        keepPlaces = settings[PostsPrefs.rememberReadingPosition],
        places = settings[MastodonPrefs.readerPlaces],
    )

    /** [timeline]'s filters and order kept; what is searched in its loaded posts is not, as in XTA. */
    fun withFilter(settings: Settings, timeline: MastodonTimeline, filter: MastodonTimelineFilter): Settings {
        val all = MastodonTimelineOptions.parse(settings[MastodonPrefs.timeline]) + (timeline.ordinal to filter.copy(query = ""))
        return settings.with(MastodonPrefs.timeline, MastodonTimelineOptions.encode(all))
    }

    /** The home instance as typed, kept as an address when it is one, else as it was typed, trimmed. */
    fun withHome(settings: Settings, typed: String): Settings =
        settings.with(MastodonPrefs.instance, MastodonInstances.normalise(typed) ?: typed.trim())

    /** One more instance after the others; nothing changes for one that is no address or is there already. */
    fun withExtra(settings: Settings, typed: String): Settings {
        val instance = MastodonInstances.normalise(typed) ?: return settings
        val extras = settings[MastodonPrefs.instances]
        return if (instance in extras) settings else settings.with(MastodonPrefs.instances, extras + instance)
    }

    fun withoutExtra(settings: Settings, instance: String): Settings =
        settings.with(MastodonPrefs.instances, settings[MastodonPrefs.instances].filterNot { it == instance })

    fun withPlaces(settings: Settings, text: String): Settings = settings.with(MastodonPrefs.readerPlaces, text)
}
