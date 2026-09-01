import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:quax/constants.dart';
import 'package:quax/settings/settings_chrome.dart';
import 'package:quax/settings/settings_view_store.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/ui/x_look_theme.dart';

Widget _app(Widget child) => MaterialApp(
  theme: xLookLightTheme(null),
  home: Scaffold(body: child),
);

void main() {
  test('export selection enforces group-member dependencies', () {
    final store = SettingsExportStore();
    addTearDown(store.destroy);

    store.toggle(SettingsExportOption.subscriptions);
    store.toggle(SettingsExportOption.groups);
    store.toggle(SettingsExportOption.groupMembers);
    expect(store.state.canIncludeGroupMembers, isTrue);
    expect(store.state.includes(SettingsExportOption.groupMembers), isTrue);

    store.toggle(SettingsExportOption.groups);
    expect(store.state.canIncludeGroupMembers, isFalse);
    expect(store.state.includes(SettingsExportOption.groupMembers), isFalse);
  });

  testWidgets('settings rows retain the established touch target', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        const SettingsRow(
          icon: Icons.tune_outlined,
          title: 'Reading preferences',
          description: 'A supporting description that may wrap.',
        ),
      ),
    );

    expect(
      tester.getSize(find.byType(SettingsRow)).height,
      greaterThanOrEqualTo(kTweetTouchTarget),
    );
  });

  testWidgets('preference selector exposes and persists selected state', (
    tester,
  ) async {
    final prefs = PrefServiceCache(
      cache: {optionMediaGridLayout: mediaGridLayoutMasonry},
    );
    await tester.pumpWidget(
      _app(
        SettingsPreferenceSelector<String>(
          prefs: prefs,
          pref: optionMediaGridLayout,
          options: const [
            SettingsOption(
              value: mediaGridLayoutMasonry,
              label: 'Masonry',
              icon: Icons.dashboard_outlined,
            ),
            SettingsOption(
              value: mediaGridLayoutFeed,
              label: 'Timeline',
              icon: Icons.view_agenda_outlined,
            ),
          ],
        ),
      ),
    );

    await tester.tap(find.text('Timeline'));
    await tester.pump();

    expect(prefs.get<String>(optionMediaGridLayout), mediaGridLayoutFeed);
    final selectedSemantics = tester
        .widgetList<Semantics>(
          find.ancestor(
            of: find.text('Timeline'),
            matching: find.byType(Semantics),
          ),
        )
        .where((widget) => widget.properties.selected != null)
        .single;
    expect(selectedSemantics.properties.selected, isTrue);
  });
}
