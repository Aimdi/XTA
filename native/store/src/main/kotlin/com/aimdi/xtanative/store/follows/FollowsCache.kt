package com.aimdi.xtanative.store.follows

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One account someone follows, as their Following list showed it. */
data class KnownFollow(val id: String, val handle: String, val name: String, val avatarUrl: String? = null, val bio: String? = null)

/** One account's Following list as last read: when (epoch milliseconds), and whom it follows, newest follow first. */
data class RememberedFollows(val at: Long, val follows: List<KnownFollow>) {
    val ids: List<String> get() = follows.map { it.id }
}

/** What one read of several accounts' lists gave: every list known for them, and a failure that cut the read short. */
data class FollowsRead(val follows: Map<String, List<KnownFollow>>, val error: Throwable? = null)

/**
 * Whom followed accounts follow, as their Following lists were last read
 * (XTA's DiscoveryFollowsCache): kept on the device across launches, one
 * file per account under `follows/`. A group's Discover reads a list again
 * once it is older than [ttlMillis]; "Followed by" on a profile trusts one
 * for [retentionMillis], since a following list barely changes in a month.
 * A file that cannot be read is a list never read.
 */
class FollowsCache(
    private val dir: StoreDir,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = TTL_MILLIS,
    private val retentionMillis: Long = RETENTION_MILLIS,
) {
    private val memory = ConcurrentHashMap<String, RememberedFollows>()
    private val restoring = Mutex()

    @Volatile
    private var restored = false

    /** Keeps [follows] as [memberId]'s latest list, read now. */
    suspend fun remember(memberId: String, follows: List<KnownFollow>) {
        restore()
        val entry = RememberedFollows(clock(), follows)
        memory[memberId] = entry
        runCatching { dir.file(fileName(memberId)).write(encode(memberId, entry).toString()) }
    }

    /** Every list read within [retentionMillis], by account, without asking anyone. */
    suspend fun remembered(): Map<String, RememberedFollows> {
        restore()
        val now = clock()
        return memory.filterValues { now - it.at <= retentionMillis }
    }

    /** [members] never read first, then the longest unread; ties keep their order (XTA's prioritizeByAttempt). */
    suspend fun prioritize(members: List<String>): List<String> {
        restore()
        return FollowsOrder.prioritized(members.distinct()) { memory[it]?.at }
    }

    /**
     * The lists of [members]: every one still remembered, after [fetch] has
     * been asked, two at a time, for up to [maxFetches] of those missing or
     * older than [ttlMillis], each within [timeoutMillis]. [onPartial] hears
     * the lists known so far each time one arrives. A member whose read fails
     * keeps its old list, and the failure comes back with the rest.
     */
    suspend fun read(
        members: List<String>,
        maxFetches: Int,
        timeoutMillis: Long,
        fetch: suspend (String) -> List<KnownFollow>,
        onPartial: (Map<String, List<KnownFollow>>) -> Unit = {},
    ): FollowsRead {
        val ordered = prioritize(members)
        val pending = ordered.filterNot(::fresh).take(maxFetches.coerceAtLeast(0))
        val error = FollowsOrder.eachWithin(pending, CONCURRENCY, timeoutMillis, fetch) { member, follows ->
            remember(member, follows)
            onPartial(known(ordered))
        }
        return FollowsRead(known(ordered), error)
    }

    private fun fresh(member: String): Boolean = memory[member]?.let { clock() - it.at <= ttlMillis } == true

    private fun known(members: List<String>): Map<String, List<KnownFollow>> =
        members.mapNotNull { member -> memory[member]?.let { member to it.follows } }.toMap()

    private suspend fun restore() {
        if (restored) return
        restoring.withLock {
            if (restored) return
            val files = withContext(Dispatchers.IO) { File(dir.root, FOLDER).listFiles { file -> file.name.endsWith(SUFFIX) }.orEmpty().toList() }
            val now = clock()
            files.forEach { file ->
                val text = dir.file(FOLDER + "/" + file.name).read() ?: return@forEach
                val (member, entry) = decode(J.parse(text)) ?: return@forEach
                if (now - entry.at <= retentionMillis) memory.putIfAbsent(member, entry)
            }
            restored = true
        }
    }

    companion object {
        const val TTL_MILLIS = 3L * 24 * 60 * 60 * 1000
        const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000

        /** Lists read at once: one Following page each, from a generous bucket. */
        const val CONCURRENCY = 2

        private const val FOLDER = "follows"
        private const val SUFFIX = ".json"
        private const val VERSION = 1

        /** An X id is digits; anything else is kept to letters and digits, so a name never leaves the folder. */
        internal fun fileName(member: String): String = FOLDER + "/" + member.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64) + SUFFIX

        private fun encode(member: String, entry: RememberedFollows): JsonElement = buildJsonObject {
            put("v", VERSION)
            put("member", member)
            put("at", entry.at)
            put("users", JsonArray(entry.follows.map(::encode)))
        }

        private fun encode(follow: KnownFollow): JsonElement = buildJsonObject {
            put("id", follow.id)
            put("handle", follow.handle)
            put("name", follow.name)
            follow.avatarUrl?.let { put("avatar", it) }
            follow.bio?.let { put("bio", it) }
        }

        private fun decode(root: J): Pair<String, RememberedFollows>? {
            if (root["v"].int != VERSION) return null
            val member = root["member"].string ?: return null
            val at = root["at"].long ?: return null
            val follows = root["users"].list.mapNotNull { user ->
                user["id"].string?.takeIf { it.isNotEmpty() }?.let { id ->
                    KnownFollow(id, user["handle"].string.orEmpty(), user["name"].string.orEmpty(), user["avatar"].string, user["bio"].string)
                }
            }
            return member to RememberedFollows(at, follows)
        }
    }
}

/** The order lists are read in, and how a few of them are read at once. */
object FollowsOrder {
    /** [keys] never attempted first, then the longest ago; ties keep their order. */
    fun prioritized(keys: List<String>, attemptedAt: (String) -> Long?): List<String> =
        keys.withIndex().sortedWith(compareBy({ attemptedAt(it.value) ?: Long.MIN_VALUE }, { it.index })).map { it.value }

    /**
     * [fetch] for each of [keys], [concurrency] at a time and each within
     * [timeoutMillis], every answer handed to [answered] as it comes; one that
     * fails or runs out of time leaves the others reading, and the first
     * failure is what comes back.
     */
    suspend fun <T> eachWithin(
        keys: List<String>,
        concurrency: Int,
        timeoutMillis: Long,
        fetch: suspend (String) -> T,
        answered: suspend (String, T) -> Unit,
    ): Throwable? {
        val error = AtomicReference<Throwable?>(null)
        val gate = Semaphore(concurrency.coerceAtLeast(1))
        coroutineScope {
            keys.forEach { key ->
                launch {
                    gate.withPermit {
                        try {
                            answered(key, withTimeout(timeoutMillis) { fetch(key) })
                        } catch (failure: Throwable) {
                            if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                            error.compareAndSet(null, failure)
                        }
                    }
                }
            }
        }
        return error.get()
    }
}
