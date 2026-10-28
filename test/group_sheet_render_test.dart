import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/subscriptions/group_membership_sheet.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/groups_harness.dart';

/// Renders the group picker in Inter for review:
///   fvm flutter test test/group_sheet_render_test.dart --dart-define=RENDER_GROUP_SHEET=true
const _render = bool.fromEnvironment('RENDER_GROUP_SHEET');

const _groups = [
  ('Ai', Color(0xFF3B82F6)),
  ('Ai Art', Color(0xFF8B5CF6)),
  ('Alternativeto', Color(0xFF64748B)),
  ('Anime', Color(0xFFE0457B)),
  ('Art (1)', Color(0xFF22A06B)),
  ('Art (2)', Color(0xFF84CC16)),
  ('Art NSFW', Color(0xFFD93B3B)),
  ('Asia', Color(0xFFF59E0B)),
  ('Black', Color(0xFF334155)),
  ('Blue', Color(0xFF0EA5E9)),
  ('China', Color(0xFFDC2626)),
];

List<SubscriptionGroup> _list() => [
  for (final (index, (name, color)) in _groups.indexed)
    SubscriptionGroup(
      id: 'g$index',
      name: name,
      icon: '',
      color: color,
      numberOfMembers: 4,
      createdAt: DateTime.utc(2024),
    ),
];

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    for (final weight in ['Regular', 'Medium', 'SemiBold', 'Bold']) {
      await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-$weight.ttf'))).load();
    }
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  const shot = ValueKey('group-sheet-shot');

  Future<void> open(WidgetTester tester, {required Locale locale, required double width, double scale = 1}) async {
    tester.view.physicalSize = Size(width, 844);
    tester.view.devicePixelRatio = 1;
    tester.view.padding = const FakeViewPadding(top: 24, bottom: 16);
    tester.platformDispatcher.textScaleFactorTestValue = scale;
    addTearDown(tester.view.reset);
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    final harness = GroupsHarness(groups: _list(), cache: {'subscription_groups.recent_picks': 'g7,g9,g6'});
    await harness.pump(
      tester,
      Scaffold(
        body: Builder(
          builder: (context) => Center(
            child: TextButton(
              onPressed: () => showGroupMembershipSheet(context, groups: harness.groups.state, selected: ['g3', 'g5']),
              child: const Text('open'),
            ),
          ),
        ),
      ),
      locale: locale,
      shotKey: shot,
      theme: xLookThemeData(XLookTokens.lightsOut.copyWith(accent: const Color(0xFF8B7CF6)), null),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
  }

  for (final (locale, width, scale) in const [
    (Locale('de'), 390.0, 1.0),
    (Locale('en'), 390.0, 1.0),
    (Locale('de'), 320.0, 2.0),
  ]) {
    final tag = '${locale.languageCode}-${width.round()}-x$scale';

    testWidgets('group-sheet-$tag', skip: !_render, (tester) async {
      await open(tester, locale: locale, width: width, scale: scale);
      expect(tester.takeException(), isNull);
      await expectLater(find.byKey(shot), matchesGoldenFile('../review-artifacts/renders/group-sheet-$tag.png'));
    });

    testWidgets('group-sheet-create-$tag', skip: !_render, (tester) async {
      await open(tester, locale: locale, width: width, scale: scale);
      await tester.enterText(find.byType(TextField), 'Japan');
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await expectLater(find.byKey(shot), matchesGoldenFile('../review-artifacts/renders/group-sheet-create-$tag.png'));
    });
  }
}
