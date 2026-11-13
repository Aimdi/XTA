package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

/** One of the links a profile lists under its bio (`bio_links`): where it goes, and the title its owner gave it. */
data class ThreadsBioLink(val url: String, val title: String? = null)

/**
 * What a profile's own page says about it beyond its meta tags: its bio as
 * Meta split it, and its links; and which counts came only from the meta
 * tags' rounded "5.7M Followers", so no exact figure can be shown for them.
 */
data class ThreadsProfileFacts(
    val bioFragments: List<ThreadsTextFragment> = emptyList(),
    val links: List<ThreadsBioLink> = emptyList(),
    val roundedFollowers: Boolean = false,
    val roundedPosts: Boolean = false,
)

/**
 * The profile a guest is shown on `threads.com/@handle`, read from the user
 * object the page's own query embeds (`BarcelonaProfilePageDirectQuery`):
 * the exact follower count, the verified badge, the largest picture, the bio
 * with its mentions and links as Meta split them, and the links listed under
 * it. Every field is optional: what the object lacks, the meta tags' card keeps.
 */
object ThreadsProfileOwner {
    private val outboundHosts = setOf("l.threads.com", "l.threads.net", "l.instagram.com")

    /** The meta tags' [card] with what the page's own user object adds; [card] alone when the page embeds none. */
    fun enrich(card: ThreadsProfile?, html: String, handle: String): ThreadsProfile? {
        val owner = owner(html, handle)
        return when {
            card == null -> owner
            owner == null -> card.copy(facts = card.facts.copy(roundedFollowers = card.followerCount != null, roundedPosts = card.mediaCount != null))
            else -> merge(card, owner)
        }
    }

    /** [handle]'s user object on [html], the fullest one when the page names it more than once; null when it names none. */
    fun owner(html: String, handle: String): ThreadsProfile? {
        val key = (ThreadsLinks.normaliseHandle(handle) ?: handle).trim().lowercase()
        if (key.isEmpty()) return null
        val named = Regex(""""username"\s*:\s*"${Regex.escape(key)}"""", RegexOption.IGNORE_CASE)
        val users = Jsoup.parse(html).select("script[data-sjs]")
            .map { it.data() }
            .filter { named.containsMatchIn(it) }
            .mapNotNull(ThreadsJson::decode)
            .flatMap(::objects)
            .map(::J)
            .filter { it["username"].string?.trim()?.lowercase() == key }
            .sortedByDescending(::fullness)
        return users.firstOrNull()?.let { profile(it, users) }
    }

    /** [card] with [owner]'s exact and fuller facts in place of what the meta tags guessed. */
    fun merge(card: ThreadsProfile, owner: ThreadsProfile): ThreadsProfile = card.copy(
        pk = card.pk.ifEmpty { owner.pk },
        id = card.id.ifEmpty { owner.id },
        fullName = owner.fullName.trim().ifEmpty { card.fullName },
        isVerified = card.isVerified || owner.isVerified,
        isPrivate = card.isPrivate || owner.isPrivate,
        profilePicUrl = owner.profilePicUrl.ifEmpty { card.profilePicUrl },
        hdPictureUrl = owner.hdPictureUrl ?: card.hdPictureUrl,
        pictureSizes = owner.pictureSizes.ifEmpty { card.pictureSizes },
        biography = owner.biography.trim().ifEmpty { card.biography },
        followerCount = owner.followerCount ?: card.followerCount,
        followingCount = owner.followingCount ?: card.followingCount,
        mediaCount = owner.mediaCount ?: card.mediaCount,
        externalUrl = owner.externalUrl ?: card.externalUrl,
        facts = owner.facts.copy(
            roundedFollowers = owner.followerCount == null && card.followerCount != null,
            roundedPosts = owner.mediaCount == null && card.mediaCount != null,
        ),
    )

    /** The links under a bio, each once, unwrapped from Meta's redirect; an entry without an address is skipped. */
    fun links(user: J): List<ThreadsBioLink> = user["bio_links"].list.mapNotNull { link ->
        val url = link["url"].string?.trim()?.ifEmpty { null } ?: return@mapNotNull null
        ThreadsBioLink(unwrap(url), link["title"].string?.trim()?.ifEmpty { null })
    }.distinctBy { it.url }

    /** The bio as Meta split it (`text_app_biography.text_fragments`); empty unless its pieces spell [bio]. */
    fun bioFragments(user: J, bio: String): List<ThreadsTextFragment> {
        val split = user["text_app_biography"]["text_fragments"].raw ?: return emptyList()
        val shaped = J(JsonObject(mapOf("text_post_app_info" to JsonObject(mapOf("text_fragments" to split)))))
        return ThreadsParser.fragments(shaped, bio.trim())
    }

    private fun profile(best: J, all: List<J>): ThreadsProfile? {
        val base = ThreadsParser.profile(best) ?: return null
        val bio = base.biography.trim().ifEmpty { all.firstOf { it["biography"].string?.trim()?.ifEmpty { null } }.orEmpty() }
        return base.copy(
            isVerified = all.any { it["is_verified"].boolean == true },
            biography = bio,
            hdPictureUrl = all.firstOf(ThreadsParser::hdPicture),
            pictureSizes = ThreadsImages.ordered(all.flatMap(ThreadsParser::pictureSizes)),
            // ThreadsParser reads a missing count as 0; a guest page tells only the counts it holds.
            followerCount = all.firstOf { it["follower_count"].integer },
            followingCount = all.firstOf { it["following_count"].integer },
            mediaCount = all.firstOf { it["media_count"].integer },
            facts = ThreadsProfileFacts(
                bioFragments = all.firstOf { bioFragments(it, bio).ifEmpty { null } }.orEmpty(),
                links = all.firstOf { links(it).ifEmpty { null } }.orEmpty(),
            ),
        )
    }

    private fun <T : Any> List<J>.firstOf(read: (J) -> T?): T? = firstNotNullOfOrNull(read)

    private fun fullness(user: J): Int = user.keys.size + if (user["follower_count"].exists) 100 else 0

    private fun unwrap(url: String): String {
        val link = url.toHttpUrlOrNull() ?: return url
        if (link.host !in outboundHosts) return url
        return link.queryParameter("u")?.trim()?.ifEmpty { null } ?: url
    }

    private fun objects(node: JsonElement): List<JsonObject> = when (node) {
        is JsonObject -> listOf(node) + node.values.flatMap(::objects)
        is JsonArray -> node.flatMap(::objects)
        else -> emptyList()
    }
}
