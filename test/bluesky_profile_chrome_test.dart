import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_screen.dart';

Widget _app(Widget child) {
  return MaterialApp(
    locale: const Locale('en'),
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    home: Scaffold(body: child),
  );
}

const _alice = BlueskyProfile(
  did: 'did:plc:abc',
  handle: 'alice.bsky.social',
  displayName: 'Alice',
  description: 'Hello from the other side',
  createdAt: DateTime(2023, 4, 13),
  followersCount: 340,
  followsCount: 12,
);

void main() {
  test('home remounts with posts already painted do not fetch', () {
    expect(
      blueskyHomeShouldFetch(force: false, feedEmpty: false),
      isFalse,
    );
  });

  test('the first empty paint still fetches', () {
    expect(blueskyHomeShouldFetch(force: false, feedEmpty: true), isTrue);
  });

  test('pull-to-refresh fetches even when posts are on screen', () {
    expect(blueskyHomeShouldFetch(force: true, feedEmpty: false), isTrue);
  });

  testWidgets('profile identity matches the X header: name, handle, joined, counts', (
    tester,
  ) async {
    await tester.pumpWidget(_app(const BlueskyProfileCard(profile: _alice)));
    await tester.pumpAndSettle();

    expect(find.text('Alice'), findsOneWidget);
    expect(find.text('@alice.bsky.social'), findsOneWidget);
    expect(find.text('Hello from the other side'), findsOneWidget);
    expect(find.textContaining('Joined'), findsOneWidget);
    expect(find.textContaining('following'), findsOneWidget);
    expect(find.textContaining('followers'), findsOneWidget);
    expect(find.byWidgetPredicate((w) => w is SegmentedButton), findsNothing);
    expect(find.text('Follow'), findsNothing);
  });
}
