package com.aimdi.xtanative.store.settings

/**
 * A network's preferences, kept under `plugin.<id>.`. [enabled] is its row in
 * the plugin store; what a network keeps as JSON is held as the text XTA
 * wrote, for the network's own store to read.
 */
abstract class NetworkPrefs(val id: String) : PrefSection() {
    protected fun key(name: String) = "plugin.$id.$name"

    abstract val enabled: Pref<Boolean>

    /** Its own tab in the bottom bar; null for a network that never has one. */
    open val showTab: Pref<Boolean>? get() = null

    /** Its followed accounts also in Following and For you; null where they never join them. */
    open val inHomeFeed: Pref<Boolean>? get() = null

    /** Another network's preferences it writes as well, where XTA's network writes them: the web sources' read marks among RSS's. */
    open val shared: List<Pref<*>> get() = emptyList()
}

/** Settings › Plugin store. */
object PluginStorePrefs : PrefSection() {
    /** Also lists the private networks the public catalogue holds back. */
    val showPrivate = flag("plugin_store.show_private", false)
}

/** Threads: public guest reads by default; an RSSHub instance, an Xy server or a pasted session are optional. */
object ThreadsPrefs : NetworkPrefs("threads") {
    override val enabled = flag(key("enabled"), false, seeded = false)
    override val showTab = flag(key("show_tab"), true, seeded = false)
    override val inHomeFeed = flag(key("in_home_feed"), false)

    /** The RSSHub instance posts are read through; empty for none. */
    val instance = text(key("instance"), "", seeded = false)
    val likedPosts = text(key("liked_posts"), "[]")
    val searchHistory = jsonList(key("search_history"))

    /** The tab's filter choices (content, replies, reposts), as JSON. */
    val feedOptions = text(key("feed_options"), "", seeded = false)

    /** The tab's newest posts, so a restart paints them at once. */
    val feedSnapshot = text(key("feed_snapshot"), "", seeded = false)

    /** Followed accounts and people search use the pasted session; the one switch that can cost a Threads login. */
    val useSessionApis = flag(key("use_session_apis"), false)

    /** The Xy server for richer profiles. */
    val apiBase = text(key("api_base"), "https://xy-threads.fly.dev", seeded = false)
    val apiToken = text(key("api_token"), "", seeded = false)
    val directCookies = text(key("direct.cookies_token"), "")
    val directBearer = text(key("direct.bearer_token"), "")
    val directDeviceId = text(key("direct.device_id"), "")

    /** When the session may talk to Meta again after a throttle (ISO-8601); kept across restarts on purpose. */
    val directCooldownUntil = text(key("direct.cooldown_until"), "", seeded = false)

    /** Handle to Meta user id, as JSON, so an account is looked up once. */
    val userIds = text(key("user_ids"), "{}", seeded = false)

    /** The guest token and when it was taken, so a cold open skips a profile page per account. */
    val guestLsd = text(key("guest_lsd"), "")
    val guestLsdAt = text(key("guest_lsd_at"), "")
}

/** Bluesky, read through the public AppView with local follows. */
object BlueskyPrefs : NetworkPrefs("bluesky") {
    override val enabled = flag(key("enabled"), false)
    override val inHomeFeed = flag(key("in_home_feed"), false)
    override val showTab = flag(key("show_tab"), true)

    /** The AppView; empty falls back to the public one. */
    val instance = text(key("instance"), "https://public.api.bsky.app")
    val likedPosts = text(key("liked_posts"), "[]")
    val searchHistory = jsonList(key("search_history"))

    /** Pinned custom feeds and lists, by AT-URI, as JSON. */
    val pinnedFeeds = text(key("pinned_feeds"), "[]")
    val pinnedLists = text(key("pinned_lists"), "[]")

    /** A public handle whose lists and feeds are offered. */
    val handle = text(key("handle"), "")

    /** Where each Bluesky feed was left, as JSON. */
    val readerPlaces = text(key("reader.v1"), "", seeded = false)
    val selectedFeeds = text(key("selectedFeeds"), "{}", seeded = false)
    val selectedLists = text(key("selectedLists"), "{}", seeded = false)
}

/** Mastodon and the Fediverse, read through a home instance's public API. */
object MastodonPrefs : NetworkPrefs("mastodon") {
    override val enabled = flag(key("enabled"), false)
    override val inHomeFeed = flag(key("in_home_feed"), false)
    override val showTab = flag(key("show_tab"), true)

    /** The home instance: account ids are local to each server. */
    val instance = text(key("instance"), "")

    /** Further instances, tried after the home one. */
    val instances = jsonList(key("instances"), seeded = false)

    /** Where each timeline was left, and the timeline's controls, as JSON. */
    val readerPlaces = text(key("reading.v1"), "", seeded = false)
    val timeline = text(key("timeline.v1"), "", seeded = false)
}

object TikTokPrefs : NetworkPrefs("tiktok") {
    override val enabled = flag(key("enabled"), false)
    override val showTab = flag(key("show_tab"), true)
    val cookies = text(key("cookies"), "")
    val deviceId = text(key("device_id"), "")
    val searchHistory = jsonList(key("search_history"))
    val likedPosts = text(key("liked_posts"), "[]")
    val preferEmbed = flag(key("prefer_embed"), false)
}

object InstagramPrefs : NetworkPrefs("instagram") {
    override val enabled = flag(key("enabled"), false)
    override val showTab = flag(key("show_tab"), true)
    val cookies = text(key("cookies"), "")
    val searchHistory = jsonList(key("search_history"))
    val likedPosts = text(key("liked_posts"), "[]")
}

/** Reddit: public endpoints, or the reader's own client id and sign-in. */
object RedditPrefs : NetworkPrefs("reddit") {
    override val enabled = flag(key("enabled"), false)
    val clientId = text(key("client_id"), "")

    /** Followed subreddits as XTA kept them before they had a table, as JSON. */
    val subreddits = text(key("subreddits"), "[]")
    val source = choice(key("source"), RedditSource.AUTO, RedditSource.entries)

    /** The one long-lived credential kept after a sign-in. */
    val refreshToken = text(key("refresh_token"), "")
    override val inHomeFeed = flag(key("in_home_feed"), false)
    override val showTab = flag(key("show_tab"), true)
    val sort = choice(key("sort"), RedditSort.HOT, RedditSort.entries)
    val timeFilter = choice(key("time_filter"), RedditTimeFilter.DAY, RedditTimeFilter.entries)
    val feedMode = choice(key("feed_mode"), RedditFeedMode.FOLLOWING, RedditFeedMode.entries)

    /** The followed subreddit open in the tab; empty for a discovery rail. */
    val selectedSubreddit = text(key("selected_subreddit"), "", seeded = false)
    val nsfwMode = choice(key("nsfw_mode"), RedditNsfwMode.TAP, RedditNsfwMode.entries)

    /** Spoiler-tagged media without the cover. */
    val showSpoilers = flag(key("show_spoilers"), false)
    val savedPosts = text(key("saved_posts"), "[]")

    /** Thread permalink to when it was last opened (epoch milliseconds), as JSON. */
    val threadVisits = text(key("thread_visits"), "{}")
}

object HackerNewsPrefs : NetworkPrefs("hackernews") {
    override val enabled = flag(key("enabled"), false)
    override val showTab = flag(key("show_tab"), true)
    val likedPosts = text(key("liked_posts"), "[]")
    val savedPosts = text(key("saved_posts"), "[]")

    /** Followed authors, as JSON: subscriptions kept in preferences. */
    val follows = text(key("follows"), "[]")
    val searchHistory = jsonList(key("search_history"))
}

object SubstackPrefs : NetworkPrefs("substack") {
    override val enabled = flag(key("enabled"), false)
    override val showTab = flag(key("show_tab"), true)

    /** Followed publications as XTA kept them before they had a table, as JSON. */
    val publications = text(key("publications"), "[]")
    val readIds = text(key("read_ids"), "[]")
    val likedPosts = text(key("liked_posts"), "[]")
    val savedPosts = text(key("saved_posts"), "[]")

    /** Pinned publications, comma-separated. */
    val pinnedPublications = text(key("pinned_publications"), "")
    val feedSnapshot = text(key("feed_snapshot"), "", seeded = false)

    /** Articles open in the app's own reader rather than the web page. */
    val nativeReader = flag(key("native_reader"), false)
    val readerFont = choice(key("reader_font"), SubstackReaderFont.SERIF, SubstackReaderFont.entries)
    val columnWidth = choice(key("column_width"), SubstackColumnWidth.MEDIUM, SubstackColumnWidth.entries)
    val markReadOnScroll = flag(key("mark_read_on_scroll"), false)

    /** The episode playing and where, and each episode's place, as JSON. */
    val podcast = text(key("podcast.v1"), "", seeded = false)
    val podcastPlaces = text(key("podcast.places"), "", seeded = false)
    val podcastSpeed = real(key("podcast.speed"), 1.0, choices = setOf(0.8, 1.0, 1.25, 1.5, 1.75, 2.0), seeded = false)
    val searchHistory = jsonList(key("search_history"), seeded = false)
    val highlights = text(key("highlights"), "", seeded = false)
}

object RssPrefs : NetworkPrefs("rss") {
    override val enabled = flag(key("enabled"), false)
    override val showTab = flag(key("show_tab"), true)
    override val inHomeFeed = flag(key("in_home_feed"), false)

    /** Followed feeds as XTA kept them before they had a table, as JSON. */
    val feeds = text(key("feeds"), "[]")

    /** Ids of items read, as JSON, the newest 800. */
    val readIds = text(key("read_ids"), "[]")
    val tags = text(key("tags"), "{}")
}

/** Web sources: lists read from sites through installed source files. */
object WebSourcesPrefs : NetworkPrefs("websources") {
    override val enabled = flag(key("enabled"), false)
    override val showTab = flag(key("show_tab"), true)

    /** Each installed source: its file, origin, the sites agreed to, and an update awaiting agreement, as JSON. */
    val installed = text(key("installed"), "[]")

    /** Followed lists, as JSON: subscriptions kept in preferences. */
    val follows = text(key("follows"), "[]")
    override val inHomeFeed = flag(key("in_home_feed"), false)
    val checkedAt = text(key("checked_at"), "")

    /** A post finished in the RSS reader is marked read among RSS's items, as XTA's RssReadStore marks it. */
    override val shared: List<Pref<*>> get() = listOf(RssPrefs.readIds)
}

/** Pixiv: a private reading network signed in with a refresh token; nothing is posted. */
object PixivPrefs : NetworkPrefs("pixiv") {
    override val enabled = flag(key("enabled"), false)
    override val showTab = flag(key("show_tab"), true)
    val refreshToken = text(key("refresh_token"), "")
    val accessToken = text(key("access_token"), "")
    val accessExpiresAt = text(key("access_expires_at"), "")
    val userId = long(key("user_id"), 0)
    val isPremium = flag(key("is_premium"), false)
    val showR18 = flag(key("show_r18"), false)
    val hideAi = flag(key("hide_ai"), false)
    val mutedAuthors = text(key("muted_authors"), "[]")
    val mutedTags = text(key("muted_tags"), "[]")
    val mutedIllusts = text(key("muted_illusts"), "[]")
    val mutedComments = text(key("muted_comments"), "[]")
    val mutedNovels = text(key("muted_novels"), "[]")

    /** Followed artists, as JSON: subscriptions kept in preferences. */
    val groupSubscriptions = text(key("group_subscriptions"), "[]")
    val searchHistory = jsonList(key("search_history"))
    val defaultPrivateBookmark = flag(key("default_private_bookmark"), false)
    val autoTagBookmarks = flag(key("auto_tag_bookmarks"), false)
    val followAfterBookmark = flag(key("follow_after_bookmark"), false)
    val downloadAfterBookmark = flag(key("download_after_bookmark"), false)
    val bookmarkAfterDownload = flag(key("bookmark_after_download"), false)
    val haptics = flag(key("haptics"), true)

    /** Every account signed in on this device, refresh tokens included, as JSON. */
    val accounts = text(key("accounts"), "[]")
    val startSection = choice(key("start_section"), PixivSection.HOME, PixivSection.entries)
    val copyTemplate = text(key("copy_template"), "")

    /** Opened works stay out of the viewing history. */
    val historyPaused = flag(key("history_paused"), false)

    /** Where each novel was left, as JSON; viewing history, so it never leaves the device. */
    val novelReading = text(key("novel_reading"), "{}")

    /** The image server: Pixiv's own, the public mirror `i.pixiv.re`, or the reader's. */
    val imageHost = text(key("image_host"), "i.pximg.net")
    val qualityFeed = choice(key("quality_feed"), PixivImageQuality.MEDIUM, listOf(PixivImageQuality.MEDIUM, PixivImageQuality.LARGE))
    val qualityDetail = choice(key("quality_detail"), PixivImageQuality.LARGE, PixivImageQuality.entries)
    val qualityReader = choice(key("quality_reader"), PixivImageQuality.LARGE, listOf(PixivImageQuality.LARGE, PixivImageQuality.ORIGINAL))

    /** Columns of a works grid; 0 picks them from the width. */
    val gridColumnsPortrait = whole(key("grid_columns_portrait"), 0, choices = setOf(0, 2, 3, 4))
    val gridColumnsLandscape = whole(key("grid_columns_landscape"), 0, choices = setOf(0, 2, 3, 4))
    val swipeBetweenWorks = flag(key("swipe_between_works"), false)
    val detailLayout = choice(key("detail_layout"), PixivDetailLayout.AUTO, PixivDetailLayout.entries)

    /** The pictures' share of a split detail, where the divider was last let go. */
    val detailSplit = real(key("detail_split"), 0.64, range = 0.1..0.9)
    val aiBadge = flag(key("ai_badge"), true)

    /** Downloaded files' names, with `{illust_id}`, `{part}`, `{title}`, `{user_id}` and `{user_name}`. */
    val fileNameTemplate = text(key("file_name_template"), "{illust_id}_p{part}")
    val folderPerArtist = flag(key("folder_per_artist"), false)
    val folderR18 = flag(key("folder_r18"), false)

    /** The pages saved to this device's storage, as JSON. */
    val downloadIndex = text(key("download_index"), "[]")

    /** Ranking boards pinned as chips, by Pixiv's mode name. */
    val rankingModes = jsonList(key("ranking_modes"), listOf("day", "week", "month", "day_male", "day_female", "week_original", "week_rookie", "day_manga"))
    val novelRankingModes = jsonList(key("novel_ranking_modes"), listOf("day", "week", "day_male", "day_female"))

    /** Search filters the reader chose to remember, as JSON; empty for none. */
    val searchFilters = text(key("search_filters"), "")
    val novelSearchFilters = text(key("novel_search_filters"), "")
    val novelSearchHistory = jsonList(key("novel_search_history"))

    /** Tags pinned as saved searches, in the reader's order, as JSON. */
    val favoriteTags = text(key("favorite_tags"), "[]")
}

/** Boorus: Danbooru and the sites that speak its siblings' APIs. */
object BooruPrefs : NetworkPrefs("booru") {
    override val enabled = flag(key("enabled"), false)
    override val showTab = flag(key("show_tab"), true)
    val engine = choice(key("engine"), BooruEngine.DANBOORU, BooruEngine.entries, parse = BooruEngine::parse)
    val host = text(key("host"), "https://danbooru.donmai.us")

    /** The preset site's id (`danbooru`, `safebooru`, `e926`, …) or a custom site's. */
    val preset = text(key("preset"), "danbooru")
    val login = text(key("login"), "")
    val apiKey = text(key("api_key"), "")

    /** The host [login] and [apiKey] were entered for; no other host is sent them. */
    val credentialsHost = text(key("credentials_host"), "")
    val maxRating = choice(key("max_rating"), BooruRating.GENERAL, BooruRating.entries, parse = BooruRating::parse)

    /** Each site's own rating cap and blacklist while another is in use, as JSON by host. */
    val siteSettings = text(key("site_settings"), "{}")

    /** Each site's own login and key while another is in use, as JSON by host. */
    val siteCredentials = text(key("site_credentials_api_key"), "{}")
    override val inHomeFeed = flag(key("in_home_feed"), false)
    val searchHistory = jsonList(key("search_history"))
    val mutedTags = text(key("muted_tags"), "[]")
    val customSites = text(key("custom_sites"), "[]")

    /** Grid columns; 0 picks them from the width. */
    val gridColumns = whole(key("grid_columns"), 0, choices = setOf(0, 2, 3, 4, 5))
    val smallThumbnails = flag(key("small_thumbnails"), false)
    val originalInViewer = flag(key("original_in_viewer"), false)
    val tileDetails = flag(key("tile_details"), true)
    val blurExplicit = flag(key("blur_explicit"), false)
    val tagSort = choice(key("tag_sort"), BooruTagSort.NAME, BooruTagSort.entries)

    /** Downloaded files' names, with `{id}`, `{md5}`, `{site}` and the rest; empty keeps the file's own. */
    val fileNameTemplate = text(key("file_name_template"), "")

    /** The kinds of tag "Copy tags as prompt" writes, in order; empty for the default. */
    val promptKinds = text(key("prompt_kinds"), "")

    /** Each site's newest post seen on Following, as JSON by host. */
    val followingSeen = text(key("following_seen"), "{}")
}

object EhViewerPrefs : NetworkPrefs("ehviewer") {
    override val enabled = flag(key("enabled"), false)
    override val showTab = flag(key("show_tab"), true)
    val cookies = text(key("cookies"), "")
    val useExhentai = flag(key("use_exhentai"), false)

    /** The categories searched; empty for all. */
    val categories = text(key("categories"), "")
    val searchHistory = jsonList(key("search_history"))
    val preferJapanese = flag(key("prefer_japanese"), true)
    val keepScreenOn = flag(key("keep_screen_on"), true)
    val readingMode = choice(key("reading_mode"), EhReadingMode.LEFT_TO_RIGHT, EhReadingMode.entries)
}

/**
 * Stocks, with the ticker screen a cashtag opens. Its chart and the reading
 * of a cashtag naming both a coin and a stock are kept where XTA keeps them,
 * under `other.`: they are the plugin's all the same.
 */
object StocksPrefs : NetworkPrefs("stocks") {
    override val enabled = flag(key("enabled"), false)
    override val showTab = flag(key("show_tab"), true)

    /** The watchlist's ids in the reader's order; empty sorts them by label. */
    val watchlistOrder = texts(key("watchlist_order"), emptyList(), seeded = false)

    /** Posts shaped like a trading-signal group's arrive folded. */
    val foldSignalPosts = flag(key("fold_signal_posts"), false)

    /** The chart on a ticker screen: the one thing it reads from outside X, so it can be switched off. */
    val tickerChart = flag("other.ticker_chart", true)

    /** `SYMBOL=stock` or `SYMBOL=crypto`, one per cashtag the reader chose for. */
    val tickerInstruments = texts("other.ticker_instruments", emptyList(), seeded = false)
}

/** Karakeep: saved posts sent to the reader's own bookmark server. */
object KarakeepPrefs : NetworkPrefs("karakeep") {
    override val enabled = flag(key("enabled"), false)
    val serverUrl = text(key("server_url"), "")
    val apiKey = text(key("api_key"), "")
}

/** Deepmarks: saved posts kept as Nostr bookmarks. */
object DeepmarksPrefs : NetworkPrefs("deepmarks") {
    override val enabled = flag(key("enabled"), false)
    val apiBase = text(key("api_base"), "")
    val apiKey = text(key("api_key"), "")

    /** The reader's Nostr signing key. */
    val secretKey = text(key("secret_key"), "")
}

/** Immich: saved media sent to the reader's own photo server. */
object ImmichPrefs : NetworkPrefs("immich") {
    override val enabled = flag(key("enabled"), false)
    val serverUrl = text(key("server_url"), "")
    val apiKey = text(key("api_key"), "")

    /** Each saved folder gets an album of its own. */
    val albumPerFolder = flag(key("album_per_folder"), true)
    val includeVideos = flag(key("include_videos"), true)
}
