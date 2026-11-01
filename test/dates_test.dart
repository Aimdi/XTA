import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:timeago/timeago.dart' as timeago;
import 'package:xta/ui/dates.dart';

void main() {
  setUpAll(() {
    timeago.setLocaleMessages('en_short', timeago.EnShortMessages());
    compactDateLocales.add('en');
  });

  test('compact date uses the short form when the locale has one', () {
    final stamp = createCompactDate(DateTime.now().subtract(const Duration(minutes: 5)));
    expect(stamp, '5m');
  });

  test('compact date keeps the long form for a locale without short messages', () {
    final fiveMinutesAgo = DateTime.now().subtract(const Duration(minutes: 5));
    compactDateLocales.remove('en');
    addTearDown(() => compactDateLocales.add('en'));

    expect(createCompactDate(fiveMinutesAgo), createRelativeDate(fiveMinutesAgo));
  });

  testWidgets('tapping a relative timestamp shows the absolute date', (tester) async {
    final timestamp = DateTime.now().subtract(const Duration(minutes: 5));
    await tester.pumpWidget(MaterialApp(home: Timestamp(timestamp: timestamp)));

    expect(find.text('5 minutes ago'), findsOneWidget);

    await tester.tap(find.byType(Timestamp));
    await tester.pump();

    expect(find.text('5 minutes ago'), findsNothing);
    expect(find.text(absoluteDateFormat().format(timestamp.toLocal())), findsOneWidget);
  });

  testWidgets('a timestamp a list hands to another post says that post\'s time', (tester) async {
    final now = DateTime.now();
    await tester.pumpWidget(MaterialApp(home: Timestamp(timestamp: now.subtract(const Duration(minutes: 5)))));
    await tester.tap(find.byType(Timestamp));
    await tester.pump();

    // The same element, as an unkeyed card scrolled into another post's place.
    await tester.pumpWidget(MaterialApp(home: Timestamp(timestamp: now.subtract(const Duration(hours: 3)))));
    expect(find.text('3 hours ago'), findsOneWidget, reason: 'relative, as a fresh timestamp starts out');
  });
}
