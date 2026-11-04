package com.aimdi.xtanative.plugins.hackernews

import com.aimdi.xtanative.networks.hackernews.HackerNewsClient
import com.aimdi.xtanative.networks.hackernews.HnDevice
import com.aimdi.xtanative.networks.hackernews.HnLink
import com.aimdi.xtanative.networks.hackernews.HnLinks
import com.aimdi.xtanative.networks.hackernews.HnStory
import com.aimdi.xtanative.store.settings.HackerNewsPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.PluginHost
import com.aimdi.xtanative.ui.plugins.PluginRoute
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

/** A story as Saved keeps a post from a network with no archive of its own: XTA's link snapshot (plugin_link_post.dart), under the story's page. */
internal object HnArchive {
    fun id(story: HnStory): String = "hackernews:${story.hnUrl}"

    fun content(story: HnStory): JsonObject = buildJsonObject {
        put("archiveId", null as String?)
        put("archiveUserId", null as String?)
        put("xtaPlugin", "link")
        put("source", "hackernews")
        put("url", story.hnUrl)
        put("author", story.author.orEmpty())
        put("text", listOf(story.title, story.text).filter { it.isNotEmpty() }.joinToString("\n"))
        put("images", JsonArray(emptyList()))
    }
}

/**
 * What the Hacker News plugin's screens share while it runs: the guest
 * client, what is kept (hearts, saved stories, follows) as the app's settings
 * hold it, and where things open. Nothing is voted, commented or submitted.
 */
internal class HnApp(val host: PluginHost) {
    val client = HackerNewsClient(host.http)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val kept: StateFlow<HnKept> = host.settings.state.map(HnKept::of).stateIn(scope, SharingStarted.Eagerly, HnKept.of(host.settings.state.value))

    fun toggleLike(story: HnStory) = change { HnKept.withLike(it, story) }

    fun toggleSaved(story: HnStory) = change { HnKept.withSaved(it, story) }

    fun toggleFollow(id: String) = change { HnKept.withFollow(it, id) }

    private fun change(edit: (Settings) -> Settings) {
        scope.launch { host.settings.update(edit) }
    }

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

    /** The network's name where a problem names it. */
    val network: String get() = Strings.plugin_name.get(host.language)

    private companion object {
        const val MAX_OPENED = 64
    }
}
