package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A Bluesky account followed here, as it looked when it was followed. Nothing is told to Bluesky. */
data class BlueskyFollow(
    val did: String,
    val handle: String,
    val name: String,
    val avatarUrl: String?,
    /** When it was followed, epoch milliseconds. */
    val followedAt: Long,
    /** XTA's "in the home timeline" switch for it, kept for XTA's backup. */
    val inFeed: Boolean = true,
) {
    /** What the AppView reads it by: the DID, which outlives a renamed handle. */
    val actor: String get() = did.ifEmpty { handle }

    /** How it is named under its name: its handle, or its DID when that is all there is. */
    val shownHandle: String get() = handle.ifEmpty { did }

    fun isOf(actor: String): Boolean = actor.isNotEmpty() && (actor == did || actor.equals(handle, ignoreCase = true))

    companion object {
        fun of(profile: BlueskyProfile, at: Long): BlueskyFollow =
            BlueskyFollow(profile.did, profile.handle, profile.displayName, profile.avatarUrl, at)
    }
}

/** A post liked here and only here, by its URI: Bluesky is never told. */
data class BlueskyLike(val uri: String, val likedAt: Long)

/**
 * The follows and likes kept on the device, as XTA keeps them: following
 * again keeps an account's place and date, a like goes first, and what a
 * backup brings is merged in, a row with a known key replacing the old one.
 * A like is its URI (XTA's `bluesky_local_like`); the posts the Liked tab
 * shows are kept beside, as XTA keeps them in its liked posts' preference,
 * the newest [LIKED_POSTS_CAP].
 */
object BlueskyLocal {
    /** How many liked posts XTA keeps to show (blueskyLikedPostsCap). */
    const val LIKED_POSTS_CAP = 400

    /** [follows] with [follow]: one already there keeps its place and date, with its name and picture brought up to date. */
    fun followed(follows: List<BlueskyFollow>, follow: BlueskyFollow): List<BlueskyFollow> {
        val known = follows.firstOrNull { it.isOf(follow.did) || it.isOf(follow.handle) } ?: return follows + follow
        val kept = follow.copy(followedAt = known.followedAt, inFeed = known.inFeed).withIdsOf(known)
        return follows.map { if (it === known) kept else it }
    }

    fun unfollowed(follows: List<BlueskyFollow>, actor: String): List<BlueskyFollow> = follows.filterNot { it.isOf(actor) }

    /** [follows] with those of [people] not followed yet, followed [at]: an import, which skips who is already there. */
    fun withNew(follows: List<BlueskyFollow>, people: List<BlueskyProfile>, at: Long): List<BlueskyFollow> =
        people.filter { it.handle.isNotBlank() || it.did.isNotBlank() }.fold(follows) { all, person ->
            if (all.any { it.isOf(person.did) || it.isOf(person.handle) }) all else all + BlueskyFollow.of(person, at)
        }

    /** [follows] with each of [incoming] followed: XTA's import, which replaces rows by their id and keeps the rest. */
    fun merged(follows: List<BlueskyFollow>, incoming: List<BlueskyFollow>): List<BlueskyFollow> =
        incoming.fold(follows) { all, follow ->
            val known = all.firstOrNull { it.isOf(follow.did) || it.isOf(follow.handle) }
            if (known == null) all + follow else all.map { if (it === known) follow.withIdsOf(known) else it }
        }

    /** [this], keeping the DID or handle of [known] where it has none: XTA's rows name an account by one of them. */
    private fun BlueskyFollow.withIdsOf(known: BlueskyFollow): BlueskyFollow =
        copy(did = did.ifEmpty { known.did }, handle = handle.ifEmpty { known.handle })

    /** [likes] with [uri] liked at [at], first; liked again, it moves up. */
    fun liked(likes: List<BlueskyLike>, uri: String, at: Long): List<BlueskyLike> = listOf(BlueskyLike(uri, at)) + unliked(likes, uri)

    fun unliked(likes: List<BlueskyLike>, uri: String): List<BlueskyLike> = likes.filterNot { it.uri == uri }

    /** [likes] with the posts of [uris] not liked yet added after them, as XTA's import adds likes it did not have. */
    fun mergedLikes(likes: List<BlueskyLike>, uris: List<String>, at: Long): List<BlueskyLike> {
        val known = likes.mapTo(HashSet()) { it.uri }
        return likes + uris.filter { it.startsWith("at://") }.distinct().filterNot { it in known }.map { BlueskyLike(it, at) }
    }

    /** The liked posts to show with [post] liked, first, at most [LIKED_POSTS_CAP]. */
    fun withLikedPost(posts: List<BlueskyPost>, post: BlueskyPost): List<BlueskyPost> =
        (listOf(post) + posts.filterNot { it.uri == post.uri }).take(LIKED_POSTS_CAP)

    /** What the Liked tab shows: the liked posts kept to show that are still liked, as XTA's likes store reads them. */
    fun library(posts: List<BlueskyPost>, likes: List<BlueskyLike>): List<BlueskyPost> {
        val liked = likes.mapTo(HashSet()) { it.uri }
        return posts.filter { it.uri in liked }.distinctBy { it.uri }
    }

    /** The liked posts written by the account [did] or [handle]: what its profile's Likes tab shows (a like stays the reader's). */
    fun likedBy(posts: List<BlueskyPost>, did: String, handle: String): List<BlueskyPost> {
        val handleKey = handle.trim().lowercase()
        if (did.isBlank() && handleKey.isEmpty()) return emptyList()
        return posts.filter { post -> (did.isNotBlank() && post.did == did.trim()) || (handleKey.isNotEmpty() && post.handle.lowercase() == handleKey) }
    }

    /** The liked posts as XTA's preference keeps them: a JSON list of [BlueskySnapshot]s. */
    fun likedPostsJson(posts: List<BlueskyPost>): String = JsonArray(posts.map(BlueskySnapshot::json)).toString()

    /** The follows as the plugin's own file keeps them. */
    fun followsJson(follows: List<BlueskyFollow>): String = buildJsonObject {
        put("v", 1)
        put("follows", JsonArray(follows.map(::followJson)))
    }.toString()

    private fun followJson(follow: BlueskyFollow): JsonObject = buildJsonObject {
        put("did", follow.did)
        put("handle", follow.handle)
        put("name", follow.name)
        follow.avatarUrl?.let { put("avatar", it) }
        put("followedAt", follow.followedAt)
        if (!follow.inFeed) put("inFeed", false)
    }

    /** The follows of the plugin's own file; a row naming no one is left out, and each account comes once. */
    fun follows(text: String): List<BlueskyFollow> = J.parse(text)["follows"].list.mapNotNull { row ->
        val did = row["did"].string.orEmpty()
        val handle = row["handle"].string.orEmpty()
        if (did.isEmpty() && handle.isEmpty()) return@mapNotNull null
        BlueskyFollow(did, handle, row["name"].string ?: handle, row["avatar"].string, row["followedAt"].long ?: 0, row["inFeed"].boolean ?: true)
    }.distinctBy { it.actor }

    /** The likes as the plugin's own file keeps them. */
    fun likesJson(likes: List<BlueskyLike>): String = buildJsonObject {
        put("v", 1)
        put("likes", JsonArray(likes.map { like -> buildJsonObject { put("uri", like.uri); put("likedAt", like.likedAt) } }))
    }.toString()

    fun likes(text: String): List<BlueskyLike> = J.parse(text)["likes"].list.mapNotNull { row ->
        val uri = row["uri"].string?.takeIf { it.startsWith("at://") } ?: return@mapNotNull null
        BlueskyLike(uri, row["likedAt"].long ?: 0)
    }.distinctBy { it.uri }
}
