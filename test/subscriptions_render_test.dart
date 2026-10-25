// ignore_for_file: avoid_print

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
import 'package:xta/subscriptions/subscriptions.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/subscriptions/widgets/group_tile.dart';
import 'package:xta/ui/x_look_theme.dart';

/// Renders the subscriptions screen in Inter for review:
///   fvm flutter test test/subscriptions_render_test.dart --dart-define=RENDER_SUBSCRIPTIONS=true
const _render = bool.fromEnvironment('RENDER_SUBSCRIPTIONS');

const _groups = [
  ('Ai', 14),
  ('Ai Art', 31),
  ('Anime', 22),
  ('Cooking', 8),
  ('Dart & Flutter', 17),
  ('Demographics', 5),
  ('Gaming', 40),
  ('Music', 12),
  ('News', 9),
  ('Photography', 19),
  ('Science', 11),
  ('Tech', 28),
  ('Travel', 6),
  ('Weather', 3),
];

class _Groups extends GroupsModel {
  _Groups(super.prefs) {
    update([
      for (final (index, (name, members)) in _groups.indexed)
        SubscriptionGroup(
          id: '$index',
          name: name,
          icon: defaultGroupIcon,
          color: null,
          numberOfMembers: members,
          createdAt: DateTime.utc(2024),
          memberPreviews: [
            for (var i = 0; i < 4; i++) GroupMemberPreview(id: '$name-$i', name: '$name $i', avatarUrl: null),
          ],
        ),
    ]);
  }
}

Future<void> _pump(WidgetTester tester, {required Locale locale, required double width, required bool compact}) async {
  tester.view.physicalSize = Size(width, 915);
  tester.view.devicePixelRatio = 1;
  tester.view.padding = const FakeViewPadding(top: 24);
  addTearDown(tester.view.reset);
  final prefs = PrefServiceCache(
    cache: {
      optionSubscriptionGroupsOrderByField: 'name',
      optionSubscriptionGroupsOrderByAscending: true,
      optionSubscriptionGroupsLayout: subscriptionGroupsLayoutBoard,
      optionSubscriptionGroupsColumns: 2,
      optionSubscriptionGroupsCompact: compact,
      optionSubscriptionOrderByField: 'name',
      optionSubscriptionOrderByAscending: true,
      optionSubscriptionOrderCustom: '',
      optionDisableAnimations: true,
      optionAiBaseUrl: '',
      optionAiApiKey: '',
      optionAiModel: '',
    },
  );
  final groups = _Groups(prefs);
  final people = SubscriptionsModel(prefs, groups);
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
          theme: xLookLightsOutTheme(null),
          locale: locale,
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          home: RepaintBoundary(
            key: const ValueKey('subscriptions-shot'),
            child: SubscriptionsScreen(scrollController: ScrollController()),
          ),
        ),
      ),
    ),
  );
  for (var i = 0; i < 10; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    for (final weight in ['Regular', 'Medium', 'SemiBold', 'Bold']) {
      await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-$weight.ttf'))).load();
    }
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  for (final (locale, width) in const [(Locale('de'), 412.0), (Locale('en'), 412.0), (Locale('de'), 360.0)]) {
    for (final compact in [false, true]) {
      final name = 'subscriptions-${locale.languageCode}-${width.round()}-${compact ? 'kompakt' : 'board'}';
      testWidgets(name, skip: !_render, (tester) async {
        await _pump(tester, locale: locale, width: width, compact: compact);
        expect(tester.takeException(), isNull);

        final appBar = tester.widget<AppBar>(find.byType(AppBar));
        final header = tester.getRect(find.byType(AppBar));
        final tiles = find.byType(GroupTile).evaluate().map((e) => tester.getRect(find.byWidget(e.widget)));
        final fullyVisible = tiles.where((r) => r.bottom <= 915).length;
        print(
          '$name: header ${header.height - 24} dp ${appBar.bottom == null ? 'one row' : 'two rows'}, '
          'tab icons ${find.byIcon(Icons.folder_outlined).evaluate().isNotEmpty}, '
          'first tile at ${tiles.first.top} dp, $fullyVisible groups fully on screen',
        );
        await expectLater(
          find.byKey(const ValueKey('subscriptions-shot')),
          matchesGoldenFile('../review-artifacts/renders/$name.png'),
        );
      });
    }
  }
}
