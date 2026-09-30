import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/reading/feed_appearance_controls.dart';
import 'package:xta/reading/feed_appearance_store.dart';
import 'support/bluesky_reading_harness.dart';

void main() {
  testWidgets('controls expose independent nullable media override', (tester) async {
    final host = BlueReadingHarness();
    final store = FeedAppearanceStore(host.prefs);
    const feed = FeedIdentity('bluesky', 'following');
    await tester.pumpWidget(
      host.app(
        Scaffold(
          body: SingleChildScrollView(
            child: FeedAppearanceControls(feed: feed, store: store),
          ),
        ),
      ),
    );
    await tester.pump();
    expect(find.byKey(const ValueKey('appearance-media')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('appearance-media')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Hide').last);
    await tester.pumpAndSettle();
    expect(store.appearance(feed).media, isFalse);
    expect(store.appearance(feed).counts, isNull);
    await host.close(tester);
    await store.destroy();
  });
}
