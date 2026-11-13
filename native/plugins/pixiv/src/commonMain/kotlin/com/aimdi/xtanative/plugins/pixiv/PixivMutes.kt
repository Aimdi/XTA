package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivNovel
import com.aimdi.xtanative.networks.pixiv.PixivisionWork
import com.aimdi.xtanative.networks.pixiv.PixivTag
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The reader's mute rules for tags: a name, or a pattern written `r'pattern'`. */
internal object PixivMuteTags {
    /** The pattern inside an entry written `r'pattern'`, or null for a plain tag name. */
    fun pattern(entry: String): String? {
        val text = entry.trim()
        return if (text.length >= 4 && text.startsWith("r'") && text.endsWith("'")) text.substring(2, text.length - 1) else null
    }

    /**
     * How a muted tag is kept: a pattern exactly as written (lower-casing would
     * turn `\D` into `\d`), a name trimmed and lower-cased. The `#` the mute
     * list shows before a name goes, so typing `#cat` mutes `cat`.
     */
    fun normalized(entry: String): String {
        val text = entry.trim()
        if (pattern(text) != null) return text
        return text.removePrefix("#").trim().lowercase()
    }

    /** Whether [entry] can be muted: not blank, and a pattern that compiles. */
    fun valid(entry: String): Boolean {
        val pattern = pattern(entry) ?: return normalized(entry).isNotEmpty()
        return runCatching { Regex(pattern) }.isSuccess
    }

    /** How the mute list names an entry: a name with its `#`, a pattern as written. */
    fun label(entry: String): String = if (pattern(entry) == null) "#$entry" else entry
}

/**
 * Muted tags compiled once: a name matches a tag exactly, whatever its case; a
 * pattern is tried on each tag and on the work's tags joined as `#a#b`, so
 * one rule can ask for several tags together.
 */
internal class PixivTagMatcher(entries: Collection<String>) {
    private val names: Set<String> = entries.filter { PixivMuteTags.pattern(it) == null }.mapTo(HashSet()) { it.lowercase() }
    private val patterns: List<Pair<String, Regex>> = entries.mapNotNull { entry ->
        PixivMuteTags.pattern(entry)?.let { source -> runCatching { entry to Regex(source, RegexOption.IGNORE_CASE) }.getOrNull() }
    }

    /** The entry that hides a work with [tags], or null when none does. */
    fun match(tags: List<PixivTag>): String? {
        tags.firstOrNull { it.name.lowercase() in names }?.let { return it.name.lowercase() }
        if (patterns.isEmpty() || tags.isEmpty()) return null
        val joined = tags.joinToString("") { "#" + it.name }
        return patterns.firstOrNull { (_, pattern) -> pattern.containsMatchIn(joined) || tags.any { pattern.containsMatchIn(it.name) } }?.first
    }
}

/** Everything the reader muted on this device: creators (with the name they had), tags, works, comments and novels. */
internal data class PixivMutes(
    val authorIds: Set<Long> = emptySet(),
    val authorNames: Map<Long, String> = emptyMap(),
    val tags: Set<String> = emptySet(),
    val illustIds: Set<Long> = emptySet(),
    val commentIds: Set<Long> = emptySet(),
    val novelIds: Set<Long> = emptySet(),
) {
    val isEmpty: Boolean get() = authorIds.isEmpty() && tags.isEmpty() && illustIds.isEmpty() && commentIds.isEmpty() && novelIds.isEmpty()

    val tagMatcher: PixivTagMatcher by lazy { PixivTagMatcher(tags) }

    /** The muted tag entry that hides [illust], or null. */
    fun mutedTagOf(illust: PixivIllust): String? = tagMatcher.match(illust.tags)

    fun isMuted(illust: PixivIllust): Boolean = illust.userId in authorIds || illust.id in illustIds || mutedTagOf(illust) != null

    fun filter(illusts: List<PixivIllust>): List<PixivIllust> = if (isEmpty) illusts else illusts.filterNot(::isMuted)

    /** [items] without those by a muted creator, [authorOf] naming each one's. */
    /** Whether a work a Pixivision article features shows: neither it nor its creator is muted. */
    fun shows(work: PixivisionWork): Boolean = work.userId !in authorIds && work.artworkId !in illustIds

    fun <T> withoutMutedAuthors(items: List<T>, authorOf: (T) -> Long): List<T> =
        if (authorIds.isEmpty()) items else items.filterNot { authorOf(it) in authorIds }

    fun hidesNovel(novel: PixivNovel): Boolean = novel.user.id in authorIds || novel.id in novelIds || tagMatcher.match(novel.tags) != null

    fun filterNovels(novels: List<PixivNovel>): List<PixivNovel> = filterNovelsOf(novels) { it }

    /** [items] without those whose novel ([novelOf]) the mutes hide, such as a series' chapters. */
    fun <T> filterNovelsOf(items: List<T>, novelOf: (T) -> PixivNovel): List<T> = if (isEmpty) items else items.filterNot { hidesNovel(novelOf(it)) }

    /** These mutes without the one on [creatorId]: a profile shows its own creator's works, as Show anyway asks. */
    fun showing(creatorId: Long): PixivMutes = if (creatorId in authorIds) copy(authorIds = authorIds - creatorId) else this

    /** Why [illust] is hidden, or null when it is not. */
    fun reasonFor(illust: PixivIllust): PixivMuteReason? = when {
        illust.id in illustIds -> PixivMuteReason.Work
        illust.userId in authorIds -> PixivMuteReason.Author((authorNames[illust.userId] ?: illust.userName).ifEmpty { "${illust.userId}" })
        else -> mutedTagOf(illust)?.let { PixivMuteReason.Tag(it) }
    }

    /** Why [novel] is hidden, or null when it is not. */
    fun reasonFor(novel: PixivNovel): PixivMuteReason? = when {
        novel.id in novelIds -> PixivMuteReason.Work
        novel.user.id in authorIds -> PixivMuteReason.Author((authorNames[novel.user.id] ?: novel.user.name).ifEmpty { "${novel.user.id}" })
        else -> tagMatcher.match(novel.tags)?.let { PixivMuteReason.Tag(it) }
    }

    fun withAuthor(id: Long, name: String): PixivMutes =
        copy(authorIds = authorIds + id, authorNames = if (name.isBlank()) authorNames else authorNames + (id to name.trim()))

    fun withoutAuthor(id: Long): PixivMutes = copy(authorIds = authorIds - id, authorNames = authorNames - id)

    /** These mutes as XTA keeps them in its preferences. */
    fun writtenTo(settings: Settings): Settings = settings
        .with(PixivPrefs.mutedAuthors, authorsJson())
        .with(PixivPrefs.mutedTags, JsonArray(tags.sorted().map(::JsonPrimitive)).toString())
        .with(PixivPrefs.mutedIllusts, idsJson(illustIds))
        .with(PixivPrefs.mutedComments, idsJson(commentIds))
        .with(PixivPrefs.mutedNovels, idsJson(novelIds))

    private fun authorsJson(): String = JsonArray(
        authorIds.sorted().map { id -> buildJsonObject { put("id", id); put("name", authorNames[id].orEmpty()) } },
    ).toString()

    companion object {
        /** The mutes in [settings]; XTA's older list of bare author ids reads too. */
        fun of(settings: Settings): PixivMutes {
            val authors = authorsIn(settings[PixivPrefs.mutedAuthors])
            return PixivMutes(
                authorIds = authors.keys,
                authorNames = authors.filterValues { it.isNotEmpty() },
                tags = J.parse(settings[PixivPrefs.mutedTags]).list.mapNotNull { it.string?.trim()?.ifEmpty { null } }.mapTo(HashSet(), PixivMuteTags::normalized),
                illustIds = idsIn(settings[PixivPrefs.mutedIllusts]),
                commentIds = idsIn(settings[PixivPrefs.mutedComments]),
                novelIds = idsIn(settings[PixivPrefs.mutedNovels]),
            )
        }

        private fun authorsIn(text: String): Map<Long, String> = J.parse(text).list.mapNotNull { entry ->
            val id = entry.long ?: entry["id"].long
            id?.takeIf { it > 0 }?.let { it to entry["name"].string?.trim().orEmpty() }
        }.toMap()

        private fun idsIn(text: String): Set<Long> = J.parse(text).list.mapNotNullTo(HashSet()) { it.long?.takeIf { id -> id > 0 } }

        private fun idsJson(ids: Set<Long>): String = JsonArray(ids.sorted().map(::JsonPrimitive)).toString()
    }
}

/** Why a work is hidden: the work itself, its creator, or one of its tags. */
internal sealed interface PixivMuteReason {
    data object Work : PixivMuteReason

    data class Author(val name: String) : PixivMuteReason

    /** [entry] is the muted name or `r'pattern'` that matched. */
    data class Tag(val entry: String) : PixivMuteReason
}

/** The reader's mutes, kept in the plugin's settings. Each change builds on what is stored, so mutes a backup just restored stay. */
internal class PixivMuteStore(private val settings: PixivSettingsStore) {
    /** The mutes now. */
    val now: PixivMutes get() = PixivMutes.of(settings.now)

    fun muteAuthor(id: Long, name: String = "") = change { it.withAuthor(id, name) }

    fun unmuteAuthor(id: Long) = change { it.withoutAuthor(id) }

    /** Mutes a tag name or an `r'pattern'`; false, and nothing kept, when it is blank or its pattern does not compile. */
    fun muteTag(entry: String): Boolean {
        if (!PixivMuteTags.valid(entry)) return false
        change { it.copy(tags = it.tags + PixivMuteTags.normalized(entry)) }
        return true
    }

    fun unmuteTag(entry: String) = change { it.copy(tags = it.tags - PixivMuteTags.normalized(entry)) }

    fun muteIllust(id: Long) = change { it.copy(illustIds = it.illustIds + id) }

    fun unmuteIllust(id: Long) = change { it.copy(illustIds = it.illustIds - id) }

    fun muteComment(id: Long) = change { it.copy(commentIds = it.commentIds + id) }

    fun unmuteComment(id: Long) = change { it.copy(commentIds = it.commentIds - id) }

    fun muteNovel(id: Long) = change { it.copy(novelIds = it.novelIds + id) }

    fun unmuteNovel(id: Long) = change { it.copy(novelIds = it.novelIds - id) }

    private fun change(edit: (PixivMutes) -> PixivMutes) = settings.update { stored -> edit(PixivMutes.of(stored)).writtenTo(stored) }
}
