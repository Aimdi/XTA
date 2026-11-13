package com.aimdi.xtanative.networks.mastodon

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A Fediverse account followed on this device, as XTA's
 * `mastodon_subscription` row keeps it: [acct] (`user@domain`) is its key.
 * No instance is ever told. [createdAt] is the text XTA wrote, kept as it is.
 */
data class MastodonFollow(
    val acct: String,
    val name: String,
    val avatarUrl: String? = null,
    val inFeed: Boolean = true,
    val createdAt: String? = null,
) {
    fun isOf(other: String): Boolean = acct.equals(other.trim().removePrefix("@"), ignoreCase = true)
}

/**
 * The follows as XTA's backup writes them (`mastodonSubscriptions`: id, name,
 * avatar_url, in_feed, created_at), as its import takes them (a row replaces
 * the one with its id, the others stay), listed by name as XTA lists them.
 */
object MastodonFollowRows {
    /** XTA's backup key for these rows. */
    const val BACKUP_KEY = "mastodonSubscriptions"

    fun encode(follows: List<MastodonFollow>): JsonArray = JsonArray(follows.map(::row))

    fun row(follow: MastodonFollow): JsonElement = buildJsonObject {
        put("id", follow.acct)
        put("name", follow.name)
        put("avatar_url", follow.avatarUrl)
        put("in_feed", if (follow.inFeed) 1 else 0)
        put("created_at", follow.createdAt)
    }

    /** The rows in [json], each with an id and a name; a later row with an id already read replaces it. */
    fun decode(json: J): List<MastodonFollow> = json.list.mapNotNull { row ->
        val acct = row["id"].string?.trim()?.ifEmpty { null } ?: return@mapNotNull null
        val name = row["name"].string ?: return@mapNotNull null
        val inFeed = row["in_feed"].let { it.long?.let { bit -> bit != 0L } ?: it.boolean ?: true }
        MastodonFollow(acct, name, row["avatar_url"].string, inFeed, row["created_at"].string)
    }.let { upsert(emptyList(), it) }

    /** [incoming] over [current], as SQLite's INSERT OR REPLACE writes it: same account, same place. */
    fun upsert(current: List<MastodonFollow>, incoming: List<MastodonFollow>): List<MastodonFollow> {
        val fresh = LinkedHashMap<String, MastodonFollow>()
        incoming.forEach { fresh[it.acct.lowercase()] = it }
        val kept = current.map { fresh.remove(it.acct.lowercase()) ?: it }
        return kept + fresh.values
    }

    /** By name, whatever its case, as XTA's `ORDER BY name COLLATE NOCASE` lists them. */
    fun ordered(follows: List<MastodonFollow>): List<MastodonFollow> = follows.sortedBy { it.name.lowercase() }
}
