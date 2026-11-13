package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsBioLink
import com.aimdi.xtanative.networks.threads.ThreadsFediverse
import com.aimdi.xtanative.networks.threads.ThreadsImage
import com.aimdi.xtanative.networks.threads.ThreadsPodcast
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsProfileExtras
import com.aimdi.xtanative.networks.threads.ThreadsProfileFacts
import com.aimdi.xtanative.networks.threads.ThreadsProfilePage
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.backup.XtaTime
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A profile as it was last read: its card and its threads, and when. */
internal data class ThreadsKeptProfile(val handle: String, val page: ThreadsProfilePage, val readAt: Instant)

/**
 * How a profile is kept on the device ([ThreadsProfileShelf]): its card and
 * its first [POSTS] threads, never its replies page, which is read when its
 * tab opens. Also reads the one file earlier versions kept every profile in.
 */
internal object ThreadsProfilesKept {
    const val POSTS = 20
    private const val VERSION = 1

    /** What of [page] is kept: its card and first threads, no replies page and no cursor. */
    fun trimmed(page: ThreadsProfilePage): ThreadsProfilePage = page.copy(posts = page.posts.take(POSTS), replies = null, next = null)

    fun json(page: ThreadsProfilePage): JsonObject = JsonObject(
        mapOf(
            "profile" to (page.profile?.let(::card) ?: JsonNull),
            "posts" to JsonArray(page.posts.map(ThreadsPostJson::json)),
        ),
    )

    /** The page [row] keeps; null when it holds neither a card nor a thread. */
    fun pageOf(row: J): ThreadsProfilePage? =
        ThreadsProfilePage(card(row["profile"]), row["posts"].list.mapNotNull(ThreadsPostJson::post)).takeIf { !it.isEmpty }

    /** The profiles the one file of earlier versions ([text]) kept, newest first; anything unreadable is nothing kept. */
    fun legacy(text: String?): List<ThreadsKeptProfile> {
        val root = J.parse(text.orEmpty())
        if (root["v"].long != VERSION.toLong()) return emptyList()
        return root["profiles"].list.mapNotNull(::profileOf)
    }

    private fun key(handle: String) = handle.trim().lowercase()

    private fun profileOf(row: J): ThreadsKeptProfile? {
        val handle = row["handle"].string?.let(::key)?.ifEmpty { null } ?: return null
        val readAt = XtaTime.parse(row["readAt"].string) ?: return null
        return pageOf(row)?.let { ThreadsKeptProfile(handle, it, readAt) }
    }

    private fun card(profile: ThreadsProfile): JsonObject = JsonObject(
        mapOf(
            "pk" to JsonPrimitive(profile.pk),
            "id" to JsonPrimitive(profile.id),
            "username" to JsonPrimitive(profile.username),
            "fullName" to JsonPrimitive(profile.fullName),
            "verified" to JsonPrimitive(profile.isVerified),
            "private" to JsonPrimitive(profile.isPrivate),
            "picture" to JsonPrimitive(profile.profilePicUrl),
            "hdPicture" to text(profile.hdPictureUrl),
            "pictures" to JsonArray(profile.pictureSizes.map(::picture)),
            "biography" to JsonPrimitive(profile.biography),
            "followers" to number(profile.followerCount),
            "following" to number(profile.followingCount),
            "posts" to number(profile.mediaCount),
            "link" to text(profile.externalUrl),
            "bio" to JsonArray(profile.facts.bioFragments.map(ThreadsPostJson::fragment)),
            "links" to JsonArray(profile.facts.links.map(::link)),
            "roundedFollowers" to JsonPrimitive(profile.facts.roundedFollowers),
            "roundedPosts" to JsonPrimitive(profile.facts.roundedPosts),
            "tags" to (J.parse(ThreadsTopics.encode(profile.facts.tags)).raw ?: JsonArray(emptyList())),
            "podcast" to (profile.facts.podcast?.let(::podcast) ?: JsonNull),
            "fediverse" to text(profile.facts.fediverse?.address),
        ),
    )

    private fun podcast(podcast: ThreadsPodcast): JsonObject =
        JsonObject(mapOf("title" to text(podcast.title), "url" to text(podcast.url), "platform" to text(podcast.platform)))

    private fun podcast(row: J): ThreadsPodcast? {
        if (row.raw !is JsonObject) return null
        val podcast = ThreadsPodcast(row["title"].string, row["url"].string?.takeIf { it.startsWith("https://") }, row["platform"].string)
        return podcast.takeIf { it.title != null || it.url != null }
    }

    private fun link(link: ThreadsBioLink): JsonObject = JsonObject(mapOf("url" to JsonPrimitive(link.url), "title" to text(link.title)))

    private fun link(row: J): ThreadsBioLink? = row["url"].string?.ifEmpty { null }?.let { ThreadsBioLink(it, row["title"].string) }

    private fun card(row: J): ThreadsProfile? {
        val username = row["username"].string?.ifEmpty { null } ?: return null
        return ThreadsProfile(
            pk = row["pk"].string.orEmpty(),
            id = row["id"].string.orEmpty(),
            username = username,
            fullName = row["fullName"].string.orEmpty(),
            isVerified = row["verified"].boolean == true,
            isPrivate = row["private"].boolean == true,
            profilePicUrl = row["picture"].string.orEmpty(),
            biography = row["biography"].string.orEmpty(),
            followerCount = row["followers"].long,
            followingCount = row["following"].long,
            mediaCount = row["posts"].long,
            externalUrl = row["link"].string,
            hdPictureUrl = row["hdPicture"].string,
            pictureSizes = row["pictures"].list.mapNotNull(::picture),
            facts = ThreadsProfileFacts(
                bioFragments = row["bio"].list.mapNotNull(ThreadsPostJson::fragment),
                links = row["links"].list.mapNotNull(::link),
                roundedFollowers = row["roundedFollowers"].boolean == true,
                roundedPosts = row["roundedPosts"].boolean == true,
                tags = row["tags"].raw?.let { ThreadsTopics.decode(it.toString()) }.orEmpty().take(ThreadsProfileExtras.MAX_TAGS),
                podcast = podcast(row["podcast"]),
                fediverse = row["fediverse"].string?.ifEmpty { null }?.let(::ThreadsFediverse),
            ),
        )
    }

    private fun picture(image: ThreadsImage): JsonObject =
        JsonObject(mapOf("url" to JsonPrimitive(image.url), "width" to number(image.width?.toLong()), "height" to number(image.height?.toLong())))

    private fun picture(row: J): ThreadsImage? =
        row["url"].string?.ifEmpty { null }?.let { ThreadsImage(it, row["width"].long?.toInt(), row["height"].long?.toInt()) }

    private fun text(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

    private fun number(value: Long?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
}

/**
 * The profiles opened lately, kept on the device ([ThreadsKeptShelf]) so
 * that one opened again shows at once what it showed last while Meta is
 * asked again: the [CAP] newest of the last [MAX_AGE_DAYS] days, at most
 * [MAX_BYTES] in all. The one file earlier versions kept them in is taken
 * over once, then deleted.
 */
internal class ThreadsProfileShelf(file: (String) -> StoreFile, private val legacy: StoreFile? = null, clock: () -> Instant = Instant::now) {
    private val shelf = ThreadsKeptShelf(
        file, FOLDER, CAP, MAX_AGE_DAYS * 24 * 60 * 60 * 1000L, MAX_BYTES,
        encode = ThreadsProfilesKept::json, decode = ThreadsProfilesKept::pageOf, clock = clock,
    )
    private val adopting = Mutex()
    private var adopted = legacy == null

    suspend fun of(handle: String): ThreadsKeptProfile? {
        adoptLegacy()
        val key = key(handle)
        return shelf.of(key)?.let { ThreadsKeptProfile(key, it.value, it.readAt) }
    }

    /** [page] kept as [handle]'s, read at [at]; a page with nothing in it changes nothing. */
    suspend fun keep(handle: String, page: ThreadsProfilePage, at: Instant) {
        if (page.isEmpty) return
        adoptLegacy()
        shelf.keep(key(handle), ThreadsProfilesKept.trimmed(page), at)
    }

    private suspend fun adoptLegacy() = adopting.withLock {
        val old = legacy?.takeIf { !adopted } ?: return@withLock
        adopted = true
        shelf.adopt(ThreadsProfilesKept.legacy(old.read()).map { it.handle to ThreadsKeptEntry(it.page, it.readAt) })
        old.delete()
    }

    private fun key(handle: String) = handle.trim().removePrefix("@").lowercase()

    companion object {
        const val CAP = 50
        const val MAX_AGE_DAYS = 7L
        const val MAX_BYTES = 4L * 1024 * 1024
        private const val FOLDER = "kept_profiles"
    }
}
