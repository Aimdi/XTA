import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/combined_groups.dart';
import 'package:xta/group/feed_session_cache.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_screen.dart';
import 'package:xta/plugins/plugin_session.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'support/memory_json_store.dart';

class _Canned extends GroupDiscoveryStore {
  _Canned() : super(storage: MemoryJsonStore());

  @override
  Future<void> load({
    required List<DiscoveryLoad> sources,
    required Set<String> followed,
    required String groupName,
    String? groupId,
  }) => super.load(
    sources: [
      DiscoveryLoad(
        DiscoverySource.x,
        members: 1,
        read: (_) async => const DiscoveryBatch([
          DiscoveryAccount(source: DiscoverySource.x, id: '1', handle: 'artist', name: 'An artist', text: 'A post'),
        ], read: 1),
      ),
    ],
    followed: followed,
    groupName: groupName,
    groupId: groupId,
  );
}

/// Database work runs outside the widget test's fake clock, or it never completes.
Future<Map<String, Object?>> _row(WidgetTester tester) async {
  final row = await tester.runAsync(() async {
    final db = await Repository.readOnly();
    return (await db.query(tableSubscriptionGroup, where: 'id = ?', whereArgs: ['g'])).single;
  });
  return row!;
}

/// Lets real I/O (the group's own read, a save) land between frames. Bounded
/// pumps rather than pumpAndSettle: a live feed keeps an indicator moving.
Future<void> _settle(WidgetTester tester) async {
  for (var frame = 0; frame < 10; frame++) {
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 10)));
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  late PrefServiceCache prefs;
  late GroupsModel groups;
  late SubscriptionsModel subscriptions;
  final scroll = ScrollController();

  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfiNoIsolate;
    final dir = await Directory.systemTemp.createTemp('xta-discover-mode');
    await databaseFactory.setDatabasesPath(dir.path);
    await Repository().migrate();
    // Repository caches this Future. Create it outside any widget-test zone,
    // otherwise later tests inherit the first test's stopped fake clock.
    await Repository.readOnly();
    final db = await Repository.writable();
    await db.insert(tableSubscriptionGroup, {
      'id': 'g',
      'name': 'Reading',
      'icon': defaultGroupIcon,
      'popular': 1,
      'custom': 0,
    });
  });

  setUp(() {
    prefs = PrefServiceCache(
      defaults: {
        optionDisableAnimations: true,
        optionGlobalIncludeReplies: true,
        optionGlobalIncludeRetweets: true,
        optionMediaDefaultMute: true,
        optionUseAbsoluteTimestamp: true,
        optionShareBaseUrl: 'https://x.com',
        optionThemeTrueBlack: false,
        optionThemeTrueBlackTweetCards: false,
        optionLocale: 'en',
        optionZenMode: false,
        optionCalmMode: false,
        optionNonConfirmationBiasMode: false,
      },
    );
    groups = GroupsModel(prefs);
    subscriptions = SubscriptionsModel(prefs, groups);
  });
  tearDown(() async {
    await subscriptions.destroy();
    await groups.destroy();
  });

  Widget app() => PrefService(
    service: prefs,
    child: MultiProvider(
      providers: [
        Provider<GroupsModel>.value(value: groups),
        Provider<SubscriptionsModel>.value(value: subscriptions),
        Provider(create: (_) => CombinedGroupsStore(), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => PluginSessionStore(), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => FeedSessionCache()),
        Provider(create: (_) => LikedTweetModel(), dispose: (_, store) => store.destroy()),
        Provider(create: (_) => SavedTweetModel(), dispose: (_, store) => store.destroy()),
      ],
      child: MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: SubscriptionGroupScreen(
          scrollController: scroll,
          id: 'g',
          name: 'Reading',
          cacheKey: 'g',
          createDiscoveryStore: _Canned.new,
        ),
      ),
    ),
  );

  testWidgets('Discover is its own view: only its chip shows, Back returns to the feed, nothing is saved', (
    tester,
  ) async {
    await tester.pumpWidget(app());
    await _settle(tester);
    final model = tester.element(find.byType(SubscriptionGroupScreenContent)).read<GroupModel>();
    expect(model.state.popular, isTrue);
    // Only a save goes through execute(), which flips loading; a reload never does.
    var writes = 0;
    final stop = model.observer(
      onLoading: (loading) {
        if (loading) writes++;
      },
    );
    addTearDown(stop);

    await tester.tap(find.text('Popular'));
    await _settle(tester);
    expect(writes, 0);

    await tester.tap(find.text('Discover'));
    await _settle(tester);
    expect(find.text('An artist'), findsOneWidget);
    expect(find.text('Discover'), findsOneWidget);
    for (final chip in ['Recent', 'Popular', 'Custom', 'Media']) {
      expect(find.text(chip), findsNothing);
    }
    expect(find.byIcon(Icons.manage_search), findsNothing);
    expect(find.byIcon(Icons.refresh), findsOneWidget);
    expect(writes, 0);

    await tester.state<NavigatorState>(find.byType(Navigator)).maybePop();
    await _settle(tester);
    expect(find.byType(SubscriptionGroupScreen), findsOneWidget);
    expect(find.text('An artist'), findsNothing);
    expect(find.text('Recent'), findsOneWidget);
    expect(find.text('Popular'), findsOneWidget);
    expect(find.byIcon(Icons.manage_search), findsOneWidget);
    expect(writes, 0);
    expect(model.state.popular, isTrue);
    expect((await _row(tester))['popular'], 1);

    await tester.tap(find.text('Recent'));
    await _settle(tester);
    expect(writes, 1);
    expect(model.state.popular, isFalse);
    expect((await _row(tester))['popular'], 0);
    expect(tester.takeException(), isNull);
  });
}
