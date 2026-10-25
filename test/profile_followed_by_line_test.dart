import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_discovery_follows.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/profile/profile_followed_by.dart';
import 'package:xta/profile/profile_followed_by_line.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/user.dart';

UserSubscription _sub(String id) => UserSubscription(
  id: id,
  screenName: id,
  name: 'Name $id',
  profileImageUrlHttps: null,
  verified: false,
  createdAt: DateTime(2026),
  inFeed: true,
);

/// [followers] subscriptions follow `p`; one more was read and does not.
Map<String, RememberedFollows> _remembered(int followers) => {
  for (var i = 0; i < followers; i++)
    's$i': (at: DateTime(2026, 10), follows: const [DiscoveryFollow(id: 'p', handle: 'p', name: 'P')]),
  'other': (at: DateTime(2026, 10), follows: const []),
};

Future<void> _pump(WidgetTester tester, int followers, {Locale locale = const Locale('en'), bool settle = true}) async {
  final prefs = PrefServiceCache();
  final groups = GroupsModel(prefs);
  final subscriptions = SubscriptionsModel(prefs, groups);
  addTearDown(subscriptions.destroy);
  addTearDown(groups.destroy);
  final subs = [for (var i = 0; i < followers; i++) _sub('s$i'), _sub('other'), _sub('unread')];
  await tester.pumpWidget(
    MultiProvider(
      providers: [
        Provider.value(value: groups),
        Provider.value(value: subscriptions),
      ],
      child: MaterialApp(
        locale: locale,
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: Scaffold(
          body: ProfileFollowedByLine(
            profileId: 'p',
            subscriptions: subs,
            createStore: () => ProfileFollowedByStore(remembered: () async => _remembered(followers)),
          ),
        ),
      ),
    ),
  );
  if (settle) await tester.pumpAndSettle();
}

int _avatars() =>
    find.descendant(of: find.byType(ProfileFollowedByRow), matching: find.byType(UserAvatar)).evaluate().length;

void main() {
  testWidgets('no known follower shows nothing', (tester) async {
    await _pump(tester, 0);
    expect(find.byType(ProfileFollowedByRow), findsNothing);
    expect(find.textContaining('Followed by'), findsNothing);
  });

  testWidgets('the row grows and fades in once its followers are known', (tester) async {
    await _pump(tester, 1, settle: false);
    final line = find.byType(ProfileFollowedByLine);
    for (var frame = 0; frame < 30 && find.byType(ProfileFollowedByRow).evaluate().isEmpty; frame++) {
      await tester.pump(const Duration(milliseconds: 16));
    }
    await tester.pump(const Duration(milliseconds: 90));
    final rowOpacity = tester
        .widget<Opacity>(find.ancestor(of: find.byType(ProfileFollowedByRow), matching: find.byType(Opacity)).first)
        .opacity;
    expect(rowOpacity, inExclusiveRange(0, 1));
    final growing = tester.getSize(line).height;

    await tester.pumpAndSettle();
    expect(growing, inExclusiveRange(0, tester.getSize(line).height));
  });

  testWidgets('one follower', (tester) async {
    await _pump(tester, 1);
    expect(find.text('Followed by Name s0'), findsOneWidget);
    expect(_avatars(), 1);
  });

  testWidgets('two followers', (tester) async {
    await _pump(tester, 2);
    expect(find.text('Followed by Name s0 and Name s1'), findsOneWidget);
    expect(_avatars(), 2);
  });

  testWidgets('three followers', (tester) async {
    await _pump(tester, 3);
    expect(find.text('Followed by Name s0, Name s1 and Name s2'), findsOneWidget);
    expect(_avatars(), 3);
  });

  testWidgets('many followers cap the avatars and open the list', (tester) async {
    await _pump(tester, 14);
    expect(find.text('Followed by Name s0, Name s1 and 12 others you subscribe to'), findsOneWidget);
    expect(_avatars(), 3);
    expect(tester.getSize(find.byType(ProfileFollowedByRow)).height, greaterThanOrEqualTo(48));

    await tester.tap(find.byType(ProfileFollowedByRow));
    await tester.pumpAndSettle();

    expect(find.text('Followers you subscribe to'), findsOneWidget);
    expect(find.byType(UserTile), findsWidgets);
    expect(find.text('Name s0'), findsOneWidget);
    await tester.scrollUntilVisible(find.textContaining('for 15 of your 16 subscriptions'), 200);
    expect(find.textContaining('for 15 of your 16 subscriptions'), findsOneWidget);
  });

  testWidgets('a long German line wraps on a narrow screen', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await _pump(tester, 40, locale: const Locale('de'));
    expect(find.textContaining('und 38 weiteren deiner Abos'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
