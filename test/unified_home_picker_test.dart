import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_timeline_picker.dart';

Widget pickerFixture(int count) => MaterialApp(
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  home: Scaffold(
    body: HomeTimelinePicker(
      selected: 'source-0',
      options: [
        for (var i = 0; i < count; i++)
          HomeTimelineOption(
            id: 'source-$i',
            label: 'Source $i',
            mark: const Icon(Icons.rss_feed),
            plugin: true,
            unread: i == 0,
          ),
      ],
    ),
  ),
);

void main() {
  testWidgets('ordinary source rows use at most 56px without shrinking targets', (tester) async {
    await tester.pumpWidget(pickerFixture(3));
    await tester.pumpAndSettle();
    final row = find.byKey(const ValueKey('home-source-source-0'));
    expect(tester.getSize(row).height, inInclusiveRange(48, 56));
    expect(find.byType(TextField), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('large chooser searches locally and preserves Add', (tester) async {
    await tester.pumpWidget(pickerFixture(12));
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'Source 11');
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('home-source-source-11')), findsOneWidget);
    expect(find.byKey(const ValueKey('home-source-source-0')), findsNothing);
    expect(find.byKey(const ValueKey('home-add-timeline')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
