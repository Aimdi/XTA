package com.aimdi.xtanative.store.settings

/** Settings › General, and the switches XTA turns once at launch. */
object GeneralPrefs : PrefSection() {
    /** `system`, or a locale code such as `pt_BR` or `zh_Hant`. */
    val locale = text("locale", "system")

    /** Off in this fork: it builds whenever something is fixed, so the check fired on most launches. */
    val checkForUpdates = flag("should_check_for_updates", false)

    /** Set once the update check has been switched off for an install that had it on. */
    val updateCheckReset = flag("should_check_for_updates.reset", false)

    val confirmClose = flag("confirm_close", true)

    /** Links open in the in-app browser; off, they go to [externalBrowser]. */
    val openLinksInApp = flag("open_links_in_embedded_browser", true)

    /** Set once links have been moved to the in-app browser for an install that had it off. */
    val openLinksInAppReset = flag("open_links_in_embedded_browser.reset", false)

    /** Package name of the browser links are handed to; empty for whatever Android picks. */
    val externalBrowser = text("external_browser", "")

    /** Tracking parameters come off a link before it is opened or shared. */
    val cleanLinks = flag("clean_links", true)

    val disableScreenshots = flag("disable_screenshots", false)

    /** The Home page the app opens on: `feed`, `subscriptions`, `trending`, `saved`, a network's id or `group-<id>`. */
    val initialTab = text("home.initial_tab", "feed")

    /** The feed Home opens on: `following`, `x` (For you) or a network's id. */
    val defaultFeedTab = text("home.default_feed_tab", "following")

    val defaultProfileTab = choice("subscription.default_tab", ProfileTab.POSTS, ProfileTab.entries)

    /** Where shared links point; empty means https://x.com. */
    val shareBaseUrl = text("share_base_url", "", seeded = false)

    /** The first-launch cards were seen. XTA seeds it false on a first launch, true on any later one. */
    val introSeen = flag("intro.seen", false)

    /** Kept by the builds XTA was forked from; nothing reads it. */
    val helloLastBuild = whole("hello.last_build", 0, seeded = false)
}

/** Settings › Theme: X Look's background and accent, and the switches beside them. */
object ThemePrefs : PrefSection() {
    val background = choice("theme.x_look.background", ThemeBackground.SYSTEM, ThemeBackground.entries)
    val accent = choice("theme.x_look.accent", ThemeAccent.BLUE, ThemeAccent.entries)

    /** True black for the dark theme; XTA's switch sets [trueBlackPostCards] along with it. */
    val trueBlack = flag("theme.true_black", true)
    val trueBlackPostCards = flag("theme.true_black_tweet_cards", true)
    val showNavigationLabels = flag("theme.show_navigation_labels", false)

    /** The Home bar slips away while scrolling down and comes back on the first scroll up. */
    val hideNavigationOnScroll = flag("theme.hide_navigation_on_scroll", true)

    val mode = choice("theme.mode", ThemeMode.SYSTEM, ThemeMode.entries)
    val color = choice("theme.color", ThemeColor.ACCENT, ThemeColor.entries)

    /** Moved onto [background] at every launch, then set back to none. */
    val preset = choice("theme.preset", ThemePreset.NONE, ThemePreset.entries)
}

/** Settings › Home: the bottom bar's pages, the feed strip, and what Home remembers. */
object HomePrefs : PrefSection() {
    /** The bottom bar, in order: `feed`, `subscriptions`, `trending`, `saved`, networks' ids and `group-<id>`. */
    val pages = texts("home.pages", listOf("feed", "subscriptions", "trending", "saved"))

    /** Networks the reader added beside Following and For you, in their order. */
    val feedStripNetworks = texts("home.feed_strip_plugins", emptyList(), seeded = false)

    /** Set at the first launch; older installs kept what they showed before timelines were hand-picked. */
    val feedStripHandPicked = flag("home.feed_strip_hand_picked", true, seeded = false)

    /** Networks the old automatic pinning offered; nothing adds to it now. */
    val seededStripNetworks = texts("home.seeded_strip_plugins", emptyList())

    /** Networks opened on the strip, newest first, at most 12. */
    val recentNetworks = texts("home.recent_networks", emptyList())

    /** Groups whose members are kept out of Following, as a JSON array; a group's own tab is unchanged. */
    val followingHiddenGroupIds = jsonList("home.feed_disabled_group_ids")

    /** Network tabs already offered to the bar once, so removing one sticks. */
    val seededNetworkTabs = texts("plugins.seeded_tabs", emptyList())

    /** Groups shown as deck columns on wide screens, comma-separated. */
    val deckGroupIds = text("home.deck_group_ids", "")

    /** Mastodon, Bluesky and Threads share one entry in the feed switcher. */
    val altMicrobloggingGrouped = flag("home.alt_microblogging.grouped", true)

    /** Which of those three was opened last. */
    val altMicrobloggingLastSource = text("home.alt_microblogging.last_source", "", seeded = false)

    /** The reading controls stay on screen instead of hiding while scrolling. */
    val keepControlsVisible = flag("home_keep_controls_visible", false, seeded = false)

    /** Options-sheet entries pinned to a network's top bar, in pin order, by network id. */
    val topBarPins = PrefFamily("home.top_bar_pins.") { key -> textsPref(key, emptyList(), seeded = false) }
}

/** Settings › Account: the picture in the top corner, and which signed-in accounts For you reads. */
object AccountPrefs : PrefSection() {
    /** The picture's revision; 0 shows the monogram. */
    val avatarRevision = whole("chrome.avatar_revision", 0)

    /**
     * The X accounts turned off, as a JSON array (XTA's AccountFetchGate): For
     * you leaves them out, and every read goes to the accounts left on while
     * there is one. A new account is on until turned off here or in Home feed
     * accounts. The key is XTA's, from when the switch was For you's alone.
     */
    val disabledAccountIds = jsonList("home.feed_disabled_account_ids")
}

/** Settings › Posts: how posts read, the feeds' defaults, zen and calm, and the language filter. */
object PostsPrefs : PrefSection() {
    val absoluteTimestamps = flag("option.absolute_timestamp", false)
    val hideSensitive = flag("tweets.hide_sensitive", true)

    /** Once a sensitive-media gate is opened, the others stay open too. */
    val alwaysShowSensitiveMedia = flag("tweets.always_show_sensitive_media", false)

    /** Posts are not clipped unless the reader asks for the compact form. */
    val fullPostContents = flag("always_show_full_tweet_contents", true)
    val nonConfirmationBias = flag("other.improve_non_confirmation_bias", false)
    val hideUnrelatedPostWarnings = flag("disable_warnings_for_unrelated_posts_in_feed", false)
    val subscribeBadge = flag("tweets.show_subscribe_badge", true)

    /** The feeds' default; a group may choose otherwise. */
    val includeReplies = flag("feed.global_include_replies", true)
    val includeReposts = flag("feed.global_include_retweets", true)

    /** Replies under an opened post as an indented tree. */
    val threadedReplies = flag("tweets.threaded_replies", true)

    /** A run of reposts collapses into one row. */
    val collapseReposts = flag("feed.collapse_boosts", false)

    /** Set once [collapseReposts] has been turned off for installs that had the old default. */
    val collapseRepostsMigrated = flag("feed.collapse_boosts_default_off_v1", false)

    val zenMode = flag("other.zen_mode", false)

    /** Pages one zen feed session reads. */
    val zenPageCap = whole("other.zen_mode_page_cap", 5, choices = setOf(3, 5, 10, 20))

    /** Engagement counts are hidden, without zen's other limits. */
    val calmMode = flag("other.calm_mode", false)
    val rememberReadingPosition = flag("feed.reading_position", true)

    /** Language prefixes the reader reads, comma-separated (`en,fr,ja`). */
    val languages = text("feed.languages", "")
    val languageAction = choice("feed.language_action", LanguageFilterAction.OFF, LanguageFilterAction.entries)

    /** When For you last cached a first page (ISO-8601), for the strip's unread dot. */
    val forYouNewestCachedAt = text("feed.for_you_newest_cached_at", "", seeded = false)

    /** "Finish the feed" mode, per group id (`-1` is the old key of Following). Off unless a feed opts in. */
    val catchUp = PrefFamily("feed.catch_up_mode.") { key -> flagPref(key, false, seeded = false) }
}

/** Settings › Media: sizes, videos and downloads. */
object MediaPrefs : PrefSection() {
    /** Media waits for a tap before loading. */
    val loadManually = flag("media.disable_autoload", false)
    val imageQuality = choice("media.size", MediaQuality.MEDIUM, MediaQuality.entries)
    val videoQuality = choice("media.video_quality", MediaQuality.MEDIUM, MediaQuality.entries)

    /** Set once the old single quality has been split into image and video. */
    val qualitySplitMigrated = flag("media.quality_split_migrated", false)
    val gridColumns = whole("media.grid_columns", 3, choices = (1..5).toSet())
    val gridLayout = choice("media.grid_layout", MediaGridLayout.MASONRY, MediaGridLayout.entries)
    val mute = flag("media.mute", true)
    val loop = flag("media.loop", false)
    val autoplay = flag("media.auto_play", false)

    /** Seconds a video reads ahead; 0 is no limit. */
    val prefetchSeconds = whole("media.video_prefetch_seconds", 0, choices = setOf(0, 1, 5, 15, 30, 60))

    /** Frames go straight from the decoder to the screen; renders black on some devices, so it is the reader's to turn on. */
    val directHardwareDecoding = flag("media.direct_hardware_decoding", false)
    val backgroundPlayback = flag("media.allow_background_play", true)
    val backgroundPlaybackWithOtherApps = flag("media.allow_background_play.other_apps", false)
    val downloadType = choice("download.type", DownloadType.AUTO, DownloadType.entries)

    /** The picked folder's name, kept so an old setting can still be shown. */
    val downloadPath = text("download.path", "")

    /** The picked folder, as Android's document tree. */
    val downloadTreeUri = text("download.tree_uri", "")

    /** Set once installs left on the old "ask" default have moved to saving without asking. */
    val downloadTypeMigrated = flag("download.type_auto_default_v1", false)

    /** Files downloading at once. */
    val downloadConcurrency = whole("download.concurrency", 2, choices = setOf(1, 2, 3, 4))
}

/** Settings › Accessibility, gestures among them. */
object AccessibilityPrefs : PrefSection() {
    val textScale = real("accessibility.text_scale_factor", 1.0, range = 1.0..1.5)
    val disableAnimations = flag("accessibility.disable_animations", false)
    val haptics = flag("accessibility.haptic_feedback", true)

    /** The chart on a ticker screen, which Settings › Accessibility also offers: Stocks' own ([StocksPrefs.tickerChart]). */
    val tickerChart: Pref<Boolean> get() = StocksPrefs.tickerChart

    /** Off by default: a double-tap handler makes every single tap wait for a second. */
    val doubleTapLike = flag("gestures.double_tap_like", false)

    /** An opened post goes back when dragged from anywhere; off, only from the screen's edge. */
    val swipeBackAnywhere = flag("gestures.swipe_back_anywhere", true)

    /** The dragged post turns around the finger and swings with its speed. */
    val swipeBackTilt = flag("gestures.swipe_back_tilt", true)
}

/** Reading aloud: one engine, voice and speed for every network. */
object SpeechPrefs : PrefSection() {
    /** The engine's package; empty for the system's own. */
    val engine = text("tts.engine", "")
    val voiceName = text("tts.voice_name", "")
    val voiceLocale = text("tts.voice_locale", "")
    val rate = real("tts.rate", 0.45, range = 0.2..1.0)

    /** A downloaded on-device voice reads when one speaks the article's language. */
    val offlineVoice = flag("tts.offline_voice", true)
}
