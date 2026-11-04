package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.settings.RssPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * What the article reader ([RssReaderScreen]) has finished, as XTA's
 * RssReadStore keeps it for the RSS plugin's items and the web sources'
 * posts alike: their ids in `plugin.rss.read_ids` (RssPrefs.readIds),
 * newest first, the latest [CAP].
 */
object RssReads {
    const val CAP = 800

    fun decode(text: String?): List<String> = J.parse(text.orEmpty()).list.mapNotNull { it.string?.ifEmpty { null } }

    fun encode(ids: List<String>): String = JsonArray(ids.map(::JsonPrimitive)).toString()

    /** [ids] with each of [read] not read yet in front; the same list when there is none. */
    fun marked(ids: List<String>, read: Iterable<String>): List<String> {
        val known = ids.toSet()
        val fresh = read.filter { it.isNotEmpty() && it !in known }.distinct()
        return if (fresh.isEmpty()) ids else (fresh + ids).take(CAP)
    }

    /** The ids read in [settings]; none before the settings are read. */
    fun of(settings: Settings?): Set<String> = settings?.let { decode(it[RssPrefs.readIds]).toSet() }.orEmpty()

    /** [read] marked read in the app's settings, as the reader finishing them marks them (RssReadStore.markRead). */
    suspend fun mark(settings: PluginSettings, read: Iterable<String>) =
        settings.update { it.with(RssPrefs.readIds, encode(marked(decode(it[RssPrefs.readIds]), read))) }
}
