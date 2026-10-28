import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/group_ungrouped_screen.dart';
import 'package:xta/subscriptions/group_ungrouped_sorter_screen.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/x_look_theme.dart';

/// Renders the one-by-one sorter in Inter for review:
///   fvm flutter test test/group_ungrouped_sorter_render_test.dart --dart-define=RENDER_SORTER=true
const _render = bool.fromEnvironment('RENDER_SORTER');

const _groups = [
  ('Art', Color(0xFFE0457B)),
  ('Japan', Color(0xFFD93B3B)),
  ('News', Color(0xFF3B82F6)),
  ('Games', Color(0xFF22A06B)),
  ('Photography', Color(0xFFF59E0B)),
  ('Science', Color(0xFF8B5CF6)),
  ('Dart & Flutter', Color(0xFF0EA5E9)),
];

final _accounts = [
  for (final (index, (name, handle)) in [
    ('Mika Tanaka', 'mk_t0927'),
    ('Kai', 'x_1029'),
    ('Studio Ghibli Archive', 'ghibli_archive_jp'),
    for (var i = 0; i < 38; i++) ('Account $i', 'account_$i'),
  ].indexed)
    UserSubscription(
      id: '$index',
      screenName: handle,
      name: name,
      profileImageUrlHttps: null,
      verified: false,
      createdAt: DateTime.utc(2024),
      inFeed: true,
    ),
];

class _Groups extends GroupsModel {
  _Groups(super.prefs) {
    update([
      for (final (index, (name, color)) in _groups.indexed)
        SubscriptionGroup(
          id: '$index',
          name: name,
          icon: defaultGroupIcon,
          color: color,
          numberOfMembers: 4,
          createdAt: DateTime.utc(2024),
        ),
    ]);
  }

  @override
  Future<void> addToGroups(String profile, Iterable<String> groupIds) async {}

  @override
  Future<List<SubscriptionGroupMember>> listGroupMembers() async => [
    SubscriptionGroupMember(group: '2', profile: 'nasa'),
  ];
}

Future<void> _pump(
  WidgetTester tester,
  Widget home, {
  required Locale locale,
  required Size size,
  double scale = 1,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  tester.view.padding = const FakeViewPadding(top: 24, bottom: 16);
  tester.platformDispatcher.textScaleFactorTestValue = scale;
  addTearDown(tester.view.reset);
  addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
  final prefs = PrefServiceCache(
    cache: {
      optionSubscriptionGroupsOrderByField: 'name',
      optionSubscriptionGroupsOrderByAscending: true,
      optionDisableAnimations: true,
      optionAiBaseUrl: '',
      optionAiApiKey: '',
      optionAiModel: '',
    },
  );
  final groups = _Groups(prefs);
  final people = SubscriptionsModel(prefs, groups)
    ..update([
      UserSubscription(
        id: 'nasa',
        screenName: 'nasa',
        name: 'NASA',
        profileImageUrlHttps: null,
        verified: false,
        createdAt: DateTime.utc(2024),
        inFeed: true,
      ),
      UserSubscription(
        id: 'apnews',
        screenName: 'ap_news',
        name: 'AP News',
        profileImageUrlHttps: null,
        verified: false,
        createdAt: DateTime.utc(2024),
        inFeed: true,
      ),
      ..._accounts.take(3),
    ]);
  addTearDown(groups.destroy);
  addTearDown(people.destroy);
  await tester.pumpWidget(
    PrefService(
      service: prefs,
      child: MultiProvider(
        providers: [
          Provider<GroupsModel>.value(value: groups),
          Provider<SubscriptionsModel>.value(value: people),
        ],
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: xLookThemeData(XLookTokens.lightsOut.copyWith(accent: const Color(0xFF8B7CF6)), null),
          locale: locale,
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          home: RepaintBoundary(key: const ValueKey('sorter-shot'), child: home),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    for (final weight in ['Regular', 'Medium', 'SemiBold', 'Bold']) {
      await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-$weight.ttf'))).load();
    }
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  Future<void> shot(WidgetTester tester, String name) async {
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(const ValueKey('sorter-shot')),
      matchesGoldenFile('../review-artifacts/renders/$name.png'),
    );
  }

  for (final (locale, width, scale) in const [
    (Locale('de'), 390.0, 1.0),
    (Locale('en'), 390.0, 1.0),
    (Locale('de'), 320.0, 2.0),
  ]) {
    final tag = '${locale.languageCode}-${width.round()}-x$scale';

    testWidgets('sorter-card-$tag', skip: !_render, (tester) async {
      await _pump(
        tester,
        UngroupedSorterScreen(accounts: _accounts),
        locale: locale,
        size: Size(width, 844),
        scale: scale,
      );
      await tester.tap(find.byKey(const ValueKey('sorter-group-0')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('sorter-skip')));
      await tester.pumpAndSettle();
      await shot(tester, 'sorter-card-$tag');
    });

    testWidgets('sorter-end-$tag', skip: !_render, (tester) async {
      await _pump(
        tester,
        UngroupedSorterScreen(accounts: _accounts.take(3).toList()),
        locale: locale,
        size: Size(width, 844),
        scale: scale,
      );
      await tester.tap(find.byKey(const ValueKey('sorter-group-0')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('sorter-group-1')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('sorter-skip')));
      await tester.pumpAndSettle();
      await shot(tester, 'sorter-end-$tag');
    });

    testWidgets('sorter-plan-$tag', skip: !_render, (tester) async {
      await _pump(tester, const SortUngroupedScreen(), locale: locale, size: Size(width, 844), scale: scale);
      await shot(tester, 'sorter-plan-$tag');
    });
  }
}
