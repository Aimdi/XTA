package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.store.StoreFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A Bluesky account followed here, as it looked when it was followed. Nothing is told to Bluesky. */
internal data class BlueskyFollow(val did: String, val handle: String, val name: String, val avatarUrl: String?, val followedAt: Long) {
    /** What the AppView reads it by: the DID, which outlives a renamed handle. */
    val actor: String get() = did.ifEmpty { handle }

    fun isOf(actor: String): Boolean = actor.isNotEmpty() && (actor == did || actor.equals(handle, ignoreCase = true))
}

/** The accounts followed here, in the plugin's own folder: what the Following tab reads and the backup keeps. */
internal class BlueskyFollows(private val file: StoreFile) {
    private val mutable = MutableStateFlow<List<BlueskyFollow>?>(null)

    /** Null until the file has been read. */
    val state: StateFlow<List<BlueskyFollow>?> = mutable.asStateFlow()

    private val mutex = Mutex()

    suspend fun load(): List<BlueskyFollow> = mutex.withLock { loaded() }

    /** [profile] followed from [at] (epoch milliseconds); one already followed keeps its place, with its name and picture brought up to date. */
    suspend fun follow(profile: BlueskyProfile, at: Long) = change { follows ->
        val known = follows.firstOrNull { it.isOf(profile.did) || it.isOf(profile.handle) }
        val follow = BlueskyFollow(profile.did, profile.handle, profile.displayName, profile.avatarUrl, known?.followedAt ?: at)
        if (known == null) follows + follow else follows.map { if (it === known) follow else it }
    }

    suspend fun unfollow(actor: String) = change { follows -> follows.filterNot { it.isOf(actor) } }

    /** The follows as the backup keeps them. */
    suspend fun export(): String = mutex.withLock { encode(loaded()) }

    /** The follows a backup kept, in place of these. */
    suspend fun import(text: String) = mutex.withLock {
        val follows = decode(text)
        file.write(encode(follows))
        mutable.value = follows
    }

    private suspend fun change(edit: (List<BlueskyFollow>) -> List<BlueskyFollow>) = mutex.withLock {
        val next = edit(loaded())
        file.write(encode(next))
        mutable.value = next
    }

    private suspend fun loaded(): List<BlueskyFollow> = mutable.value ?: decode(file.read().orEmpty()).also { mutable.value = it }

    private companion object {
        fun encode(follows: List<BlueskyFollow>): String = buildJsonObject {
            put("v", 1)
            put(
                "follows",
                JsonArray(
                    follows.map { follow ->
                        buildJsonObject {
                            put("did", follow.did)
                            put("handle", follow.handle)
                            put("name", follow.name)
                            follow.avatarUrl?.let { put("avatar", it) }
                            put("followedAt", follow.followedAt)
                        }
                    },
                ),
            )
        }.toString()

        fun decode(text: String): List<BlueskyFollow> = J.parse(text)["follows"].list.mapNotNull { row ->
            val did = row["did"].string.orEmpty()
            val handle = row["handle"].string.orEmpty()
            if (did.isEmpty() && handle.isEmpty()) return@mapNotNull null
            BlueskyFollow(did, handle, row["name"].string ?: handle, row["avatar"].string, row["followedAt"].long ?: 0)
        }.distinctBy { it.actor }
    }
}

/** The plugin's settings: what the Following tab shows. */
internal data class BlueskySettings(val replies: Boolean = true, val reposts: Boolean = true)

internal class BlueskySettingsStore(private val file: StoreFile) {
    private val mutable = MutableStateFlow(BlueskySettings())
    val state: StateFlow<BlueskySettings> = mutable.asStateFlow()

    private val mutex = Mutex()
    private var loaded = false

    suspend fun load() = mutex.withLock { loadOnce() }

    suspend fun update(edit: (BlueskySettings) -> BlueskySettings) = mutex.withLock {
        loadOnce()
        val next = edit(mutable.value)
        mutable.value = next
        file.write(buildJsonObject { put("replies", next.replies); put("reposts", next.reposts) }.toString())
    }

    private suspend fun loadOnce() {
        if (loaded) return
        val root = J.parse(file.read().orEmpty())
        mutable.value = BlueskySettings(replies = root["replies"].boolean ?: true, reposts = root["reposts"].boolean ?: true)
        loaded = true
    }
}
