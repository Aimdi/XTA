import 'package:flutter/material.dart';

const optionDisableAnimations = 'accessibility.disable_animations';
const optionTextScaleFactor = 'accessibility.text_scale_factor';

const optionWizardCompleted = 'option.wizard_completed';

const optionDisableScreenshots = 'disable_screenshots';
const optionHelloLastBuild = 'hello.last_build';

const optionHomePages = 'home.pages';
const optionHomeInitialTab = 'home.initial_tab';
const optionHomeDefaultFeedTab = 'home.default_feed_tab';
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
const optionMediaAllowBackgroundPlayOtherApps = 'media.allow_background_play.other_apps';
const optionMediaVideoPrefetchSeconds = 'media.video_prefetch_seconds';

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

const pluginIdKarakeep = 'karakeep';
const optionPluginKarakeepEnabled = 'plugin.karakeep.enabled';
const optionPluginKarakeepServerUrl = 'plugin.karakeep.server_url';
const optionPluginKarakeepApiKey = 'plugin.karakeep.api_key';
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

const pluginIdSubstack = 'substack';
const optionPluginSubstackEnabled = 'plugin.substack.enabled';
const optionPluginSubstackShowTab = 'plugin.substack.show_tab';
const optionPluginSubstackPublications = 'plugin.substack.publications';
const optionPluginSubstackReadIds = 'plugin.substack.read_ids';
const substackFeedPageSize = 8;
const substackReadIdsCap = 400;

const optionShouldCheckForUpdates = 'should_check_for_updates';

/// Marks that the update check has been turned off once for this fork. Without
/// it the change would only reach installs that had never stored the old
/// default — which is not the installs that were being interrupted.
const optionUpdateCheckReset = 'should_check_for_updates.reset';
// This fork's own repository. Releases and crash reports belong here, not on
// upstream teskann/quax, whose versions this fork never matches.
const githubRepo = 'Aimdi/QuaX-fix';
const optionConfirmClose = 'confirm_close';
const optionOpenLinksInEmbeddedBrowser = 'open_links_in_embedded_browser';
const optionShareBaseUrl = 'share_base_url';

const optionCrashReportsEnabled = 'crash.reports_enabled';
const optionCrashGithubRepo = 'crash.github_repo';
const optionCrashGithubToken = 'crash.github_token';
const defaultCrashGithubRepo = githubRepo;

const optionDisableWarningsForUnrelatedPostsInFeed = 'disable_warnings_for_unrelated_posts_in_feed';

const alwaysShowFullTweetContents = 'always_show_full_tweet_contents';

// An OpenAI-compatible or Anthropic endpoint the reader supplies themselves.
// Stored on the device like every other credential here; QuaX calls it only
// when a feature asks it to.
const optionAiBaseUrl = 'ai.base_url';
const optionAiApiKey = 'ai.api_key';
const optionAiModel = 'ai.model';

const optionSubscriptionGroupsOrderByAscending = 'subscription_groups.order_by.ascending';
const optionSubscriptionGroupsOrderByField = 'subscription_groups.order_by.field';
// How many tile columns the groups board shows (2 = bold, 3 = compact).
const optionSubscriptionGroupsColumns = 'subscription_groups.columns';
// How the groups tab is laid out. Previously implied by the sort order,
// which made the layout seem unchangeable.
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

const optionSavedShowAllTab = 'saved.show_all_tab';
const optionSavedShowUnfiledTab = 'saved.show_unfiled_tab';
const optionSavedShowFavoritesTab = 'saved.show_favorites_tab';
const optionSavedTabOrder = 'saved.tab_order';
const optionSavedFolderHintShown = 'saved.folder_hint_shown';
const optionLikedFirstToastShown = 'saved.liked_first_toast_shown';

const optionUserTrendsLocations = 'trends.locations';

const optionNonConfirmationBiasMode = 'other.improve_non_confirmation_bias';
const optionTweetsShowSubscribeBadge = 'tweets.show_subscribe_badge';
// Double-tapping a post likes it. Off by default, and deliberately so: a
// double-tap handler makes every single tap wait to see whether a second one
// is coming, so a reader who does not want the gesture should not pay the
// delay for it.
const optionGestureDoubleTapLike = 'gestures.double_tap_like';

// The TradingView chart on a ticker screen. It is the one thing QuaX loads
// from outside X, so it is named plainly and can be switched off; the posts
// about the ticker do not depend on it.
const optionTickerChart = 'other.ticker_chart';

const optionZenMode = 'other.zen_mode';
const optionZenModePageCap = 'other.zen_mode_page_cap';
const optionFeedReadingPosition = 'feed.reading_position';
// Global defaults for feeds; a group can override each per-feed (null override
// = follow these).
const optionGlobalIncludeReplies = 'feed.global_include_replies';
const optionGlobalIncludeRetweets = 'feed.global_include_retweets';
// Show replies under an opened post as a nested, indented tree.
const optionThreadedReplies = 'tweets.threaded_replies';
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

// Reading position ("You're caught up"): how close to the top counts as
// having read everything, and how many frames the divider restore may take.
const feedReadPositionTopThresholdPx = 8.0;
const maxCaughtUpRestoreFrames = 30;


final Map<String, String> userAgentHeader = {
  'user-agent':
      "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.3",
  "Pragma": "no-cache",
  "Cache-Control": "no-cache"
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
const defaultEndpointRegistryUrl =
    'https://raw.githubusercontent.com/$githubRepo/master/endpoints.json';
const Duration endpointRegistryTimeout = Duration(seconds: 10);

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
