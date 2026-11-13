package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsBioLink
import com.aimdi.xtanative.networks.threads.ThreadsProfile
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
 * The profiles opened lately, kept in the plugin's own folder so that one
 * opened again shows at once what it showed last while Meta is asked again:
 * the [CAP] newest, each with its card and its first [POSTS] threads (never
 * its replies page, which is read when its tab opens).
 */
internal data class ThreadsProfilesKept(val profiles: List<ThreadsKeptProfile> = emptyList()) {
    /** What [handle] showed last; null when it was not kept. */
    fun of(handle: String): ThreadsKeptProfile? = profiles.firstOrNull { it.handle == key(handle) }

    /** These with [page], read for [handle] at [at], first; the oldest beyond [CAP] let go. A page with nothing in it changes nothing. */
    fun with(handle: String, page: ThreadsProfilePage, at: Instant): ThreadsProfilesKept {
        if (page.isEmpty) return this
        val kept = ThreadsKeptProfile(key(handle), page.copy(posts = page.posts.take(POSTS), replies = null), at)
        return ThreadsProfilesKept((listOf(kept) + profiles.filter { it.handle != kept.handle }).take(CAP))
    }

    fun encode(): String = JsonObject(mapOf("v" to JsonPrimitive(VERSION), "profiles" to JsonArray(profiles.map(::json)))).toString()

    companion object {
        const val CAP = 24
        const val POSTS = 20
        private const val VERSION = 1

        /** What [text] keeps; anything unreadable is nothing kept. */
        fun decode(text: String?): ThreadsProfilesKept {
            val root = J.parse(text.orEmpty())
            if (root["v"].long != VERSION.toLong()) return ThreadsProfilesKept()
            return ThreadsProfilesKept(root["profiles"].list.mapNotNull(::profileOf).take(CAP))
        }

        private fun key(handle: String) = handle.trim().lowercase()

        private fun json(kept: ThreadsKeptProfile): JsonObject = JsonObject(
            mapOf(
                "handle" to JsonPrimitive(kept.handle),
                "readAt" to JsonPrimitive(XtaTime.iso(kept.readAt)),
                "profile" to (kept.page.profile?.let(::card) ?: JsonNull),
                "posts" to JsonArray(kept.page.posts.map(ThreadsPostJson::json)),
            ),
        )

        private fun profileOf(row: J): ThreadsKeptProfile? {
            val handle = row["handle"].string?.let(::key)?.ifEmpty { null } ?: return null
            val readAt = XtaTime.parse(row["readAt"].string) ?: return null
            val page = ThreadsProfilePage(card(row["profile"]), row["posts"].list.mapNotNull(ThreadsPostJson::post))
            return ThreadsKeptProfile(handle, page, readAt).takeIf { !page.isEmpty }
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
                "biography" to JsonPrimitive(profile.biography),
                "followers" to number(profile.followerCount),
                "following" to number(profile.followingCount),
                "posts" to number(profile.mediaCount),
                "link" to text(profile.externalUrl),
                "bio" to JsonArray(profile.facts.bioFragments.map(ThreadsPostJson::fragment)),
                "links" to JsonArray(profile.facts.links.map(::link)),
                "roundedFollowers" to JsonPrimitive(profile.facts.roundedFollowers),
                "roundedPosts" to JsonPrimitive(profile.facts.roundedPosts),
            ),
        )

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
                facts = ThreadsProfileFacts(
                    bioFragments = row["bio"].list.mapNotNull(ThreadsPostJson::fragment),
                    links = row["links"].list.mapNotNull(::link),
                    roundedFollowers = row["roundedFollowers"].boolean == true,
                    roundedPosts = row["roundedPosts"].boolean == true,
                ),
            )
        }

        private fun text(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

        private fun number(value: Long?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
    }
}

/**
 * [ThreadsProfilesKept] in its file: read once, written after each profile
 * read; a file that cannot be read or written keeps nothing, never fails a
 * read.
 */
internal class ThreadsProfileShelf(private val file: StoreFile) {
    private val lock = Mutex()
    private var kept: ThreadsProfilesKept? = null

    suspend fun of(handle: String): ThreadsKeptProfile? = lock.withLock { loaded().of(handle) }

    suspend fun keep(handle: String, page: ThreadsProfilePage, at: Instant) = lock.withLock {
        val next = loaded().with(handle, page, at)
        kept = next
        file.write(next.encode())
    }

    private suspend fun loaded(): ThreadsProfilesKept = kept ?: ThreadsProfilesKept.decode(file.read()).also { kept = it }
}
