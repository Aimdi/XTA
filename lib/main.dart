import 'dart:async';
import 'dart:convert';
import 'dart:developer';
import 'dart:io';

import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/foundation.dart' show LicenseEntryWithLineBreaks, LicenseRegistry;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_portal/flutter_portal.dart';
import 'package:media_kit/media_kit.dart';
import 'package:quax/client/accounts.dart';
import 'package:quax/client/endpoint_overrides.dart';
import 'package:quax/client/login_webview.dart';

import 'package:quax/constants.dart';
import 'package:quax/database/repository.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/group/feed_session_cache.dart';
import 'package:quax/tweet/video_controller_pool.dart';
import 'package:quax/group/group_model.dart';
import 'package:quax/group/group_screen.dart';
import 'package:quax/home/_feed.dart';
import 'package:quax/home/home_model.dart';
import 'package:quax/home/home_screen.dart';
import 'package:quax/import_data_model.dart';
import 'package:quax/profile/profile.dart';
import 'package:quax/plugins/substack/substack_client.dart';
import 'package:quax/plugins/substack/substack_store.dart';
import 'package:quax/saved/liked_tweet_model.dart';
import 'package:quax/saved/saved_folders_screen.dart';
import 'package:quax/saved/saved_tweet_folder_model.dart';
import 'package:quax/saved/saved_tweet_model.dart';
import 'package:quax/search/search.dart';
import 'package:quax/search/search_model.dart';
import 'package:quax/settings/_data.dart';
import 'package:quax/settings/_home.dart';
import 'package:quax/settings/settings.dart';
import 'package:quax/settings/settings_export_screen.dart';
import 'package:quax/status.dart';
import 'package:quax/tweet/quotes_screen.dart';
import 'package:quax/tweet/ticker_screen.dart';
import 'package:quax/subscriptions/_import_list.dart';
import 'package:quax/subscriptions/users_model.dart';
import 'package:quax/trends/trends_model.dart';
import 'package:quax/tweet/_video.dart';
import 'package:quax/ui/errors.dart';
import 'package:quax/ui/x_look_theme.dart';
import 'package:quax/utils/crash_reporter.dart';
import 'package:quax/utils/updates.dart';
import 'package:logging/logging.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:quax/utils/urls.dart';
import 'package:secure_content/secure_content.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:timeago/timeago.dart' as timeago;
import 'package:app_links/app_links.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:quax/plugins/karakeep/karakeep_client.dart';
import 'package:quax/plugins/deepmarks/deepmarks_client.dart';
import 'package:quax/plugins/reddit/reddit_auth.dart';
import 'package:quax/plugins/reddit/reddit_client.dart';
import 'package:quax/plugins/reddit/reddit_store.dart';
import 'package:quax/plugins/reddit/reddit_subreddit_avatar.dart';

Future checkForUpdates(context) async {
  Logger.root.info('Checking for updates');

  PackageInfo packageInfo = await PackageInfo.fromPlatform();
  final client = HttpClient();
  client.userAgent =
      "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36";

  final request = await client.getUrl(Uri.parse('https://api.github.com/repos/$githubRepo/releases/latest'));
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
        await showDialog(
          context: context,
          builder: (BuildContext context) {
            return AlertDialog(
              title: Text(L10n.of(context).an_update_for_fritter_is_available),
              content: Text(L10n.of(context).view_version_on_github(latestTag)),
              actions: [
                TextButton(child: Text(L10n.of(context).dismiss), onPressed: () => Navigator.of(context).pop()),
                TextButton(
                  child: Text(L10n.of(context).view_on_github),
                  onPressed: () async {
                    await openUri(context, map['html_url']);
                    Navigator.of(context).pop();
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

Future checkForAccounts(context) async {
  Logger.root.info('Checking for accounts');

  final accounts = await getAccounts();
  if (accounts.isEmpty) {
    await showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: Text("⚠️ ${L10n.of(context).not_logged_in}"),
          content: Text(L10n.of(context).quax_doesnt_work_without_account_please_login),
          actions: [
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
                Navigator.push(context, MaterialPageRoute(builder: (_) => const TwitterLoginWebview()));
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
  final quality = disabled ? 'large' : (previous ?? 'medium');

  await prefs.set(optionMediaDisableAutoload, disabled);
  await prefs.set(optionImageQuality, quality);
  await prefs.set(optionMediaVideoQuality, quality);
  await prefs.set(optionMediaQualitySplitMigrated, true);
}

/// Earlier builds seeded the crash-report repository with a name that does not
/// exist on GitHub. The stored preference wins over the default, so installs
/// that already ran keep the dead value until it is rewritten here. Only the
/// broken value is touched — a repository the user chose is left alone.
Future<void> _migrateCrashRepoPref(BasePrefService prefs) async {
  const brokenRepo = 'Aimdi/QuaX-gamma';
  if (prefs.get<String>(optionCrashGithubRepo)?.trim() == brokenRepo) {
    await prefs.set(optionCrashGithubRepo, defaultCrashGithubRepo);
  }
}

Future<void> main() async {
  Logger.root.onRecord.listen((event) async {
    log(event.message, error: event.error, stackTrace: event.stackTrace);
  });

  if (Platform.isLinux) {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  }

  WidgetsFlutterBinding.ensureInitialized();

  // The bundled Inter font ships under the SIL Open Font License, which
  // requires the licence to travel with the software.
  LicenseRegistry.addLicense(() async* {
    final license = await rootBundle.loadString('assets/fonts/Inter-OFL.txt');
    yield LicenseEntryWithLineBreaks(const ['Inter'], license);
  });

  MediaKit.ensureInitialized();

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
      optionImageQuality: 'medium',
      optionMediaVideoQuality: 'medium',
      optionMediaDisableAutoload: false,
      optionMediaQualitySplitMigrated: false,
      optionMediaGridColumns: 3,
      optionMediaDefaultMute: true,
      optionMediaDefaultLoop: false,
      optionMediaDefaultAutoPlay: false,
      optionMediaBackgroundPlayback: true,
      optionMediaAllowBackgroundPlayOtherApps: false,
      optionMediaVideoPrefetchSeconds: 0,
      optionNonConfirmationBiasMode: false,
      optionTweetsShowSubscribeBadge: true,
      optionZenMode: false,
      optionZenModePageCap: 5,
      optionFeedReadingPosition: false,
      optionGlobalIncludeReplies: true,
      optionGlobalIncludeRetweets: true,
      optionThreadedReplies: true,
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
      optionWebDavUrl: '',
      optionWebDavUsername: '',
      optionWebDavPassword: '',
      optionWebDavIncludeAccounts: false,
      optionWebDavLastSyncAt: '',
      optionOpenLinksInEmbeddedBrowser: false,
      optionCrashReportsEnabled: false,
      optionCrashGithubRepo: defaultCrashGithubRepo,
      optionCrashGithubToken: '',
    optionPluginDeepmarksEnabled: false,
    optionPluginDeepmarksApiBase: '',
    optionPluginDeepmarksApiKey: '',
    optionPluginDeepmarksSecretKey: '',
    optionPluginKarakeepEnabled: false,
      optionPluginKarakeepServerUrl: '',
      optionPluginKarakeepApiKey: '',
      optionSeededPluginTabs: <String>[],
      optionPluginRedditEnabled: false,
      optionPluginRedditClientId: '',
      optionPluginRedditInHomeFeed: false,
      optionPluginRedditShowTab: false,
      optionPluginRedditSort: 'hot',
      optionPluginRedditSource: redditSourceAuto,
      optionPluginRedditSubreddits: '[]',
      optionPluginRedditRefreshToken: '',
      optionPluginSubstackEnabled: false,
      optionPluginSubstackShowTab: true,
      optionPluginSubstackPublications: '[]',
      optionPluginSubstackReadIds: '[]',
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
      optionSavedShowAllTab: true,
      optionSavedShowUnfiledTab: true,
      optionSavedShowFavoritesTab: true,
      optionSavedTabOrder: '',
      optionSavedFolderHintShown: false,
      optionLikedFirstToastShown: false,
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
  await _migrateCrashRepoPref(prefService);

  CrashReporter.install(prefService);

  // Apply the last known query ids before the first request goes out; the
  // network refresh runs unawaited so a slow or blocked fetch never delays
  // startup.
  final endpointRegistry = EndpointRegistry(prefService);
  endpointRegistry.applyCached();
  unawaited(endpointRegistry.refresh());

  try {
    // Run the migrations early, so models work. We also do this later on so we can display errors to the user
    try {
      await Repository().migrate();
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
    // GroupFeedShell reload listener, so by the time the shell remounts the
    // body via KeyedSubtree, the inner feed reads fresh controllers from the
    // cache. LinkedHashMap iterates in insertion order, and registering here
    // (before any shell exists) guarantees we win.
    groupsModel.addReloadListener('FeedSessionCache', feedSessionCache.invalidateAll);
    subscriptionsModel.addReloadListener('FeedSessionCache', feedSessionCache.invalidateAll);

    var trendLocationModel = UserTrendLocationModel(prefService);

    final deepmarksClient = DeepmarksClient();
    final karakeepClient = KarakeepClient();
    final redditClient = RedditClient();
    final redditIcons = RedditIcons(redditClient);
    final redditAuth = RedditAuth();
    final redditSubreddits = RedditSubredditsStore(prefService);
    final redditFeed = RedditFeedStore(redditClient, redditSubreddits, prefService, auth: redditAuth);
    final substackClient = SubstackClient();
    final substackPublications = SubstackPublicationsStore(prefService);
    final substackFeed = SubstackFeedStore(substackClient, substackPublications);
    final substackAdd = SubstackAddPublicationStore(substackClient);
    final substackRead = SubstackReadStore(prefService);

    // Everything above only constructs; the reads all happen here. They were a
    // chain of awaits, each waiting on the last for no reason — none of them
    // depends on another's result — so the slowest used to be the sum rather
    // than the max. A disabled plugin's store is skipped entirely: it has no home tab and no
    // screen, so nothing can read it, and its screen loads the store itself on
    // mount if the plugin is turned on later.
    // The one read that cannot join them: it moves the followed subreddits out
    // of preferences and into the database, and the subscription list below
    // reads that table. Run in parallel, whether a subreddit could be added to a group
    // came down to which of the two finished first.
    if (prefService.get<bool>(optionPluginRedditEnabled) == true) {
      await redditSubreddits.load();
    }

    await Future.wait([
      homeModel.loadPages(),
      subscriptionsModel.reloadSubscriptions(),
      if (prefService.get<bool>(optionPluginSubstackEnabled) == true) ...[
        substackPublications.load(),
        substackRead.load(),
      ],
    ]);

    runApp(
      PrefService(
        service: prefService,
        child: MultiProvider(
          providers: [
            Provider(create: (context) => groupsModel),
            Provider(create: (context) => redditIcons),
            Provider(create: (context) => feedSessionCache),
            Provider(create: (context) => VideoControllerPool(maxSize: 5)),
            Provider(create: (context) => homeModel),
            ChangeNotifierProvider(create: (context) => importDataModel),
            Provider(create: (context) => subscriptionsModel),
            Provider(create: (context) => SavedTweetModel()),
            Provider(create: (context) => SavedTweetFolderModel()),
            Provider(create: (context) => LikedTweetModel()),
            Provider(create: (context) => SearchUsersModel()),
            Provider(create: (context) => trendLocationModel),
            Provider(create: (context) => TrendLocationsModel()),
            Provider(create: (context) => TrendsModel(trendLocationModel)),
            Provider(create: (_) => deepmarksClient),
            Provider(create: (_) => karakeepClient),
            Provider(create: (_) => redditClient),
            Provider(create: (_) => redditAuth),
            Provider(create: (_) => redditSubreddits),
            Provider(create: (_) => redditFeed),
            Provider(create: (_) => substackClient),
            Provider(create: (_) => substackPublications),
            Provider(create: (_) => substackFeed),
            Provider(create: (_) => substackAdd),
            Provider(create: (_) => substackRead),
            ChangeNotifierProvider(create: (_) => VideoContextState(prefService.get(optionMediaDefaultMute))),
          ],
          child: FritterApp(),
        ),
      ),
    );
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

  final GlobalKey<NavigatorState> _navigatorKey = GlobalKey<NavigatorState>(); // NEW: Navigator key

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
            _locale = Locale.fromSubtags(languageCode: splitLocale[0], scriptCode: splitLocale[1]);
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
      prefService.set(optionXLookBackground, xLookBackgroundForPreset(storedPreset));
      prefService.set(optionThemePreset, themePresetNone);
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

    prefService.addKeyListener(optionShouldCheckForUpdates, () {
      // Re-read rather than only rebuild: the value is held in a field, so a
      // rebuild alone would keep showing the answer from before the change —
      // including the one the reset above makes on this very launch.
      setState(() => _checkUpdates = prefService.get(optionShouldCheckForUpdates));
    });

    prefService.addKeyListener(optionLocale, () {
      setState(() {
        setLocale(prefService.get<String>(optionLocale));
      });
    });

    // Whenever the "true black" preference is toggled, apply the toggle
    prefService.addKeyListener(optionThemeTrueBlack, () {
      setState(() {
        });
    });

    prefService.addKeyListener(optionThemeMode, () {
      setState(() {
        });
    });

    prefService.addKeyListener(optionThemeColor, () {
      setState(() {
        });
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
        _textScaleFactor = prefService.get<double?>(optionTextScaleFactor) ?? 1.0;
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

    final systemOverlayStyle = SystemUiOverlayStyle.dark.copyWith(systemNavigationBarColor: Colors.transparent);
    SystemChrome.setSystemUIOverlayStyle(systemOverlayStyle);
    final systemScaleFactor = MediaQuery.textScalerOf(context).scale(1.0);

    return MediaQuery(
      data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(_textScaleFactor * systemScaleFactor)),
      child: DynamicColorBuilder(
        builder: (lightDynamic, darkDynamic) {
          return Portal(
            child: SecureWidget(
              isSecure: _isSecure,
              builder: (BuildContext context, a, b) => MaterialApp(
                navigatorKey: _navigatorKey,
                localizationsDelegates: const [
                  L10n.delegate,
                  GlobalMaterialLocalizations.delegate,
                  GlobalWidgetsLocalizations.delegate,
                  GlobalCupertinoLocalizations.delegate,
                ],
                supportedLocales: L10n.delegate.supportedLocales,
                locale: _locale,
                title: 'QuaX',
                theme: xLookThemeData(xLookTokensFor(xLookBackgroundLight, _xLookAccent), pageTransitions),
                darkTheme: xLookThemeData(xLookDarkTokensFor(_xLookBackground, _xLookAccent), pageTransitions),
                themeMode: xLookThemeModeFor(_xLookBackground),
                initialRoute: '/',
                routes: {
                  routeHome: (context) => const DefaultPage(),
                  routeGroup: (context) => const GroupScreen(),
                  routeProfile: (context) => const ProfileScreen(),
                  routeSearch: (context) => const ResultsScreen(),
                  routeSavedFolders: (context) => const SavedFoldersScreen(),
                  routeSettings: (context) => const SettingsScreen(),
                  routeSettingsExport: (context) => const SettingsExportScreen(),
                  routeSettingsHome: (context) => const SettingsHomeFragment(),
                  routeQuotes: (context) => const QuotesScreen(),
                  routeTicker: (context) => const TickerScreen(),
                  routeStatus: (context) => const StatusScreen(),
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
                  ErrorWidget.builder = (FlutterErrorDetails details) => FullPageErrorWidget(
                    error: details.exception,
                    stackTrace: details.stack,
                    prefix: L10n.of(context).something_broke_in_fritter,
                  );

                  return child ?? Container();
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
    final parsed = await parseUri(link);
    if (!mounted) {
      return;
    }
    switch (parsed) {
      case ProfileUriInfo(screenName: final screenName, profileTabIndex: final tab):
        Navigator.pushNamed(context, routeProfile, arguments: ProfileScreenArguments.fromScreenName(screenName, tab));
        return;
      case PostUriInfo(screenName: final screenName, id: final id):
        Navigator.pushNamed(
          context,
          routeStatus,
          arguments: StatusScreenArguments(id: id, username: screenName),
        );
        return;
      case ListUriInfo(id: final id):
        Navigator.push(context, MaterialPageRoute(builder: (_) => ListImportScreen(initialListId: id)));
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
                  onPressed: () => openUri(context, 'https://github.com/teskann/quax/issues'),
                ),
                TextButton(
                  child: Text(L10n.of(context).open_in_browser),
                  onPressed: () {
                    openInDefaultBrowser(link.toString());
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

    // Run the database migrations
    Repository().migrate().catchError((e, s) {
      setState(() {
        _migrationError = e;
        _migrationStackTrace = s;
      });
      return e;
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
          showSnackBar(context, icon: '🔗', message: L10n.of(context).unable_to_open_link);
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
              TextButton(child: Text(L10n.current.no), onPressed: () => Navigator.pop(c, false)),
              TextButton(child: Text(L10n.current.yes), onPressed: () => Navigator.pop(c, true)),
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
