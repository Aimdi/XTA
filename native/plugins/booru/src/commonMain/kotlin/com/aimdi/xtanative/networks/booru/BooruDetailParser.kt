package com.aimdi.xtanative.networks.booru

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Reads what a post's page loads on demand: its tags' kinds, wiki pages, comments, related tags, artists, pools and notes. */
object BooruDetailParser {
    /** Moebooru answers `include_tags` with a name to kind map; the others with tag rows carrying kinds and counts. */
    fun tagInfo(reply: BooruTagReply, engine: BooruEngine): Map<String, BooruTagInfo> {
        val kinds = (reply as? BooruTagReply.Json)?.body?.get("tags")?.raw
        if (engine == BooruEngine.MOEBOORU && kinds is JsonObject) {
            return kinds.mapNotNull { (name, kind) -> moebooruKind(J(kind))?.let { name to BooruTagInfo(category = it) } }.toMap()
        }
        return BooruParser.tagSuggestions(reply, engine).associate { it.name to BooruTagInfo(it.category, it.postCount) }
    }

    /** The body of [tag]'s wiki page, or null when there is none. A title search answers a list; a single page is taken as it is. */
    fun wiki(root: J, tag: String): String? {
        val page = if (root.raw is JsonArray) root.list.firstOrNull { sameTitle(it["title"].string, tag) } else root
        if (page == null || page["is_deleted"].boolean == true) return null
        return page["body"].string?.trim()?.ifEmpty { null }
    }

    /**
     * The comments still shown, oldest first: by date, or by id when a comment has none (the
     * hosts' ids grow with time). Deleted and hidden ones are left out.
     */
    fun comments(root: J): List<BooruComment> {
        val rows = if (root.raw is JsonArray) root.list else root["comments"].list
        val comments = rows.mapNotNull(::comment)
        return if (comments.all { it.createdAt != null }) {
            comments.sortedBy { it.createdAt }
        } else {
            comments.sortedBy { it.id.toLongOrNull() ?: 0 }
        }
    }

    /** Related tag names in the host's order: Danbooru's `related_tags` entries, or e621's tag row, whose `related_tags` alternates names and weights. */
    fun relatedTags(root: J, engine: BooruEngine): List<String> = when (engine) {
        BooruEngine.DANBOORU -> root["related_tags"].list.mapNotNull { it["tag"]["name"].string }
        BooruEngine.E621 -> root.list.firstOrNull()?.get("related_tags")?.string.orEmpty()
            .split(' ').filter { it.isNotEmpty() }
            .chunked(2).filter { it.size == 2 && it[1].toDoubleOrNull() != null }.map { it[0] }
        else -> emptyList()
    }

    /** The entry for the artist [name] names: the one by that name, else the first. Deleted entries are skipped, and links the host marks inactive left out. */
    fun artist(root: J, name: String): BooruArtist? {
        val entries = root.list.filter { it["is_deleted"].boolean != true && it["name"].string != null }
        val entry = entries.firstOrNull { it["name"].string?.lowercase() == name.lowercase() } ?: entries.firstOrNull() ?: return null
        return BooruArtist(
            name = entry["name"].string.orEmpty(),
            otherNames = entry["other_names"].list.mapNotNull { it.string },
            urls = entry["urls"].list.filter { it["is_active"].boolean != false }.mapNotNull { it["url"].string },
        )
    }

    /** The pools of a `/pools.json` answer, deleted ones left out. */
    fun pools(root: J): List<BooruPool> = root.list.filter { it["is_deleted"].boolean != true }.mapNotNull { pool ->
        val id = pool["id"].integer?.toString() ?: pool["id"].string
        val name = pool["name"].string
        if (id == null || name == null) null else BooruPool(id, name, pool["post_count"].integer?.toInt())
    }

    /** The active notes of a `/notes.json` answer, their HTML read as plain text; notes without a box or text are left out. */
    fun notes(root: J): List<BooruNote> = root.list.filter { it["is_active"].boolean != false }.mapNotNull { note ->
        val (x, y, width, height) = listOf("x", "y", "width", "height").map { note[it].integer?.toInt() }
        val text = BooruText.plain(note["body"].string.orEmpty())
        if (x == null || y == null || width == null || height == null || text.isEmpty()) null else BooruNote(x, y, width, height, text)
    }

    private fun comment(json: J): BooruComment? {
        val id = json["id"].integer
        val body = json["body"].string?.trim()
        val hidden = json["is_deleted"].boolean == true || json["is_hidden"].boolean == true
        if (id == null || body.isNullOrEmpty() || hidden) return null
        return BooruComment(
            id = id.toString(),
            author = json["creator"]["name"].string ?: json["creator_name"].string ?: json["creator"].string,
            body = body,
            createdAt = BooruDates.parse(json["created_at"].string.orEmpty()),
            score = json["score"].integer?.toInt(),
        )
    }

    /** A kind in Moebooru's tag map: a number, or a word. */
    private fun moebooruKind(kind: J): BooruTagCategory? {
        val code = (kind.raw as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
        return if (code != null) BooruTagCategory.fromWire(code.toInt(), BooruEngine.MOEBOORU) else BooruTagCategory.named(kind.string)
    }

    private fun sameTitle(title: String?, tag: String): Boolean = title != null && title.lowercase().replace(' ', '_') == tag.lowercase()
}

/**
 * Wiki pages, comments and notes arrive as DText (Danbooru, e621) or light HTML (Moebooru). XTA
 * shows them as plain text: links keep their label, markup goes.
 */
object BooruText {
    private val WIKI_LINK = Regex("""\[\[([^\]|]+)\|([^\]]*)\]\]""")
    private val PLAIN_WIKI_LINK = Regex("""\[\[([^\]]+)\]\]""")
    private val TAG_SEARCH = Regex("""\{\{([^}]+)\}\}""")
    private val NAMED_LINK = Regex(""""([^"]+)":\[?(?:https?://|/)[^\s\]]*\]?""")
    private val BRACKET_MARKUP = Regex("""\[/?[a-z]+(?:=[^\]]*)?\]""", RegexOption.IGNORE_CASE)
    private val HEADING = Regex("""^h[1-6](?:#[\w-]+)?\.\s*""", RegexOption.MULTILINE)
    private val HTML_BREAK = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val HTML_TAG = Regex("""<[^>]+>""")
    private val BLANK_RUN = Regex("""\n{3,}""")

    /** `&amp;` last, so `&amp;lt;` stays the text `&lt;`. */
    private val ENTITIES = listOf("&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&nbsp;" to " ", "&amp;" to "&")

    fun plain(raw: String): String {
        val text = raw.replace("\r\n", "\n")
            .replace(WIKI_LINK) { it.groupValues[2].ifEmpty { it.groupValues[1] } }
            .replace(PLAIN_WIKI_LINK) { it.groupValues[1] }
            .replace(TAG_SEARCH) { it.groupValues[1] }
            .replace(NAMED_LINK) { it.groupValues[1] }
            .replace(BRACKET_MARKUP, "")
            .replace(HEADING, "")
            .replace(HTML_BREAK, "\n")
            .replace(HTML_TAG, "")
        return ENTITIES.fold(text) { decoded, (entity, character) -> decoded.replace(entity, character) }
            .replace(BLANK_RUN, "\n\n")
            .trim()
    }
}
