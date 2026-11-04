package com.aimdi.xtanative.networks.websources

import com.aimdi.xtanative.core.json.J
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The installed sources as XTA keeps them in `plugin.websources.installed`:
 * each one's file, where it is read again from, the sites agreed to, and a
 * newer version waiting for the reader to agree to a site it adds.
 */
object WebSourcesInstalled {
    /** What is stored; an entry whose file no longer reads (a newer format) is left out. */
    fun decode(text: String?): List<InstalledWebSource> = J.parse(text.orEmpty()).list.mapNotNull(::entryOf)

    fun encode(installed: List<InstalledWebSource>): String = JsonArray(installed.map(::jsonOf)).toString()

    /** [source] installed from [origin], replacing the one with its id: the reader has just agreed to every site it lists. */
    fun installed(installed: List<InstalledWebSource>, source: WebSourceDefinition, origin: String?): List<InstalledWebSource> =
        installed.filter { it.source.id != source.id } + InstalledWebSource(source, source.hosts, origin?.toHttpUrlOrNull())

    fun removed(installed: List<InstalledWebSource>, id: String): List<InstalledWebSource> = installed.filter { it.source.id != id }

    /** The update waiting on [id] taken: the reader agreed to its sites. */
    fun agreed(installed: List<InstalledWebSource>, id: String): List<InstalledWebSource> = installed.map { entry ->
        val waiting = entry.waiting
        if (entry.source.id == id && waiting != null) InstalledWebSource(waiting, waiting.hosts, entry.origin) else entry
    }

    /** [installed] with each entry [updates] names replaced by its update ([WebSourceUpdates.updated]). */
    fun updated(installed: List<InstalledWebSource>, updates: Map<String, InstalledWebSource>): List<InstalledWebSource> =
        installed.map { updates[it.source.id] ?: it }

    /**
     * Whether the files are due to be read again: never read, or not within
     * [WebSourceUpdates.INTERVAL_HOURS]. A time XTA wrote without a zone is
     * the device's own.
     */
    fun dueForUpdate(checkedAt: String?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val text = checkedAt?.trim()?.ifEmpty { null } ?: return true
        val last = runCatching { Instant.parse(text) }.recoverCatching { LocalDateTime.parse(text).atZone(zone).toInstant() }.getOrNull() ?: return true
        return Duration.between(last, now) >= Duration.ofHours(WebSourceUpdates.INTERVAL_HOURS)
    }

    /** When the files were read again, as XTA writes it: Dart's `toIso8601String()`. */
    fun checkedAt(now: Instant): String = ISO.format(now)

    private val ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    private fun entryOf(row: J): InstalledWebSource? {
        val source = (WebSources.fromJson(row["source"].raw) as? WebSourceFile.Read)?.source ?: return null
        return InstalledWebSource(
            source = source,
            agreedHosts = row["hosts"].list.mapNotNull { it.string },
            origin = row["origin"].string?.toHttpUrlOrNull(),
            waiting = (WebSources.fromJson(row["waiting"].raw) as? WebSourceFile.Read)?.source,
        )
    }

    private fun jsonOf(entry: InstalledWebSource): JsonObject = buildJsonObject {
        put("source", entry.source.raw)
        put("origin", entry.origin?.toString())
        put("hosts", JsonArray(entry.agreedHosts.map(::JsonPrimitive)))
        entry.waiting?.let { put("waiting", it.raw) }
    }
}

/**
 * A followed list, as XTA keeps it in `plugin.websources.follows`: a
 * subscription row, `id` its source and list (`lobsters/hottest`), `name`
 * the source's and `screen_name` the list's. [row] is the row as stored.
 */
data class WebSourceFollow(val row: JsonObject) {
    val key: String get() = J(row)["id"].string.orEmpty()
    val sourceId: String get() = key.substringBefore('/')
    val listId: String get() = key.substringAfter('/', "")

    /** Its id among the app's subscriptions and groups: `websources:lobsters/hottest`. */
    val subscriptionId: String get() = WebSourceFollows.subscriptionId(sourceId, listId)
}

/** The followed lists, kept in preferences as subscriptions, so the settings backup carries them. */
object WebSourceFollows {
    fun key(sourceId: String, listId: String): String = "$sourceId/$listId"

    fun subscriptionId(sourceId: String, listId: String): String = "${WebSources.NETWORK}:${key(sourceId, listId)}"

    /** The source and list [key] names; null when it names no list. */
    fun parts(key: String): Pair<String, String>? {
        val at = key.indexOf('/')
        return if (at <= 0 || at == key.length - 1) null else key.substring(0, at) to key.substring(at + 1)
    }

    /** What is stored; a row that names no list is left out. */
    fun decode(text: String?): List<WebSourceFollow> = J.parse(text.orEmpty()).list
        .mapNotNull { (it.raw as? JsonObject)?.let(::WebSourceFollow) }
        .filter { parts(it.key) != null }

    fun encode(follows: List<WebSourceFollow>): String = JsonArray(follows.map { it.row }).toString()

    fun isFollowing(follows: List<WebSourceFollow>, sourceId: String, listId: String): Boolean = follows.any { it.key == key(sourceId, listId) }

    /** [list] of [source] followed, last and once, as XTA writes the row. */
    fun followed(follows: List<WebSourceFollow>, source: WebSourceDefinition, list: WebSourceList, now: Instant): List<WebSourceFollow> {
        val row = buildJsonObject {
            put("id", key(source.id, list.id))
            put("name", source.name)
            put("screen_name", list.name)
            put("avatar_url", source.icon?.toString())
            put("in_feed", 1)
            put("created_at", WebSourcesInstalled.checkedAt(now))
        }
        return follows.filter { it.key != key(source.id, list.id) } + WebSourceFollow(row)
    }

    fun unfollowed(follows: List<WebSourceFollow>, subscriptionId: String): List<WebSourceFollow> = follows.filter { it.subscriptionId != subscriptionId }

    /** Every list of [sourceId] unfollowed, as removing the source does. */
    fun withoutSource(follows: List<WebSourceFollow>, sourceId: String): List<WebSourceFollow> = follows.filter { it.sourceId != sourceId }
}
