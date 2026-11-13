package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.core.json.J
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A link, @mention or #tag in a post's text. The AT Protocol counts its
 * offsets in UTF-8 bytes, not in the characters of a Kotlin string.
 */
data class BlueskyFacet(
    val byteStart: Int,
    val byteEnd: Int,
    val kind: Kind,
    /** The link's URI, the mentioned account's DID, or the tag without its `#`. */
    val value: String,
) {
    enum class Kind { LINK, MENTION, TAG }
}

/** A run of a post's text: a plain one, or the part a [facet] covers. */
data class BlueskyTextRun(val text: String, val facet: BlueskyFacet? = null)

/**
 * The whitespace of Dart's regular expressions (JavaScript's `\s`), which
 * Java's `\s` narrows to ASCII: for patterns that must split as XTA's do.
 */
internal const val JS_SPACE = """\t\n\u000B\f\r    -     　﻿"""

/** Facets as XTA reads, cuts and finds them. */
object BlueskyFacets {
    /** A bare link in a profile description, which has no facets of its own. */
    private val bareLink = Regex("""(?<![A-Za-z0-9_])https?://[^$JS_SPACE<>"']+""", RegexOption.IGNORE_CASE)

    private val trailingPunctuation = Regex("[.,;:!?]+$")

    private val brackets = listOf('(' to ')', '[' to ']', '{' to '}')

    /**
     * The facets of a post record (or a quoted post's `value`), by byte
     * start. Each facet is its first feature that names something; one
     * whose range is missing or empty is left out.
     */
    fun of(record: J): List<BlueskyFacet> = record["facets"].list.mapNotNull { facet ->
        val start = facet["index"]["byteStart"].integer?.toIntClamped()
        val end = facet["index"]["byteEnd"].integer?.toIntClamped()
        if (start == null || end == null || end <= start) null else facet["features"].list.firstNotNullOfOrNull { feature(it, start, end) }
    }.sortedBy { it.byteStart }

    private fun feature(feature: J, start: Int, end: Int): BlueskyFacet? {
        val type = feature["\$type"].string.orEmpty()
        val (kind, value) = when {
            "facet#link" in type -> BlueskyFacet.Kind.LINK to feature["uri"]
            "facet#mention" in type -> BlueskyFacet.Kind.MENTION to feature["did"]
            "facet#tag" in type -> BlueskyFacet.Kind.TAG to feature["tag"]
            else -> return null
        }
        return value.nonBlank()?.let { BlueskyFacet(start, end, kind, it) }
    }

    /**
     * For each UTF-8 byte offset into [text] (and one past its end), the
     * index of the character it falls in. A lone surrogate counts the three
     * bytes of the replacement character Dart encodes it as.
     */
    fun utf16Indices(text: String): IntArray {
        val indices = IntArray(utf8Length(text) + 1) { text.length }
        var byte = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val size = utf8Size(codePoint)
            indices.fill(index, byte, byte + size)
            byte += size
            index += Character.charCount(codePoint)
        }
        return indices
    }

    /**
     * [text] cut at [facets] (by byte start, as [of] orders them) into plain
     * and annotated runs that join back into [text]. A facet that is empty
     * or overlaps the one before it stays plain text.
     */
    fun runs(text: String, facets: List<BlueskyFacet>): List<BlueskyTextRun> {
        if (text.isEmpty()) return emptyList()
        if (facets.isEmpty()) return listOf(BlueskyTextRun(text))
        val indices = utf16Indices(text)
        fun at(byte: Int) = when {
            byte <= 0 -> 0
            byte >= indices.size -> indices.last()
            else -> indices[byte]
        }.coerceIn(0, text.length)
        val runs = ArrayList<BlueskyTextRun>()
        var cursor = 0
        for (facet in facets) {
            val start = at(facet.byteStart)
            val end = at(facet.byteEnd)
            if (end <= start || start < cursor) continue
            if (start > cursor) runs += BlueskyTextRun(text.substring(cursor, start))
            runs += BlueskyTextRun(text.substring(start, end), facet)
            cursor = end
        }
        if (cursor < text.length) runs += BlueskyTextRun(text.substring(cursor))
        return runs
    }

    /**
     * The links in a profile description, as link facets: descriptions
     * carry plain text only. Trailing punctuation and unbalanced closing
     * brackets are not part of a link; a link with a user name is no link.
     */
    fun profileLinks(text: String): List<BlueskyFacet> = bareLink.findAll(text).mapNotNull { match ->
        profileUrl(match.value)?.let { url ->
            val start = match.range.first
            val end = start + url.length
            BlueskyFacet(utf8Length(text.substring(0, start)), utf8Length(text.substring(0, end)), BlueskyFacet.Kind.LINK, url)
        }
    }.toList()

    private fun profileUrl(candidate: String): String? {
        val url = brackets.fold(candidate.replace(trailingPunctuation, "")) { url, (open, close) -> balanced(url, open, close) }
        val parsed = url.toHttpUrlOrNull() ?: return null
        return url.takeIf { parsed.host.isNotEmpty() && parsed.username.isEmpty() && parsed.password.isEmpty() }
    }

    private tailrec fun balanced(url: String, open: Char, close: Char): String =
        if (url.endsWith(close) && url.count { it == close } > url.count { it == open }) balanced(url.dropLast(1), open, close) else url

    internal fun utf8Length(text: String): Int = text.codePoints().map { utf8Size(it) }.sum()

    private fun utf8Size(codePoint: Int): Int = when {
        codePoint < 0x80 -> 1
        codePoint < 0x800 -> 2
        codePoint < 0x10000 -> 3
        else -> 4
    }

    private fun Long.toIntClamped(): Int = coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}
