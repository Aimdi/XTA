package com.aimdi.xtanative.networks.instagram

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.Page
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Instagram's answers turned into accounts and posts, as XTA's
 * instagram_parse.dart reads them: `web_profile_info`, `feed/user`,
 * `topsearch` and the Explore grids. Nothing here throws: what is missing
 * is null or empty, and a post without an id, a code or an author is left out.
 */
object InstagramParser {
    private val handlePattern = Regex("^[A-Za-z0-9._]{1,30}$")

    /** The first path segments on instagram.com that are not an account. */
    private val notAccounts = setOf("p", "reel", "reels", "stories", "explore")

    /** How deep the Explore grids are searched for posts. */
    private const val EXPLORE_DEPTH = 14

    /**
     * The username [raw] names, lowercased: "@name", "name" or a profile
     * link. Null for a post, a reel, Explore, or text that cannot be one.
     */
    fun handle(raw: String?): String? {
        val value = raw?.trim() ?: return null
        if (value.startsWith("@")) return validHandle(value.drop(1).trim())
        val url = (if ("://" in value) value else "https://$value").toHttpUrlOrNull()
        return when {
            url != null && isInstagram(url.host) -> url.pathSegments.firstOrNull().orEmpty().trim()
                .takeIf { it.isNotEmpty() && it !in notAccounts }
                ?.let(::validHandle)
            '/' in value -> null
            else -> validHandle(value)
        }
    }

    /** The account a `web_profile_info` answer describes; null when it names none. */
    fun profile(json: J): InstagramProfile? {
        val user = userOf(json)
        val username = user["username"].string.orEmpty().trim()
        val id = idOf(user)
        if (username.isEmpty() || id.isEmpty()) return null
        return InstagramProfile(
            id = id,
            username = username,
            fullName = user["full_name"].string ?: username,
            biography = user["biography"].string,
            avatarUrl = firstUrl(user["profile_pic_url_hd"], user["profile_pic_url"], user["hd_profile_pic_url_info"]["url"]),
            isPrivate = user["is_private"].boolean ?: false,
            isVerified = user["is_verified"].boolean ?: false,
            followerCount = user["edge_followed_by"]["count"].long ?: user["follower_count"].long ?: 0,
            followingCount = user["edge_follow"]["count"].long ?: user["following_count"].long ?: 0,
            mediaCount = user["edge_owner_to_timeline_media"]["count"].long ?: user["media_count"].long ?: 0,
        )
    }

    /**
     * The newest posts `web_profile_info` carries beside the profile, each by
     * the profile's account, and the GraphQL cursor after them.
     */
    fun profileMedia(json: J): Page<InstagramPost> {
        val user = userOf(json)
        val author = authorOf(user)
        val timeline = user["edge_owner_to_timeline_media"]
        val posts = timeline["edges"].list.mapNotNull { mediaNode(it["node"], author) }
            .ifEmpty { user["media"]["nodes"].list.mapNotNull { mediaNode(it, author) } }
        val info = timeline["page_info"]
        return Page(posts, nextOf(info["end_cursor"].string, info["has_next_page"].boolean ?: false))
    }

    /** A page of `feed/user/{pk}`, and the `max_id` that asks for the one after. */
    fun userFeed(json: J): Page<InstagramPost> {
        val posts = json["items"].list.mapNotNull { mediaNode(it) }
        val cursor = json["next_max_id"].string ?: json["next_max_id"].long?.toString()
        return Page(posts, nextOf(cursor, json["more_available"].boolean ?: !cursor.isNullOrEmpty()))
    }

    /**
     * A post from either shape Instagram sends: a GraphQL node (`shortcode`,
     * `display_url`, `edge_…`) or an API item (`code`, `image_versions2`,
     * `caption.text`). [author] stands in for the post's own user, which a
     * profile's grid leaves out. Null without an id, a code or an author.
     */
    fun mediaNode(node: J, author: InstagramAuthor? = null): InstagramPost? {
        val id = idOf(node)
        val shortcode = (node["shortcode"].string ?: node["code"].string).orEmpty().trim()
        val by = author ?: authorOf(if (node["user"].exists) node["user"] else node["owner"])
        if (id.isEmpty() || shortcode.isEmpty() || by.username.isEmpty()) return null
        return InstagramPost(
            id = id,
            shortcode = shortcode,
            caption = captionOf(node),
            createdAt = epoch(node["taken_at_timestamp"].long ?: node["taken_at"].long),
            author = by,
            coverUrl = firstUrl(node["display_url"], node["image_versions2"]["candidates"][0]["url"], node["thumbnail_src"]),
            isVideo = node["is_video"].boolean ?: (node["media_type"].long == 2L || node["product_type"].string == "clips"),
            likeCount = node["edge_liked_by"]["count"].long ?: node["like_count"].long ?: 0,
            commentCount = node["edge_media_to_comment"]["count"].long ?: node["comment_count"].long ?: 0,
            carouselUrls = carouselUrls(node),
        )
    }

    /** The accounts a `topsearch` answer lists, each username once. */
    fun topSearch(json: J): List<InstagramSearchUser> =
        json["users"].list
            .map { if (it["user"].exists) it["user"] else it }
            .filter { usernameOf(it).isNotEmpty() }
            .distinctBy { usernameOf(it).lowercase() }
            .map(::searchUser)

    /**
     * The posts of an Explore answer (`explore_grid`, `topical_explore`),
     * wherever its sections hold them, each once, and the `max_id` after them.
     */
    fun explore(json: J): Page<InstagramPost> {
        val posts = mediaIn(json, depth = 0).distinctBy { it.id }
        val cursor = json["next_max_id"].string ?: json["max_id"].string ?: json["next_max_id"].long?.toString()
        return Page(posts, nextOf(cursor, json["more_available"].boolean ?: !cursor.isNullOrEmpty()))
    }

    /** Whether Instagram answered with `login_required` (or a failure about logging in) instead of what was asked. */
    fun loginRequired(json: J): Boolean {
        val message = json["message"].string.orEmpty().lowercase()
        return "login_required" in message || (json["status"].string == "fail" && "login" in message)
    }

    private fun mediaIn(node: J, depth: Int): List<InstagramPost> {
        if (depth > EXPLORE_DEPTH) return emptyList()
        return when (node.raw) {
            is JsonArray -> node.list.flatMap { mediaIn(it, depth + 1) }
            is JsonObject -> listOfNotNull(mediaHere(node)) + node.keys.flatMap { mediaIn(node[it], depth + 1) }
            else -> emptyList()
        }
    }

    private fun mediaHere(node: J): InstagramPost? {
        val coded = (node["shortcode"].string ?: node["code"].string).orEmpty().isNotEmpty()
        if (!coded || idOf(node).isEmpty()) return null
        return mediaNode(if (node["media"].exists) node["media"] else node)
    }

    private fun searchUser(user: J): InstagramSearchUser {
        val username = usernameOf(user)
        val id = (pkOf(user) ?: user["id"].string).orEmpty().trim()
        return InstagramSearchUser(
            id = id.ifEmpty { username },
            username = username,
            fullName = user["full_name"].string ?: username,
            avatarUrl = firstUrl(user["profile_pic_url"]),
            isPrivate = user["is_private"].boolean ?: false,
            isVerified = user["is_verified"].boolean ?: false,
        )
    }

    private fun authorOf(user: J): InstagramAuthor {
        val username = usernameOf(user)
        return InstagramAuthor(
            username = username,
            fullName = user["full_name"].string ?: username,
            avatarUrl = firstUrl(user["profile_pic_url_hd"], user["profile_pic_url"]),
            pk = (user["id"].string ?: pkOf(user)).orEmpty(),
            isVerified = user["is_verified"].boolean ?: false,
        )
    }

    private fun captionOf(node: J): String =
        node["edge_media_to_caption"]["edges"][0]["node"]["text"].string
            ?: node["caption"]["text"].string
            ?: node["caption"].string
            ?: ""

    private fun carouselUrls(node: J): List<String> {
        val api = node["carousel_media"].list.map { firstUrl(it["image_versions2"]["candidates"][0]["url"], it["display_url"]) }
        val graphql = node["edge_sidecar_to_children"]["edges"].list
            .map { firstUrl(it["node"]["display_url"], it["node"]["image_versions2"]["candidates"][0]["url"]) }
        return (api + graphql).filterNotNull().distinct()
    }

    private fun userOf(json: J): J = if (json["data"]["user"].exists) json["data"]["user"] else json["user"]

    private fun usernameOf(user: J): String = user["username"].string.orEmpty().trim()

    private fun pkOf(node: J): String? = node["pk"].string ?: node["pk"].long?.toString()

    private fun idOf(node: J): String = (node["id"].string ?: pkOf(node)).orEmpty().trim()

    private fun firstUrl(first: J, second: J = J(null), third: J = J(null)): String? =
        listOf(first, second, third).firstNotNullOfOrNull { node -> node.string?.takeIf { it.startsWith("http") } }

    private fun nextOf(cursor: String?, hasMore: Boolean): String? = cursor?.takeIf { hasMore && it.isNotEmpty() }

    private fun validHandle(handle: String): String? = handle.takeIf(handlePattern::matches)?.lowercase()

    private fun isInstagram(host: String): Boolean = host == "instagram.com" || host.endsWith(".instagram.com")

    /** Instagram's times are seconds, now and then milliseconds; nothing or zero is no time. */
    private fun epoch(value: Long?): Instant? = when {
        value == null || value <= 0 -> null
        value > 100_000_000_000 -> Instant.ofEpochMilli(value)
        else -> Instant.ofEpochSecond(value)
    }
}
