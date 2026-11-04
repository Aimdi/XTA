package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.pixiv.PixivBookmarkRange
import com.aimdi.xtanative.networks.pixiv.PixivDatePreset
import com.aimdi.xtanative.networks.pixiv.PixivDateRange
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivSearchFilter
import com.aimdi.xtanative.networks.pixiv.PixivSearchKind
import com.aimdi.xtanative.networks.pixiv.PixivSearchSort
import com.aimdi.xtanative.networks.pixiv.PixivSearchTarget
import com.aimdi.xtanative.networks.pixiv.PixivTag
import com.aimdi.xtanative.networks.pixiv.PixivUgoiraFilter
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.Settings
import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The search filter the reader chose to remember, in the JSON XTA keeps it as, one per kind of search. */
internal object PixivSearchMemory {
    fun prefOf(kind: PixivSearchKind): Pref<String> = if (kind.isWorks) PixivPrefs.searchFilters else PixivPrefs.novelSearchFilters

    fun historyOf(kind: PixivSearchKind): Pref<List<String>> = if (kind.isWorks) PixivPrefs.searchHistory else PixivPrefs.novelSearchHistory

    fun json(filter: PixivSearchFilter): String = buildJsonObject {
        put("target", filter.target.api)
        put("sort", filter.sort.api)
        put("date", datePresetName(filter.datePreset))
        filter.customRange?.let {
            put("start", it.start.toString())
            put("end", it.end.toString())
        }
        filter.usersIri?.let { put("users_iri", it) }
        filter.bookmarks?.let { range ->
            put("bookmark_min", range.min)
            range.max?.let { put("bookmark_max", it) }
        }
        put("hide_ai", filter.hideAi)
        put("ugoira", filter.ugoira.name.lowercase())
    }.toString()

    /** A kept filter; what is missing or unknown keeps [fallback]'s choice. */
    fun parse(text: String, fallback: PixivSearchFilter = PixivSearchFilter()): PixivSearchFilter? {
        val data = J.parse(text).takeIf { it.raw is JsonObject } ?: return null
        val start = data["start"].string?.let(::dateOrNull)
        val end = data["end"].string?.let(::dateOrNull)
        val min = data["bookmark_min"].int
        return fallback.copy(
            target = PixivSearchTarget.entries.firstOrNull { it.api == data["target"].string } ?: fallback.target,
            sort = PixivSearchSort.entries.firstOrNull { it.api == data["sort"].string } ?: fallback.sort,
            usersIri = data["users_iri"].int ?: fallback.usersIri,
            bookmarks = if (min != null) PixivBookmarkRange(min, data["bookmark_max"].int) else fallback.bookmarks,
            hideAi = data["hide_ai"].boolean ?: fallback.hideAi,
            ugoira = PixivUgoiraFilter.entries.firstOrNull { it.name.lowercase() == data["ugoira"].string } ?: fallback.ugoira,
        ).withDates(
            PixivDatePreset.entries.firstOrNull { datePresetName(it) == data["date"].string } ?: fallback.datePreset,
            if (start != null && end != null) PixivDateRange(start, end) else fallback.customRange,
        )
    }

    /** The filter kept for [kind], or null when the reader keeps none. */
    fun kept(settings: Settings, kind: PixivSearchKind, fallback: PixivSearchFilter = PixivSearchFilter()): PixivSearchFilter? =
        settings[prefOf(kind)].takeIf { it.isNotBlank() }?.let { parse(it, fallback) }

    /** [settings] keeping [filter] for later searches of [kind], or keeping none. */
    fun keeping(settings: Settings, kind: PixivSearchKind, filter: PixivSearchFilter?): Settings =
        settings.with(prefOf(kind), filter?.let(::json).orEmpty())

    /** A filter nobody touched: it hides AI works exactly when the feeds do. */
    fun fresh(settings: Settings): PixivSearchFilter = PixivSearchFilter(hideAi = settings[PixivPrefs.hideAi])

    /** Where a new search of [kind] starts: the kept filter, else a fresh one, as the account may send it. */
    fun starting(settings: Settings, kind: PixivSearchKind, isPremium: Boolean): PixivSearchFilter {
        val fresh = fresh(settings)
        return (kept(settings, kind, fresh) ?: fresh).forAccount(isPremium, kind)
    }

    /** XTA writes the date presets by their Dart names. */
    private fun datePresetName(preset: PixivDatePreset): String = when (preset) {
        PixivDatePreset.HALF_YEAR -> "halfYear"
        else -> preset.name.lowercase()
    }

    private fun dateOrNull(text: String): LocalDate? = runCatching { LocalDate.parse(text.take(10)) }.getOrNull()
}

/** Recent searches, newest first: one differing only in case replaces the older; at most [CAP]. */
internal object PixivRecentSearches {
    const val CAP = 20

    /** How many show before the rest fold behind Show all. */
    const val FOLDED = 12

    fun remembered(queries: List<String>, query: String): List<String> {
        val value = query.trim()
        if (value.isEmpty()) return queries
        return (listOf(value) + queries.filterNot { it.equals(value, ignoreCase = true) }).take(CAP)
    }

    fun forgotten(queries: List<String>, query: String): List<String> = queries.filterNot { it == query }
}

/** Text typed into the search field: every word followed by a space is finished, what follows the last space is being typed. */
internal object PixivTyping {
    private val spaces = Regex("\\s+")

    fun words(text: String): List<String> = text.trim().split(spaces).filter { it.isNotEmpty() }

    /** The word still being typed. */
    fun typedWord(text: String): String = text.substring(lastSpace(text) + 1).trim()

    fun hasEarlierWords(text: String): Boolean = lastSpace(text) >= 0 && words(text.substring(0, lastSpace(text))).isNotEmpty()

    /** [text] with the word being typed replaced by [tag], and a space after it for the next one. */
    fun replacedTypedWord(text: String, tag: String): String {
        val done = if (lastSpace(text) < 0) emptyList() else words(text.substring(0, lastSpace(text)))
        return (done + tag).joinToString(" ") + " "
    }

    /** The id a query of digits alone names, for the open-by-id shortcuts. */
    fun numericId(text: String): Long? = text.trim().takeIf { Regex("^\\d{1,12}$").matches(it) }?.toLong()?.takeIf { it > 0 }

    private fun lastSpace(text: String): Int = text.indexOfLast { it.isWhitespace() }
}

/** Tags pinned as saved searches (`plugin.pixiv.favorite_tags`), in the reader's order; a tag differing only in case is the same. */
internal object PixivFavoriteTags {
    fun of(settings: Settings): List<PixivTag> = J.parse(settings[PixivPrefs.favoriteTags]).list.mapNotNull { entry ->
        (entry.string ?: entry["name"].string)?.trim()?.ifEmpty { null }?.let { PixivTag(it, entry["translated_name"].string?.trim()) }
    }

    fun contains(settings: Settings, name: String): Boolean = of(settings).any { same(it.name, name) }

    fun added(settings: Settings, tag: PixivTag): Settings = stored(settings, of(settings).filterNot { same(it.name, tag.name) } + tag)

    fun removed(settings: Settings, name: String): Settings = stored(settings, of(settings).filterNot { same(it.name, name) })

    /** [settings] with the tag at [from] moved to [to]. */
    fun moved(settings: Settings, from: Int, to: Int): Settings {
        val tags = of(settings)
        if (from !in tags.indices) return settings
        val rest = tags.toMutableList().apply { removeAt(from) }
        rest.add(to.coerceIn(0, rest.size), tags[from])
        return stored(settings, rest)
    }

    fun same(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)

    private fun stored(settings: Settings, tags: List<PixivTag>): Settings = settings.with(
        PixivPrefs.favoriteTags,
        JsonArray(
            tags.map { tag ->
                buildJsonObject {
                    put("name", tag.name)
                    tag.translatedName?.let { put("translated_name", it) }
                }
            },
        ).toString(),
    )
}

/** What the reader's copy template may say about a work, each written `{name}`, and the links built from them. */
internal object PixivCopyInfo {
    val PLACEHOLDERS = listOf("{title}", "{illust_id}", "{user_id}", "{user_name}", "{tags}")
    const val ARTWORK_URL = "https://www.pixiv.net/artworks/{illust_id}"
    const val USER_URL = "https://www.pixiv.net/users/{user_id}"

    private val placeholder = Regex("\\{(title|illust_id|user_id|user_name|tags)\\}")

    /** [template] with every placeholder replaced by [illust]'s value in one pass, so a title holding `{user_name}` stays as written. */
    fun filled(template: String, illust: PixivIllust): String {
        val values = mapOf(
            "title" to illust.title,
            "illust_id" to "${illust.id}",
            "user_id" to "${illust.userId}",
            "user_name" to illust.userName,
            "tags" to illust.tags.joinToString(" ") { "#" + it.name },
        )
            return placeholder.replace(template) { match -> values[match.groupValues[1]] ?: match.value }
    }

    /** The reader's template, or [default] (worded in their language) while they have written none. */
    fun template(settings: Settings, default: String): String = settings[PixivPrefs.copyTemplate].takeIf { it.isNotBlank() } ?: default
}
