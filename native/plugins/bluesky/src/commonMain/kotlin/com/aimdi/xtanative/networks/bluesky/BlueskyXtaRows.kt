package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Bluesky's rows in XTA's backup, as XTA writes and reads them
 * (plugin_backup.dart, backup_rows.dart): `blueskySubscriptions`, a row per
 * account followed here (`bluesky_subscription`: id, name, avatar_url,
 * in_feed, created_at), and `blueskyLocalLikes`, a row per post liked here
 * (`bluesky_local_like`: id, the post's URI).
 */
object BlueskyXtaRows {
    /** The section key of the follows, as XTA's backup names it. */
    const val FOLLOWS = "blueskySubscriptions"

    /** The section key of the likes. */
    const val LIKES = "blueskyLocalLikes"

    /**
     * The follows of a `blueskySubscriptions` section. XTA names an account
     * by its handle, or by its DID when that is what it kept; a row without
     * an id or a name is no row, as XTA's own import reads it. A row without
     * a time was followed [now].
     */
    fun follows(text: String, now: Instant): List<BlueskyFollow> = J.parse(text).list.mapNotNull { row ->
        val id = row["id"].string?.trim()?.removePrefix("@")?.ifEmpty { null } ?: return@mapNotNull null
        val name = row["name"].string ?: return@mapNotNull null
        val did = id.takeIf { it.startsWith("did:") }.orEmpty()
        BlueskyFollow(
            did = did,
            handle = if (did.isEmpty()) id else "",
            name = name.ifBlank { id },
            avatarUrl = row["avatar_url"].nonBlank(),
            followedAt = (BlueskyTime.parse(row["created_at"].string) ?: now).toEpochMilli(),
            inFeed = row["in_feed"].let { it.integer?.let { bit -> bit == 1L } ?: it.boolean } ?: true,
        )
    }.distinctBy { it.actor.lowercase() }

    /** [follow]'s id in XTA's rows, which XTA's groups hold it by: its handle, as XTA names one, else its DID. */
    fun idOf(follow: BlueskyFollow): String = follow.handle.ifEmpty { follow.did }

    /** [follows] as XTA's rows, each by [idOf]. */
    fun followRows(follows: List<BlueskyFollow>): String = JsonArray(
        follows.map { follow ->
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive(idOf(follow)),
                    "name" to JsonPrimitive(follow.name),
                    "avatar_url" to (follow.avatarUrl?.let(::JsonPrimitive) ?: JsonNull),
                    "in_feed" to JsonPrimitive(if (follow.inFeed) 1 else 0),
                    "created_at" to JsonPrimitive(BlueskyTime.sqlite(Instant.ofEpochMilli(follow.followedAt))),
                ),
            )
        },
    ).toString()

    /** The liked posts' URIs of a `blueskyLocalLikes` section. */
    fun likes(text: String): List<String> = J.parse(text).list.mapNotNull { it["id"].string?.trim()?.takeIf { id -> id.startsWith("at://") } }.distinct()

    /** [likes] as XTA's rows. */
    fun likeRows(likes: List<BlueskyLike>): String = JsonArray(likes.map { JsonObject(mapOf("id" to JsonPrimitive(it.uri))) }).toString()
}
