package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.bluesky.BlueskyFeedGenerator
import com.aimdi.xtanative.networks.bluesky.BlueskyFollow
import com.aimdi.xtanative.networks.bluesky.BlueskyLike
import com.aimdi.xtanative.networks.bluesky.BlueskyListInfo
import com.aimdi.xtanative.networks.bluesky.BlueskyLocal
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.networks.bluesky.BlueskyProfile
import com.aimdi.xtanative.networks.bluesky.BlueskyReadPoint
import com.aimdi.xtanative.networks.bluesky.BlueskyReading
import com.aimdi.xtanative.networks.bluesky.BlueskyReadingPrefs
import com.aimdi.xtanative.networks.bluesky.BlueskySearchRules
import com.aimdi.xtanative.networks.bluesky.BlueskySelection
import com.aimdi.xtanative.networks.bluesky.BlueskySnapshot
import com.aimdi.xtanative.networks.bluesky.BlueskyUrls
import com.aimdi.xtanative.networks.bluesky.BlueskyXtaRows
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.settings.BlueskyPrefs
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.ui.plugins.PluginSettings
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The accounts followed here, in the plugin's own folder: what Following reads and the backup keeps, as XTA's `bluesky_subscription`. */
internal class BlueskyFollows(private val file: StoreFile) {
    private val mutable = MutableStateFlow<List<BlueskyFollow>?>(null)

    /** Null until the file has been read. */
    val state: StateFlow<List<BlueskyFollow>?> = mutable.asStateFlow()

    private val mutex = Mutex()

    suspend fun load(): List<BlueskyFollow> = mutex.withLock { loaded() }

    /** Whether [actor] (a handle or a DID) is followed here, as far as what was read says. */
    fun follows(actor: String): Boolean = mutable.value.orEmpty().any { it.isOf(actor) }

    /** [profile] followed from [at] (epoch milliseconds); one already followed keeps its place and date, with its name and picture brought up to date. */
    suspend fun follow(profile: BlueskyProfile, at: Long) = change { BlueskyLocal.followed(it, BlueskyFollow.of(profile, at)) }

    suspend fun unfollow(actor: String) = change { BlueskyLocal.unfollowed(it, actor) }

    /** [people] followed here, those already followed skipped; how many were new. */
    suspend fun followAll(people: List<BlueskyProfile>, at: Long): Int {
        var added = 0
        change { before -> BlueskyLocal.withNew(before, people, at).also { added = it.size - before.size } }
        return added
    }

    /** The follows as XTA's backup rows. */
    suspend fun exportRows(): String = mutex.withLock { BlueskyXtaRows.followRows(loaded()) }

    /** XTA's backup rows merged in, as XTA's import writes them. */
    suspend fun importRows(text: String, now: Instant) = change { BlueskyLocal.merged(it, BlueskyXtaRows.follows(text, now)) }

    /** What an earlier build of this plugin put in the backup ("bluesky.follows"), merged in as XTA's rows are. */
    suspend fun importOwn(text: String) = change { BlueskyLocal.merged(it, BlueskyLocal.follows(text)) }

    private suspend fun change(edit: (List<BlueskyFollow>) -> List<BlueskyFollow>) = mutex.withLock {
        val next = edit(loaded())
        file.write(BlueskyLocal.followsJson(next))
        mutable.value = next
    }

    private suspend fun loaded(): List<BlueskyFollow> = mutable.value ?: BlueskyLocal.follows(file.read().orEmpty()).also { mutable.value = it }
}

/**
 * The posts liked here, as XTA keeps them: which posts (by URI, in the
 * plugin's folder, XTA's `bluesky_local_like`) and the posts themselves to
 * show, the newest few hundred, in Bluesky's liked posts' preference. Nothing
 * is told to Bluesky.
 */
internal class BlueskyLikes(private val file: StoreFile, private val prefs: BlueskyPreferences) {
    private val mutable = MutableStateFlow<List<BlueskyLike>?>(null)

    /** Null until the file has been read. */
    val state: StateFlow<List<BlueskyLike>?> = mutable.asStateFlow()

    private val mutex = Mutex()

    suspend fun load(): List<BlueskyLike> = mutex.withLock { loaded() }

    fun isLiked(uri: String): Boolean = mutable.value.orEmpty().any { it.uri == uri }

    /** Likes [post] here, or takes the like back; whether it is liked now. */
    suspend fun toggle(post: BlueskyPost, at: Long): Boolean {
        val liked = mutex.withLock {
            val before = loaded()
            val now = before.none { it.uri == post.uri }
            write(if (now) BlueskyLocal.liked(before, post.uri, at) else BlueskyLocal.unliked(before, post.uri))
            now
        }
        prefs.likedPosts { posts -> if (liked) BlueskyLocal.withLikedPost(posts, post) else posts.filterNot { it.uri == post.uri } }
        return liked
    }

    /** The likes as XTA's backup rows. */
    suspend fun exportRows(): String = mutex.withLock { BlueskyXtaRows.likeRows(loaded()) }

    /** XTA's backup rows merged in: likes of posts XTA showed, whose posts its settings brought. */
    suspend fun importRows(text: String, now: Instant) = mutex.withLock {
        write(BlueskyLocal.mergedLikes(loaded(), BlueskyXtaRows.likes(text), now.toEpochMilli()))
    }

    private suspend fun write(likes: List<BlueskyLike>) {
        file.write(BlueskyLocal.likesJson(likes))
        mutable.value = likes
    }

    private suspend fun loaded(): List<BlueskyLike> = mutable.value ?: BlueskyLocal.likes(file.read().orEmpty()).also { mutable.value = it }
}

/**
 * Bluesky's preferences, where XTA kept them: in the app's settings, under
 * `:store`'s [BlueskyPrefs] keys, so XTA's backup and the app's bring them
 * back. Each is read as XTA wrote it.
 */
internal class BlueskyPreferences(private val settings: PluginSettings, private val scope: CoroutineScope) {
    private val started = SharingStarted.Eagerly

    private fun <T> value(pref: Pref<T>): T = settings.state.value?.get(pref) ?: pref.default

    private fun <T, R> read(pref: Pref<T>, parse: (T) -> R): StateFlow<R> =
        settings.state.map { it?.get(pref) ?: pref.default }.distinctUntilChanged().map(parse).stateIn(scope, started, parse(value(pref)))

    /** The AppView as typed, empty for Bluesky's public one. */
    val instance: StateFlow<String> = read(BlueskyPrefs.instance) { it }

    /** The AppView every read goes to. */
    val appView: StateFlow<String> = read(BlueskyPrefs.instance, BlueskyUrls::appViewOrDefault)

    /** The reader's own public handle, whose lists and custom feeds are offered. */
    val handle: StateFlow<String> = read(BlueskyPrefs.handle) { it.trim() }

    val pinnedFeeds: StateFlow<List<BlueskyFeedGenerator>> = read(BlueskyPrefs.pinnedFeeds, BlueskySnapshot::feeds)

    val pinnedLists: StateFlow<List<BlueskyListInfo>> = read(BlueskyPrefs.pinnedLists, BlueskySnapshot::lists)

    /** The liked posts to show, newest first. */
    val likedPosts: StateFlow<List<BlueskyPost>> = read(BlueskyPrefs.likedPosts, BlueskySnapshot::posts)

    val reading: StateFlow<BlueskyReading> = read(BlueskyPrefs.readerPlaces, BlueskyReadingPrefs::parse)

    val searches: StateFlow<List<String>> = read(BlueskyPrefs.searchHistory) { it }

    /** Settings › Posts' Remember reading position, which keeps where Following was left. */
    val keepPlace: StateFlow<Boolean> = settings.state.map { it?.get(PostsPrefs.rememberReadingPosition) ?: PostsPrefs.rememberReadingPosition.default }
        .distinctUntilChanged().stateIn(scope, started, value(PostsPrefs.rememberReadingPosition))

    /** Whether the settings have been read yet. */
    val loaded: Boolean get() = settings.state.value != null

    /** Where Following was left on [source], when it was kept. */
    fun place(source: String): BlueskyReadPoint? = BlueskyReadingPrefs.point(value(BlueskyPrefs.readerPlaces), source)

    suspend fun place(source: String, point: BlueskyReadPoint?) =
        settings.update { it.with(BlueskyPrefs.readerPlaces, BlueskyReadingPrefs.write(it[BlueskyPrefs.readerPlaces], source, point)) }

    /** Zen or calm mode is on: posts show their glyphs without counts, as XTA's Bluesky card does then. */
    val hideCounts: StateFlow<Boolean> = settings.state.map { it != null && (it[PostsPrefs.zenMode] || it[PostsPrefs.calmMode]) }
        .distinctUntilChanged().stateIn(scope, started, false)

    suspend fun <T> set(pref: Pref<T>, value: T) = settings.update { it.with(pref, value) }

    suspend fun likedPosts(change: (List<BlueskyPost>) -> List<BlueskyPost>) =
        settings.update { it.with(BlueskyPrefs.likedPosts, BlueskyLocal.likedPostsJson(change(BlueskySnapshot.posts(it[BlueskyPrefs.likedPosts])))) }

    suspend fun pinnedFeeds(change: (List<BlueskyFeedGenerator>) -> List<BlueskyFeedGenerator>) =
        settings.update { it.with(BlueskyPrefs.pinnedFeeds, BlueskySnapshot.feedsJson(change(BlueskySnapshot.feeds(it[BlueskyPrefs.pinnedFeeds])))) }

    suspend fun pinnedLists(change: (List<BlueskyListInfo>) -> List<BlueskyListInfo>) =
        settings.update { it.with(BlueskyPrefs.pinnedLists, BlueskySnapshot.listsJson(change(BlueskySnapshot.lists(it[BlueskyPrefs.pinnedLists])))) }

    suspend fun reading(change: (BlueskyReading) -> BlueskyReading) = settings.update {
        val raw = it[BlueskyPrefs.readerPlaces]
        it.with(BlueskyPrefs.readerPlaces, BlueskyReadingPrefs.write(raw, change(BlueskyReadingPrefs.parse(raw))))
    }

    /** [query] first among the recent searches. */
    suspend fun remember(query: String) = settings.update { it.with(BlueskyPrefs.searchHistory, BlueskySearchRules.remembered(it[BlueskyPrefs.searchHistory], query)) }

    suspend fun clearSearches() = set(BlueskyPrefs.searchHistory, emptyList())

    /** The feed (or, with [lists], the list) last opened on [server]. */
    fun selection(server: String, lists: Boolean): BlueskySelection? = BlueskySnapshot.selection(value(selections(lists)), server)

    suspend fun select(server: String, lists: Boolean, selection: BlueskySelection) =
        settings.update { it.with(selections(lists), BlueskySnapshot.withSelection(it[selections(lists)], server, selection)) }

    private fun selections(lists: Boolean): Pref<String> = if (lists) BlueskyPrefs.selectedLists else BlueskyPrefs.selectedFeeds

    /**
     * What an earlier build of this plugin kept in its own folder (whether
     * Following showed replies and reposts), moved into these preferences
     * once, as the Following list's filters.
     */
    suspend fun adopt(file: StoreFile) {
        val text = file.read() ?: return
        val old = J.parse(text)
        reading { kept ->
            val following = kept.following
            kept.copy(following = following.copy(hideReplies = following.hideReplies || old["replies"].boolean == false, hideReposts = following.hideReposts || old["reposts"].boolean == false))
        }
        file.delete()
    }
}
