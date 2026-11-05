package com.aimdi.xtanative.store.offline

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XAffiliation
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XVerifiedType
import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.saved.sha256
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A profile as X last gave it: the account, its pinned posts' ids, and when it was read (epoch milliseconds). */
data class KeptProfile(val user: XUser, val pinned: List<String>, val at: Long)

/**
 * Profiles as last read from X (XTA's ProfileModel `_read` and `_remember`),
 * kept a week under the account's id and its handle, so a profile opens at
 * once, offline or while every account is rate limited, while X is asked
 * again. One file per key under `profiles/`, the newest [MAX_ENTRIES] kept;
 * a file that cannot be read or written is a miss, never a failure.
 */
class KeptProfiles(private val dir: StoreDir, private val clock: () -> Long = System::currentTimeMillis) {
    private val folder = File(dir.root, FOLDER)

    /** The profile of account [userId], when one younger than [MAX_AGE_MS] is kept. */
    suspend fun byId(userId: String): KeptProfile? = read(idKey(userId))

    /** The profile of [screenName] (with or without its @), when one younger than [MAX_AGE_MS] is kept. */
    suspend fun byName(screenName: String): KeptProfile? = read(nameKey(screenName))

    /** Keeps [user] and its [pinned] ids, read now, under its id and its handle; then trims the folder. */
    suspend fun remember(user: XUser, pinned: List<String>) {
        val at = clock()
        val keys = listOf(idKey(user.id), nameKey(user.screenName))
        keys.forEach { key ->
            val text = buildJsonObject {
                put("v", VERSION)
                put("key", key)
                put("at", at)
                put("pinned", JsonArray(pinned.map(::JsonPrimitive)))
                put("user", encode(user))
            }.toString()
            runCatching { dir.file(file(key)).write(text) }
        }
        runCatching { trim(keys.mapTo(HashSet()) { File(file(it)).name }) }
    }

    private suspend fun read(key: String): KeptProfile? = runCatching {
        val root = J.parse(dir.file(file(key)).read() ?: return null)
        if (root["v"].int != VERSION || root["key"].string != key) return null
        val at = root["at"].long ?: return null
        if (clock() - at > MAX_AGE_MS) return null
        val user = decode(root["user"]) ?: return null
        KeptProfile(user, root["pinned"].list.mapNotNull { it.string }, at)
    }.getOrNull()

    /** Drops what is older than a week, then all but the newest [MAX_ENTRIES], [written] always among them. */
    private suspend fun trim(written: Set<String>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val files = folder.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }.orEmpty()
        val (fresh, old) = files.partition { it.name in written || now - it.lastModified() <= MAX_AGE_MS }
        val newestFirst = fresh.sortedWith(compareByDescending<File> { it.name in written }.thenByDescending { it.lastModified() })
        (old + newestFirst.drop(MAX_ENTRIES)).forEach { it.delete() }
    }

    private fun file(key: String) = "$FOLDER/${sha256(key)}.json"

    companion object {
        private const val VERSION = 1
        private const val FOLDER = "profiles"

        /** How long a kept profile is shown: a week, as XTA. */
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        /** How many keys survive, newest first: XTA prunes its profiles to a hundred. */
        const val MAX_ENTRIES = 100

        internal fun idKey(userId: String) = "id:$userId"

        internal fun nameKey(screenName: String) = "name:" + screenName.removePrefix("@").lowercase()

        private fun encode(user: XUser): JsonElement = buildJsonObject {
            put("id", user.id)
            put("sn", user.screenName)
            put("name", user.name)
            user.avatarUrl?.let { put("avatar", it) }
            put("verified", user.verified)
            put("protected", user.protected)
            user.description?.let { put("bio", it) }
            user.bannerUrl?.let { put("banner", it) }
            user.location?.let { put("location", it) }
            user.website?.let { put("site", it) }
            user.followers?.let { put("followers", it) }
            user.following?.let { put("following", it) }
            user.posts?.let { put("posts", it) }
            user.likes?.let { put("likes", it) }
            user.listed?.let { put("listed", it) }
            user.createdAt?.let { put("created", it.toEpochMilli()) }
            put("check", user.verifiedType.name)
            user.followedByReader?.let { put("followedBy", it) }
            put("sensitive", user.sensitive)
            user.affiliation?.let { badge ->
                put(
                    "badge",
                    buildJsonObject {
                        put("url", badge.badgeUrl)
                        badge.name?.let { put("name", it) }
                        badge.profileUrl?.let { put("profile", it) }
                        badge.labelType?.let { put("type", it) }
                    },
                )
            }
            if (user.descriptionLinks.isNotEmpty()) {
                put(
                    "links",
                    JsonArray(
                        user.descriptionLinks.map { link ->
                            buildJsonObject {
                                put("s", link.start)
                                put("e", link.end)
                                put("url", link.url)
                                put("expanded", link.expanded)
                                put("display", link.display)
                            }
                        },
                    ),
                )
            }
        }

        private fun decode(json: J): XUser? {
            val id = json["id"].string ?: return null
            val screenName = json["sn"].string ?: return null
            val verified = json["verified"].boolean ?: false
            val badge = json["badge"]
            return XUser(
                id = id,
                screenName = screenName,
                name = json["name"].string ?: screenName,
                avatarUrl = json["avatar"].string,
                verified = verified,
                protected = json["protected"].boolean ?: false,
                description = json["bio"].string,
                bannerUrl = json["banner"].string,
                location = json["location"].string,
                website = json["site"].string,
                followers = json["followers"].long,
                following = json["following"].long,
                posts = json["posts"].long,
                createdAt = json["created"].long?.let(Instant::ofEpochMilli),
                descriptionLinks = json["links"].list.mapNotNull { link ->
                    val start = link["s"].int ?: return@mapNotNull null
                    val end = link["e"].int ?: return@mapNotNull null
                    XEntity.Link(start, end, link["url"].string.orEmpty(), link["expanded"].string.orEmpty(), link["display"].string.orEmpty())
                },
                verifiedType = json["check"].string?.let { runCatching { XVerifiedType.valueOf(it) }.getOrNull() }
                    ?: if (verified) XVerifiedType.BLUE else XVerifiedType.NONE,
                affiliation = badge["url"].string?.let { XAffiliation(it, badge["name"].string, badge["profile"].string, badge["type"].string) },
                followedByReader = json["followedBy"].boolean,
                likes = json["likes"].long,
                listed = json["listed"].long,
                sensitive = json["sensitive"].boolean ?: false,
            )
        }
    }
}
