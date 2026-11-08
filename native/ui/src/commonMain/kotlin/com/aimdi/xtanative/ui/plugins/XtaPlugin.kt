package com.aimdi.xtanative.ui.plugins

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.store.offline.OfflineArticle
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/**
 * The plugin API this app is built with (PLUGINS.md). A plugin's
 * xta-plugin.json names the version it was built against, and the app loads
 * only plugins built against one it supports. Changing what is in this
 * package in a way a plugin already built would notice means a new version.
 * 1 is what native-0.1.41 shipped, 2 what native-0.1.45 shipped; 3 adds the
 * held cashtag's entries, a post's extras, an account's route and the
 * accounts a plugin follows, for Subscriptions (native-0.1.52); 4 adds a
 * post's own card, the networks of the contracts a plugin watches, for X
 * posts' text, the likes a network keeps on the device, a kept article's
 * reader, a plugin's part of a group's Discover, the search's landing, the
 * settings' title, the viewer's Open post and a picture's own download and
 * share, the tiles' ALT, a plugin's own words for its home timeline
 * switch, its tab switch, the tabs' swipes and a long piece of work's
 * progress (native-0.1.54); 5 adds what a group's add box can follow on a
 * plugin's network (native-0.1.56); 6 adds X's search with its replies and
 * conversations switches (`XClient.search`), which the Stocks plugin reads
 * through; 7 adds [XtaPlugin.mightRouteLater] and [XtaPlugin.routeForLater],
 * a link a plugin can claim only after asking the network; 8 adds a home
 * timeline's reading controls ([HomeReading], [rememberHomeReading],
 * [ReadingControlsBar], [ReadingControlsPinItem], [ReportsReadingScroll]), so a
 * plugin's own timeline keeps them as XTA's does.
 */
const val PLUGIN_API_VERSION: Int = 8

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
 * [query] finds, and is drawn again for each query the reader sends; until
 * one is sent, its own search screen shows [Landing].
 */
abstract class PluginSearch {
    /** The field's placeholder in [language]: "Search Bluesky". */
    abstract fun hint(language: String): String

    @Composable
    abstract fun Results(query: String, frame: PluginFrame)

    /**
     * What the search shows before a query is sent, as XTA's searches land on
     * their recent searches, topics or trends; nothing by default. [send] puts
     * a query in the field and sends it, as the search key does.
     */
    @Composable
    open fun Landing(frame: PluginFrame, send: (String) -> Unit) {}

    /**
     * Whether Discover shows [Landing] too, under the plugin's chip while
     * nothing is typed, as XTA's Mastodon does; otherwise Discover says what
     * the plugin searches until something is.
     */
    open val landsInDiscover: Boolean get() = false

    /**
     * Whether the plugin's recent searches are the app's, kept under its id
     * where Discover keeps those of its chip (XTA's Mastodon): its own search
     * screen then remembers each query sent and lists them above [Landing].
     * A plugin keeping its own ([sent]) leaves this false.
     */
    open val sharesRecentSearches: Boolean get() = false

    /**
     * The reader sent [query], trimmed and not blank, in the plugin's own
     * search screen (Discover's chip keeps its own): a plugin keeping its
     * recent searches, as XTA's do ([PluginSearchHistory]), remembers it here.
     */
    open fun sent(query: String) {}
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

    /** The title of its [settings] screen in [language], as XTA's "Booru settings"; its name by default. */
    open fun settingsTitle(language: String): String = title(language)

    /** Its search; null when it has none. */
    open val search: PluginSearch? get() = null

    /**
     * The route of the screen of its own that shows [url] (a post or a
     * profile on its network), for a link it opens: the app then opens that
     * screen instead of the browser. Null for any other link.
     */
    open fun routeFor(url: String): String? = null

    /**
     * Whether [url] might be one of its screens only after asking the network,
     * as Substack asks an unknown domain whether a `/p/` link is a post.
     * Quick: no request. The app then calls [routeForLater], and only off the
     * desktop, where XTA asks. False by default, so a plugin already built is
     * unchanged.
     */
    open fun mightRouteLater(url: String): Boolean = false

    /**
     * The route of the screen [url] opens once the network has been asked, or
     * null when it does not. Called only after [mightRouteLater], and given up
     * on after a few seconds. Null by default.
     */
    open suspend fun routeForLater(url: String): String? = null

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
     * The networks of the tokens it watches, by their contract addresses, as
     * they change: an X post's text cuts a contract address in the middle and
     * writes a watched token's network in front of it, as XTA's does (Stocks'
     * "Base · 0x23a2…fe51"). The app matches an address as contracts compare
     * ([com.aimdi.xtanative.networks.CryptoAddresses.same]); null when it
     * watches none.
     */
    open val contractNetworks: Flow<Map<String, String>>? get() = null

    /**
     * The route of its own screen for [account], one of its accounts as its
     * [feed] reads it and a group holds it ("<plugin id>:<account>"): its page
     * there, as XTA's subscription sources open a member (destinationFor).
     * Null when it has none.
     */
    open fun routeForAccount(account: String): String? = null

    /**
     * The route of its own screen for [article], one the app's offline
     * collection keeps (its `kind` says whose: `rss`, `substack`), as XTA's
     * offline library opens a kept article in the reader it came from; the
     * reader takes the kept copy. Null when the article is none of its, and
     * the app shows the copy itself.
     */
    open fun routeForOffline(article: OfflineArticle): String? = null

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
     * The accounts it follows, as they change: the app's Subscriptions list
     * shows them beside X's, each with Add to group, Unfollow and its page
     * (XTA's SubscriptionSource). Null when it follows none.
     */
    open val subscriptions: Flow<List<PluginSubscription>>? get() = null

    /**
     * What [text], typed into a group's add box, names on its network (a
     * subreddit, a handle, a feed's address), to follow there and add to the
     * group, as XTA's box offers a row per network ([PluginFollowable]); null
     * when it can name nothing there. The app asks only the plugins the text
     * could belong to, as XTA reads it; asked as the reader types, so quick:
     * the network is read by [PluginFollowable.follow].
     */
    open fun followable(text: String): PluginFollowable? = null

    /**
     * Its part of a group's Discover: the accounts its members in the group
     * point at on its network (XTA's Bluesky, Mastodon and Pixiv sources).
     * Null when it finds none.
     */
    open val discovery: PluginDiscovery? get() = null

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
     * Its own card for [post], one of its posts, where the app draws it among
     * others' (Home's timelines, the groups), in place of the shared row, as
     * XTA draws an RSS item there as RSS's article row with its read mark,
     * and a web source's post as its layout draws it. The card draws its
     * whole row, the line under it included, and leads where it leads on the
     * plugin's own lists; the app marks where it came from down its side.
     * Null leaves the shared row, with [postExtras]. Asked as the row is
     * drawn, so quick.
     */
    open fun postCard(post: NetworkPost): (@Composable () -> Unit)? = null

    /**
     * One of its posts the archive kept ([PluginPostArchive.content]), as
     * Saved draws it with the shared row: null for a record it does not read,
     * which Saved draws as its own card.
     */
    open fun archived(content: JsonElement): NetworkPost? = null

    /**
     * The entries it adds to the post sheets for [post] (an X post's share
     * sheet, before its Cancel, and the shared post menu, below the app's
     * own), as XTA's share sheet offers "Save to Karakeep"; none by default.
     * Asked as a sheet opens, so quick: the work is the entry's
     * [PluginPostAction.run].
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
