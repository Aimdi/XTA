import 'package:flutter/material.dart';

const optionDisableAnimations = 'accessibility.disable_animations';
const optionTextScaleFactor = 'accessibility.text_scale_factor';

const optionDisableScreenshots = 'disable_screenshots';
const optionHelloLastBuild = 'hello.last_build';

const optionHomePages = 'home.pages';
const optionHomeInitialTab = 'home.initial_tab';
const optionHomeDefaultFeedTab = 'home.default_feed_tab';

/// Plugin ids pinned next to Following / For you on the home feed strip.
///
/// Null means “never configured” — every enabled network that can sit on the
/// strip is offered. An empty list means the reader cleared every plugin tab
/// on purpose.
const optionHomeFeedStripPlugins = 'home.feed_strip_plugins';

/// Plugin ids already offered a home-strip pin, so removing one sticks.
const optionSeededStripPlugins = 'home.seeded_strip_plugins';

/// Plugin ids used most recently on the home strip, newest first.
const optionHomeRecentNetworks = 'home.recent_networks';

/// Login accounts excluded from the merged For you timeline (JSON string list).
/// Empty means every saved account participates. New accounts stay included
/// until the reader turns them off.
const optionHomeFeedDisabledAccountIds = 'home.feed_disabled_account_ids';

/// Revision of the local chrome (upper-left) avatar. `0` = use the monogram.
const optionChromeAvatarRevision = 'chrome.avatar_revision';

/// Plugin tabs already offered to the bar once, so removing one sticks.
const optionSeededPluginTabs = 'plugins.seeded_tabs';

const optionImageQuality = 'media.size';
const optionMediaVideoQuality = 'media.video_quality';
const optionMediaDisableAutoload = 'media.disable_autoload';
const optionMediaQualitySplitMigrated = 'media.quality_split_migrated';
const optionMediaGridColumns = 'media.grid_columns';
const optionMediaDefaultMute = 'media.mute';
const optionMediaDefaultLoop = 'media.loop';
const optionMediaDefaultAutoPlay = 'media.auto_play';
const optionMediaBackgroundPlayback = 'media.allow_background_play';
const optionMediaAllowBackgroundPlayOtherApps =
    'media.allow_background_play.other_apps';
const optionMediaVideoPrefetchSeconds = 'media.video_prefetch_seconds';

/// Whether decoded frames go straight from the hardware decoder to the screen.
///
/// Off, libmpv uses `mediacodec-copy`: every frame is copied out of the decoder
/// into system memory and then uploaded to a texture, which is memory bandwidth
/// spent competing with the thread that composites a scrolling feed. On, it uses
/// `mediacodec` and copies nothing — but the direct path renders black on some
/// devices, so this is the reader's to turn on rather than a default.
const optionMediaDirectHardwareDecoding = 'media.direct_hardware_decoding';

/// How far ahead a feed video reads when the reader has not asked for more.
///
/// libmpv's own defaults are built for watching one film, not for scrolling
/// past twenty clips — most of which are watched for seconds, and many of which
/// are never watched at all.
const int kVideoReadaheadSeconds = 10;

/// The bytes behind that readahead, and how much of what already played is kept
/// for scrubbing back. A feed is not somewhere anyone rewinds far.
const int kVideoDemuxerMaxBytes = 16 * 1024 * 1024;
const int kVideoDemuxerMaxBackBytes = 4 * 1024 * 1024;

/// How long a video that has scrolled off screen keeps its player before handing
/// it back to the pool. Long enough that overshooting a video and scrolling back
/// re-attaches to it at the same position; short enough that a fling does not
/// leave a trail of live players behind it.
const Duration kVideoHiddenReleaseDelay = Duration(seconds: 3);

/// How long a video tile must stay at least half on screen before it is allowed
/// to build a player. A fling sweeps past tiles that are never watched; without
/// this, each of them allocated libmpv and a native texture on the way by.
const Duration kVideoCreationSettleDelay = Duration(milliseconds: 250);

/// How many video players may be alive at once.
///
/// Each one is a libmpv instance holding a MediaCodec session, a demuxer thread
/// and its cache — and Android caps how many hardware video decoders exist at
/// all, across every app. Exhausting that cap does not fail loudly; it silently
/// drops new videos to software decoding, which is exactly when a timeline stops
/// keeping up.
const int kVideoPoolSize = 3;

const optionDownloadType = 'download.type';
const optionDownloadPath = 'download.path';
// Android document tree for the download folder. The legacy path above is kept
// only so an existing setting can still be shown and migrated.
const optionDownloadTreeUri = 'download.tree_uri';

const optionDownloadTypeDirectory = 'directory';
const optionDownloadTypeAsk = 'ask';

const optionLocale = 'locale';
const optionLocaleDefault = 'system';

const pluginIdDeepmarks = 'deepmarks';
const optionPluginDeepmarksEnabled = 'plugin.deepmarks.enabled';
const optionPluginDeepmarksApiBase = 'plugin.deepmarks.api_base';
const optionPluginDeepmarksApiKey = 'plugin.deepmarks.api_key';
const optionPluginDeepmarksSecretKey = 'plugin.deepmarks.secret_key';

const pluginIdImmich = 'immich';
const optionPluginImmichEnabled = 'plugin.immich.enabled';
const optionPluginImmichServerUrl = 'plugin.immich.server_url';
const optionPluginImmichApiKey = 'plugin.immich.api_key';

/// Whether each bookmark folder gets an Immich album of its own, named after it.
const optionPluginImmichAlbumPerFolder = 'plugin.immich.album_per_folder';

/// Videos and GIFs are sent alongside photos unless this is off. They are much
/// larger than a photo, which is worth a choice on a metered connection.
const optionPluginImmichIncludeVideos = 'plugin.immich.include_videos';

const pluginIdKarakeep = 'karakeep';
const optionPluginKarakeepEnabled = 'plugin.karakeep.enabled';
const optionPluginKarakeepServerUrl = 'plugin.karakeep.server_url';
const optionPluginKarakeepApiKey = 'plugin.karakeep.api_key';
const pluginIdHackerNews = 'hackernews';
const optionPluginHnEnabled = 'plugin.hackernews.enabled';
const optionPluginHnShowTab = 'plugin.hackernews.show_tab';
const optionPluginHnLikedPosts = 'plugin.hackernews.liked_posts';
const optionPluginHnSavedPosts = 'plugin.hackernews.saved_posts';
const optionPluginHnFollows = 'plugin.hackernews.follows';
const optionPluginHnSearchHistory = 'plugin.hackernews.search_history';

const pluginIdReddit = 'reddit';
const optionPluginRedditEnabled = 'plugin.reddit.enabled';
const optionPluginRedditClientId = 'plugin.reddit.client_id';
const optionPluginRedditSubreddits = 'plugin.reddit.subreddits';
// Which route Reddit is read through. `auto` uses the best credential the
// reader has given; `public` sticks to the account-free endpoints even when a
// sign-in or client id exists, for readers who would rather not be identified
// at all.
const optionPluginRedditSource = 'plugin.reddit.source';
const redditSourceAuto = 'auto';
const redditSourcePublic = 'public';

/// The listing a Reddit feed uses until the reader picks another.
const redditSortHot = 'hot';

/// The time window used by top and controversial Reddit listings.
const redditTimeFilterDay = 'day';

/// The Reddit tab section shown by default.
const redditFeedModeFollowing = 'following';

/// Over-18 posts are hidden, gated, or shown.
const redditNsfwModeTap = 'tap';

/// Where the subreddit artwork is kept, named here so the plugin can delete
/// the same directory it fills.
const redditIconsCacheName = 'reddit_icons';

/// Set once the reader signs in; the only long-lived Reddit credential kept.
const optionPluginRedditRefreshToken = 'plugin.reddit.refresh_token';

/// Whether followed subreddits also appear in Following and For you.
const optionPluginRedditInHomeFeed = 'plugin.reddit.in_home_feed';

/// Whether Reddit keeps a tab of its own in the bottom bar. It does not need
/// one: it is an entry in the home feed switcher and a row in Groups, so a
/// third way in only costs a slot in a bar that has five.
const optionPluginRedditShowTab = 'plugin.reddit.show_tab';

/// Which order subreddit listings are read in, shared by every screen that
/// shows them so the choice is made once.
const optionPluginRedditSort = 'plugin.reddit.sort';

/// Time window for top and controversial Reddit listings.
const optionPluginRedditTimeFilter = 'plugin.reddit.time_filter';

/// The selected Reddit discovery section.
const optionPluginRedditFeedMode = 'plugin.reddit.feed_mode';

/// Whether over-18 Reddit posts are hidden, tap-gated, or shown.
const optionPluginRedditNsfwMode = 'plugin.reddit.nsfw_mode';

/// JSON snapshots of locally saved Reddit posts.
const optionPluginRedditSavedPosts = 'plugin.reddit.saved_posts';

const pluginIdStocks = 'stocks';
const optionPluginStocksEnabled = 'plugin.stocks.enabled';
const optionPluginStocksShowTab = 'plugin.stocks.show_tab';

const pluginIdSubstack = 'substack';

/// Reading aloud: which engine, which voice, how fast. Shared rather than
/// per-plugin — a reader picks a voice once, not once per source.
const optionTtsEngine = 'tts.engine';
const optionTtsVoiceName = 'tts.voice_name';
const optionTtsVoiceLocale = 'tts.voice_locale';
const optionTtsRate = 'tts.rate';

const optionPluginSubstackEnabled = 'plugin.substack.enabled';
const optionPluginSubstackShowTab = 'plugin.substack.show_tab';
const optionPluginSubstackPublications = 'plugin.substack.publications';
const optionPluginSubstackReadIds = 'plugin.substack.read_ids';
const optionPluginSubstackLikedPosts = 'plugin.substack.liked_posts';
const optionPluginSubstackSavedPosts = 'plugin.substack.saved_posts';
const optionPluginSubstackPinnedPublications =
    'plugin.substack.pinned_publications';
const substackFeedPageSize = 8;
const substackReadIdsCap = 400;
const substackLikedPostsCap = 400;
const substackSavedPostsCap = 200;

/// Threads — public guest reads by default; optional session / RSSHub / Xy.
///
/// There is no public RSSHub instance default on purpose: the shared one is
/// rate limited to the point of uselessness for this route. Direct mode pastes
/// browser cookies and/or an `IGT:2` Bearer; sessions can die and accounts can
/// be checkpointed — prefer a disposable secondary account.
const pluginIdThreads = 'threads';
const optionPluginThreadsEnabled = 'plugin.threads.enabled';
const optionPluginThreadsShowTab = 'plugin.threads.show_tab';
const optionPluginThreadsInstance = 'plugin.threads.instance';
const optionPluginThreadsLikedPosts = 'plugin.threads.liked_posts';
const optionPluginThreadsSearchHistory = 'plugin.threads.search_history';

/// Whether followed Threads accounts also appear in Following and For you.
const optionPluginThreadsInHomeFeed = 'plugin.threads.in_home_feed';

/// When true, followed accounts / people search use the pasted cookie session.
/// Off by default: public guest GraphQL has no account to ban. Turning this on
/// is the one setting that can cost the reader a Threads login.
const optionPluginThreadsUseSessionApis = 'plugin.threads.use_session_apis';

/// How many posts one account contributes to the merged feed.
const threadsPostsPerAccount = 20;

/// Guest (and RSSHub) refreshes this many followed handles per load.
const threadsMaxAccountsPerLoad = 20;

/// Cookie-session refreshes are capped harder — a burst of `text_feed` calls
/// is what Meta's anti-scripting treats as a bot.
const threadsSessionMaxAccountsPerLoad = 6;

/// How many full liked-post snapshots are kept for the local Liked tab.
const threadsLikedPostsCap = 400;

/// Optional Xy server for richer profile lookups. Public Threads pages already
/// expose name / bio / avatar via OG tags; Xy is an upgrade, not a requirement.
/// Separate from the RSSHub instance above (post proxy).
const optionPluginThreadsApiBase = 'plugin.threads.api_base';
const optionPluginThreadsApiToken = 'plugin.threads.api_token';
const kThreadsApiDefaultBase = 'https://xy-threads.fly.dev';

/// Direct Meta session (optional). Cookie header + Bearer are secrets.
const optionPluginThreadsDirectCookies = 'plugin.threads.direct.cookies_token';
const optionPluginThreadsDirectBearer = 'plugin.threads.direct.bearer_token';
const optionPluginThreadsDirectDeviceId = 'plugin.threads.direct.device_id';

/// When the direct session may talk to Meta again after being throttled or
/// parked. Persisted rather than kept in memory: a cooldown that a restart
/// clears is no cooldown at all, and reopening the app to immediately resume
/// hammering is how a temporary block becomes a lost account.
const optionPluginThreadsDirectCooldownUntil =
    'plugin.threads.direct.cooldown_until';

/// Handle → numeric Meta user id, so a followed account is looked up once
/// rather than searched for again on every read.
const optionPluginThreadsUserIds = 'plugin.threads.user_ids';

/// Guest GraphQL LSD token + when it was captured. Survives process death so a
/// cold open does not pay an extra profile HTML round-trip for every account.
const optionPluginThreadsGuestLsd = 'plugin.threads.guest_lsd';
const optionPluginThreadsGuestLsdAt = 'plugin.threads.guest_lsd_at';

/// Floor between guest (public) request departures. Session traffic still uses
/// the stricter [ThreadsDirectClient.minGap] — guest has no account to ban.
const threadsGuestMinGap = Duration(milliseconds: 550);

/// Floor between cookie/Bearer departures. Longer than a person tapping
/// around, short enough that a handful of opted-in session reads still finish.
const threadsSessionMinGap = Duration(seconds: 3);

/// Bluesky, read through the public AppView — local follows, no Bluesky account.
const pluginIdBluesky = 'bluesky';
const optionPluginBlueskyEnabled = 'plugin.bluesky.enabled';
const optionPluginBlueskyInHomeFeed = 'plugin.bluesky.in_home_feed';
const optionPluginBlueskyShowTab = 'plugin.bluesky.show_tab';

/// Bluesky AppView base URL. Empty falls back to [kBlueskyDefaultAppView].
const optionPluginBlueskyInstance = 'plugin.bluesky.instance';

/// Snapshots of locally liked Bluesky posts (JSON list). Ids live in SQLite.
const optionPluginBlueskyLikedPosts = 'plugin.bluesky.liked_posts';
const optionPluginBlueskySearchHistory = 'plugin.bluesky.search_history';
const blueskyLikedPostsCap = 400;

/// How many posts one account contributes to the merged Bluesky feed.
const blueskyPostsPerAccount = 20;

/// How many followed accounts one Bluesky timeline load reads.
///
/// There is no "your following feed" to ask the public AppView for, so a
/// timeline is one author-feed request per account. That was fine while a
/// reader added accounts by hand and ruinous the moment they imported somebody
/// else's following list: several hundred requests per refresh, rate limited
/// into an empty tab. The newest follows are read and the rest wait their turn.
const blueskyMaxAccountsPerLoad = 30;

/// Mastodon / Fediverse, read through a home instance's public REST API.
///
/// No login: public account lookup and statuses only. The home instance is
/// required because Mastodon account ids are local to each server.
const pluginIdMastodon = 'mastodon';
const optionPluginMastodonEnabled = 'plugin.mastodon.enabled';
const optionPluginMastodonInHomeFeed = 'plugin.mastodon.in_home_feed';
const optionPluginMastodonShowTab = 'plugin.mastodon.show_tab';
const optionPluginMastodonInstance = 'plugin.mastodon.instance';

/// JSON list of further instances the reader added, tried after the home one.
const optionPluginMastodonInstances = 'plugin.mastodon.instances';

/// How many statuses one account contributes to the merged Mastodon feed.
const mastodonPostsPerAccount = 20;

/// First wave of followed accounts asked on one Following / home-feed load.
///
/// Each acct is a lookup plus statuses, and a miss walks another instance.
/// Thirty of those used to stall the rest of the app; the cache fills the
/// rest on the next refresh.
const mastodonMaxAccountsPerLoad = 12;

/// Pixiv — private reading plugin. Refresh-token auth; no write actions.
const pluginIdPixiv = 'pixiv';
const optionPluginPixivEnabled = 'plugin.pixiv.enabled';
const optionPluginPixivShowTab = 'plugin.pixiv.show_tab';
const optionPluginPixivRefreshToken = 'plugin.pixiv.refresh_token';
const optionPluginPixivAccessToken = 'plugin.pixiv.access_token';
const optionPluginPixivAccessExpiresAt = 'plugin.pixiv.access_expires_at';
const optionPluginPixivUserId = 'plugin.pixiv.user_id';
const optionPluginPixivShowR18 = 'plugin.pixiv.show_r18';
const optionPluginPixivMutedAuthors = 'plugin.pixiv.muted_authors';
const optionPluginPixivMutedTags = 'plugin.pixiv.muted_tags';
const optionPluginPixivMutedIllusts = 'plugin.pixiv.muted_illusts';
const optionPluginPixivSearchHistory = 'plugin.pixiv.search_history';

const pluginIdBooru = 'booru';
const optionPluginBooruEnabled = 'plugin.booru.enabled';
const optionPluginBooruShowTab = 'plugin.booru.show_tab';
const optionPluginBooruEngine = 'plugin.booru.engine';
const optionPluginBooruHost = 'plugin.booru.host';
const optionPluginBooruPreset = 'plugin.booru.preset';
const optionPluginBooruLogin = 'plugin.booru.login';
const optionPluginBooruApiKey = 'plugin.booru.api_key';
const optionPluginBooruMaxRating = 'plugin.booru.max_rating';
const optionPluginBooruInHomeFeed = 'plugin.booru.in_home_feed';
const optionPluginBooruSearchHistory = 'plugin.booru.search_history';
const optionPluginBooruMutedTags = 'plugin.booru.muted_tags';
const optionPluginBooruCustomSites = 'plugin.booru.custom_sites';
const pluginIdEhViewer = 'ehviewer';
const optionPluginEhEnabled = 'plugin.ehviewer.enabled';
const optionPluginEhShowTab = 'plugin.ehviewer.show_tab';
const optionPluginEhCookies = 'plugin.ehviewer.cookies';
const optionPluginEhUseExhentai = 'plugin.ehviewer.use_exhentai';
const optionPluginEhCategories = 'plugin.ehviewer.categories';
const optionPluginEhSearchHistory = 'plugin.ehviewer.search_history';
const optionPluginEhPreferJapanese = 'plugin.ehviewer.prefer_japanese';
const optionPluginEhKeepScreenOn = 'plugin.ehviewer.keep_screen_on';

const pluginIdTiktok = 'tiktok';
const optionPluginTiktokEnabled = 'plugin.tiktok.enabled';
const optionPluginTiktokShowTab = 'plugin.tiktok.show_tab';
const optionPluginTiktokCookies = 'plugin.tiktok.cookies';
const optionPluginTiktokDeviceId = 'plugin.tiktok.device_id';
const optionPluginTiktokSearchHistory = 'plugin.tiktok.search_history';
const optionPluginTiktokLikedPosts = 'plugin.tiktok.liked_posts';
const optionPluginTiktokPreferEmbed = 'plugin.tiktok.prefer_embed';

const pluginIdInstagram = 'instagram';
const optionPluginInstagramEnabled = 'plugin.instagram.enabled';
const optionPluginInstagramShowTab = 'plugin.instagram.show_tab';
const optionPluginInstagramCookies = 'plugin.instagram.cookies';
const optionPluginInstagramSearchHistory = 'plugin.instagram.search_history';
const optionPluginInstagramLikedPosts = 'plugin.instagram.liked_posts';

/// When true, the plugin store also lists [XtaPlugin.isPrivate] plugins that
/// the public catalogue holds back with `available: false`.
const optionPluginStoreShowPrivate = 'plugin_store.show_private';

const optionShouldCheckForUpdates = 'should_check_for_updates';

/// Marks that the update check has been turned off once for this fork. Without
/// it the change would only reach installs that had never stored the old
/// default — which is not the installs that were being interrupted.
const optionUpdateCheckReset = 'should_check_for_updates.reset';
// This fork's own repository. Releases and crash reports belong here, not on
// upstream teskann/XTA, whose versions this fork never matches.
const githubRepo = 'Aimdi/XTA';
const optionConfirmClose = 'confirm_close';
const optionOpenLinksInEmbeddedBrowser = 'open_links_in_embedded_browser';

/// Package name of the browser external links are handed to. Empty means
/// whatever Android would have picked.
const optionExternalBrowser = 'external_browser';

/// Marks that links have been switched to the in-app browser once. The default
/// alone does not reach an install that already stored the old one, which is
/// every install this has ever run on.
const optionEmbeddedBrowserReset = 'open_links_in_embedded_browser.reset';
const optionShareBaseUrl = 'share_base_url';

const optionCrashReportsEnabled = 'crash.reports_enabled';
const optionCrashGithubRepo = 'crash.github_repo';
const optionCrashGithubToken = 'crash.github_token';
const defaultCrashGithubRepo = githubRepo;

const optionDisableWarningsForUnrelatedPostsInFeed =
    'disable_warnings_for_unrelated_posts_in_feed';

const alwaysShowFullTweetContents = 'always_show_full_tweet_contents';

// An OpenAI-compatible or Anthropic endpoint the reader supplies themselves.
// Stored on the device like every other credential here; XTA calls it only
// when a feature asks it to.
const optionAiBaseUrl = 'ai.base_url';
const optionAiApiKey = 'ai.api_key';
const optionAiModel = 'ai.model';

/// xAI's OpenAI-compatible root. A Grok chip in AI settings fills this in
/// so the reader only pastes a key.
const aiGrokBaseUrl = 'https://api.x.ai/v1';
const aiGrokModel = 'grok-4';
const aiOpenAiBaseUrl = 'https://api.openai.com/v1';
const aiOpenAiModel = 'gpt-4o-mini';

const optionSubscriptionGroupsOrderByAscending =
    'subscription_groups.order_by.ascending';
const optionSubscriptionGroupsOrderByField =
    'subscription_groups.order_by.field';
// How many tile columns the groups board shows (2 = bold, 3 = compact).
const optionSubscriptionGroupsColumns = 'subscription_groups.columns';
// How the groups tab is laid out. This used to be implied by the sort order —
// picking "custom" silently switched to the list and back again — so the
// layout looked as though it could not be chosen.
const optionSubscriptionGroupsLayout = 'subscription_groups.layout';
const subscriptionGroupsLayoutBoard = 'board';
const subscriptionGroupsLayoutList = 'list';
const optionSubscriptionOrderByAscending = 'subscription.order_by.ascending';
const optionSubscriptionOrderCustom = 'subscription.order_by.custom';
const optionSubscriptionOrderByField = 'subscription.order_by.field';
const optionDefaultProfileTab = 'subscription.default_tab';

const optionThemeMode = 'theme.mode';
const optionThemeColor = 'theme.color';
const optionThemePreset = 'theme.preset';

const themePresetNone = 'none';
const themePresetFairyForest = 'fairy_forest';
const themePresetPitchBlack = 'pitch_black';
const themePresetXLookLight = 'x_look_light';
const themePresetXLookDim = 'x_look_dim';
const themePresetXLookLightsOut = 'x_look_lights_out';

/// X Look is the app's design language, chosen on two axes the way X itself
/// does it: how dark the background is, and which accent colour sits on it.
const optionXLookBackground = 'theme.x_look.background';
const optionXLookAccent = 'theme.x_look.accent';

/// Follows the system, so a light phone is not dragged into a black UI.
const xLookBackgroundSystem = 'system';
const xLookBackgroundLight = 'light';
const xLookBackgroundDim = 'dim';
const xLookBackgroundLightsOut = 'lights_out';

const xLookBackgrounds = [
  xLookBackgroundSystem,
  xLookBackgroundLight,
  xLookBackgroundDim,
  xLookBackgroundLightsOut,
];

const xLookAccentBlue = 'blue';

/// X's own accent palette. Every one of these is legible on all three
/// backgrounds, which is why they are taken as-is rather than generated.
const xLookAccents = <String, Color>{
  xLookAccentBlue: Color(0xFF1D9BF0),
  'yellow': Color(0xFFFFD400),
  'pink': Color(0xFFF91880),
  'purple': Color(0xFF7856FF),
  'orange': Color(0xFFFF7A00),
  'green': Color(0xFF00BA7C),
};
const optionThemeTrueBlack = 'theme.true_black';
const optionThemeTrueBlackTweetCards = 'theme.true_black_tweet_cards';
const optionShowNavigationLabels = 'theme.show_navigation_labels';
const optionUseAbsoluteTimestamp = "option.absolute_timestamp";

const themeColors = {
  'red': Colors.red,
  'orange': Colors.orange,
  'yellow': Colors.yellow,
  'green': Colors.green,
  'blue': Colors.blue,
  'indigo': Colors.indigo,
  'violet': Color.fromARGB(255, 128, 0, 255),
};

const optionTweetsHideSensitive = 'tweets.hide_sensitive';

/// Once true, sensitive-media gates stay open across sessions (Bluesky-style).
const optionAlwaysShowSensitiveMedia = 'tweets.always_show_sensitive_media';

const optionSavedShowAllTab = 'saved.show_all_tab';
const optionSavedShowUnfiledTab = 'saved.show_unfiled_tab';
const optionSavedShowFavoritesTab = 'saved.show_favorites_tab';
const optionSavedTabOrder = 'saved.tab_order';
const optionSavedFolderHintShown = 'saved.folder_hint_shown';
const optionLikedFirstToastShown = 'saved.liked_first_toast_shown';

/// Whether a plain tap on the bookmark files a post where the reader last chose
/// to, and which folder that is. Empty means unfiled.
const optionSavedStickyFolderEnabled = 'saved.sticky_folder_enabled';
const optionSavedStickyFolderId = 'saved.sticky_folder_id';

const optionUserTrendsLocations = 'trends.locations';

const optionNonConfirmationBiasMode = 'other.improve_non_confirmation_bias';
const optionTweetsShowSubscribeBadge = 'tweets.show_subscribe_badge';
// Double-tapping a post likes it. Off by default, and deliberately so: a
// double-tap handler makes every single tap wait to see whether a second one
// is coming, so a reader who does not want the gesture should not pay the
// delay for it.
const optionGestureDoubleTapLike = 'gestures.double_tap_like';

// The TradingView chart on a ticker screen. It is the one thing XTA loads
// from outside X, so it is named plainly and can be switched off; the posts
// about the ticker do not depend on it.
const optionTickerChart = 'other.ticker_chart';

const optionZenMode = 'other.zen_mode';
const optionZenModePageCap = 'other.zen_mode_page_cap';

/// Hide engagement counts (likes, reposts, views, replies) without zen's other caps.
const optionCalmMode = 'other.calm_mode';

/// CSV of language prefixes the reader wants (`en,fr,ja`).
const optionFeedLanguages = 'feed.languages';

/// `off` / `hide` / `fold` — what to do with posts outside [optionFeedLanguages].
const optionFeedLanguageAction = 'feed.language_action';

/// CSV of group ids pinned as deck columns on wide screens.
const optionDeckGroupIds = 'home.deck_group_ids';
const optionFeedReadingPosition = 'feed.reading_position';

/// When For You last cached a first page (ISO-8601). Compared to
/// `feed_read_position.updated_at` for the home-strip unread dot.
const optionForYouNewestCachedAt = 'feed.for_you_newest_cached_at';
// Catch-up ("finish the feed") mode is per feed: the group id is appended to
// this prefix by `feedCatchUpModeKey`. Off unless a feed opts in.
const optionFeedCatchUpModePrefix = 'feed.catch_up_mode.';
// Global defaults for feeds; a group can override each per-feed (null override
// = follow these).
const optionGlobalIncludeReplies = 'feed.global_include_replies';
const optionGlobalIncludeRetweets = 'feed.global_include_retweets';
// Show replies under an opened post as a nested, indented tree.
const optionThreadedReplies = 'tweets.threaded_replies';

/// Whether a run of consecutive reposts collapses into one row. On by default,
/// which is how the timeline has always behaved; off puts every repost back in
/// the timeline as an ordinary post, so the row need not be expanded run after
/// run for a reader who never wanted it grouped.
const optionFeedCollapseBoosts = 'feed.collapse_boosts';

/// One-shot: readers who inherited the old "collapse on" default get full
/// timeline reposts once, then the Posts setting owns the choice.
const optionFeedCollapseBoostsDefaultOffMigrated =
    'feed.collapse_boosts_default_off_v1';
const optionMediaGridLayout = 'media.grid_layout';

const mediaGridLayoutMasonry = 'masonry';
const mediaGridLayoutFeed = 'feed';
const mediaGridLayoutTwoColumns = 'two_columns';

// Per-group content filter (custom feed mode)
const contentFilterSfw = 'sfw';
const contentFilterDefault = 'default';
const contentFilterNsfw = 'nsfw';

// How many posts per author survive a feed page in zen mode
const zenModeMaxTweetsPerAuthor = 4;

// Selectable values for the zen-mode page cap (pages per feed session)
const zenModePageCapChoices = [3, 5, 10, 20];

// How many extra pages an initial feed load may fetch per chunk to close the
// gap between freshly fetched posts and the previously stored ones
const maxFeedGapFillPages = 4;

// How many stored chunk rows a cache read decodes, newest first. Each row is a
// whole page of chains and rows accumulate until the 7-day purge, so reading
// them all decoded tens of MB of JSON on the UI isolate before the feed had
// painted anything -- for posts far below where anyone scrolls.
const maxCachedChunkRows = 8;

/// How far a timeline [ListView.builder] builds off-screen.
///
/// Matches the X feed. Video tiles are visibility-gated, so this window only
/// decodes images and lays out cards — not native players.
const double kFeedListCacheExtent = 600;

// How many timeline_cache rows survive the startup purge, newest first.
//
// The 7-day purge bounds how *old* a row gets, not how many there are: a row is
// written for every thread opened and every profile visited, each holding a
// whole first page of chains. A week of ordinary reading is thousands of them,
// and none is ever read again once the reader has moved on.
const maxTimelineCacheRows = 300;

// Reading position ("You're caught up"): how close to the top counts as
// having read everything, and how many frames the divider restore may take.
const feedReadPositionTopThresholdPx = 8.0;
const maxCaughtUpRestoreFrames = 30;

final Map<String, String> userAgentHeader = {
  'user-agent':
      "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.3",
  "Pragma": "no-cache",
  "Cache-Control": "no-cache",
  // "If-Modified-Since": "Sat, 1 Jan 2000 00:00:00 GMT",
};

const String bearerToken =
    "Bearer AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA";

// The guest (logged-out) path uses a different bearer from the authenticated
// one above. It was written out twice inline in client_unauthenticated.dart.
const String guestBearerToken =
    "Bearer AAAAAAAAAAAAAAAAAAAAAGHtAgAAAAAA%2Bx7ILXNILCqkSGIzy6faIHZ9s3Q%3DQy97w6SIrzE7lQwPJEYQBsArEE2fC25caFwRBvAGi456G09vGR";

// How long a derived x-client-transaction-id key is trusted. X rotates the home
// page and on-demand bundle it is built from, and a stale key makes every
// request 404 — indistinguishable from a rotated query id.
const Duration transactionKeyLifetime = Duration(hours: 6);

// How long to wait before re-deriving after a failure. Deriving costs two
// requests to x.com, so a persistent failure (X reshaping its HTML) would
// otherwise turn every single app request into two more.
const Duration transactionKeyRetryCooldown = Duration(seconds: 30);

// Account selection strategy: cooldowns and flagging thresholds.
const Duration rateLimitFallback = Duration(minutes: 15);
const Duration notFoundCooldown = Duration(hours: 6);
const int notFoundThreshold = 3;

// Endpoint registry: repairs rotated GraphQL query ids without a new release.
const optionEndpointRegistryEnabled = 'api.endpoint_registry.enabled';
const optionEndpointRegistryUrl = 'api.endpoint_registry.url';
const optionEndpointRegistryCache = 'api.endpoint_registry.cache';
const optionEndpointRegistryFetchedAt = 'api.endpoint_registry.fetched_at';

/// The branch the published documents are read from.
///
/// Named once: both registries pointed at `master`, which this fork does not
/// have, so every fetch 404'd and neither had ever been read.
const githubPublishBranch = 'main';

const defaultEndpointRegistryUrl =
    'https://raw.githubusercontent.com/$githubRepo/$githubPublishBranch/endpoints.json';
const Duration endpointRegistryTimeout = Duration(seconds: 10);

// Plugin catalogue: decides which plugins the store offers, published the same
// way the endpoint registry is. It can only narrow the built-in list — a
// plugin's code is compiled in — and never withdraws one already installed.
const optionPluginCatalogueUrl = 'plugin.catalogue.url';
const optionPluginCatalogueCache = 'plugin.catalogue.cache';
const optionPluginCatalogueFetchedAt = 'plugin.catalogue.fetched_at';
const defaultPluginCatalogueUrl =
    'https://raw.githubusercontent.com/$githubRepo/$githubPublishBranch/plugins.json';

// Offline read cache for threads and profile timelines (feed_group_chunk covers
// group feeds). Short windows: these are re-read within a session far more
// often than they change, and a stale entry is still served when a request
// fails outright.
const Duration threadCacheMaxAge = Duration(minutes: 10);
const Duration profileCacheMaxAge = Duration(minutes: 15);

// WebDAV sync of the local backup payload to a server the reader controls.
const optionWebDavUrl = 'sync.webdav.url';
const optionWebDavUsername = 'sync.webdav.username';
const optionWebDavPassword = 'sync.webdav.password';

/// Preference keys that must never leave the device in an export, a backup or
/// a crash report.
///
/// Declared as a set rather than removed one-by-one at the call site: the
/// redaction used to name two keys, and every credential added after it was
/// written -- six of them, across five plugins -- was exported verbatim in a
/// file readers share and upload. [isSecretPrefKey] also matches by suffix, so
/// the next plugin is covered before anyone remembers to come back here.
const secretPrefKeys = {
  optionCrashGithubToken,
  optionWebDavPassword,
  optionAiApiKey,
  optionPluginDeepmarksApiKey,
  optionPluginDeepmarksSecretKey,
  optionPluginImmichApiKey,
  optionPluginKarakeepApiKey,
  optionPluginRedditClientId,
  optionPluginRedditRefreshToken,
  optionPluginThreadsApiToken,
  optionPluginThreadsDirectCookies,
  optionPluginThreadsDirectBearer,
  optionPluginPixivRefreshToken,
  optionPluginPixivAccessToken,
  optionPluginEhCookies,
  optionPluginTiktokCookies,
  optionPluginInstagramCookies,
};

/// The declared keys, plus anything shaped like a credential.
bool isSecretPrefKey(String key) =>
    secretPrefKeys.contains(key) ||
    key.endsWith('api_key') ||
    key.endsWith('secret_key') ||
    key.endsWith('_token') ||
    key.endsWith('password');

// Off by default: the backup payload carries X session tokens, and uploading
// those anywhere has to be a deliberate choice rather than a default.
const optionWebDavIncludeAccounts = 'sync.webdav.include_accounts';
const optionWebDavLastSyncAt = 'sync.webdav.last_sync_at';

const routeHome = '/';
const routeGroup = '/group';
const routeProfile = '/profile';
const routeSearch = '/search';
const routeSavedFolders = '/saved/folders';
const routeSettings = '/settings';
const routeSettingsExport = '/settings/export';
const routeSettingsHome = '/settings/home';
const routeQuotes = '/quotes';
const routeTicker = '/ticker';
const routeStatus = '/status';
const routeAntennas = '/antennas';
const routeAntennaFeed = '/antenna';
const routeDeck = '/deck';
