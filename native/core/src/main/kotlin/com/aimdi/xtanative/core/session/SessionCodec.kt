package com.aimdi.xtanative.core.session

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.feed.FeedSettings
import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.XAccount
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The session file. Still version 1: everything added since is a key of its
 * own that older files lack and read as its default, so every file written
 * before reads as it was.
 */
internal object SessionCodec {
    private const val VERSION = 1

    fun encode(session: Session): String = buildJsonObject {
        put("v", VERSION)
        put("includeReplies", session.includeReplies)
        put("includeReposts", session.includeReposts)
        put("autoplay", session.autoplay)
        put("accounts", JsonArray(session.accounts.map(::account)))
        put("groups", JsonArray(session.groups.map(::group)))
        put("following", JsonArray(session.following.map(::followed)))
        put("searches", JsonArray(session.searches.map { buildJsonObject { put("query", it.query); put("followedAt", it.followedAt) } }))
        put("feeds", JsonObject(session.feeds.mapValues { FeedSettings.json(it.value) }))
        put("nesting", JsonObject(session.nesting.mapValues { JsonPrimitive(it.value) }))
        put("postCaps", JsonObject(session.postCaps.mapValues { JsonPrimitive(it.value) }))
        put("offFollowing", strings(session.offFollowing))
        put("deck", strings(session.deck))
    }.toString()

    fun decode(text: String): Session? {
        val root = J.parse(text)
        if (root["v"].int != VERSION) return null
        return Session(
            accounts = root["accounts"].list.mapNotNull(::account),
            following = root["following"].list.mapNotNull(::followed),
            groups = root["groups"].list.mapNotNull(::group),
            includeReplies = root["includeReplies"].boolean ?: true,
            includeReposts = root["includeReposts"].boolean ?: true,
            autoplay = root["autoplay"].boolean ?: true,
            searches = root["searches"].list.mapNotNull { row ->
                row["query"].string?.takeIf { it.isNotBlank() }?.let { SavedSearch(it, row["followedAt"].long ?: 0) }
            },
            feeds = entries(root["feeds"]) { FeedSettings.of(it) },
            nesting = entries(root["nesting"]) { it.string },
            postCaps = entries(root["postCaps"]) { it.int?.takeIf { cap -> cap > 0 } },
            offFollowing = root["offFollowing"].list.mapNotNullTo(LinkedHashSet()) { it.string },
            deck = root["deck"].list.mapNotNull { it.string },
        )
    }

    private fun strings(values: Collection<String>) = JsonArray(values.map(::JsonPrimitive))

    private fun <V : Any> entries(node: J, value: (J) -> V?): Map<String, V> =
        node.keys.mapNotNull { key -> value(node[key])?.let { key to it } }.toMap()

    private fun account(account: XAccount) = buildJsonObject {
        put("id", account.id)
        account.screenName?.let { put("screenName", it) }
        put("headers", JsonObject(account.headers.mapValues { JsonPrimitive(it.value) }))
    }

    private fun account(row: J): XAccount? {
        val id = row["id"].string ?: return null
        val headers = row["headers"].let { h -> h.keys.mapNotNull { key -> h[key].string?.let { key to it } }.toMap() }
        return XAccount(id, row["screenName"].string, headers)
    }

    private fun group(group: FollowGroup) = buildJsonObject {
        put("id", group.id)
        put("name", group.name)
        put("members", strings(group.memberIds))
        group.emoji?.let { put("emoji", it) }
        put("pinned", group.pinned)
        put("nsfw", group.nsfw)
        group.color?.let { put("color", it) }
        group.icon?.let { put("icon", it) }
        if (group.markStyle != 0) put("markStyle", group.markStyle)
        if (group.createdAt != 0L) put("createdAt", group.createdAt)
        group.position?.let { put("position", it) }
    }

    private fun group(row: J): FollowGroup? {
        val id = row["id"].string ?: return null
        return FollowGroup(
            id = id,
            name = row["name"].string ?: id,
            memberIds = row["members"].list.mapNotNull { it.string },
            emoji = row["emoji"].string,
            pinned = row["pinned"].boolean ?: false,
            nsfw = row["nsfw"].boolean ?: false,
            color = row["color"].long,
            icon = row["icon"].string,
            markStyle = row["markStyle"].int ?: 0,
            createdAt = row["createdAt"].long ?: 0,
            position = row["position"].int,
        )
    }

    private fun followed(followed: FollowedAccount) = buildJsonObject {
        put("id", followed.id)
        put("screenName", followed.screenName)
        put("name", followed.name)
        followed.avatarUrl?.let { put("avatar", it) }
        put("verified", followed.verified)
        put("inFeed", followed.inFeed)
        put("followedAt", followed.followedAt)
    }

    private fun followed(row: J): FollowedAccount? {
        val id = row["id"].string ?: return null
        val screenName = row["screenName"].string ?: return null
        return FollowedAccount(
            id = id,
            screenName = screenName,
            name = row["name"].string ?: screenName,
            avatarUrl = row["avatar"].string,
            verified = row["verified"].boolean ?: false,
            inFeed = row["inFeed"].boolean ?: true,
            followedAt = row["followedAt"].long ?: 0,
        )
    }
}
