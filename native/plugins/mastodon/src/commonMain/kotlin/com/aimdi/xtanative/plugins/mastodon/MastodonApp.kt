package com.aimdi.xtanative.plugins.mastodon

import com.aimdi.xtanative.networks.mastodon.MastodonClient
import com.aimdi.xtanative.networks.mastodon.MastodonExploreReader
import com.aimdi.xtanative.networks.mastodon.MastodonFollow
import com.aimdi.xtanative.networks.mastodon.MastodonFollowing
import com.aimdi.xtanative.networks.mastodon.MastodonFollowingReader
import com.aimdi.xtanative.networks.mastodon.MastodonInstances
import com.aimdi.xtanative.networks.mastodon.MastodonPost
import com.aimdi.xtanative.networks.mastodon.MastodonProfile
import com.aimdi.xtanative.networks.mastodon.MastodonPublicReader
import com.aimdi.xtanative.networks.mastodon.MastodonReplyOrder
import com.aimdi.xtanative.networks.mastodon.MastodonTimeline
import com.aimdi.xtanative.networks.mastodon.MastodonTimelineFilter
import com.aimdi.xtanative.networks.mastodon.MastodonTimelineReader
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.DrawnPosts
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginFeed
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the Mastodon plugin's screens share while it runs, as XTA's providers
 * share it: the client, the accounts followed here, its settings (the app's,
 * under XTA's keys), the four timelines Home and the client both show, the
 * places they were left, the session's choices (orders, searches of loaded
 * posts), the followed accounts' part of the app's timelines, and the posts
 * the app drew lately, which their bookmark and menu file in the app's
 * archive. The timelines are forgotten when the reader's instances change,
 * and read again where they are shown.
 */
internal class MastodonApp(val host: PluginHost) {
    val client = MastodonClient(host.http)
    val follows = MastodonFollowsStore(host.store.file("follows.json"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The plugin's settings as they change; the app reads them in before its screens show. */
    val prefs: StateFlow<MastodonPrefsView> =
        host.settings.state.map(MastodonSettings::view).stateIn(scope, SharingStarted.Eagerly, MastodonSettings.view(host.settings.state.value))

    /** The reader's own instances, home first. */
    val configured: List<String> get() = prefs.value.configured

    fun discovery(): List<String> = MastodonInstances.discovery(configured)

    /** Where [acct] is looked for: its own instance, the reader's, the built-in ones. */
    fun candidates(acct: String): List<String> = MastodonInstances.candidates(acct, configured)

    /** The hosts whose links open here. */
    fun hosts(): Set<String> = MastodonInstances.hosts(configured + MastodonInstances.defaults)

    val explore = MastodonExploreReader(client, ::discovery, scope)
    val local = MastodonPublicReader(client, ::discovery, local = true, scope)
    val federated = MastodonPublicReader(client, ::discovery, local = false, scope)
    /** Each followed account's page, kept ten minutes: what Following, Home's timelines and the groups read through. */
    private val followingPages = MastodonFollowing(client, { configured })
    val following = MastodonFollowingReader(followingPages, follows::accts, scope)

    fun reader(timeline: MastodonTimeline): MastodonTimelineReader = when (timeline) {
        MastodonTimeline.EXPLORE -> explore
        MastodonTimeline.LOCAL -> local
        MastodonTimeline.FEDERATED -> federated
        MastodonTimeline.FOLLOWING -> following
    }

    /** Where the timelines were left. */
    val places = MastodonPlaces(this, scope)

    /** What each timeline's loaded posts are searched for: for the session only, as in XTA. */
    private val queries = MutableStateFlow<Map<MastodonTimeline, String>>(emptyMap())
    val searched: StateFlow<Map<MastodonTimeline, String>> = queries.asStateFlow()

    /** The order replies, quotes and reposters were last put in: for the session, as XTA's ConversationSortStore. */
    @Volatile
    var replyOrder: MastodonReplyOrder = MastodonReplyOrder.OLDEST

    @Volatile
    var quoteOrder: MastodonOrder = MastodonOrder.RECENT

    @Volatile
    var reposterOrder: MastodonOrder = MastodonOrder.RECENT

    init {
        scope.launch {
            prefs.map { it.configured }.distinctUntilChanged().drop(1).collect { MastodonTimeline.entries.forEach { reader(it).forget() } }
        }
    }

    /** [timeline]'s filters: the kept ones, and what its loaded posts are searched for. */
    fun filter(timeline: MastodonTimeline, view: MastodonPrefsView, searched: Map<MastodonTimeline, String>): MastodonTimelineFilter =
        view.filter(timeline).copy(query = searched[timeline].orEmpty())

    /** [filter] in place of [timeline]'s; the search goes to the session, the rest to the settings. */
    fun setFilter(timeline: MastodonTimeline, filter: MastodonTimelineFilter) {
        queries.update { it + (timeline to filter.query) }
        changeSettings { MastodonSettings.withFilter(it, timeline, filter) }
    }

    fun changeSettings(change: (Settings) -> Settings) = launch { host.settings.update(change) }

    // ---- following here ----

    /** [acct] looked up where it lives and followed here, Following read again; why not, when it could not be found. */
    suspend fun follow(acct: String): Throwable? = try {
        follows.follow(client.lookupAnywhere(candidates(acct), acct), XtaTime.sqlite(Instant.now()))
        following.refresh(force = false)
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    }

    /** [profile] followed here, or no longer; Following read again. */
    fun toggleFollow(profile: MastodonProfile) = launch {
        if (follows.follows(profile.acct)) follows.unfollow(profile.acct) else follows.follow(profile, XtaTime.sqlite(Instant.now()))
        following.refresh(force = false)
    }

    fun unfollow(acct: String) = launch {
        follows.unfollow(acct)
        following.refresh(force = false)
    }

    /** [account] followed here unless it is, as putting it in a group follows it first (XTA's addMastodonAccountToGroup). */
    suspend fun ensureFollowed(account: MastodonFollow) {
        if (follows.load().any { it.isOf(account.acct) }) return
        follows.follow(account.copy(createdAt = XtaTime.sqlite(Instant.now())))
        following.refresh(force = false)
    }

    /** [account] in the app's groups, followed here first once a group is picked. */
    fun addToGroup(account: MastodonFollow) = host.addToGroup(account.acct) { ensureFollowed(account) }

    /** A backup's follows (XTA's `mastodonSubscriptions` rows) in place of these; Following read again. */
    suspend fun restoreFollows(rows: String) {
        follows.importRows(rows)
        following.refresh(force = false)
    }

    /** Runs [block] beyond the screen that asked for it: a follow, a setting. */
    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    fun close() = scope.cancel()

    // ---- where things open ----

    /** Posts opened lately, so an opened post shows at once while its conversation is read. */
    private val opened = ConcurrentHashMap<String, MastodonPost>()

    fun openThread(post: MastodonPost) {
        if (opened.size > MAX_OPENED) opened.clear()
        opened[post.url] = post
        host.open(MastodonRoutes.thread(post))
    }

    /** The post [target] names, as it was last seen. */
    fun seed(target: MastodonTarget): MastodonPost = opened[target.url] ?: target.seed()

    /**
     * Where [post]'s parts lead: the post or its quote to its conversation
     * (not the opened post itself, when it is not [opens]), names and
     * mentions to their profiles, tags to their timelines, media to the viewer.
     */
    fun actionsFor(post: MastodonPost, opens: Boolean = true): NetworkPostActions = NetworkPostActions(
        open = { shown -> listOfNotNull(post, post.quote).firstOrNull { it.url == shown.id }?.takeIf { opens || it !== post }?.let(::openThread) },
        openAuthor = { shown -> openProfile(shown.authorId) },
        openMention = ::openProfile,
        openTag = ::openTag,
        openMedia = { shown, index -> host.openMedia(shown.media, index) },
    )

    fun openProfile(acct: String) = host.open(MastodonRoutes.profile(acct))

    fun openTag(name: String) = host.open(MastodonRoutes.tag(name))

    fun openReposts(post: MastodonPost) = host.open(MastodonRoutes.reposts(post))

    fun openQuotes(post: MastodonPost) = host.open(MastodonRoutes.quotes(post))

    fun openSaved() = host.open(MastodonRoutes.saved())

    // ---- the app's timelines, Saved and the post sheet ----

    /** The posts the app's shared rows drew lately, by address: what their taps, bookmark and menu find again. */
    private val drawn = DrawnPosts<MastodonPost> { it.url }

    /** [post] as the app's shared row draws it, kept (its quote with it) so that its taps, bookmark and menu find it again. */
    fun shown(post: MastodonPost): NetworkPost {
        drawn.put(post)
        post.quote?.let(drawn::put)
        return MastodonShared.post(post, host.language)
    }

    /** Where a post the app draws leads: its conversation, its author, a tag's timeline, the viewer; its bookmark and the shared post sheet. */
    val actions = NetworkPostActions(
        open = { shown -> drawn[shown.id]?.let(::openThread) ?: shown.url?.let(host::openLink) },
        openAuthor = { shown -> openProfile(shown.authorId) },
        openMention = ::openProfile,
        openTag = ::openTag,
        openMedia = { shown, index -> host.openMedia(shown.media, index) },
        archive = { shown -> drawn[shown.id]?.let(MastodonShared::archive) },
        more = { shown -> drawn[shown.id]?.let(::openMenu) },
    )

    /** XTA's one post sheet for [post] (showMastodonPostActions): Saved, its note, its author's groups, its link, who boosted and quoted it. */
    fun openMenu(post: MastodonPost) = host.openPostMenu(
        PluginPostMenu(
            MastodonShared.archive(post),
            post.url,
            author = post.acct,
            reposts = { openReposts(post) },
            quotes = { openQuotes(post) },
            follow = { ensureFollowed(MastodonFollow(post.acct, post.authorName, post.avatarUrl)) },
        ),
    )

    /** The accounts followed here in Home's timelines and the groups' (XTA's loadMastodonInterleaved). */
    val shared: PluginFeed = SharedFeed()

    private inner class SharedFeed : PluginFeed() {
        override suspend fun followed(): List<String> = follows.load().map { it.acct }

        /** Through Following's pages and their ten minutes, ten posts in all, as XTA mixes Mastodon in. */
        override suspend fun posts(accounts: List<String>, kinds: FeedKinds, force: Boolean): List<NetworkPost> =
            MastodonShared.mixed(followingPages.posts(accounts, force), kinds).map(::shown)
    }

    private companion object {
        const val MAX_OPENED = 64
    }
}

/** How quotes and the accounts that boosted are ordered, on the device: Mastodon ranks neither. */
internal enum class MastodonOrder { RECENT, OLDEST, MOST_LIKED, MOST_FOLLOWERS }
