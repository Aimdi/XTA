package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.ui.plugins.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Where the app is asked to go, by a plugin or by a link. */
sealed interface Destination {
    data class XPost(val id: String) : Destination
    data class XProfile(val screenName: String) : Destination
    data class XSearch(val query: String) : Destination

    /** An X post's Quotes tab, as its footer's quote count opens it. */
    data class XQuotes(val id: String) : Destination

    /** An X post's Reposted by tab. */
    data class XReposters(val id: String) : Destination

    /** The X article post [id] carries, in the app's article reader. */
    data class XArticle(val id: String) : Destination

    /** A link to open as the app opens links: on a screen of the app or a plugin that reads it, else on the web. */
    data class Link(val url: String) : Destination

    data object PluginStore : Destination
    data class PluginClient(val pluginId: String) : Destination
    data class PluginRoute(val pluginId: String, val route: String) : Destination
    data class PluginSearch(val pluginId: String, val query: String?) : Destination
    data class PluginSettings(val pluginId: String) : Destination

    /** The media [MediaShelf] keeps under [key], from [start]. */
    data class Media(val key: String, val start: Int) : Destination

    /** The app's Downloads screen. */
    data object Downloads : Destination

    data object Back : Destination
}

/** What goes to a [Destination]: the screens' navigation, while it is there. */
fun interface NavTarget {
    fun go(destination: Destination)
}

/**
 * Hands each destination to the navigation the screens attached, on the UI
 * thread; with none attached (no window yet, or between two), it goes nowhere.
 */
class AppNavigator {
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var target: NavTarget? = null

    fun attach(target: NavTarget) {
        this.target = target
    }

    fun detach(target: NavTarget) {
        if (this.target === target) this.target = null
    }

    fun go(destination: Destination) {
        main.launch { target?.go(destination) }
    }
}

/** Media a plugin opened full screen: [items], of plugin [source], whose name a download takes; [openPost] opens the post they belong to. */
class ShelvedMedia(val source: String, val items: List<MediaItem>, val openPost: (() -> Unit)? = null)

/** The media plugins opened full screen, the last few kept: a route names them by key, as it cannot carry them. */
class MediaShelf {
    private val shelf = object : LinkedHashMap<String, ShelvedMedia>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ShelvedMedia>?): Boolean = size > KEPT
    }
    private var last = 0

    @Synchronized
    fun put(source: String, items: List<MediaItem>, openPost: (() -> Unit)? = null): String =
        (++last).toString().also { shelf[it] = ShelvedMedia(source, items, openPost) }

    /** What [key] names; nothing once it was let go, as after the app was restarted. */
    @Synchronized
    fun get(key: String): ShelvedMedia? = shelf[key]

    private companion object {
        const val KEPT = 16
    }
}
