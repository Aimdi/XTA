package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsLinks
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.backup.ProfileSubscriptionRow
import com.aimdi.xtanative.store.backup.XtaTime
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray

/** An account followed on this device: its handle without the `@`, its name and its picture. */
internal data class ThreadsAccount(val handle: String, val name: String, val avatarUrl: String? = null) {
    companion object {
        fun of(profile: ThreadsProfile) = ThreadsAccount(profile.username, profile.displayName, profile.profilePicUrl.ifEmpty { null })

        fun of(post: ThreadsPost) = ThreadsAccount(post.handle, post.authorName, post.avatarUrl)
    }
}

/**
 * The accounts followed on this device (XTA's `threads_subscription` rows,
 * its backup's `threadsSubscriptions`), in the plugin's folder: Meta is never
 * told. Listed by name whatever the case, as XTA's `ORDER BY name COLLATE
 * NOCASE` lists them; null until the file has been read.
 */
internal class ThreadsAccounts(private val file: StoreFile, private val now: () -> Instant = Instant::now) {
    private val mutable = MutableStateFlow<List<ProfileSubscriptionRow>?>(null)
    val state: StateFlow<List<ProfileSubscriptionRow>?> = mutable.asStateFlow()

    private val mutex = Mutex()

    suspend fun load(): List<ProfileSubscriptionRow> = mutex.withLock { loaded() }

    suspend fun add(account: ThreadsAccount) = addAll(listOf(account))

    /** [added] followed, in one write; one already followed takes the new name and picture. */
    suspend fun addAll(added: List<ThreadsAccount>) = mutex.withLock {
        val at = XtaTime.sqlite(now())
        val rows = added.mapNotNull { account ->
            val key = account.handle.trim().lowercase().ifEmpty { return@mapNotNull null }
            ProfileSubscriptionRow(key, account.name.ifBlank { key }, account.avatarUrl, inFeed = true, createdAt = at)
        }
        if (rows.isEmpty()) return@withLock
        val keys = rows.map { it.id }.toSet()
        save(loaded().filterNot { it.id in keys } + rows)
    }

    suspend fun remove(handle: String) = mutex.withLock { save(loaded().filterNot { it.id == handle.trim().lowercase() }) }

    /** Followed accounts as their newest posts show them: a new name or picture link (Meta's expire) is kept, as X subscriptions are. */
    suspend fun keepCurrent(posts: List<ThreadsPost>) = mutex.withLock {
        val rows = loaded()
        val current = current(rows, posts)
        if (current != rows) save(current)
    }

    /** The rows as XTA's backup writes `threadsSubscriptions`. */
    suspend fun export(): String = mutex.withLock { encode(loaded()) }

    suspend fun import(text: String) = mutex.withLock { save(decode(text)) }

    private suspend fun loaded(): List<ProfileSubscriptionRow> = mutable.value ?: ordered(decode(file.read().orEmpty())).also { mutable.value = it }

    private suspend fun save(rows: List<ProfileSubscriptionRow>) {
        val next = ordered(rows)
        file.write(encode(next))
        mutable.value = next
    }

    companion object {
        fun accounts(rows: List<ProfileSubscriptionRow>?): List<ThreadsAccount> = rows.orEmpty().map { ThreadsAccount(it.id, it.name, it.avatarUrl) }

        fun handles(rows: List<ProfileSubscriptionRow>?): List<String> = rows.orEmpty().map { it.id }

        /**
         * What a reader typed or pasted into Add account: handles, @handles or
         * profile links, several split by spaces, commas or lines; each once.
         * Null when any of it is not a handle, so nothing half-read is followed.
         */
        fun handlesIn(text: String): List<String>? {
            val parts = text.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
            if (parts.isEmpty()) return null
            val handles = parts.map { ThreadsLinks.normaliseHandle(it) ?: return null }
            return handles.distinct()
        }

        fun follows(rows: List<ProfileSubscriptionRow>?, handle: String): Boolean = rows.orEmpty().any { it.id == handle.trim().lowercase() }

        /** Each account once, by name whatever the case. */
        fun ordered(rows: List<ProfileSubscriptionRow>): List<ProfileSubscriptionRow> =
            rows.asReversed().distinctBy { it.id }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        /**
         * [rows] with the name and picture of each account's first post in
         * [posts] where they differ (threads_store.dart's
         * `threadsAccountsChangedBy`); a post without a name or picture keeps the one held.
         */
        fun current(rows: List<ProfileSubscriptionRow>, posts: List<ThreadsPost>): List<ProfileSubscriptionRow> {
            val shown = posts.groupBy { it.handle.lowercase() }.mapValues { it.value.first() }
            return rows.map { row ->
                val post = shown[row.id.lowercase()] ?: return@map row
                val name = post.authorName.takeIf { it.isNotBlank() } ?: row.name
                row.copy(name = name, avatarUrl = post.avatarUrl ?: row.avatarUrl)
            }
        }

        fun encode(rows: List<ProfileSubscriptionRow>): String = JsonArray(rows.map { it.toJson() }).toString()

        fun decode(text: String): List<ProfileSubscriptionRow> = J.parse(text).list.mapNotNull { ProfileSubscriptionRow.from(it) }
    }
}
