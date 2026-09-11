import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/group/group_discovery_screen.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/utils/ai_client.dart';

class _Discovery extends GroupDiscoveryStore {
  bool failLoad = false;
  @override
  Future<void> load({
    required List<DiscoveryLoad> sources,
    required Set<String> followed,
    required String groupName,
    AiConfig? ai,
  }) {
    if (failLoad) throw StateError('Unavailable');
    return super.load(
      sources: [
        () async => [
          const DiscoveryAccount(
            source: DiscoverySource.x,
            id: '123',
            handle: 'artist',
            name: 'An artist',
            text: 'A discovered post',
            postUrl: '',
          ),
        ],
      ],
      followed: followed,
      groupName: groupName,
    );
  }
}

void main() {
  late PrefServiceCache prefs;
  late GroupsModel groups;
  late SubscriptionsModel subscriptions;
  late _Discovery discovery;

  setUp(() {
    prefs = PrefServiceCache();
    groups = GroupsModel(prefs);
    subscriptions = SubscriptionsModel(prefs, groups);
    discovery = _Discovery();
  });
  tearDown(() async {
    await subscriptions.destroy();
    await groups.destroy();
  });

  Widget app() => PrefService(
    service: prefs,
    child: Provider<SubscriptionsModel>.value(
      value: subscriptions,
      child: MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        // Pushed group routes supply a NestedScrollView, with no parent Scaffold.
        home: NestedScrollView(
          headerSliverBuilder: (_, _) => [const SliverAppBar(title: Text('Art'))],
          body: GroupDiscoveryPane(
            createStore: () => discovery,
            group: SubscriptionGroupGet(
              id: 'art',
              name: 'Art',
              icon: defaultGroupIcon,
              subscriptions: [],
              includeReplies: null,
              includeRetweets: null,
              popular: false,
            ),
          ),
        ),
      ),
    ),
  );

  testWidgets('inline Discovery renders cards in a pushed group route', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.text('An artist'), findsOneWidget);
    expect(find.text('A discovered post'), findsOneWidget);
  });
  testWidgets('a setup failure offers Retry and can recover', (tester) async {
    discovery.failLoad = true;
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.text('Retry'), findsOneWidget);
    expect(tester.takeException(), isNull);
    discovery.failLoad = false;
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.text('An artist'), findsOneWidget);
    expect(find.text('Retry'), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
