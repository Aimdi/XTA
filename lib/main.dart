import 'dart:async';
import 'dart:convert';
import 'dart:developer';
import 'dart:io';

import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/foundation.dart'
    show LicenseEntryWithLineBreaks, LicenseRegistry, kReleaseMode;
import 'package:flutter/cupertino.dart' show CupertinoLocalizations;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_portal/flutter_portal.dart';
import 'package:media_kit/media_kit.dart';
import 'package:xta/client/accounts.dart';
import 'package:xta/client/endpoint_overrides.dart';
import 'package:xta/client/login_webview.dart';
import 'package:xta/client/headers.dart';

import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/feed_session_cache.dart';
import 'package:xta/tweet/video_controller_pool.dart';
import 'package:xta/group/combined_groups.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_unread_store.dart';
import 'package:xta/group/group_screen.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/chrome_avatar.dart';
import 'package:xta/home/home_account_filter.dart';
import 'package:xta/home/home_model.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/antenna/antenna_feed_screen.dart';
import 'package:xta/antenna/antenna_model.dart';
import 'package:xta/antenna/antenna_screen.dart';
import 'package:xta/home/deck_screen.dart';
import 'package:xta/import_data_model.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_store.dart';
import 'package:xta/plugins/tiktok/tiktok_client.dart';
import 'package:xta/plugins/tiktok/tiktok_store.dart';
import 'package:xta/plugins/instagram/instagram_client.dart';
import 'package:xta/plugins/instagram/instagram_store.dart';
import 'package:xta/plugins/threads/threads_api.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_likes_store.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_folders_screen.dart';
import 'package:xta/saved/saved_tweet_folder_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/search/search.dart';
import 'package:xta/search/search_model.dart';
import 'package:xta/search/search_scope.dart';
import 'package:xta/settings/_data.dart';
import 'package:xta/settings/_home.dart';
import 'package:xta/settings/settings.dart';
import 'package:xta/settings/settings_export_screen.dart';
import 'package:xta/status.dart';
import 'package:xta/tweet/quotes_screen.dart';
import 'package:xta/tweet/ticker_screen.dart';
import 'package:xta/subscriptions/_import_list.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/trends/trends_model.dart';
import 'package:xta/tweet/_video.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/utils/crash_reporter.dart';
import 'package:xta/utils/updates.dart';
import 'package:logging/logging.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/utils/urls.dart';
import 'package:secure_content/secure_content.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:timeago/timeago.dart' as timeago;
import 'package:app_links/app_links.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:xta/plugins/karakeep/karakeep_client.dart';
import 'package:xta/plugins/deepmarks/deepmarks_client.dart';
import 'package:xta/plugins/immich/immich_client.dart';
import 'package:xta/plugins/reddit/reddit_auth.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_gallery_loader.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/plugins/reddit/reddit_subreddit_avatar.dart';
import 'package:xta/plugins/reddit/reddit_votes_store.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';
import 'package:xta/tweet/ticker/ticker_quote_cache.dart';
import 'package:xta/media/xta_audio_handler.dart';
import 'package:xta/plugins/substack/podcast_store.dart';
import 'package:xta/speech/speech_bar.dart';
import 'package:xta/speech/speech_store.dart';
import 'package:xta/utils/media_quality.dart';

Future checkForUpdates(BuildContext context) async {
  Logger.root.info('Checking for updates');

  PackageInfo packageInfo = await PackageInfo.fromPlatform();
  final client = HttpClient();
  client.userAgent =
      "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36";

  final request = await client.getUrl(
    Uri.parse('https://api.github.com/repos/$githubRepo/releases/latest'),
  );
  final response = await request.close();

  if (response.statusCode == 200) {
    final contentAsString = await utf8.decodeStream(response);
    final Map<dynamic, dynamic> map = json.decode(contentAsString);
    final latestTag = map['tag_name'] as String?;
    if (latestTag != null) {
      if (isUpdateAvailable(
        latestTag: latestTag,
        installedTag: buildReleaseTag,
        installedVersion: packageInfo.version,
      )) {
        if (!context.mounted) {
          return;
        }
        await showDialog(
          context: context,
          builder: (BuildContext context) {
            return AlertDialog(
              title: Text(L10n.of(context).an_update_for_fritter_is_available),
              content: Text(L10n.of(context).view_version_on_github(latestTag)),
              actions: [
                TextButton(
                  child: Text(L10n.of(context).dismiss),
                  onPressed: () => Navigator.of(context).pop(),
                ),
                TextButton(
                  child: Text(L10n.of(context).view_on_github),
                  onPressed: () async {
                    await openUri(context, map['html_url']);
                    if (context.mounted) {
                      Navigator.of(context).pop();
                    }
                  },
                ),
              ],
            );
          },
        );
      }
    }
  }
}

Future checkForAccounts(BuildContext context) async {
  Logger.root.info('Checking for accounts');

  final accounts = await getAccounts();
  if (accounts.isEmpty && context.mounted) {
    await showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text("⚠️ ${L10n.of(context).not_logged_in}"),
          content: Text(L10n.of(context).sign_in_why_needed),
          actions: [
            // A way out that is not signing in. The app does try as a guest, so
            // dismissing this is a real choice rather than a refusal to start.
            TextButton(
              child: Text(L10n.of(context).cancel),
              onPressed: () => Navigator.of(context).pop(),
            ),
            TextButton(
              child: Text(L10n.of(context).import_backup),
              onPressed: () async {
                await importBackup(context);
                if (context.mounted) {
                  Navigator.of(context).pop();
                }
              },
            ),
            TextButton(
              child: Text(L10n.of(context).login),
              onPressed: () {
                Navigator.of(context).pop();
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => const TwitterLoginWebview(),
                  ),
                );
              },
            ),
          ],
        );
      },
    );
  }
}

class UnableToCheckForUpdatesException {
  final String body;

  UnableToCheckForUpdatesException(this.body);

  @override
  String toString() {
    return 'Unable to check for updates: {body: $body}';
  }
}

void setTimeagoLocales() {
  timeago.setLocaleMessages('ar', timeago.ArMessages());
  timeago.setLocaleMessages('az', timeago.AzMessages());
  timeago.setLocaleMessages('ca', timeago.CaMessages());
  timeago.setLocaleMessages('cs', timeago.CsMessages());
  timeago.setLocaleMessages('da', timeago.DaMessages());
  timeago.setLocaleMessages('de', timeago.DeMessages());
  timeago.setLocaleMessages('dv', timeago.DvMessages());
  timeago.setLocaleMessages('en', timeago.EnMessages());
  timeago.setLocaleMessages('es', timeago.EsMessages());
  timeago.setLocaleMessages('fa', timeago.FaMessages());
  timeago.setLocaleMessages('fr', timeago.FrMessages());
  timeago.setLocaleMessages('gr', timeago.GrMessages());
  timeago.setLocaleMessages('he', timeago.HeMessages());
  timeago.setLocaleMessages('he', timeago.HeMessages());
  timeago.setLocaleMessages('hi', timeago.HiMessages());
  timeago.setLocaleMessages('id', timeago.IdMessages());
  timeago.setLocaleMessages('it', timeago.ItMessages());
  timeago.setLocaleMessages('ja', timeago.JaMessages());
  timeago.setLocaleMessages('km', timeago.KmMessages());
  timeago.setLocaleMessages('ko', timeago.KoMessages());
  timeago.setLocaleMessages('ku', timeago.KuMessages());
  timeago.setLocaleMessages('mn', timeago.MnMessages());
  timeago.setLocaleMessages('ms_MY', timeago.MsMyMessages());
  timeago.setLocaleMessages('nb_NO', timeago.NbNoMessages());
  timeago.setLocaleMessages('nl', timeago.NlMessages());
  timeago.setLocaleMessages('nn_NO', timeago.NnNoMessages());
  timeago.setLocaleMessages('pl', timeago.PlMessages());
  timeago.setLocaleMessages('pt_BR', timeago.PtBrMessages());
  timeago.setLocaleMessages('ro', timeago.RoMessages());
  timeago.setLocaleMessages('ru', timeago.RuMessages());
  timeago.setLocaleMessages('sv', timeago.SvMessages());
  timeago.setLocaleMessages('ta', timeago.TaMessages());
  timeago.setLocaleMessages('th', timeago.ThMessages());
  timeago.setLocaleMessages('tr', timeago.TrMessages());
  timeago.setLocaleMessages('uk', timeago.UkMessages());
  timeago.setLocaleMessages('vi', timeago.ViMessages());
  timeago.setLocaleMessages('zh_CN', timeago.ZhCnMessages());
  timeago.setLocaleMessages('zh', timeago.ZhMessages());

  // The X-style compact stamps ("5m") the timeline uses. Only the languages
  // timeago ships short forms for; the rest keep their long form, which
  // createCompactDate resolves through this same registry.
  _setCompactLocale('ar', timeago.ArShortMessages());
  _setCompactLocale('az', timeago.AzShortMessages());
  _setCompactLocale('ca', timeago.CaShortMessages());
  _setCompactLocale('cs', timeago.CsShortMessages());
  _setCompactLocale('da', timeago.DaShortMessages());
  _setCompactLocale('de', timeago.DeShortMessages());
  _setCompactLocale('dv', timeago.DvShortMessages());
  _setCompactLocale('en', timeago.EnShortMessages());
  _setCompactLocale('es', timeago.EsShortMessages());
  _setCompactLocale('fr', timeago.FrShortMessages());
  _setCompactLocale('gr', timeago.GrShortMessages());
  _setCompactLocale('he', timeago.HeShortMessages());
  _setCompactLocale('hi', timeago.HiShortMessages());
  _setCompactLocale('id', timeago.IdShortMessages());
  _setCompactLocale('it', timeago.ItShortMessages());
  _setCompactLocale('km', timeago.KmShortMessages());
  _setCompactLocale('ku', timeago.KuShortMessages());
  _setCompactLocale('mn', timeago.MnShortMessages());
  _setCompactLocale('nl', timeago.NlShortMessages());
  _setCompactLocale('ro', timeago.RoShortMessages());
  _setCompactLocale('ru', timeago.RuShortMessages());
  _setCompactLocale('sv', timeago.SvShortMessages());
  _setCompactLocale('th', timeago.ThShortMessages());
  _setCompactLocale('tr', timeago.TrShortMessages());
  _setCompactLocale('uk', timeago.UkShortMessages());
  _setCompactLocale('vi', timeago.ViShortMessages());
}

void _setCompactLocale(String locale, timeago.LookupMessages messages) {
  timeago.setLocaleMessages('${locale}_short', messages);
  compactDateLocales.add(locale);
}

// One-time split of the former single "media size" pref into separate image and
// video quality settings, plus a data-saver toggle for its old "disabled" value.
Future<void> _migrateMediaQualityPrefs(BasePrefService prefs) async {
  if (prefs.get<bool>(optionMediaQualitySplitMigrated) ?? false) {
    return;
  }

  final previous = prefs.get<String>(optionImageQuality);
  final disabled = previous == 'disabled';
  // The old "disabled" value carried no real quality, so fall back to Maximum.
  final quality = disabled
      ? MediaQuality.large.stored
      : (previous ?? MediaQuality.medium.stored);

  await prefs.set(optionMediaDisableAutoload, disabled);
  await prefs.set(optionImageQuality, quality);
  await prefs.set(optionMediaVideoQuality, quality);
  await prefs.set(optionMediaQualitySplitMigrated, true);
}

/// Flip the old default so consecutive reposts fill the timeline as full posts.
/// Readers who already chose the Posts setting after this ship keep that choice.
Future<void> _migrateCollapseBoostsDefaultOff(BasePrefService prefs) async {
  if (prefs.get<bool>(optionFeedCollapseBoostsDefaultOffMigrated) ?? false) {
    return;
  }
  await prefs.set(optionFeedCollapseBoosts, false);
  await prefs.set(optionFeedCollapseBoostsDefaultOffMigrated, true);
}

/// The database migration, run once however many callers ask for it.
///
/// It ends by purging week-old rows from the largest tables, so letting both
/// `main` and [DefaultPage] run it meant paying for that twice per launch.
/// Sharing the future keeps both properties: the models see a migrated
/// database, and a failure still reaches the screen.
Future<bool>? _databaseMigration;

Future<bool> migrateDatabase() => _databaseMigration ??= Repository().migrate();

/// The delegates the app runs with, named so a test can pump the same set.
///
/// The two fallbacks come last: [Localizations] uses the first delegate that
/// supports each type, so they answer only where the global ones do not.
const List<LocalizationsDelegate<dynamic>> xtaLocalizationsDelegates = [
  L10n.delegate,
  GlobalMaterialLocalizations.delegate,
  GlobalWidgetsLocalizations.delegate,
  GlobalCupertinoLocalizations.delegate,
  _EnglishMaterialFallback(),
  _EnglishCupertinoFallback(),
];

/// Supplies Material/Cupertino strings for a locale Flutter has none for.
///
/// XTA translates its own UI into more locales than `flutter_localizations`
/// ships: there is no `material_eo.arb`, so selecting Esperanto left
/// `MaterialLocalizations.of` with nothing to return and every Scaffold threw
/// "No MaterialLocalizations found". The app's own strings stay translated;
/// only the framework's own widget strings fall back to English.
class _EnglishMaterialFallback
    extends LocalizationsDelegate<MaterialLocalizations> {
  const _EnglishMaterialFallback();

  @override
  bool isSupported(Locale locale) => true;

  @override
  Future<MaterialLocalizations> load(Locale locale) =>
      GlobalMaterialLocalizations.delegate.load(const Locale('en'));

  @override
  bool shouldReload(_EnglishMaterialFallback old) => false;
}

class _EnglishCupertinoFallback
    extends LocalizationsDelegate<CupertinoLocalizations> {
  const _EnglishCupertinoFallback();

  @override
  bool isSupported(Locale locale) => true;

  @override
  Future<CupertinoLocalizations> load(Locale locale) =>
      GlobalCupertinoLocalizations.delegate.load(const Locale('en'));

  @override
  bool shouldReload(_EnglishCupertinoFallback old) => false;
}

Future<void> main() async {
  // The listener below hands every record to dart:developer, and the client logs
  // one line per request, so a release build paid for the whole session's
  // traffic in log records. Warnings and errors still come through, and the
  // crash reporter hooks FlutterError rather than this, so it is unaffected.
  Logger.root.level = kReleaseMode ? Level.WARNING : Level.INFO;

  Logger.root.onRecord.listen((event) async {
    log(event.message, error: event.error, stackTrace: event.stackTrace);
  });

  if (Platform.isLinux) {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  }

  WidgetsFlutterBinding.ensureInitialized();

  // Flutter's 100 MiB default is a lot of decoded bitmaps next to a video
  // player. Mixed plugin feeds (Substack covers, Reddit, Bluesky) evicted
  // tiles at 64 MiB and re-decoded them on every scroll-back; 96 MiB holds
  // a few more screenfuls without the old 100 MiB default.
  PaintingBinding.instance.imageCache.maximumSizeBytes = 96 * 1024 * 1024;

  // The bundled Inter font ships under the SIL Open Font License, which
  // requires the licence to travel with the software.
  LicenseRegistry.addLicense(() async* {
    final license = await rootBundle.loadString('assets/fonts/Inter-OFL.txt');
    yield LicenseEntryWithLineBreaks(const ['Inter'], license);
  });

  // Neither belongs in front of the first frame. MediaKit is dlopen'ing
  // libmpv — it is initialised in the post-frame callback below. The audio
  // service is an Android service bind nothing on the launch path reads:
  // every consumer is `audioHandler?.`-guarded, so a handler that arrives a
  // moment later is already a supported state. Both start after first paint.
  setTimeagoLocales();

  final prefService = await PrefServiceShared.init(
    prefix: 'pref_',
    defaults: {
      optionConfirmClose: true,
      optionDisableAnimations: false,
      optionGestureDoubleTapLike: false,
      optionTickerChart: true,
      optionTextScaleFactor: 1.0,
      optionDisableScreenshots: false,
      optionDownloadPath: '',
      optionDownloadTreeUri: '',
      optionDownloadType: optionDownloadTypeAsk,
      optionHomePages: defaultHomePages.map((e) => e.id).toList(),
      optionLocale: optionLocaleDefault,
      optionHomeInitialTab: 'feed',
      optionHomeDefaultFeedTab: feedTabs[0].id.name,
      optionHomeFeedDisabledAccountIds: '[]',
      optionChromeAvatarRevision: 0,
      optionImageQuality: MediaQuality.medium.stored,
      optionMediaVideoQuality: MediaQuality.medium.stored,
      optionMediaDisableAutoload: false,
      optionMediaQualitySplitMigrated: false,
      optionMediaGridColumns: 3,
      optionMediaDefaultMute: true,
      optionMediaDefaultLoop: false,
      optionMediaDefaultAutoPlay: false,
      optionMediaDirectHardwareDecoding: false,
      optionMediaBackgroundPlayback: true,
      optionMediaAllowBackgroundPlayOtherApps: false,
      optionMediaVideoPrefetchSeconds: 0,
      optionNonConfirmationBiasMode: false,
      optionTweetsShowSubscribeBadge: true,
      optionZenMode: false,
      optionZenModePageCap: 5,
      optionCalmMode: false,
      optionFeedLanguages: '',
      optionFeedLanguageAction: 'off',
      optionDeckGroupIds: '',
      optionFeedReadingPosition: true,
      optionGlobalIncludeReplies: true,
      optionGlobalIncludeRetweets: true,
      optionThreadedReplies: true,
      optionFeedCollapseBoosts: false,
      optionFeedCollapseBoostsDefaultOffMigrated: false,
      optionMediaGridLayout: mediaGridLayoutMasonry,
      // Off by default in this fork. It checks this repository, not upstream,
      // and this repository publishes a build whenever something is fixed — so
      // the check was firing constantly and reading as an upstream release
      // notice. The switch in Settings › General still turns it back on.
      optionShouldCheckForUpdates: false,
      optionUpdateCheckReset: false,
      optionEndpointRegistryEnabled: true,
      optionEndpointRegistryUrl: defaultEndpointRegistryUrl,
      optionEndpointRegistryCache: '',
      optionEndpointRegistryFetchedAt: '',
      optionPluginCatalogueUrl: defaultPluginCatalogueUrl,
      optionPluginCatalogueCache: '',
      optionPluginCatalogueFetchedAt: '',
      optionWebDavUrl: '',
      optionWebDavUsername: '',
      optionWebDavPassword: '',
      optionWebDavIncludeAccounts: false,
      optionWebDavLastSyncAt: '',
      optionOpenLinksInEmbeddedBrowser: true,
      optionExternalBrowser: '',
      optionEmbeddedBrowserReset: false,
      optionCrashReportsEnabled: false,
      optionCrashGithubRepo: defaultCrashGithubRepo,
      optionCrashGithubToken: '',
      optionPluginDeepmarksEnabled: false,
      optionPluginDeepmarksApiBase: '',
      optionPluginDeepmarksApiKey: '',
      optionPluginDeepmarksSecretKey: '',
      optionPluginImmichEnabled: false,
      optionPluginImmichServerUrl: '',
      optionPluginImmichApiKey: '',
      optionPluginImmichAlbumPerFolder: true,
      optionPluginImmichIncludeVideos: true,
      optionPluginKarakeepEnabled: false,
      optionPluginKarakeepServerUrl: '',
      optionPluginKarakeepApiKey: '',
      optionSeededPluginTabs: <String>[],
      optionPluginRedditEnabled: false,
      optionPluginRedditClientId: '',
      optionPluginRedditInHomeFeed: false,
      optionPluginThreadsInHomeFeed: false,
      optionPluginThreadsUseSessionApis: false,
      optionPluginBlueskyInHomeFeed: false,
      optionPluginMastodonInHomeFeed: false,
      optionPluginRedditShowTab: false,
      optionPluginRedditSort: redditSortHot,
      optionPluginRedditTimeFilter: redditTimeFilterDay,
      optionPluginRedditFeedMode: redditFeedModeFollowing,
      optionPluginRedditNsfwMode: redditNsfwModeTap,
      optionPluginRedditSavedPosts: '[]',
      optionPluginRedditSource: redditSourceAuto,
      optionPluginRedditSubreddits: '[]',
      optionPluginRedditRefreshToken: '',
      optionPluginStocksEnabled: false,
      optionPluginStocksShowTab: true,
      optionTtsEngine: '',
      optionTtsVoiceName: '',
      optionTtsVoiceLocale: '',
      optionTtsRate: 0.45,
      optionPluginSubstackEnabled: false,
      optionPluginSubstackShowTab: true,
      optionPluginSubstackPublications: '[]',
      optionPluginSubstackReadIds: '[]',
      optionPluginSubstackLikedPosts: '[]',
      optionPluginSubstackSavedPosts: '[]',
      optionPluginSubstackPinnedPublications: '',
      optionPluginBlueskyEnabled: false,
      optionPluginBlueskyShowTab: true,
      optionPluginBlueskyInstance: kBlueskyDefaultAppView,
      optionPluginBlueskyLikedPosts: '[]',
      optionPluginBlueskySearchHistory: '[]',
      optionPluginMastodonEnabled: false,
      optionPluginMastodonShowTab: true,
      optionPluginMastodonInstance: '',
      optionPluginPixivEnabled: false,
      optionPluginPixivShowTab: true,
      optionPluginPixivRefreshToken: '',
      optionPluginPixivAccessToken: '',
      optionPluginPixivAccessExpiresAt: '',
      optionPluginPixivShowR18: false,
      optionPluginPixivUserId: 0,
      optionPluginPixivMutedAuthors: '[]',
      optionPluginPixivMutedTags: '[]',
      optionPluginPixivMutedIllusts: '[]',
      optionPluginPixivSearchHistory: '[]',
      optionPluginBooruEnabled: false,
      optionPluginBooruShowTab: true,
      optionPluginBooruEngine: 'danbooru',
      optionPluginBooruHost: 'https://danbooru.donmai.us',
      optionPluginBooruPreset: 'danbooru',
      optionPluginBooruLogin: '',
      optionPluginBooruApiKey: '',
      optionPluginBooruMaxRating: 'g',
      optionPluginBooruInHomeFeed: false,
      optionPluginBooruSearchHistory: '[]',
      optionPluginBooruMutedTags: '[]',
      optionPluginBooruCustomSites: '[]',
      optionPluginEhEnabled: false,
      optionPluginEhShowTab: true,
      optionPluginEhCookies: '',
      optionPluginEhUseExhentai: false,
      optionPluginEhCategories: '',
      optionPluginEhSearchHistory: '[]',
      optionPluginEhPreferJapanese: true,
      optionPluginEhKeepScreenOn: true,
      optionPluginTiktokEnabled: false,
      optionPluginTiktokShowTab: true,
      optionPluginTiktokCookies: '',
      optionPluginTiktokDeviceId: '',
      optionPluginTiktokSearchHistory: '[]',
      optionPluginTiktokLikedPosts: '[]',
      optionPluginTiktokPreferEmbed: false,
      optionPluginInstagramEnabled: false,
      optionPluginInstagramShowTab: true,
      optionPluginInstagramCookies: '',
      optionPluginInstagramSearchHistory: '[]',
      optionPluginInstagramLikedPosts: '[]',
      optionPluginThreadsDirectCookies: '',
      optionPluginThreadsDirectBearer: '',
      optionPluginThreadsDirectDeviceId: '',
      optionPluginThreadsGuestLsd: '',
      optionPluginThreadsGuestLsdAt: '',
      optionPluginThreadsLikedPosts: '[]',
      optionPluginThreadsSearchHistory: '[]',
      optionPluginStoreShowPrivate: false,
      optionSubscriptionGroupsOrderByAscending: true,
      optionDisableWarningsForUnrelatedPostsInFeed: false,
      // Reading is the whole point of the app, so posts are not clipped unless
      // the reader asks for the compact form in settings.
      alwaysShowFullTweetContents: true,
      optionSubscriptionGroupsOrderByField: 'name',
      optionSubscriptionGroupsColumns: 2,
      optionSubscriptionGroupsLayout: subscriptionGroupsLayoutBoard,
      optionAiBaseUrl: '',
      optionAiApiKey: '',
      optionAiModel: '',
      optionSubscriptionOrderByAscending: true,
      optionSubscriptionOrderByField: 'name',
      optionSubscriptionOrderCustom: '',
      optionThemeMode: 'system',
      optionThemeColor: 'accent',
      optionThemePreset: themePresetNone,
      optionXLookBackground: xLookBackgroundSystem,
      optionXLookAccent: xLookAccentBlue,
      optionThemeTrueBlack: true,
      optionThemeTrueBlackTweetCards: true,
      optionShowNavigationLabels: false,
      optionTweetsHideSensitive: true,
      optionAlwaysShowSensitiveMedia: false,
      optionSavedShowAllTab: true,
      optionSavedShowUnfiledTab: true,
      optionSavedShowFavoritesTab: true,
      optionSavedTabOrder: '',
      optionSavedFolderHintShown: false,
      optionLikedFirstToastShown: false,
      optionSavedStickyFolderEnabled: false,
      optionSavedStickyFolderId: '',
      optionUseAbsoluteTimestamp: false,
      optionDefaultProfileTab: profileTabs[0].id.name,
      optionUserTrendsLocations: jsonEncode({
        'active': {'name': 'Worldwide', 'woeid': 1},
        'locations': [
          {'name': 'Worldwide', 'woeid': 1},
        ],
      }),
    },
  );

  await _migrateMediaQualityPrefs(prefService);
  await _migrateCollapseBoostsDefaultOff(prefService);

  CrashReporter.install(prefService);

  // Apply the last known query ids before the first request goes out; the
  // network refresh runs unawaited so a slow or blocked fetch never delays
  // startup.
  final endpointRegistry = EndpointRegistry(prefService);
  endpointRegistry.applyCached();
  unawaited(endpointRegistry.refresh());

  // Deriving the transaction key costs two round trips to x.com and it is
  // triggered lazily by the first API call, so it used to sit in front of the
  // first feed fetch. Started here it overlaps the database work below
  // instead. The result is cached inside TwitterHeaders; this call only warms
  // it, and a failure is already rate-limited there, so it is swallowed rather
  // than left as an unhandled async error.
  unawaited(
    TwitterHeaders.getXClientTransactionIdHeader(
      Uri.parse('https://x.com/i/api/graphql/warmup'),
    ).catchError((Object _) => null),
  );

  try {
    // Run the migrations early, so models work. We also do this later on so we can display errors to the user
    try {
      await migrateDatabase();
    } catch (_) {
      // Ignore, as we'll catch it later instead
    }

    var importDataModel = ImportDataModel();

    var groupsModel = GroupsModel(prefService);
    await groupsModel.reloadGroups();

    var homeModel = HomeModel(prefService, groupsModel);
    var subscriptionsModel = SubscriptionsModel(prefService, groupsModel);

    var feedSessionCache = FeedSessionCache();
    // Registration order matters: invalidateAll must run before any
    // GroupFeedShell reload listener. Open feeds no longer remount on
    // membership change (they soft-update in place), but clearing the cache
    // first still keeps a later remount or revisit from reusing a controller
    // built for the old member set. LinkedHashMap iterates in insertion order,
    // and registering here (before any shell exists) guarantees we win.
    var groupUnreadStore = GroupUnreadStore(prefService);
    groupsModel.addReloadListener(
      'FeedSessionCache',
      feedSessionCache.invalidateAll,
    );
    groupsModel.addReloadListener('GroupUnreadStore', () {
      unawaited(groupUnreadStore.reload());
    });
    subscriptionsModel.addReloadListener(
      'FeedSessionCache',
      feedSessionCache.invalidateAll,
    );
    subscriptionsModel.addReloadListener('GroupUnreadStore', () {
      unawaited(groupUnreadStore.reload());
    });

    var trendLocationModel = UserTrendLocationModel(prefService);

    final deepmarksClient = DeepmarksClient();
    final immichClient = ImmichClient();
    final karakeepClient = KarakeepClient();
    final redditClient = RedditClient();
    final redditIcons = RedditIcons(redditClient);
    final redditGalleries = RedditGalleryLoader(redditClient);
    final redditAuth = RedditAuth();
    final redditSubreddits = RedditSubredditsStore(prefService);
    final redditVotes = RedditVotesStore();
    final redditSaved = RedditSavedStore(prefService);
    final redditFeed = RedditFeedStore(
      redditClient,
      redditSubreddits,
      prefService,
      auth: redditAuth,
    );
    final stocksWatchlist = StocksWatchlistStore();
    final tickerQuotes = TickerQuoteCache();
    final speech = SpeechStore();
    final podcast = PodcastStore();
    final substackClient = SubstackClient();
    final substackPublications = SubstackPublicationsStore(prefService);
    final substackRead = SubstackReadStore(prefService);
    final substackLikes = SubstackLikesStore(prefService);
    final substackSaved = SubstackSavedStore(prefService);
    final threadsClient = ThreadsClient();
    final threadsDirect = ThreadsDirectClient(prefService);
    final threadsApi = ThreadsApi();
    final threadsAccounts = ThreadsAccountsStore();
    final threadsLikes = ThreadsLikesStore(prefService);
    final threadsFeed = ThreadsFeedStore(
      threadsClient,
      threadsDirect,
      prefService,
      threadsAccounts,
    );
    final blueskyClient = BlueskyClient(
      resolveBaseUrl: () =>
          prefService.get<String>(optionPluginBlueskyInstance) ??
          kBlueskyDefaultAppView,
    );
    final blueskyAccounts = BlueskyAccountsStore();
    final blueskyLikes = BlueskyLikesStore(prefService);
    final blueskyFeed = BlueskyFeedStore(blueskyClient, blueskyAccounts);
    final mastodonClient = MastodonClient();
    final mastodonAccounts = MastodonAccountsStore();
    final mastodonFeed = MastodonFeedStore(
      mastodonClient,
      prefService,
      mastodonAccounts,
    );
    final mastodonExplore = MastodonExploreStore(mastodonClient, prefService);
    final mastodonLocal = MastodonLocalStore(mastodonClient, prefService);
    final mastodonFederated = MastodonFederatedStore(
      mastodonClient,
      prefService,
    );
    final pixivClient = PixivClient(prefService);
    final pixivMute = PixivMuteStore(prefService);
    final pixivSearchHistory = PixivSearchHistoryStore(prefService);
    final pixivBookmarks = PixivBookmarkStore();
    final pixivFeed = PixivFeedStore(pixivClient, filter: pixivMute.filter);
    final booruClient = BooruClient(prefService);
    final booruTags = BooruTagsStore();
    final booruMute = BooruMuteStore(prefService);
    final ehClient = EhClient(prefService);
    final ehFavorites = EhFavoritesStore();
    final ehHistory = EhHistoryStore();
    final tiktokClient = TikTokClient(prefService);
    final tiktokFollows = TikTokFollowsStore();
    final tiktokLikes = TikTokLikesStore(prefService);
    final tiktokSearchHistory = TikTokSearchHistoryStore(prefService);
    final tiktokFollowing = TikTokFollowingStore(tiktokClient, tiktokFollows);
    final instagramClient = InstagramClient(prefService);
    final instagramFollows = InstagramFollowsStore();
    final instagramLikes = InstagramLikesStore(prefService);
    final instagramSearchHistory = InstagramSearchHistoryStore(prefService);
    final instagramFollowing = InstagramFollowingStore(
      instagramClient,
      instagramFollows,
    );

    // Everything above only constructs; the reads all happen here. They were a
    // chain of awaits, each waiting on the last for no reason — none of them
    // depends on another's result — so the slowest used to be the sum rather
    // than the max.
    //
    // A disabled plugin's store is skipped entirely: it has no home tab and no
    // screen, so nothing can read it, and its screen loads the store itself on
    // mount if the plugin is turned on later.
    // The one read that cannot join them: it moves the followed subreddits out
    // of preferences and into the database, and the subscription list below
    // reads that table. Run in parallel, whether a subreddit could be added to
    // a group came down to which of the two finished first.
    if (prefService.get<bool>(optionPluginRedditEnabled) == true) {
      await redditSubreddits.load();
      unawaited(redditVotes.load());
      unawaited(redditSaved.load());
    }

    await Future.wait([
      homeModel.loadPages(),
      subscriptionsModel.reloadSubscriptions(),
      groupUnreadStore.reload(),
      if (prefService.get<bool>(optionPluginSubstackEnabled) == true) ...[
        substackPublications.load(),
        substackRead.load(),
        substackLikes.load(),
        substackSaved.load(),
      ],
      if (prefService.get<bool>(optionPluginThreadsEnabled) == true) ...[
        threadsAccounts.load(),
        threadsLikes.load(),
      ],
      if (prefService.get<bool>(optionPluginBlueskyEnabled) == true) ...[
        blueskyAccounts.load(),
        blueskyLikes.load(),
      ],
      if (prefService.get<bool>(optionPluginMastodonEnabled) == true)
        mastodonAccounts.load(),
    ]);

    // Gallery / media plugins are not on For You's first paint. Let them race
    // the first frame instead of holding startup for mute lists and watches.
    unawaited(
      Future.wait([
        if (prefService.get<bool>(optionPluginStocksEnabled) == true)
          stocksWatchlist.load(),
        if (prefService.get<bool>(optionPluginPixivEnabled) == true) ...[
          pixivMute.load(),
          pixivSearchHistory.load(),
        ],
        if (prefService.get<bool>(optionPluginBooruEnabled) == true) ...[
          booruTags.load(),
          booruMute.load(),
        ],
        if (prefService.get<bool>(optionPluginEhEnabled) == true) ...[
          ehFavorites.load(),
          ehHistory.load(),
        ],
        if (prefService.get<bool>(optionPluginTiktokEnabled) == true) ...[
          tiktokFollows.load(),
          tiktokLikes.load(),
          tiktokSearchHistory.load(),
        ],
        if (prefService.get<bool>(optionPluginInstagramEnabled) == true) ...[
          instagramFollows.load(),
          instagramLikes.load(),
          instagramSearchHistory.load(),
        ],
      ]),
    );

    runApp(
      PrefService(
        service: prefService,
        child: MultiProvider(
          providers: [
            Provider(create: (context) => groupsModel),
            Provider(create: (context) => groupUnreadStore),
            Provider(create: (context) => feedSessionCache),
            Provider(
              create: (context) => VideoControllerPool(maxSize: kVideoPoolSize),
            ),
            Provider(create: (context) => homeModel),
            ChangeNotifierProvider(create: (context) => importDataModel),
            Provider(create: (context) => subscriptionsModel),
            Provider(create: (context) => SavedTweetModel()),
            Provider(create: (context) => AntennaModel()),
            Provider(create: (context) => SavedTweetFolderModel()),
            Provider(create: (context) => LikedTweetModel()),
            Provider(create: (context) => SearchUsersModel()),
            Provider(create: (context) => trendLocationModel),
            Provider(create: (context) => TrendLocationsModel()),
            Provider(create: (context) => TrendsModel(trendLocationModel)),
            Provider(create: (_) => deepmarksClient),
            Provider(create: (_) => podcast),
            Provider(create: (_) => immichClient),
            Provider(create: (_) => karakeepClient),
            Provider(create: (_) => redditClient),
            Provider(create: (_) => redditIcons),
            Provider(create: (_) => redditGalleries),
            Provider(create: (_) => redditVotes),
            Provider(create: (_) => redditSaved),
            Provider(create: (_) => redditAuth),
            Provider(create: (_) => redditSubreddits),
            Provider(create: (_) => redditFeed),
            Provider(create: (_) => stocksWatchlist),
            Provider(create: (_) => tickerQuotes),
            Provider(create: (_) => speech),
            Provider(create: (_) => CombinedGroupsStore()),
            Provider(
              create: (_) => FeedTabStore(
                feedTabFromId(
                  prefService.get<String>(optionHomeDefaultFeedTab),
                ),
              ),
            ),
            Provider(create: (_) => SearchScopeStore()),
            Provider(create: (_) => FeedStripStore(prefService)),
            Provider(create: (_) => HomeAccountFilterStore(prefService)),
            Provider(create: (_) => ChromeAvatarStore(prefService)),
            Provider(create: (_) => substackClient),
            Provider(create: (_) => substackPublications),
            Provider(
              create: (context) => SubstackFeedStore(
                context.read<SubstackClient>(),
                substackPublications,
              ),
            ),
            Provider(
              create: (context) =>
                  SubstackAddPublicationStore(context.read<SubstackClient>()),
            ),
            Provider(
              create: (context) => SubstackNotesStore(
                context.read<SubstackClient>(),
                substackPublications,
              ),
            ),
            Provider(create: (_) => substackRead),
            Provider(create: (_) => substackLikes),
            Provider(create: (_) => substackSaved),
            Provider(create: (_) => threadsClient),
            Provider(create: (_) => threadsDirect),
            Provider(create: (_) => threadsApi),
            Provider(create: (_) => threadsAccounts),
            Provider(create: (_) => threadsLikes),
            Provider(create: (_) => threadsFeed),
            Provider(create: (_) => blueskyClient),
            Provider(create: (_) => blueskyAccounts),
            Provider(create: (_) => blueskyLikes),
            Provider(create: (_) => blueskyFeed),
            Provider(create: (_) => mastodonClient),
            Provider(create: (_) => mastodonAccounts),
            Provider(create: (_) => mastodonFeed),
            Provider(create: (_) => mastodonExplore),
            Provider(create: (_) => mastodonLocal),
            Provider(create: (_) => mastodonFederated),
            Provider(create: (_) => pixivClient),
            Provider(create: (_) => pixivMute),
            Provider(create: (_) => pixivSearchHistory),
            Provider(create: (_) => pixivBookmarks),
            Provider(create: (_) => pixivFeed),
            Provider(create: (_) => booruClient),
            Provider(create: (_) => booruTags),
            Provider(create: (_) => booruMute),
            Provider(create: (_) => ehClient),
            Provider(create: (_) => ehFavorites),
            Provider(create: (_) => ehHistory),
            Provider(create: (_) => tiktokClient),
            Provider(create: (_) => tiktokFollows),
            Provider(create: (_) => tiktokLikes),
            Provider(create: (_) => tiktokSearchHistory),
            Provider(create: (_) => tiktokFollowing),
            Provider(create: (_) => instagramClient),
            Provider(create: (_) => instagramFollows),
            Provider(create: (_) => instagramLikes),
            Provider(create: (_) => instagramSearchHistory),
            Provider(create: (_) => instagramFollowing),
            ChangeNotifierProvider(
              create: (_) =>
                  VideoContextState(prefService.get(optionMediaDefaultMute)),
            ),
          ],
          child: FritterApp(),
        ),
      ),
    );

    // libmpv is a large library to dlopen and a reader who never opens a video
    // never needs it, so it no longer sits in front of the first frame. The
    // earliest a player can be built is a feed tile that has already fetched its
    // stream urls and been scrolled into view, which is several async hops after
    // this callback has run.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      MediaKit.ensureInitialized();
      unawaited(initXtaAudio());
    });
  } catch (e, stackTrace) {
    log('Unable to start Fritter', error: e, stackTrace: stackTrace);
  }
}

class FritterApp extends StatefulWidget {
  const FritterApp({super.key});

  @override
  State<FritterApp> createState() => _FritterAppState();
}

class _FritterAppState extends State<FritterApp> {
  final GlobalKey<NavigatorState> _navigatorKey =
      GlobalKey<NavigatorState>(); // NEW: Navigator key

  var _prefsListening = false;
  String _xLookBackground = xLookBackgroundSystem;
  String _xLookAccent = xLookAccentBlue;
  bool _disableAnimations = false;
  bool _checkUpdates = false;
  bool _updateDialogShown = false;
  bool _accountDialogShown = false;
  bool _isSecure = false;
  double _textScaleFactor = 1.0;
  Locale? _locale;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();

    var prefService = PrefService.of(context);

    void setLocale(String? locale) {
      if (locale == null || locale == optionLocaleDefault) {
        _locale = null;
      } else {
        var splitLocale = locale.split(RegExp(r'[-_]'));
        if (splitLocale.length == 1) {
          _locale = Locale(splitLocale[0]);
        } else {
          if (splitLocale[1].length == 4) {
            // 4 characters -> unicode_script_subtag
            _locale = Locale.fromSubtags(
              languageCode: splitLocale[0],
              scriptCode: splitLocale[1],
            );
          } else {
            // Other than 4 characters -> unicode_region_subtag (country)
            _locale = Locale(splitLocale[0], splitLocale[1]);
          }
        }
      }
    }

    // An install from before X Look became the only design language still has
    // its old preset stored. Carry the three X Look ones across to the matching
    // background; the retired presets have no equivalent and land on System.
    final storedPreset = prefService.get<String>(optionThemePreset);
    if (storedPreset != null && storedPreset != themePresetNone) {
      prefService.set(
        optionXLookBackground,
        xLookBackgroundForPreset(storedPreset),
      );
      prefService.set(optionThemePreset, themePresetNone);
    }

    // Leaving the app to read a link is a worse default than staying in it, and
    // the switch was off, so every link went out to the browser. Turned on
    // once, the same way and for the same reason as the check below: a stored
    // value wins over a default, and every install has one.
    if (prefService.get<bool>(optionEmbeddedBrowserReset) != true) {
      prefService.set(optionOpenLinksInEmbeddedBrowser, true);
      prefService.set(optionEmbeddedBrowserReset, true);
    }

    // Upstream wrote the update check for occasional releases; this fork
    // publishes a build whenever something is fixed, so it fired on most
    // launches. Changing the default alone did not reach anyone who already had
    // the old value stored, which is exactly who was being interrupted — so it
    // is turned off once, here. Toggling it back on afterwards is the reader's
    // own choice and is never overridden again.
    if (prefService.get<bool>(optionUpdateCheckReset) != true) {
      prefService.set(optionShouldCheckForUpdates, false);
      prefService.set(optionUpdateCheckReset, true);
    }

    // Set any already-enabled preferences
    setState(() {
      setLocale(prefService.get<String>(optionLocale));
      _xLookBackground = prefService.get(optionXLookBackground);
      _xLookAccent = prefService.get(optionXLookAccent);
      _disableAnimations = prefService.get(optionDisableAnimations);
      _checkUpdates = prefService.get(optionShouldCheckForUpdates);
      _isSecure = prefService.get(optionDisableScreenshots);
      _textScaleFactor = prefService.get(optionTextScaleFactor);
    });

    if (_prefsListening) {
      return;
    }
    _prefsListening = true;

    prefService.addKeyListener(optionShouldCheckForUpdates, () {
      // Re-read rather than only rebuild: the value is held in a field, so a
      // rebuild alone would keep showing the answer from before the change —
      // including the one the reset above makes on this very launch.
      setState(
        () => _checkUpdates = prefService.get(optionShouldCheckForUpdates),
      );
    });

    prefService.addKeyListener(optionLocale, () {
      setState(() {
        setLocale(prefService.get<String>(optionLocale));
      });
    });

    // Whenever the "true black" preference is toggled, apply the toggle
    prefService.addKeyListener(optionThemeTrueBlack, () {
      setState(() {});
    });

    prefService.addKeyListener(optionThemeMode, () {
      setState(() {});
    });

    prefService.addKeyListener(optionThemeColor, () {
      setState(() {});
    });

    prefService.addKeyListener(optionXLookBackground, () {
      setState(() {
        _xLookBackground = prefService.get(optionXLookBackground);
      });
    });

    prefService.addKeyListener(optionXLookAccent, () {
      setState(() {
        _xLookAccent = prefService.get(optionXLookAccent);
      });
    });

    prefService.addKeyListener(optionDisableScreenshots, () {
      setState(() {
        _isSecure = prefService.get(optionDisableScreenshots);
      });
    });

    prefService.addKeyListener(optionTextScaleFactor, () {
      setState(() {
        _textScaleFactor =
            prefService.get<double?>(optionTextScaleFactor) ?? 1.0;
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    final PageTransitionsTheme? pageTransitions = _disableAnimations == true
        ? PageTransitionsTheme(
            builders: {
              TargetPlatform.android: NoAnimationPageTransitionsBuilder(),
              TargetPlatform.iOS: NoAnimationPageTransitionsBuilder(),
            },
          )
        : null;

    final systemOverlayStyle = SystemUiOverlayStyle.dark.copyWith(
      systemNavigationBarColor: Colors.transparent,
    );
    SystemChrome.setSystemUIOverlayStyle(systemOverlayStyle);
    final systemScaleFactor = MediaQuery.textScalerOf(context).scale(1.0);

    return MediaQuery(
      data: MediaQuery.of(context).copyWith(
        textScaler: TextScaler.linear(_textScaleFactor * systemScaleFactor),
      ),
      child: DynamicColorBuilder(
        builder: (lightDynamic, darkDynamic) {
          return Portal(
            child: SecureWidget(
              isSecure: _isSecure,
              builder: (BuildContext context, a, b) => MaterialApp(
                navigatorKey: _navigatorKey,
                localizationsDelegates: xtaLocalizationsDelegates,
                supportedLocales: L10n.delegate.supportedLocales,
                locale: _locale,
                title: 'XTA',
                theme: xLookThemeData(
                  xLookTokensFor(xLookBackgroundLight, _xLookAccent),
                  pageTransitions,
                ),
                darkTheme: xLookThemeData(
                  xLookDarkTokensFor(_xLookBackground, _xLookAccent),
                  pageTransitions,
                ),
                themeMode: xLookThemeModeFor(_xLookBackground),
                initialRoute: '/',
                routes: {
                  routeHome: (context) => const DefaultPage(),
                  routeGroup: (context) => const GroupScreen(),
                  routeProfile: (context) => const ProfileScreen(),
                  routeSearch: (context) => const ResultsScreen(),
                  routeSavedFolders: (context) => const SavedFoldersScreen(),
                  routeSettings: (context) => const SettingsScreen(),
                  routeSettingsExport: (context) =>
                      const SettingsExportScreen(),
                  routeSettingsHome: (context) => const SettingsHomeFragment(),
                  routeQuotes: (context) => const QuotesScreen(),
                  routeTicker: (context) => const TickerScreen(),
                  routeStatus: (context) => const StatusScreen(),
                  routeAntennas: (context) => const AntennaScreen(),
                  routeAntennaFeed: (context) => const AntennaFeedScreen(),
                  routeDeck: (context) => const DeckScreen(),
                },
                builder: (context, child) {
                  if (_checkUpdates && !_updateDialogShown) {
                    _updateDialogShown = true;
                    // Use navigatorKey's context for showDialog
                    WidgetsBinding.instance.addPostFrameCallback((_) {
                      checkForUpdates(_navigatorKey.currentContext!);
                    });
                  }

                  if (!_accountDialogShown) {
                    WidgetsBinding.instance.addPostFrameCallback((_) {
                      _accountDialogShown = true;
                      checkForAccounts(_navigatorKey.currentContext!);
                    });
                  }

                  // Replace the default red screen of death with a slightly friendlier one
                  ErrorWidget.builder = (FlutterErrorDetails details) =>
                      FullPageErrorWidget(
                        error: details.exception,
                        stackTrace: details.stack,
                        prefix: L10n.of(context).something_broke_in_fritter,
                      );

                  // Reading aloud outlives the article it started in, so the
                  // way to stop it has to be reachable from wherever the reader
                  // has gone. Nothing is added while nothing is being read.
                  return SpeechBarScaffold(child: child ?? Container());
                },
              ),
            ),
          );
        },
      ),
    );
  }
}

class DefaultPage extends StatefulWidget {
  const DefaultPage({super.key});

  @override
  State<StatefulWidget> createState() => _DefaultPageState();
}

class _DefaultPageState extends State<DefaultPage> {
  static final log = Logger('DefaultPage');

  Object? _migrationError;
  StackTrace? _migrationStackTrace;
  StreamSubscription<Uri>? _sub;

  void handleInitialLink(Uri link) async {
    if (await openWithPlugins(context, link.toString())) {
      return;
    }
    if (!mounted) {
      return;
    }
    final parsed = await parseUri(link);
    if (!mounted) {
      return;
    }
    switch (parsed) {
      case ProfileUriInfo(
        screenName: final screenName,
        profileTabIndex: final tab,
      ):
        Navigator.pushNamed(
          context,
          routeProfile,
          arguments: ProfileScreenArguments.fromScreenName(screenName, tab),
        );
        return;
      case PostUriInfo(screenName: final screenName, id: final id):
        Navigator.pushNamed(
          context,
          routeStatus,
          arguments: StatusScreenArguments(id: id, username: screenName),
        );
        return;
      case ListUriInfo(id: final id):
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => ListImportScreen(initialListId: id),
          ),
        );
        return;
      case UnknownResult():
        showDialog(
          context: context,
          builder: (BuildContext context) {
            return AlertDialog(
              icon: Icon(Icons.error),
              title: Text(L10n.of(context).unable_to_open_link),
              content: Text(L10n.of(context).unable_to_open_link_details),
              actions: [
                TextButton(
                  child: Text(L10n.of(context).report),
                  onPressed: () =>
                      openUri(context, 'https://github.com/$githubRepo/issues'),
                ),
                TextButton(
                  child: Text(L10n.of(context).open_in_browser),
                  onPressed: () {
                    // Respects the same setting as every other link: this
                    // dialog is not a reason to be thrown out of the app.
                    openUri(context, link.toString());
                    if (context.mounted) {
                      Navigator.of(context).pop();
                    }
                  },
                ),
              ],
            );
          },
        );

        return;
    }
  }

  @override
  void initState() {
    super.initState();

    // `main` has already run this; the same future is awaited here so a failure
    // still reaches the screen instead of only the log.
    migrateDatabase().catchError((Object e, StackTrace s) {
      if (mounted) {
        setState(() {
          _migrationError = e;
          _migrationStackTrace = s;
        });
      }
      return false;
    });

    // The saved and liked lists were only ever loaded by the Saved tab's
    // initState, and the home tabs are a lazily-built PageView -- so until the
    // reader opened that tab, every footer in every feed reported the post as
    // neither saved nor liked. Loaded here instead, once, unawaited: the
    // membership answer is a map lookup and the stored posts are only decoded
    // when something actually reads one.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) {
        return;
      }

      unawaited(context.read<SavedTweetModel>().listSavedTweets());
      unawaited(context.read<LikedTweetModel>().listLikedTweets());
    });

    final appLinks = AppLinks();

    // Attach a listener to the stream
    _sub = appLinks.uriLinkStream.listen(
      (link) => handleInitialLink(link),
      onError: (err, stackTrace) {
        // A link that never reaches handleInitialLink leaves the user staring at
        // whatever was already on screen, with no clue their tap did nothing.
        log.warning('Unable to handle an incoming link', err, stackTrace);

        if (mounted) {
          showSnackBar(
            context,
            icon: '🔗',
            message: L10n.of(context).unable_to_open_link,
          );
        }
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_migrationError != null || _migrationStackTrace != null) {
      return ScaffoldErrorWidget(
        error: _migrationError,
        stackTrace: _migrationStackTrace,
        prefix: L10n.of(context).unable_to_run_the_database_migrations,
      );
    }

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) async {
        if (didPop) return;
        var prefService = PrefService.of(context);
        if (!prefService.get(optionConfirmClose)) {
          SystemNavigator.pop();
          return;
        }

        final confirmed = await showDialog<bool>(
          context: context,
          builder: (c) => AlertDialog(
            title: Text(L10n.current.are_you_sure),
            content: Text(L10n.current.confirm_close_fritter),
            actions: [
              TextButton(
                child: Text(L10n.current.no),
                onPressed: () => Navigator.pop(c, false),
              ),
              TextButton(
                child: Text(L10n.current.yes),
                onPressed: () => Navigator.pop(c, true),
              ),
            ],
          ),
        );

        if (confirmed == true && context.mounted) {
          SystemNavigator.pop();
        }
      },
      child: const HomeScreen(),
    );
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }
}

class NoAnimationPageTransitionsBuilder extends PageTransitionsBuilder {
  @override
  Widget buildTransitions<T>(
    PageRoute<T> route,
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    // No animation, simply return the child
    return child;
  }
}
