import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/client/headers.dart';
import 'package:xta/client/http_client.dart';
import 'package:xta/client/x_client_transaction_id/client_transaction.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/combined_groups.dart';
import 'package:xta/group/feed_session_cache.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/chrome_avatar.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/home_account_filter.dart';
import 'package:xta/home/home_group_filter.dart';
import 'package:xta/home/network_recents_store.dart';
import 'package:xta/plugins/plugin_session.dart';
import 'package:xta/reading/shared_filter_engine.dart';
import 'package:xta/reading/shared_filter_scope.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/utils/paging.dart';
import 'package:xta/utils/read_visibility.dart';

/// The Following tab on its real read path: the group from SQLite, SearchTimeline over HTTP, tiles on screen.
///
/// Nothing is pre-seeded into the feed controller, so the first page goes through every kick-off the app has
/// (the list, the refresh indicator and the visibility resume), the way it does on a phone.
class _Harness {
  final prefs = PrefServiceCache(
    defaults: {
      optionHomeFeedStripPlugins: <String>[],
      optionSeededStripPlugins: <String>[],
      optionShareBaseUrl: 'https://x.com',
      optionMediaDefaultMute: true,
      optionMediaDefaultLoop: false,
      optionMediaDefaultAutoPlay: false,
      optionMediaBackgroundPlayback: true,
      optionMediaAllowBackgroundPlayOtherApps: false,
      optionMediaGridColumns: 2,
      optionMediaGridLayout: mediaGridLayoutMasonry,
      optionNonConfirmationBiasMode: false,
      optionThemeTrueBlack: false,
      optionThemeTrueBlackTweetCards: false,
      optionTweetsShowSubscribeBadge: false,
      optionUseAbsoluteTimestamp: true,
      optionZenMode: false,
      optionCalmMode: false,
      optionLocale: optionLocaleDefault,
      optionDisableAnimations: true,
      optionSubscriptionGroupsOrderByField: 'name',
      optionSubscriptionGroupsOrderByAscending: true,
      optionGlobalIncludeReplies: true,
      optionGlobalIncludeRetweets: true,
      optionDisableWarningsForUnrelatedPostsInFeed: true,
    },
  );
  final selected = FeedTabStore(FeedTab.following);
  final cache = FeedSessionCache();
  final scroll = ScrollController();
  late final groups = GroupsModel(prefs);
  late final subscriptions = SubscriptionsModel(prefs, groups);
  final requests = <String>[];

  /// When set, the first SearchTimeline answer waits for it, so a change can land while the first page is in flight.
  Completer<void>? holdFirstSearch;

  Future<void> seed() async {
    final db = await Repository.writable();
    await db.delete(tableSubscription);
    await db.delete(tableFeedGroupChunk);
    await db.update(tableSubscriptionGroup, {'popular': 0, 'custom': 0}, where: 'id = ?', whereArgs: ['-1']);
    await db.insert(tableSubscription, _member('reader', DateTime(2026, 9, 7)).toMap());
  }

  static UserSubscription _member(String name, DateTime createdAt) => UserSubscription(
    id: name,
    screenName: name,
    name: '$name fixture',
    profileImageUrlHttps: null,
    verified: false,
    createdAt: createdAt,
    inFeed: true,
  );

  Iterable<String> get searches => requests.where((request) => request.contains('SearchTimeline'));

  /// X refuses the signing bootstrap pages, as it does for blocked networks; no timeline request can be signed.
  http.Client refusingClient() => MockClient((request) async {
    requests.add('${request.method} ${request.url.host}${request.url.path}');
    if (request.url.path.endsWith('/guest/activate.json')) {
      return http.Response(jsonEncode({'guest_token': 'guest'}), 200);
    }
    return http.Response('<html>blocked</html>', 403);
  });

  http.Client client() {
    final entries = jsonDecode(File('test/fixtures/UserTweets/add_entries.json').readAsStringSync())['entries'];
    return MockClient((request) async {
      requests.add('${request.method} ${request.url.path}');
      if (request.url.path.endsWith('/guest/activate.json')) {
        return http.Response(jsonEncode({'guest_token': 'guest'}), 200);
      }
      if (request.url.path.contains('SearchTimeline')) {
        final hold = holdFirstSearch;
        holdFirstSearch = null;
        if (hold != null) await hold.future;
        final timeline = {
          'timeline': {
            'instructions': [
              {'type': 'TimelineAddEntries', 'entries': entries},
            ],
          },
        };
        return http.Response(
          jsonEncode({
            'data': {
              'search_by_raw_query': {'search_timeline': timeline},
            },
          }),
          200,
          headers: {'content-type': 'application/json'},
        );
      }
      return http.Response('{}', 404);
    });
  }

  Widget app() => PrefService(
    service: prefs,
    child: MultiProvider(
      providers: [
        Provider<FeedTabStore>.value(value: selected),
        Provider(create: (_) => FeedStripStore(prefs), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => HomeAccountFilterStore(prefs), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => HomeGroupFilterStore(prefs), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => NetworkRecentsStore(prefs), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => ChromeAvatarStore(prefs), dispose: (_, store) => store.destroy()),
        Provider<GroupsModel>.value(value: groups),
        Provider<SubscriptionsModel>.value(value: subscriptions),
        Provider(create: (_) => CombinedGroupsStore(), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => PluginSessionStore(), dispose: (_, store) => store.destroy()),
        Provider<FeedSessionCache>.value(value: cache),
        Provider(create: (_) => LikedTweetModel(), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => SavedTweetModel(), dispose: (_, store) => store.destroy()),
      ],
      child: SharedFilterRoot(
        engine: SharedFilterEngine.forPrefs(prefs),
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          navigatorObservers: [readRouteObserver],
          theme: xLookLightTheme(null),
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          home: Scaffold(
            drawer: const Drawer(),
            body: FeedScreen(scrollController: scroll, id: '-1', name: 'Home'),
          ),
        ),
      ),
    ),
  );

  Future<void> close(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    scroll.dispose();
    selected.destroy();
    subscriptions.destroy();
    groups.destroy();
    cache.getOrCreateController('home--1').dispose();
  }
}

/// Native SQLite and the mock client answer on the real event loop, so both clocks are advanced.
Future<void> _waitFor(WidgetTester tester, bool Function() ready, {int frames = 80}) async {
  for (var frame = 0; frame < frames && !ready(); frame++) {
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 25)));
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Object? _firstPageFailure(_Harness h) {
  final error = h.cache.getOrCreateController('home--1').controller.value.error;
  return error is PagingError ? error.error : error;
}

void main() {
  final post = find.textContaining('big day to be an Android user', findRichText: true);

  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfiNoIsolate;
    final dir = await Directory.systemTemp.createTemp('xta-home-live-path');
    await databaseFactory.setDatabasesPath(dir.path);
    await Repository().migrate();
    await Repository.readOnly();
  });

  setUp(() {
    TwitterHeaders.resetForTesting();
    TwitterHeaders.initializer = () async =>
        ClientTransaction.forTesting(keyBytes: [1, 2, 3, 4], animationKey: 'test-animation-key');
  });
  tearDown(() {
    xHttpClient = null;
    TwitterHeaders.resetForTesting();
  });

  Future<_Harness> open(WidgetTester tester, {Completer<void>? holdFirstSearch, bool refusing = false}) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final h = _Harness();
    addTearDown(() => h.close(tester));
    h.holdFirstSearch = holdFirstSearch;
    xHttpClient = refusing ? h.refusingClient() : h.client();
    await tester.runAsync(h.seed);
    await tester.pumpWidget(h.app());
    return h;
  }

  testWidgets('Following loads posts from the network into the Home tab', (tester) async {
    final h = await open(tester);
    await _waitFor(tester, () => post.evaluate().isNotEmpty);
    expect(post, findsOneWidget, reason: 'The Following tab should show the posts SearchTimeline returned');
    expect(h.searches, hasLength(1), reason: 'One first page, however many things kick it off');
    expect(tester.takeException(), isNull);
  });

  testWidgets('following someone while the first page is still loading ends with posts, not a cancelled read', (
    tester,
  ) async {
    final held = Completer<void>();
    final h = await open(tester, holdFirstSearch: held);
    await _waitFor(tester, () => h.searches.isNotEmpty);
    expect(h.searches, hasLength(1));

    // Following a second account rebuilds the group's chunks while the first search is still open.
    await tester.runAsync(
      () => h.subscriptions.toggleSubscribe(_Harness._member('second', DateTime(2026, 9, 8)), false),
    );
    await _waitFor(tester, () => h.searches.length >= 2, frames: 30);
    held.complete();
    await _waitFor(tester, () => post.evaluate().isNotEmpty);

    expect(h.searches.length, greaterThanOrEqualTo(2), reason: 'The wider group is searched again');
    expect(post, findsOneWidget, reason: 'The feed should show posts after the membership change');
    expect(_firstPageFailure(h), isNull);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Following reports why X answered nothing instead of a read it cancelled itself', (tester) async {
    final h = await open(tester, refusing: true);
    TwitterHeaders.initializer = null;
    for (var second = 0; second < 40 && _firstPageFailure(h) == null; second++) {
      await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 20)));
      await tester.pump(const Duration(seconds: 1));
    }
    expect(h.searches, isNotEmpty, reason: 'A refused network still gets a real request before any error');
    expect(_firstPageFailure(h), isNotNull);
    expect(_firstPageFailure(h).toString(), isNot(contains('ReadCancelled')));
    expect(find.text('Retry'), findsWidgets);
    expect(tester.takeException(), isNull);
  });
}
