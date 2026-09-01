import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:xta/main.dart' as app;

/// Scroll-jank benchmark for the feed.
///
/// Device-only, and deliberately not part of `flutter test`: it drives the real
/// app against real data, because the jank being measured comes from media
/// tiles, text layout and image decode at real sizes, none of which a synthetic
/// list reproduces honestly.
///
/// Run it on a phone that is already signed in:
///
///   flutter drive \
///     --driver=test_driver/scroll_perf_test.dart \
///     --target=integration_test/feed_scroll_perf_test.dart \
///     --profile
///
/// Profile mode matters — a debug build's numbers say nothing about what a
/// reader experiences. The summary lands in `build/feed_scroll_summary.json`;
/// record it in `docs/perf-baseline.md`.
void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('scrolling the feed holds its frame budget', (tester) async {
    app.main();
    await tester.pumpAndSettle(const Duration(seconds: 2));

    // The feed loads over the network, so settling is not enough — wait until
    // something scrollable actually exists before timing anything.
    final feed = find.byType(Scrollable).first;
    for (var attempt = 0; attempt < 30 && feed.evaluate().isEmpty; attempt++) {
      await tester.pump(const Duration(seconds: 1));
    }
    expect(feed, findsWidgets, reason: 'no feed to scroll — is the device signed in?');

    await binding.traceAction(() async {
      for (var i = 0; i < 10; i++) {
        await tester.fling(feed, const Offset(0, -600), 2000);
        await tester.pumpAndSettle();
      }
      for (var i = 0; i < 10; i++) {
        await tester.fling(feed, const Offset(0, 600), 2000);
        await tester.pumpAndSettle();
      }
    }, reportKey: 'scroll_timeline');
  });
}
