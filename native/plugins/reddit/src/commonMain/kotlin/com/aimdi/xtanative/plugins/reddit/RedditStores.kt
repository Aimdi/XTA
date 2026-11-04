package com.aimdi.xtanative.plugins.reddit

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.reddit.RedditComment
import com.aimdi.xtanative.networks.reddit.RedditPost
import com.aimdi.xtanative.networks.reddit.normaliseSubreddit
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.backup.LocalMarkRow
import com.aimdi.xtanative.store.backup.NamedSubscriptionRow
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.PrefValue
import com.aimdi.xtanative.store.settings.RedditPrefs
import com.aimdi.xtanative.store.settings.SecretPrefs
import com.aimdi.xtanative.store.settings.Settings
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The plugin's preferences, in its own folder under XTA's keys
 * ([RedditPrefs]): the sort, the route, the NSFW display, the saved posts,
 * the thread visits, the sign-in. Read once, each change written whole.
 */
internal class RedditPrefsStore(private val file: StoreFile) {
    private val mutable = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = mutable.asStateFlow()

    private val mutex = Mutex()
    private var loaded = false

    suspend fun load(): Settings = mutex.withLock { loaded() }

    suspend fun <T> set(pref: Pref<T>, value: T) {
        update { it.with(pref, value) }
    }

    suspend fun update(edit: (Settings) -> Settings): Settings = mutex.withLock {
        val before = loaded()
        edit(before).also { after ->
            if (after != before) {
                file.write(encode(after))
                mutable.value = after
            }
        }
    }

    /** What a backup keeps of them, as XTA's settings carry them: by key, the sign-in and the client id left out. */
    suspend fun export(): String = JsonObject(shared(load()).mapValues { it.value.toJson() }).toString()

    /** A backup's preferences in place of these; the sign-in and the client id stay this device's. */
    suspend fun import(text: String) {
        val restored = (J.parse(text).raw as? JsonObject).orEmpty()
            .filterKeys { it in known && !SecretPrefs.isSecret(it) }
            .mapNotNull { (key, value) -> PrefValue.fromJson(value)?.let { key to it } }
            .toMap()
        update { current -> Settings(current.values.filterKeys(SecretPrefs::isSecret) + restored) }
    }

    private suspend fun loaded(): Settings {
        if (!loaded) {
            mutable.value = decode(file.read().orEmpty())
            loaded = true
        }
        return mutable.value
    }

    companion object {
        private val known = RedditPrefs.prefs.mapTo(HashSet()) { it.key }

        fun shared(settings: Settings): Map<String, PrefValue> = settings.values.filterKeys { it in known && !SecretPrefs.isSecret(it) }

        fun encode(settings: Settings): String = buildJsonObject {
            put("v", 1)
            put("preferences", JsonObject(settings.values.filterKeys { it in known }.mapValues { it.value.toJson() }))
        }.toString()

        fun decode(text: String): Settings {
            val preferences = (J.parse(text)["preferences"].raw as? JsonObject).orEmpty()
            return Settings(preferences.filterKeys { it in known }.mapNotNull { (key, value) -> PrefValue.fromJson(value)?.let { key to it } }.toMap())
        }
    }
}

/**
 * The communities followed on this device (XTA's `reddit_subscription`
 * rows), listed by name as XTA lists them. Reddit is never told. The list
 * XTA kept in preferences before it had a table comes in on the first read
 * and leaves the preferences, as XTA's own first load does.
 */
internal class RedditSubscriptions(
    private val file: StoreFile,
    private val prefs: RedditPrefsStore,
    private val now: () -> Instant = Instant::now,
) {
    private val mutable = MutableStateFlow<List<NamedSubscriptionRow>?>(null)

    /** Null until the file has been read. */
    val state: StateFlow<List<NamedSubscriptionRow>?> = mutable.asStateFlow()

    private val mutex = Mutex()

    suspend fun load(): List<NamedSubscriptionRow> = mutex.withLock { loaded() }

    /** Follows [raw], a name or a link; false when it names no community. */
    suspend fun add(raw: String): Boolean = mutex.withLock {
        val row = rowFor(raw, now()) ?: return@withLock false
        save(loaded().filterNot { it.id == row.id } + row)
        true
    }

    suspend fun remove(name: String) = mutex.withLock { save(loaded().filterNot { it.id == name.lowercase() }) }

    /** The rows as XTA's backup writes `redditSubscriptions`. */
    suspend fun export(): String = mutex.withLock { encode(loaded()) }

    suspend fun import(text: String) = mutex.withLock { save(decode(text)) }

    private suspend fun loaded(): List<NamedSubscriptionRow> {
        mutable.value?.let { return it }
        val legacy = legacyNames(prefs.load())
        val stored = decode(file.read().orEmpty())
        if (legacy == null) return ordered(stored).also { mutable.value = it }
        val rows = ordered(stored + legacy.mapNotNull { rowFor(it, now()) })
        file.write(encode(rows))
        prefs.set(RedditPrefs.subreddits, "")
        mutable.value = rows
        return rows
    }

    private suspend fun save(rows: List<NamedSubscriptionRow>) {
        val next = ordered(rows)
        file.write(encode(next))
        mutable.value = next
    }

    companion object {
        fun names(rows: List<NamedSubscriptionRow>?): List<String> = rows.orEmpty().map { it.name }

        /** Compared as Reddit compares names: whatever the case. */
        fun isFollowed(rows: List<NamedSubscriptionRow>?, name: String): Boolean = rows.orEmpty().any { it.id == name.lowercase() }

        fun rowFor(raw: String, at: Instant): NamedSubscriptionRow? =
            normaliseSubreddit(raw)?.let { NamedSubscriptionRow(it.lowercase(), it, inFeed = true, createdAt = XtaTime.sqlite(at)) }

        /** Each community once, by name whatever the case, as XTA's `ORDER BY name COLLATE NOCASE`. */
        fun ordered(rows: List<NamedSubscriptionRow>): List<NamedSubscriptionRow> =
            rows.asReversed().distinctBy { it.id }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        fun encode(rows: List<NamedSubscriptionRow>): String = JsonArray(rows.map { it.toJson() }).toString()

        fun decode(text: String): List<NamedSubscriptionRow> = J.parse(text).list.mapNotNull { NamedSubscriptionRow.from(it) }

        /** The names XTA kept in `plugin.reddit.subreddits`, or null when there is nothing there to bring in. */
        private fun legacyNames(settings: Settings): List<String>? {
            if (!settings.isSet(RedditPrefs.subreddits)) return null
            val raw = settings[RedditPrefs.subreddits]
            return if (raw.isEmpty()) null else J.parse(raw).list.mapNotNull { it.string }
        }
    }
}

/**
 * Upvotes kept on this device, as XTA keeps its `reddit_local_vote` rows:
 * Reddit is never told, the arrow remembers. A post's is under its id, a
 * comment's under `t1_<id>`, since the two are numbered apart.
 */
internal class RedditVotes(private val file: StoreFile) {
    private val mutable = MutableStateFlow<Set<String>>(emptySet())
    val state: StateFlow<Set<String>> = mutable.asStateFlow()

    private val mutex = Mutex()
    private var loaded = false

    suspend fun load(): Set<String> = mutex.withLock { loaded() }

    suspend fun toggle(id: String) = mutex.withLock {
        val now = loaded()
        save(if (id in now) now - id else now + id)
    }

    /** The rows as XTA's backup writes `redditLocalVotes`. */
    suspend fun export(): String = mutex.withLock { encode(loaded()) }

    suspend fun import(text: String) = mutex.withLock { save(decode(text)) }

    private suspend fun loaded(): Set<String> {
        if (!loaded) {
            mutable.value = decode(file.read().orEmpty())
            loaded = true
        }
        return mutable.value
    }

    private suspend fun save(ids: Set<String>) {
        file.write(encode(ids))
        mutable.value = ids
    }

    companion object {
        fun commentId(id: String): String = "t1_$id"

        fun encode(ids: Set<String>): String = JsonArray(ids.map { LocalMarkRow(it).toJson() }).toString()

        fun decode(text: String): Set<String> = J.parse(text).list.map { LocalMarkRow.from(it) }.mapNotNullTo(LinkedHashSet()) { it.id.ifEmpty { null } }
    }
}

/** The posts saved on this device, newest first, in `plugin.reddit.saved_posts` as XTA keeps them. */
internal object RedditSaved {
    /** As many as XTA keeps. */
    const val CAP = 200

    fun posts(settings: Settings): List<RedditPost> = RedditPostJson.list(settings[RedditPrefs.savedPosts])

    fun isSaved(posts: List<RedditPost>, post: RedditPost): Boolean = posts.any { it.id == post.id }

    /** [posts] with [post] taken out when saved, else put first. */
    fun toggled(posts: List<RedditPost>, post: RedditPost): List<RedditPost> =
        if (isSaved(posts, post)) posts.filter { it.id != post.id } else (listOf(post) + posts.filter { it.id != post.id }).take(CAP)

    fun with(settings: Settings, posts: List<RedditPost>): Settings = settings.with(RedditPrefs.savedPosts, RedditPostJson.listText(posts))
}

/**
 * When the reader last opened each thread (XTA's `reddit_thread_visits.dart`),
 * so a comment written since says it is new: by permalink, in milliseconds,
 * for the most recent threads only. Kept on this device.
 */
internal object RedditVisits {
    const val KEPT = 200

    fun of(text: String): Map<String, Long> {
        val root = J.parse(text)
        return root.keys.mapNotNull { key -> (root[key].raw as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toLongOrNull()?.let { key to it } }.toMap()
    }

    fun last(text: String, permalink: String): Instant? = of(text)[threadKey(permalink)]?.let(Instant::ofEpochMilli)

    /** [text] noting that [permalink] was opened [at], the oldest visits beyond [KEPT] forgotten. */
    fun recorded(text: String, permalink: String, at: Instant): String =
        JsonObject(keepRecent(of(text) + (threadKey(permalink) to at.toEpochMilli())).mapValues { JsonPrimitive(it.value) }).toString()

    fun keepRecent(visits: Map<String, Long>, keep: Int = KEPT): Map<String, Long> =
        if (visits.size <= keep) visits else visits.entries.sortedByDescending { it.value }.take(keep).associate { it.key to it.value }

    /** One key per thread however its permalink was written. */
    fun threadKey(permalink: String): String = permalink.removeSuffix("/")

    /** Written after [lastVisit]: nothing is new on a first visit. */
    fun isNew(comment: RedditComment, lastVisit: Instant?): Boolean {
        val created = comment.createdAt
        return lastVisit != null && created != null && created.isAfter(lastVisit)
    }
}

/**
 * Communities' pictures, kept a week in the plugin's folder (XTA's
 * `reddit_icons` cache): finding one costs a page, and a logo changes as
 * often as a logo does. A miss is never kept: a refused lookup is not "this
 * community has no picture".
 */
internal class RedditIconCache(private val file: StoreFile, private val now: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    private var known: Map<String, Pair<String, Long>>? = null

    suspend fun get(name: String): String? = mutex.withLock {
        loaded()[name.lowercase()]?.takeIf { now() - it.second < WEEK }?.first
    }

    suspend fun put(name: String, url: String) = mutex.withLock {
        val next = loaded().filterValues { now() - it.second < WEEK } + (name.lowercase() to (url to now()))
        known = next
        file.write(JsonObject(next.mapValues { (_, value) -> buildJsonObject { put("url", value.first); put("at", value.second) } }).toString())
    }

    private suspend fun loaded(): Map<String, Pair<String, Long>> = known ?: J.parse(file.read().orEmpty()).let { root ->
        root.keys.mapNotNull { key ->
            val url = root[key]["url"].string ?: return@mapNotNull null
            key to (url to (root[key]["at"].long ?: 0L))
        }.toMap()
    }.also { known = it }

    private companion object {
        const val WEEK = 7 * 24 * 60 * 60 * 1000L
    }
}
