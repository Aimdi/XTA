package com.aimdi.xtanative.core.x.search

import com.aimdi.xtanative.core.x.search.XAdvancedSearch.Content
import com.aimdi.xtanative.core.x.search.XAdvancedSearch.Inclusion
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * X's query syntax, both ways: the query [XAdvancedSearch] writes, and a
 * typed query read back into filters (XTA's advanced_search_reader.dart).
 */
internal object XSearchQuery {
    private val separators = Regex("""[,\s\p{Z}﻿]+""")
    private val isoDay = DateTimeFormatter.ISO_LOCAL_DATE

    private fun tokens(input: String): List<String> = input.split(separators).filter { it.isNotEmpty() }

    private fun orGroup(items: List<String>): String = if (items.size == 1) items.first() else items.joinToString(" OR ", "(", ")")

    private fun prefixedGroup(input: String, operator: (String) -> String): String? = tokens(input).map(operator).takeIf { it.isNotEmpty() }?.let(::orGroup)

    private fun minimum(input: String, operator: String): String? = input.trim().toIntOrNull()?.takeIf { it > 0 }?.let { "$operator:$it" }

    fun positive(value: String): Boolean = (value.trim().toIntOrNull() ?: 0) > 0

    /** X's syntax, not the reader's: `2026-01-31` in Western digits. */
    fun date(value: LocalDate?): String = value?.format(isoDay).orEmpty()

    fun build(search: XAdvancedSearch): String = (
        words(search) + listOfNotNull(
            search.language.takeIf { it.isNotEmpty() }?.let { "lang:$it" },
            prefixedGroup(search.fromAccounts) { "from:${it.replace("@", "")}" },
            prefixedGroup(search.toAccounts) { "to:${it.replace("@", "")}" },
            prefixedGroup(search.mentioningAccounts) { "@${it.replace("@", "")}" },
            minimum(search.minReplies, "min_replies"),
            minimum(search.minLikes, "min_faves"),
            minimum(search.minRetweets, "min_retweets"),
            search.since?.let { "since:${date(it)}" },
            search.until?.let { "until:${date(it)}" },
        ) + kinds(search)
        ).joinToString(" ")

    private fun words(search: XAdvancedSearch): List<String> {
        val any = tokens(search.anyWords)
        return listOfNotNull(
            search.allWords.trim().ifEmpty { null },
            search.exactPhrase.trim().ifEmpty { null }?.let { "\"$it\"" },
            if (any.isEmpty()) null else orGroup(any),
        ) + tokens(search.noneWords).map { "-$it" } + listOfNotNull(prefixedGroup(search.hashtags) { if (it.startsWith("#")) it else "#$it" })
    }

    private fun kinds(search: XAdvancedSearch): List<String> = listOfNotNull(
        when (search.content) {
            Content.ALL -> null
            Content.MEDIA -> "filter:media"
            Content.PHOTOS -> "filter:images"
            Content.VIDEOS -> "filter:videos"
        },
        inclusion("links", search.links),
        inclusion("replies", search.replies),
        "include:nativeretweets".takeIf { search.includeReposts },
        "-filter:retweets".takeIf { search.excludeRetweets },
    )

    private fun inclusion(kind: String, inclusion: Inclusion): String? = when (inclusion) {
        Inclusion.INCLUDE -> null
        Inclusion.ONLY -> "filter:$kind"
        Inclusion.EXCLUDE -> "-filter:$kind"
    }

    // ---- reading a typed query ----

    /**
     * The control of the filters screen an operator sets. Two operators of a
     * query setting the same control contradict or combine in a way the
     * screen cannot show, so both then stay in the words.
     */
    private enum class Control { LANGUAGE, FROM, TO, MIN_REPLIES, MIN_LIKES, MIN_RETWEETS, SINCE, UNTIL, CONTENT, LINKS, REPLIES, REPOSTS }

    private class Reading(val control: Control, val apply: (XAdvancedSearch) -> XAdvancedSearch)

    private val operator = Regex("""^(-?)(\w+):(\S+)$""")
    private val accountGroup = Regex("""^\((from|to):\w{1,50}( OR \1:\w{1,50})+\)$""")
    private val langCode = Regex("""^[a-z]{2,3}(-[a-z]{2,4})?$""")
    private val account = Regex("""^\w{1,50}$""")
    private val count = Regex("""^[1-9]\d{0,8}$""")
    private val isoDate = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val booleanWords = setOf("AND", "OR", "NOT")

    fun read(query: String): XAdvancedSearch {
        val text = query.trim()
        val tokens = topLevelTokens(text).orEmpty()
        val readings = tokens.indices.map { i -> if (standsAlone(tokens, i)) readToken(tokens[i]) else null }
        val once = readings.filterNotNull().groupingBy { it.control }.eachCount().filterValues { it == 1 }.keys
        val moved = readings.map { it != null && it.control in once }
        if (moved.none { it }) return XAdvancedSearch(allWords = text)
        val words = tokens.filterIndexed { i, _ -> !moved[i] }.joinToString(" ")
        return readings.filterIndexed { i, _ -> moved[i] }.fold(XAdvancedSearch(allWords = words)) { search, reading -> reading!!.apply(search) }
    }

    /** [query] split at its top-level spaces, a quoted phrase or a bracketed group one token; null when they do not balance. */
    private fun topLevelTokens(query: String): List<String>? {
        val tokens = ArrayList<String>()
        var start = 0
        var depth = 0
        var quoted = false
        var escaped = false
        for ((i, character) in query.withIndex()) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> quoted = !quoted
                !quoted && character == '(' -> depth++
                !quoted && character == ')' -> if (--depth < 0) return null
                !quoted && depth == 0 && character.isWhitespace() -> {
                    if (i > start) tokens += query.substring(start, i)
                    start = i + 1
                }
            }
        }
        if (quoted || escaped || depth != 0) return null
        return if (query.length > start) tokens + query.substring(start) else tokens
    }

    /** An operator next to OR, AND or NOT belongs to that expression, not to the filters. */
    private fun standsAlone(tokens: List<String>, index: Int): Boolean =
        !(index > 0 && tokens[index - 1] in booleanWords) && !(index + 1 < tokens.size && tokens[index + 1] in booleanWords)

    private fun readToken(token: String): Reading? {
        accountGroup.matchEntire(token)?.let { return readAccountGroup(it.groupValues[1], token) }
        val match = operator.matchEntire(token) ?: return null
        val (sign, name, value) = match.destructured
        return readOperator(sign == "-", name, value)
    }

    /** `(from:alice OR from:bob)`, as the form writes several accounts. */
    private fun readAccountGroup(operator: String, group: String): Reading {
        val accounts = group.substring(1, group.length - 1).split(" OR ").joinToString(" ") { it.substring(operator.length + 1) }
        return if (operator == "from") Reading(Control.FROM) { it.copy(fromAccounts = accounts) } else Reading(Control.TO) { it.copy(toAccounts = accounts) }
    }

    /** Only values the form writes back unchanged are read, so a read query rebuilds to the same text. */
    private fun readOperator(negated: Boolean, name: String, value: String): Reading? = when {
        !negated && name == "lang" -> valued(value, langCode, Control.LANGUAGE) { copy(language = it) }
        !negated && name == "from" -> valued(value, account, Control.FROM) { copy(fromAccounts = it) }
        !negated && name == "to" -> valued(value, account, Control.TO) { copy(toAccounts = it) }
        !negated && name == "min_replies" -> valued(value, count, Control.MIN_REPLIES) { copy(minReplies = it) }
        !negated && name == "min_faves" -> valued(value, count, Control.MIN_LIKES) { copy(minLikes = it) }
        !negated && name == "min_retweets" -> valued(value, count, Control.MIN_RETWEETS) { copy(minRetweets = it) }
        !negated && name == "since" -> dated(value, Control.SINCE) { copy(since = it) }
        !negated && name == "until" -> dated(value, Control.UNTIL) { copy(until = it) }
        name != "filter" -> if (!negated && name == "include" && value == "nativeretweets") Reading(Control.REPOSTS) { it.copy(includeReposts = true) } else null
        value == "links" -> Reading(Control.LINKS) { it.copy(links = inclusion(negated)) }
        value == "replies" -> Reading(Control.REPLIES) { it.copy(replies = inclusion(negated)) }
        value == "retweets" -> if (negated) Reading(Control.REPOSTS) { it.copy(excludeRetweets = true) } else null
        negated -> null
        else -> mapOf("media" to Content.MEDIA, "images" to Content.PHOTOS, "videos" to Content.VIDEOS)[value]
            ?.let { content -> Reading(Control.CONTENT) { it.copy(content = content) } }
    }

    private fun valued(value: String, pattern: Regex, control: Control, apply: XAdvancedSearch.(String) -> XAdvancedSearch): Reading? =
        if (pattern.matches(value)) Reading(control) { it.apply(value) } else null

    private fun dated(value: String, control: Control, apply: XAdvancedSearch.(LocalDate) -> XAdvancedSearch): Reading? {
        // 2026-02-30 is no day: it is not read, rather than read as another one.
        val day = if (isoDate.matches(value)) runCatching { LocalDate.parse(value) }.getOrNull() else null
        return if (day == null || date(day) != value) null else Reading(control) { it.apply(day) }
    }

    private fun inclusion(negated: Boolean): Inclusion = if (negated) Inclusion.EXCLUDE else Inclusion.ONLY
}
