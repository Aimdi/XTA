package com.aimdi.xtanative.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.savedstate.read
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.home_antennas
import com.aimdi.xtanative.ui.generated.resources.home_deck
import com.aimdi.xtanative.ui.generated.resources.home_discover
import com.aimdi.xtanative.ui.generated.resources.home_downloads
import com.aimdi.xtanative.ui.generated.resources.home_plugin_store
import com.aimdi.xtanative.ui.generated.resources.home_saved
import com.aimdi.xtanative.ui.generated.resources.home_subscriptions
import com.aimdi.xtanative.ui.generated.resources.post
import com.aimdi.xtanative.ui.generated.resources.search
import com.aimdi.xtanative.ui.generated.resources.settings_title
import com.aimdi.xtanative.ui.group.ReadMarks
import com.aimdi.xtanative.ui.pluginstore.PluginRoutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What the window's title names before the app's name, for the screen shown. */
sealed interface TitlePart {
    /** A screen known by its name: Settings, Downloads… */
    data class Named(val name: StringResource) : TitlePart

    /** A profile: `@name`. */
    data class Profile(val screenName: String) : TitlePart
}

/**
 * The window's title as a desktop app's names what it shows ("Search – XTA
 * Native", "@name – XTA Native"), for the window switcher, the bar and the
 * task list: [of] picks the part for a screen, from its route and arguments.
 * A search's words stay out: one typed on the Search screen does not change
 * its route, so the title could not follow it.
 * Home, and screens whose name the route does not hold (a community, a
 * list), leave the app's name alone.
 */
object WindowTitles {
    private val NAMED: Map<String, StringResource> = mapOf(
        Routes.DECK to Res.string.home_deck,
        Routes.SEARCH to Res.string.search,
        Routes.POST to Res.string.post,
        Routes.QUOTES to Res.string.post,
        Routes.REPOSTERS to Res.string.post,
        Routes.ARTICLE to Res.string.post,
        Routes.SETTINGS to Res.string.settings_title,
        Routes.SETTINGS_SECTION to Res.string.settings_title,
        Routes.SPEECH_SETTINGS to Res.string.settings_title,
        Routes.BACKUP to Res.string.settings_title,
        Routes.TRENDS to Res.string.home_discover,
        Routes.SAVED to Res.string.home_saved,
        Routes.SAVED_FOLDERS to Res.string.home_saved,
        Routes.ARCHIVE to Res.string.home_saved,
        Routes.DOWNLOADS to Res.string.home_downloads,
        Routes.OFFLINE to Res.string.home_downloads,
        Routes.ANTENNAS to Res.string.home_antennas,
        Routes.ANTENNA to Res.string.home_antennas,
        Routes.SUBSCRIPTIONS to Res.string.home_subscriptions,
        Routes.SUBSCRIPTIONS_IMPORT to Res.string.home_subscriptions,
        Routes.SUBSCRIPTIONS_IMPORT_LIST to Res.string.home_subscriptions,
        Routes.SUBSCRIPTIONS_SORT to Res.string.home_subscriptions,
        Routes.SUBSCRIPTIONS_SORT_ONE to Res.string.home_subscriptions,
        PluginRoutes.STORE to Res.string.home_plugin_store,
    )

    /** The part for the screen at [route] (a route pattern), its arguments read by [arg]; null for none. */
    fun of(route: String?, arg: (String) -> String?): TitlePart? = when (route) {
        null -> null
        Routes.PROFILE, Routes.FOLLOWING, Routes.FOLLOWERS -> arg("screenName")?.trim()?.ifEmpty { null }?.let(TitlePart::Profile)
        else -> NAMED[route]?.let(TitlePart::Named)
    }

    /**
     * The whole title: [part] before [app], or [app] alone; a part that is
     * only a count of unread posts ([counted]) goes before [app] with no dash.
     */
    fun title(part: String?, app: String): String = when {
        part == null -> app
        COUNT_ALONE.matches(part) -> "$part $app"
        else -> "$part – $app"
    }

    /** [part] with [unread] posts counted before it, "(12) Search"; [part] alone for none. */
    fun counted(part: String?, unread: Int): String? {
        if (unread <= 0) return part
        val count = "(" + ReadMarks.label(unread) + ")"
        return part?.let { "$count $it" } ?: count
    }

    private val COUNT_ALONE = Regex("""\(\d+\+?\)""")
}

/**
 * The window's title part, as the screens shown name it, with the count of
 * unread posts before it while the window is not the one in use
 * ([DesktopAttention]); the window reads it.
 */
object DesktopWindowTitle {
    private val shown = MutableStateFlow<String?>(null)
    private var screen: String? = null
    private var unread = 0
    private var focused = true

    val part: StateFlow<String?> = shown

    internal fun show(part: String?) = change { screen = part }

    /** How many posts are unread; 0 says nothing. */
    fun unread(count: Int) = change { unread = count }

    /** Whether the main window is the one in use, where the count is not needed. */
    internal fun focused(inUse: Boolean) = change { focused = inUse }

    private fun change(how: () -> Unit) = synchronized(this) {
        how()
        shown.value = WindowTitles.counted(screen, if (focused) 0 else unread)
    }
}

/** Keeps [DesktopWindowTitle] naming the screen [nav] shows; in a [SecondaryWindow], that window's own title. */
@Composable
internal actual fun WindowTitleFollows(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val part = WindowTitles.of(entry?.destination?.route) { name -> entry?.arguments?.read { getStringOrNull(name) } }
    val text = when (part) {
        null -> null
        is TitlePart.Named -> stringResource(part.name)
        is TitlePart.Profile -> "@" + part.screenName
    }
    val secondary = LocalSecondaryWindow.current
    LaunchedEffect(text, secondary) { if (secondary != null) secondary.showTitle(text) else DesktopWindowTitle.show(text) }
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused, secondary) { if (secondary == null) DesktopWindowTitle.focused(focused) }
}
