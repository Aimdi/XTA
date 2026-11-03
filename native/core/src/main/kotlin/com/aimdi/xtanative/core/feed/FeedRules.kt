package com.aimdi.xtanative.core.feed

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.parse.XText
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What the rules read of a post that [XPost] does not carry yet: X's
 * `possibly_sensitive` and `lang`. Until the parser keeps them, nothing counts
 * as sensitive and no language is known, which is how XTA treats a post that
 * lacks them.
 */
interface PostFacts {
    fun sensitive(post: XPost): Boolean

    fun language(post: XPost): String?

    companion object {
        val UNKNOWN: PostFacts = object : PostFacts {
            override fun sensitive(post: XPost) = false

            override fun language(post: XPost): String? = null
        }
    }
}

/** What a rule does with one thread: shows it, hides it, or shows it folded behind [Fold.reason]. */
sealed interface Verdict {
    data object Keep : Verdict

    data object Hide : Verdict

    data class Fold(val reason: String) : Verdict
}

/** Threads to show and, for the folded ones, why, by thread id. */
data class Shaped(val threads: List<XThread>, val foldReasons: Map<String, String> = emptyMap())

/** A group's content filter: everything, only posts X marks sensitive, or none of them. */
enum class ContentFilter(val stored: String) {
    SFW("sfw"),
    DEFAULT("default"),
    NSFW("nsfw"),
    ;

    companion object {
        fun parse(raw: String?): ContentFilter = entries.firstOrNull { it.stored == raw } ?: DEFAULT
    }
}

/** What a muted keyword does to a post that has it. */
enum class KeywordAction(val stored: String) {
    HIDE("hide"),
    FOLD("fold"),
    ;

    companion object {
        fun parse(raw: String?): KeywordAction = if (raw == FOLD.stored) FOLD else HIDE
    }
}

/** A muted word or phrase, muted until [until] (for good when null). */
data class MutedKeyword(val term: String, val until: Instant? = null, val action: KeywordAction = KeywordAction.HIDE) {
    fun activeAt(now: Instant): Boolean = until == null || !until.isBefore(now)
}

/**
 * Muted keywords as XTA keeps and matches them (lib/group/muted_keyword.dart,
 * custom_feed_rules.dart).
 */
object MutedKeywords {
    private val wholeWord = Regex("^[\\p{L}\\p{N}]+$")

    /**
     * Whether [text] contains [term], ignoring case. A single word matches on
     * word boundaries, so muting "cat" does not hide "category"; a phrase or
     * anything with punctuation matches as written, which is what someone
     * typing "black friday" or "$TSLA" expects.
     */
    fun matches(text: String, term: String): Boolean {
        val needle = term.trim().lowercase()
        if (needle.isEmpty()) return false
        val haystack = text.lowercase()
        if (!wholeWord.matches(needle)) return haystack.contains(needle)
        return Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(needle) + "(?![\\p{L}\\p{N}])").containsMatchIn(haystack)
    }

    /** What was typed, split into terms at commas and line breaks. */
    fun parseTyped(raw: String?): List<String> =
        raw.orEmpty().split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun join(terms: List<String>): String = terms.joinToString(", ")

    fun active(keywords: List<MutedKeyword>, now: Instant): List<MutedKeyword> = keywords.filter { it.activeAt(now) }

    /** The stored form: XTA's JSON array, or the comma-separated terms older builds wrote. */
    fun parseStored(raw: String?): List<MutedKeyword> {
        val text = raw?.trim().orEmpty()
        return when {
            text.isEmpty() -> emptyList()
            text.startsWith("[") -> J.parse(text).list.mapNotNull(::fromJson)
            else -> parseTyped(text).map { MutedKeyword(it) }
        }
    }

    /** JSON, as XTA writes it; nothing when there is nothing muted. */
    fun encodeStored(keywords: List<MutedKeyword>): String = if (keywords.isEmpty()) "" else json(keywords).toString()

    internal fun json(keywords: List<MutedKeyword>) = JsonArray(
        keywords.map { keyword ->
            buildJsonObject {
                put("term", keyword.term)
                keyword.until?.let { put("until", it.toString()) }
                if (keyword.action != KeywordAction.HIDE) put("action", keyword.action.stored)
            }
        },
    )

    internal fun fromJson(row: J): MutedKeyword? {
        val term = row["term"].string?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return MutedKeyword(term, instantOf(row["until"].string), KeywordAction.parse(row["action"].string))
    }

    /** XTA writes the time with its zone, or as local time when the reader picked a day. */
    internal fun instantOf(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        val zone = ZoneId.systemDefault()
        return runCatching { Instant.parse(raw) }
            .recoverCatching { OffsetDateTime.parse(raw).toInstant() }
            .recoverCatching { LocalDateTime.parse(raw).atZone(zone).toInstant() }
            .recoverCatching { LocalDate.parse(raw).atStartOfDay(zone).toInstant() }
            .getOrNull()
    }
}

/**
 * What a feed hides beyond replies and reposts, as XTA's custom feed rules:
 * a content filter, engagement floors and muted keywords. They apply whatever
 * the feed's sort: a mute list stops nothing if it only works in one order.
 */
data class FeedRules(
    val contentFilter: ContentFilter = ContentFilter.DEFAULT,
    /** Hides posts with fewer likes; 0 turns it off. */
    val minLikes: Int = 0,
    /** Hides posts with fewer reposts and quotes together; 0 turns it off. */
    val minReposts: Int = 0,
    val mutedKeywords: List<MutedKeyword> = emptyList(),
) {
    val isEmpty: Boolean get() = contentFilter == ContentFilter.DEFAULT && minLikes == 0 && minReposts == 0 && mutedKeywords.isEmpty()

    /** Two feeds with different rules never share what they read. */
    val cacheKey: String get() = "${contentFilter.stored}|$minLikes|$minReposts|${MutedKeywords.encodeStored(mutedKeywords)}"

    /**
     * What the rules do with [thread]. The floors read the thread's first post,
     * as the footer and the popular order do; keywords read every post in it
     * and what each reposts and quotes, since a muted word there is on screen too.
     */
    fun verdict(thread: XThread, active: List<MutedKeyword>, facts: PostFacts = PostFacts.UNKNOWN): Verdict {
        if (filtered(thread, facts)) return Verdict.Hide
        if (active.isEmpty()) return Verdict.Keep
        val text = searchText(thread)
        val keyword = active.firstOrNull { MutedKeywords.matches(text, it.term) } ?: return Verdict.Keep
        return if (keyword.action == KeywordAction.FOLD) Verdict.Fold(keyword.term) else Verdict.Hide
    }

    private fun filtered(thread: XThread, facts: PostFacts): Boolean {
        val first = thread.posts.firstOrNull()
        val byContent = when (contentFilter) {
            ContentFilter.SFW -> thread.posts.any(facts::sensitive)
            ContentFilter.NSFW -> thread.posts.none(facts::sensitive)
            ContentFilter.DEFAULT -> false
        }
        return byContent || (minLikes > 0 && (first?.likeCount ?: 0) < minLikes) || (minReposts > 0 && reposts(first) < minReposts)
    }

    /** Keeps what the rules let through, and says why the folded ones are folded. */
    fun apply(threads: List<XThread>, now: Instant = Instant.now(), facts: PostFacts = PostFacts.UNKNOWN): Shaped {
        if (isEmpty) return Shaped(threads)
        val active = MutedKeywords.active(mutedKeywords, now)
        val verdicts = threads.map { it to verdict(it, active, facts) }
        return Shaped(
            threads = verdicts.filter { it.second != Verdict.Hide }.map { it.first },
            foldReasons = verdicts.mapNotNull { (thread, verdict) -> (verdict as? Verdict.Fold)?.let { thread.id to it.reason } }.toMap(),
        )
    }

    companion object {
        private fun reposts(post: XPost?): Long = (post?.repostCount ?: 0) + (post?.quoteCount ?: 0)

        /**
         * The text keyword muting looks at, as the reader sees it: X escapes
         * `&`, `<` and `>`, which XTA matched against, so "AT&T" never matched.
         */
        fun searchText(thread: XThread): String = thread.posts
            .flatMap { post -> listOfNotNull(post, post.reposted, post.quoted, post.reposted?.quoted) }
            .joinToString(" ") { XText.unescape(it.text) }

        internal fun json(rules: FeedRules): JsonObject = buildJsonObject {
            put("contentFilter", rules.contentFilter.stored)
            put("minLikes", rules.minLikes)
            put("minReposts", rules.minReposts)
            put("muted", MutedKeywords.json(rules.mutedKeywords))
        }

        internal fun of(row: J) = FeedRules(
            contentFilter = ContentFilter.parse(row["contentFilter"].string),
            minLikes = row["minLikes"].int?.coerceAtLeast(0) ?: 0,
            minReposts = row["minReposts"].int?.coerceAtLeast(0) ?: 0,
            mutedKeywords = row["muted"].list.mapNotNull(MutedKeywords::fromJson),
        )
    }
}

/** What the language filter does with a post outside the languages the reader reads. */
enum class LanguageAction(val stored: String) {
    OFF("off"),
    HIDE("hide"),
    FOLD("fold"),
    ;

    companion object {
        fun parse(raw: String?): LanguageAction = entries.firstOrNull { it.stored == raw && it != OFF } ?: OFF
    }
}

/**
 * XTA's global language filter: posts whose language starts with none of
 * [allowed] are hidden or folded. A post whose language X did not say passes.
 */
data class LanguageFilter(val allowed: List<String> = emptyList(), val action: LanguageAction = LanguageAction.OFF) {
    fun verdict(thread: XThread, facts: PostFacts = PostFacts.UNKNOWN): Verdict {
        if (action == LanguageAction.OFF || allowed.isEmpty()) return Verdict.Keep
        val language = thread.posts.firstOrNull()?.let(facts::language)
        return when {
            isAllowed(language) -> Verdict.Keep
            action == LanguageAction.HIDE -> Verdict.Hide
            else -> Verdict.Fold(language ?: "?")
        }
    }

    /** [threads] filtered, with [priorFolds] kept and a language's fold reason taking a thread's place in them. */
    fun apply(threads: List<XThread>, priorFolds: Map<String, String> = emptyMap(), facts: PostFacts = PostFacts.UNKNOWN): Shaped {
        val verdicts = threads.map { it to verdict(it, facts) }
        return Shaped(
            threads = verdicts.filter { it.second != Verdict.Hide }.map { it.first },
            foldReasons = priorFolds + verdicts.mapNotNull { (thread, verdict) -> (verdict as? Verdict.Fold)?.let { thread.id to it.reason } },
        )
    }

    private fun isAllowed(language: String?): Boolean {
        if (language.isNullOrEmpty()) return true
        val lowered = language.lowercase()
        return allowed.any { prefix -> prefix.trim().lowercase().let { it.isNotEmpty() && lowered.startsWith(it) } }
    }

    companion object {
        /** XTA's setting: comma-separated language prefixes (`en,fr,ja`). */
        fun parseLanguages(raw: String?): List<String> = raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
