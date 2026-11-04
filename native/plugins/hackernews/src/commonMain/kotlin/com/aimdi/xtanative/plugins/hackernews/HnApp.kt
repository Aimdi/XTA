package com.aimdi.xtanative.plugins.hackernews

import com.aimdi.xtanative.networks.hackernews.HackerNewsClient
import com.aimdi.xtanative.networks.hackernews.HnDevice
import com.aimdi.xtanative.networks.hackernews.HnLink
import com.aimdi.xtanative.networks.hackernews.HnLinks
import com.aimdi.xtanative.networks.hackernews.HnStory
import com.aimdi.xtanative.store.settings.HackerNewsPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.DrawnPosts
import com.aimdi.xtanative.ui.plugins.FeedKinds
import com.aimdi.xtanative.ui.plugins.FeedReads
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.NetworkPostActions
import com.aimdi.xtanative.ui.plugins.PluginFeed
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.PluginRoute
import com.aimdi.xtanative.ui.plugins.PluginSearchHistory
import com.aimdi.xtanative.ui.plugins.PluginSubscription
import com.aimdi.xtanative.ui.plugins.loaded
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the plugin keeps, as the app's settings hold it under XTA's keys (`:store`'s HackerNewsPrefs). */
internal data class HnKept(val liked: List<String> = emptyList(), val saved: List<HnStory> = emptyList(), val follows: List<String> = emptyList()) {
    fun isLiked(story: HnStory): Boolean = "${story.id}" in liked

    fun isSaved(story: HnStory): Boolean = HnDevice.isSaved(saved, story.id)

    fun isFollowing(id: String): Boolean = HnDevice.isFollowing(follows, id)

    companion object {
        fun of(settings: Settings?): HnKept = if (settings == null) HnKept() else HnKept(
            liked = HnDevice.strings(settings[HackerNewsPrefs.likedPosts]),
            saved = HnDevice.saved(settings[HackerNewsPrefs.savedPosts]),
            follows = HnDevice.follows(settings[HackerNewsPrefs.follows]),
        )

        fun withLike(settings: Settings, story: HnStory): Settings =
            settings.with(HackerNewsPrefs.likedPosts, HnDevice.stringsText(HnDevice.toggleLike(HnDevice.strings(settings[HackerNewsPrefs.likedPosts]), story.id)))

        fun withSaved(settings: Settings, story: HnStory): Settings =
            settings.with(HackerNewsPrefs.savedPosts, HnDevice.savedText(HnDevice.toggleSaved(HnDevice.saved(settings[HackerNewsPrefs.savedPosts]), story)))

        /** The follows as XTA writes them: the newest first, as they were toggled. */
        fun withFollow(settings: Settings, id: String): Settings =
            settings.with(HackerNewsPrefs.follows, HnDevice.stringsText(HnDevice.toggleFollow(HnDevice.strings(settings[HackerNewsPrefs.follows]), id)))
    }
}

/** The plugin's screens: a story with its comments, and an account. */
internal sealed interface HnRoute {
    data class Story(val id: Long) : HnRoute

    data class User(val id: String) : HnRoute
}

internal object HnRoutes {
    fun story(id: Long): String = PluginRoute("story", "id" to "$id").toString()

    fun user(id: String): String = PluginRoute("user", "id" to id).toString()

    fun parse(route: String): HnRoute? {
        val parsed = PluginRoute.parse(route)
        return when (parsed.name) {
            "story" -> parsed["id"]?.toLongOrNull()?.let { HnRoute.Story(it) }
            "user" -> parsed["id"]?.let { HnRoute.User(it) }
            else -> null
        }
    }

    /** A story or a user on news.ycombinator.com, opened here as XTA opens them. */
    fun forUrl(url: String): String? = when (val link = HnLinks.parse(url)) {
        is HnLink.Story -> story(link.id)
        is HnLink.User -> user(link.id)
        null -> null
    }
}

/**
 * What the Hacker News plugin's screens share while it runs: the guest
 * client, what is kept (hearts, saved stories, follows) as the app's settings
 * hold it, where things open, and the stories the app's shared rows drew,
 * which their bookmark and menu file in the app's Saved. Nothing is voted,
 * commented or submitted.
 */
internal class HnApp(val host: PluginHost) {
    val client = HackerNewsClient(host.http)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val kept: StateFlow<HnKept> = host.settings.state.map(HnKept::of).stateIn(scope, SharingStarted.Eagerly, HnKept.of(host.settings.state.value))

    fun toggleLike(story: HnStory) = change { HnKept.withLike(it, story) }

    fun toggleSaved(story: HnStory) = change { HnKept.withSaved(it, story) }

    fun toggleFollow(id: String) {
        scope.launch { toggleNow(id) }
    }

    /** [id] followed here, or no longer and then out of the app's groups, as XTA's HnFollowsStore.toggle takes it out of them. */
    private suspend fun toggleNow(id: String) {
        val before = HnKept.of(host.settings.loaded()).follows
        host.settings.update { HnKept.withFollow(it, id) }
        val after = HnKept.of(host.settings.loaded()).follows.toSet()
        before.filterNot { it in after }.forEach(host::unfollowed)
    }

    /** The accounts followed here as the app's Subscriptions list shows them (XTA's Hacker News subscriptions): a tap opens the account. */
    val subscriptions: Flow<List<PluginSubscription>> = kept.map { it.follows.map(::subscriptionOf) }

    private fun subscriptionOf(id: String) = PluginSubscription(
        id = id,
        name = id,
        subtitle = id,
        route = HnRoutes.user(id),
        unfollow = { if (HnKept.of(host.settings.loaded()).isFollowing(id)) toggleNow(id) },
    )

    private fun change(edit: (Settings) -> Settings) {
        scope.launch { host.settings.update(edit) }
    }

    /** [query] first among the searches run here, as XTA's HnSearchHistoryStore keeps them. */
    fun rememberSearch(query: String) = change { it.with(HackerNewsPrefs.searchHistory, PluginSearchHistory.remembered(it[HackerNewsPrefs.searchHistory], query)) }

    fun close() = scope.cancel()

    /** Stories opened lately, so a story's screen shows it at once while its comments are read. */
    private val opened = ConcurrentHashMap<Long, HnStory>()

    fun openStory(story: HnStory) {
        if (opened.size > MAX_OPENED) opened.clear()
        opened[story.id] = story
        host.open(HnRoutes.story(story.id))
    }

    fun seen(id: Long): HnStory? = opened[id]

    fun openUser(id: String) = host.open(HnRoutes.user(id))

    /** The shared post sheet for [story] (hn_story_card.dart's long press): Saved, its note, its page shared or opened. */
    fun openMenu(story: HnStory) = host.openPostMenu(PluginPostMenu(HnShared.archive(story), story.hnUrl))

    /** [id]'s groups, followed here first once a group is picked (hn_group.dart's addHnToGroup). */
    fun addToGroup(id: String) = host.addToGroup(id) { host.settings.update { if (HnKept.of(it).isFollowing(id)) it else HnKept.withFollow(it, id) } }

    private val drawn = DrawnPosts<HnStory> { "${it.id}" }

    /** [story] as the app's shared rows draw it, kept so that what a row asks of it finds the story; with [words], its text too. */
    fun shown(story: HnStory, words: Boolean = false): NetworkPost = HnShared.post(drawn.put(story), words)

    /** Where a story the app draws leads: its comments, its author's page; its bookmark and the shared sheet. */
    val actions = NetworkPostActions(
        open = { post -> drawn[post.id]?.let(::openStory) ?: post.id.toLongOrNull()?.let { host.open(HnRoutes.story(it)) } },
        openAuthor = { post -> post.authorId.takeIf { it.isNotEmpty() }?.let(::openUser) },
        openMention = ::openUser,
        openTag = { query -> host.openSearch(query) },
        openMedia = { _, _ -> },
        archive = { post -> drawn[post.id]?.let(HnShared::archive) },
        more = { post -> drawn[post.id]?.let(::openMenu) },
    )

    /**
     * The accounts followed here in the groups that hold them (hn_group.dart's
     * loadHnGroupPosts): each one's latest stories that have a date.
     */
    val feed: PluginFeed = object : PluginFeed() {
        override suspend fun followed(): List<String> = kept.value.follows

        override suspend fun posts(accounts: List<String>, kinds: FeedKinds, force: Boolean): List<NetworkPost> =
            FeedReads.each(accounts, FeedReads.ACCOUNTS_AT_ONCE) { client.submissions(it).items }.filter { it.createdAt != null }.map { shown(it) }
    }

    /** The network's name where a problem names it. */
    val network: String get() = Strings.plugin_name.get(host.language)

    private companion object {
        const val MAX_OPENED = 64
    }
}
