package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.json.JsonElement

/**
 * The plugin API this app is built with (PLUGINS.md). A plugin's
 * xta-plugin.json names the version it was built against, and the app loads
 * only plugins built against one it supports. Changing what is in this
 * package in a way a plugin already built would notice means a new version.
 * 1 is what native-0.1.41 shipped, 2 what native-0.1.45 shipped; 3 adds the
 * held cashtag's entries, a post's extras and an account's route.
 */
const val PLUGIN_API_VERSION: Int = 3

/**
 * The oldest plugin API whose plugins this app still loads: what they use of
 * it is all still here, as `tool/plugin_abi.py` checks against the published
 * plugins in CI. It moves up when a change leaves them behind, or when the
 * published plugins are of a newer one and the older can no longer be checked.
 */
const val OLDEST_PLUGIN_API: Int = 2

/** What a plugin draws inside the app's own chrome: its home tab, its client, its settings. */
typealias PluginBody = @Composable (PluginFrame) -> Unit

/** A whole screen a plugin draws, its bar included (see [com.aimdi.xtanative.ui.common.ScreenScaffold]). */
typealias PluginScreen = @Composable () -> Unit

/**
 * Where a [PluginBody] is drawn. [padding] keeps it clear of the app's bars:
 * it is the content padding of the body's list. [toTop] grows each time the
 * reader asks for the top again (a tap on the tab that is already selected):
 * a body scrolls its list to the top when it grows ([ScrollsToTop]).
 * [refresh] grows each time the reader asks for it to be read again (the
 * desktop's refresh button, F5): a body reads its page again when it grows
 * ([RefreshesOn]).
 */
@Immutable
class PluginFrame(val padding: PaddingValues, val toTop: Int = 0, val refresh: Int = 0) {
    override fun equals(other: Any?): Boolean =
        other is PluginFrame && other.padding == padding && other.toTop == toTop && other.refresh == refresh

    override fun hashCode(): Int = (padding.hashCode() * 31 + toTop) * 31 + refresh
}

/**
 * A plugin's search. The app draws the search field; [Results] draws what
 * [query] finds, and is drawn again for each query the reader sends.
 */
abstract class PluginSearch {
    /** The field's placeholder in [language]: "Search Bluesky". */
    abstract fun hint(language: String): String

    @Composable
    abstract fun Results(query: String, frame: PluginFrame)
}

/**
 * Something a plugin keeps that belongs in the app's backup, as the section
 * [name] ("bluesky.follows"): [export] gives it as text, and [import] takes
 * such a text back, replacing what the plugin kept there.
 *
 * Named by one of the keys of XTA's backup ("redditSubscriptions"), the text
 * is that network's rows as XTA writes them, a JSON list: the backup carries
 * it under that key, where XTA reads it, and a restore of XTA's rows imports
 * them written over the ones [export] gave, as XTA's import writes a table.
 */
class PluginBackupSection(
    val name: String,
    val export: suspend () -> String?,
    val import: suspend (String) -> Unit,
)

/** What a plugin keeps on the device, as its row in the store counts it: [items] things (follows, saved posts). The app adds the bytes of its folder. */
class PluginFootprint(val items: Int)

/**
 * A network made into a downloadable plugin (PLUGINS.md): what every plugin's
 * entry class implements. The app makes it with its no-argument constructor
 * (the class xta-plugin.json names), calls [attach] once, and then asks it
 * for its tab, its screens and its search as the reader goes there.
 *
 * A plugin only reads its network: nothing is posted, liked, followed or
 * voted on there. What it keeps (its follows, its settings) goes in
 * [PluginHost.store], which uninstalling erases, with its home tab, its pins
 * and the group memberships of its accounts.
 *
 * Its text comes from Kotlin, never from Android or Compose resources: the
 * plugins build makes a table per language from its strings/values/strings.xml
 * and strings/values-<language>/strings.xml (see [PluginStrings]), which loads
 * from any class loader.
 */
abstract class XtaPlugin {
    /** As in the catalogue and in xta-plugin.json: "bluesky". */
    abstract val id: String

    /** The colour people know the network by: its mark, and its tab when selected. */
    abstract val brandColor: Color

    /** Its mark, drawn in [brandColor]. */
    abstract val icon: ImageVector

    /** The store's group for it. */
    abstract val category: Category

    /** Offered only while the store shows private plugins, as the catalogue says too. */
    open val isPrivate: Boolean get() = false

    /** Its name in [language], a BCP 47 tag such as "de" or "pt-BR". */
    abstract fun title(language: String): String

    /** One line of what it does, in [language]. */
    abstract fun description(language: String): String

    /** What the app gives it, once, before anything else is asked of it. Quick: nothing is read here. */
    abstract fun attach(host: PluginHost)

    /** The app no longer runs it (it is uninstalled or replaced by an update): stop what it started. */
    open fun detach() {}

    /** Its timeline on Home's strip, beside For you, where the reader pins it; null when it has none. */
    open val homeTab: PluginBody? get() = null

    /** What it shows opened on its own (from the store), when that is not [homeTab]. */
    open val clientTab: PluginBody? get() = null

    /** Its settings, reached from its row in the store; null when it has none. */
    open val settings: PluginBody? get() = null

    /** Its search; null when it has none. */
    open val search: PluginSearch? get() = null

    /**
     * The route of the screen of its own that shows [url] (a post or a
     * profile on its network), for a link it opens: the app then opens that
     * screen instead of the browser. Null for any other link.
     */
    open fun routeFor(url: String): String? = null

    /** Its own screen named [route] (one [PluginHost.open] pushes, or [routeFor] gave); null when it has none by that name. */
    open fun screen(route: String): PluginScreen? = null

    /**
     * What it draws under an X post that names tickers, as Stocks draws its
     * price strip (cashtag_quotes.dart): [symbols] are the post's first
     * cashtags, upper-cased, each once, and none for a quoted post. Null
     * when it draws nothing there.
     */
    open val cashtagStrip: (@Composable (symbols: List<String>) -> Unit)? get() = null

    /**
     * The route of its own screen for a cashtag tapped in an X post
     * ([symbol] without its `$`), as Stocks' ticker screen; null when it has
     * none, and the tap searches X for the cashtag as it does without it.
     */
    open fun routeForCashtag(symbol: String): String? = null

    /**
     * Its entries in the sheet a long press on a cashtag in an X post opens
     * ([symbol] without its `$`, upper-cased), below the cashtag itself, which
     * opens what a tap opens (rich_text.dart's cashtag sheet): Stocks' Add to
     * watchlist. Asked as the sheet opens, so quick; none by default.
     */
    open fun cashtagActions(symbol: String): List<PluginCashtagAction> = emptyList()

    /**
     * The route of its own screen for [account], one of its accounts as its
     * [feed] reads it and a group holds it ("<plugin id>:<account>"): its page
     * there, as XTA's subscription sources open a member (destinationFor).
     * Null when it has none.
     */
    open fun routeForAccount(account: String): String? = null

    /** What it keeps that the app's backup holds. */
    open val backupSections: List<PluginBackupSection> get() = emptyList()

    /** What it keeps on the device now. */
    open suspend fun footprint(): PluginFootprint = PluginFootprint(0)

    /**
     * Its followed accounts' posts in the app's timelines: Home's Following
     * and For you while its "show in the home timeline" switch is on, and the
     * groups that hold its accounts. Null when it has none to mix in.
     */
    open val feed: PluginFeed? get() = null

    /**
     * Where its posts lead where the app draws them (Home's timelines, the
     * groups, Saved), with their bookmark and their menu; null leaves a post
     * opening its link, as the app opens links.
     */
    open val postActions: NetworkPostActions? get() = null

    /**
     * What it adds to the shared row where the app draws [post], one of its
     * posts (Home's timelines, the groups, Saved), as XTA's Mastodon card
     * shows a poll's results there; null adds nothing. Asked as the row is
     * drawn, so quick.
     */
    open fun postExtras(post: NetworkPost): NetworkPostExtras? = null

    /**
     * One of its posts the archive kept ([PluginPostArchive.content]), as
     * Saved draws it with the shared row: null for a record it does not read,
     * which Saved draws as its own card.
     */
    open fun archived(content: JsonElement): NetworkPost? = null

    /**
     * The entries it adds to the post sheets for [post] (the app's X post
     * sheet and the shared post menu), below the app's own, as XTA's share
     * sheet offers "Save to Karakeep"; none by default. Asked as a sheet
     * opens, so quick: the work is the entry's [PluginPostAction.run].
     */
    open fun postSheetActions(post: PluginSharedPost): List<PluginPostAction> = emptyList()

    /** Its switch in Saved's folder dialog (XTA's "Upload media to Immich"); null for none. */
    open val folderOption: PluginFolderOption? get() = null

    /**
     * [post] was filed in one of Saved's folders whose switch ([folderOption])
     * is on: from the bookmark, the save sheet or a note. It runs beyond the
     * screen that filed it, and says how it went itself ([PluginHost.tell]).
     */
    open suspend fun filed(post: PluginFiledPost) {}

    /** The store's groups, in the order XTA's store shows them; [key] as the catalogue writes it. */
    enum class Category(val key: String) {
        SOCIAL("social"),
        COMMUNITIES("communities"),
        NEWSLETTERS("newsletters"),
        ART("art"),
        MARKETS("markets"),
        BOOKMARKS("bookmarks"),
        MEDIA("media"),
        SITES("sites"),
    }
}
