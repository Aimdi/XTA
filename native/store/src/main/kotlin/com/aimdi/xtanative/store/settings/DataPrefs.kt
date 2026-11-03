package com.aimdi.xtanative.store.settings

/** Settings › Data: sync to a WebDAV server the reader runs, and the documents XTA's repository publishes. */
object DataPrefs : PrefSection() {
    /** An https address only: the payload can carry X sessions. */
    val webDavUrl = text("sync.webdav.url", "")
    val webDavUsername = text("sync.webdav.username", "")
    val webDavPassword = text("sync.webdav.password", "")

    /** Off by default: uploading X sessions anywhere has to be a choice. */
    val webDavIncludeAccounts = flag("sync.webdav.include_accounts", false)

    /** ISO-8601, empty before the first upload. */
    val webDavLastSyncAt = text("sync.webdav.last_sync_at", "")

    /** Rotated X query ids are repaired from the repository's `endpoints.json`. */
    val endpointRegistryEnabled = flag("api.endpoint_registry.enabled", true)
    val endpointRegistryUrl = text("api.endpoint_registry.url", PublishedDocuments.ENDPOINTS)
    val endpointRegistryCache = text("api.endpoint_registry.cache", "")
    val endpointRegistryFetchedAt = text("api.endpoint_registry.fetched_at", "")

    /** Which networks the plugin store offers, from the repository's `plugins.json`. */
    val pluginCatalogueUrl = text("plugin.catalogue.url", PublishedDocuments.PLUGINS)
    val pluginCatalogueCache = text("plugin.catalogue.cache", "")
    val pluginCatalogueFetchedAt = text("plugin.catalogue.fetched_at", "")

    /** The networks' published repairs (`repairs.json`) last read, and when. */
    val pluginRepairsKept = text("plugin.repairs.kept", "", seeded = false)
    val pluginRepairsReadAt = text("plugin.repairs.read_at", "", seeded = false)
}

/** The documents XTA's repository publishes for installs to read, on its default branch. */
object PublishedDocuments {
    private const val ROOT = "https://raw.githubusercontent.com/Aimdi/XTA/HEAD/"
    const val ENDPOINTS = ROOT + "endpoints.json"
    const val PLUGINS = ROOT + "plugins.json"
    const val REPAIRS = ROOT + "repairs.json"
    const val WEB_SOURCES = ROOT + "sources.json"

    /** Branches earlier releases read them from. */
    val formerBranches = listOf("master", "main")

    /** Whether [stored] is the address an earlier release saved as the default for [current]. */
    fun isFormerDefault(stored: String, current: String): Boolean =
        formerBranches.any { stored.trim() == current.replaceFirst("/HEAD/", "/$it/") }
}

/** Settings › AI: an OpenAI-compatible or Anthropic endpoint the reader supplies; see [AiProvider]. */
object AiPrefs : PrefSection() {
    val baseUrl = text("ai.base_url", "")
    val apiKey = text("ai.api_key", "")
    val model = text("ai.model", "")
}

/** Crash reports, filed as issues on GitHub when the reader turns them on. */
object CrashReportPrefs : PrefSection() {
    val enabled = flag("crash.reports_enabled", false)
    val githubRepo = text("crash.github_repo", "Aimdi/XTA")
    val githubToken = text("crash.github_token", "")
}

/** The Saved tab: its built-in tabs, their order, and the folder a plain tap files into. */
object SavedPrefs : PrefSection() {
    val showAllTab = flag("saved.show_all_tab", true)
    val showUnfiledTab = flag("saved.show_unfiled_tab", true)
    val showFavoritesTab = flag("saved.show_favorites_tab", true)

    /** The strip's order as tokens and folder ids; empty for the default order. */
    val tabOrder = jsonList("saved.tab_order", seedText = "")
    val folderHintShown = flag("saved.folder_hint_shown", false)
    val likedFirstToastShown = flag("saved.liked_first_toast_shown", false)

    /** A plain tap on the bookmark files into [stickyFolderId]; empty means unfiled. */
    val stickyFolderEnabled = flag("saved.sticky_folder_enabled", false)
    val stickyFolderId = text("saved.sticky_folder_id", "")
}

/** The Subscriptions tab: how followed accounts and groups are ordered and laid out. */
object SubscriptionPrefs : PrefSection() {
    val groupsAscending = flag("subscription_groups.order_by.ascending", true)
    val groupsOrder = choice("subscription_groups.order_by.field", GroupsOrder.NAME, GroupsOrder.entries)

    /** Tile columns on the groups board: 2 is bold, 3 compact. */
    val groupsColumns = whole("subscription_groups.columns", 2, choices = setOf(2, 3))

    /** Sideways tiles with the faces beside the name. */
    val groupsCompact = flag("subscription_groups.compact", false)
    val groupsLayout = choice("subscription_groups.layout", GroupsLayout.BOARD, GroupsLayout.entries)

    /** Groups last picked in the group picker, newest first, comma-separated. */
    val groupsRecentPicks = text("subscription_groups.recent_picks", "")
    val ascending = flag("subscription.order_by.ascending", true)
    val order = choice("subscription.order_by.field", SubscriptionsOrder.NAME, SubscriptionsOrder.entries)

    /** The reader's own order of followed accounts; empty for none. */
    val customOrder = text("subscription.order_by.custom", "")
}

/** The Discover tab: trend places, and how a cashtag naming both a coin and a stock is read. */
object DiscoverPrefs : PrefSection() {
    /** The places offered for trends and the one in use, as JSON. */
    val trendLocations = text(
        "trends.locations",
        """{"active":{"name":"Worldwide","woeid":1},"locations":[{"name":"Worldwide","woeid":1}]}""",
    )

    /** `SYMBOL=stock` or `SYMBOL=crypto`, one per cashtag the reader chose for. */
    val tickerInstruments = texts("other.ticker_instruments", emptyList(), seeded = false)
}

/** Recent searches per network, as JSON; [com.aimdi.xtanative.store.search.RecentSearches] reads them. */
object SearchPrefs : PrefSection() {
    val recent = text("search.recent_by_network.v1", "", seeded = false)
}

/** Article reading, as JSON: where each article was left, and the reader's type size and spacing. */
object ReadingPrefs : PrefSection() {
    val articles = text("reading.articles.v1", "", seeded = false)
    val appearance = text("reading.appearance.v1", "", seeded = false)
}
