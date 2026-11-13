package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.Page
import java.math.BigInteger

/**
 * The lists of people Threads shows a signed-in reader: an account's
 * followers, whom it follows, the followers it and the reader both have,
 * who liked a post and who reposted it. Read only, through the session the reader gave:
 * nothing here follows or likes anyone on Threads.
 */
enum class ThreadsPeopleList {
    FOLLOWERS,
    FOLLOWING,

    /** The account's followers the signed-in reader follows too: Threads' "Followers you both have". */
    MUTUAL,

    /** Who liked a post. */
    LIKERS,

    /** Who reposted a post. */
    REPOSTERS,
    ;

    /** A post's list, asked for by its media id; the others are an account's. */
    val ofPost: Boolean get() = this == LIKERS || this == REPOSTERS

    /** Whether Meta searches this list itself for typed words; the others are filtered where they are shown. */
    val searchesOnServer: Boolean get() = this == FOLLOWERS || this == FOLLOWING
}

/**
 * How the people lists are asked for and read, as the Threads app asks the
 * Instagram API behind it (the same paths serve threads.com's cookie API):
 * `friendships/{user}/followers/`, `…/following/` and `…/mutual_followers/`,
 * paged with `max_id` = the `next_max_id` of the page before, and
 * `media/{media}/likers/`. Who reposted a post has no path seen working
 * yet: [paths] names those tried in turn, a 404 moving on to the next, and
 * CI's live probe (ThreadsPostListsLiveTest) logs how each answers. Every
 * field is read as if it might be gone.
 */
object ThreadsPeople {
    /** How many people one page asks for. */
    const val PAGE_SIZE = 50

    /** The API path of [list] for [target]: a user's numeric id, or for a post's list its media id. The first of [paths]. */
    fun path(list: ThreadsPeopleList, target: String): String = paths(list, target).first()

    /**
     * Every path [list] may be read at, in the order they are tried: one for
     * the lists the Threads app is known to read, the likely ones for who
     * reposted a post, whose path no probe has confirmed.
     */
    fun paths(list: ThreadsPeopleList, target: String): List<String> = when (list) {
        ThreadsPeopleList.FOLLOWERS -> listOf("/api/v1/friendships/$target/followers/")
        ThreadsPeopleList.FOLLOWING -> listOf("/api/v1/friendships/$target/following/")
        ThreadsPeopleList.MUTUAL -> listOf("/api/v1/friendships/$target/mutual_followers/")
        ThreadsPeopleList.LIKERS -> listOf("/api/v1/media/$target/likers/")
        ThreadsPeopleList.REPOSTERS -> listOf("/api/v1/text_feed/$target/reposters/", "/api/v1/media/$target/reposters/")
    }

    /** The query of one page: after [cursor] when it is not the first, and Meta's own search for [query] where the list has one. */
    fun params(list: ThreadsPeopleList, cursor: String?, query: String?): List<Pair<String, String>> {
        val after = cursor?.trim()?.ifEmpty { null }
        val words = query?.trim()?.removePrefix("@")?.trim()?.ifEmpty { null }?.takeIf { list.searchesOnServer }
        return listOfNotNull(
            ("count" to "$PAGE_SIZE").takeIf { !list.ofPost },
            ("search_surface" to "follow_list_page").takeIf { list.searchesOnServer },
            words?.let { "query" to it },
            after?.let { "max_id" to it },
        )
    }

    /**
     * One page of people: `users`, each once, and the cursor after them; null
     * when Meta named none or said there is no more. A user without a username
     * is skipped, a missing list is an empty page.
     */
    fun page(body: J): Page<ThreadsProfile> {
        val people = body["users"].list.ifEmpty { body["reposters"].list }.map { it["user"].takeIf { user -> user.exists } ?: it }.mapNotNull(::person).distinctBy { it.username.lowercase() }
        val next = (body["next_max_id"].string ?: body["next_max_id"].integer?.toString())?.trim()?.ifEmpty { null }
        val ended = body["has_more"].boolean == false || body["big_list"].boolean == false && next == null
        return Page(people, next.takeIf { !ended && people.isNotEmpty() })
    }

    /** A row of a list: a user as [ThreadsParser.profile] reads one, without the counts a row never carries. */
    private fun person(user: J): ThreadsProfile? =
        ThreadsParser.profile(user)?.copy(followerCount = null, followingCount = null, mediaCount = null)

    /**
     * [people] whose handle or name holds every word of [text], ignoring case
     * and a leading `@`; all of them for blank text. What a list shows while
     * typed words are searched, and all a list Meta does not search shows.
     */
    fun filter(people: List<ThreadsProfile>, text: String): List<ThreadsProfile> {
        val words = text.trim().removePrefix("@").lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return people
        return people.filter { person ->
            val said = (person.username + " " + person.fullName).lowercase()
            words.all { it in said }
        }
    }

    /**
     * The tabs an account's lists show: its followers and whom it follows,
     * and, for a signed-in reader looking at someone else, the followers they
     * both have; "both" needs a reader.
     */
    fun tabs(signedIn: Boolean, ownProfile: Boolean): List<ThreadsPeopleList> =
        listOfNotNull(ThreadsPeopleList.FOLLOWERS, ThreadsPeopleList.FOLLOWING, ThreadsPeopleList.MUTUAL.takeIf { signedIn && !ownProfile })

    /**
     * The media id Meta keeps [post] under, for its likers, reposters and quotes: decoded from the
     * short code in its link, which names the original even for a repost; else
     * its id when that is Meta's numeric one. Null when neither says it.
     */
    fun mediaId(post: ThreadsPost): String? =
        post.shortcode?.let(::idOfShortcode) ?: post.id.takeIf { post.repostedByHandle == null && it.isNotEmpty() && it.all(Char::isDigit) }

    /**
     * The numeric id a short code spells: Instagram's and Threads' codes are
     * the id in base 64 (`A–Z a–z 0–9 - _`). A longer private code carries the
     * id in its first eleven characters. Null for a code with another letter.
     */
    fun idOfShortcode(code: String): String? {
        val digits = code.trim().take(SHORTCODE_LENGTH)
        if (digits.isEmpty()) return null
        var id = BigInteger.ZERO
        digits.forEach { letter ->
            val value = ALPHABET.indexOf(letter).takeIf { it >= 0 } ?: return null
            id = id.shiftLeft(6).add(BigInteger.valueOf(value.toLong()))
        }
        return id.toString().takeIf { id.signum() > 0 }
    }

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private const val SHORTCODE_LENGTH = 11
}
