package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What Threads' "About this profile" says of an account: when it joined, as
 * a moment ([joinedAt]) where Meta gave a time, else in Meta's own words
 * ([joined], "July 2023").
 */
data class ThreadsProfileAbout(val joinedAt: Instant? = null, val joined: String? = null)

/**
 * The guest reads threads.com asks by a doc id only its JavaScript bundles
 * name ([ThreadsDocIds]): the names its client sends them under, the
 * variables each takes, and what their answers say. No id is shipped yet
 * ([ThreadsRepairs.WAITING]), so each is off; CI's `ThreadsDocIdsLiveTest`
 * logs the ids, the variables and the fields the bundles show. All three are
 * queries: nothing here writes to Threads.
 *
 * Their answers were never seen, so each is read under every key it might
 * use and anything else is nothing: a read in another shape shows the
 * reader what they had before it.
 */
object ThreadsBundleReads {
    /** The replies under a reply, in place ([ThreadsDirectClient.deeperReplies]). */
    const val INLINE_REPLIES = "BarcelonaInlineRepliesExpansionQuery"

    /** "About this profile" ([ThreadsDirectClient.aboutProfile]). */
    const val ABOUT_PROFILE = "BarcelonaAboutThisProfileDialogQuery"

    /** The search page's trending topics, when the page itself carries none ([ThreadsDirectClient.trends]). */
    const val TRENDING_TOPICS = "BarcelonaSearchTrendingTopicsSectionQuery"

    private val JOINED_KEYS = listOf(
        "joined_date", "date_joined", "join_date", "joined_at", "joined_time", "join_time", "joined",
        "account_creation_date", "account_created_time", "creation_date", "created_at", "date_created", "created_time",
    )
    private val TEXT_KEYS = listOf("text", "formatted", "formatted_date", "display", "label")
    private val TIME_KEYS = listOf("timestamp", "time", "seconds", "epoch")
    private const val LONGEST_WORDS = 60

    /** Before Threads, and Instagram whose accounts it took over, no account could have joined. */
    private val EARLIEST: Instant = Instant.parse("2010-01-01T00:00:00Z")
    private const val MILLIS_FROM = 100_000_000_000L
    private const val DAY_SECONDS = 86_400L
    private val spaces = Regex("\\s+")

    /** The variables of a read that takes one id, under the [name] its query declares. */
    fun variables(name: String, id: String): JsonObject = JsonObject(mapOf(name to JsonPrimitive(id)))

    /**
     * What an "About this profile" answer says, wherever under its `data` it
     * says it; null when it names no date this reads. [now] bounds a moment
     * that cannot be a join date.
     */
    fun about(roots: List<JsonElement>, now: Instant = Instant.now()): ThreadsProfileAbout? = roots.asSequence()
        .mapNotNull { (it as? JsonObject)?.get("data") }
        .flatMap { joined(it, 0) }
        .mapNotNull { about(J(it), now) }
        .firstOrNull()

    /** Every value under [node] kept under one of the names a join date might go by, shallowest first within an object. */
    private fun joined(node: JsonElement, depth: Int): Sequence<JsonElement> = when {
        depth > ThreadsJson.MAX_DEPTH -> emptySequence()
        node is JsonArray -> node.asSequence().flatMap { joined(it, depth + 1) }
        node is JsonObject -> JOINED_KEYS.asSequence().mapNotNull { node[it] } + node.values.asSequence().flatMap { joined(it, depth + 1) }
        else -> emptySequence()
    }

    private fun about(value: J, now: Instant): ThreadsProfileAbout? {
        val moment = moment(value, now) ?: TIME_KEYS.firstNotNullOfOrNull { moment(value[it], now) }
        if (moment != null) return ThreadsProfileAbout(joinedAt = moment)
        val words = (value.string ?: TEXT_KEYS.firstNotNullOfOrNull { value[it].string })
            ?.replace(spaces, " ")?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= LONGEST_WORDS && it.any(Char::isLetter) }
        return words?.let { ThreadsProfileAbout(joined = it) }
    }

    /** A number, or digits, as seconds or milliseconds since 1970: a moment between [EARLIEST] and tomorrow, else null. */
    private fun moment(value: J, now: Instant): Instant? {
        val number = value.integer ?: value.string?.trim()?.takeIf { it.length in 9..13 && it.all(Char::isDigit) }?.toLongOrNull() ?: return null
        val seconds = if (number >= MILLIS_FROM) number / 1000 else number
        return Instant.ofEpochSecond(seconds).takeIf { it.isAfter(EARLIEST) && it.isBefore(now.plusSeconds(DAY_SECONDS)) }
    }
}
