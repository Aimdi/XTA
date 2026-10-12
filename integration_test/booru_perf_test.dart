import 'dart:io';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/main.dart' as app;
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_grid.dart';
import 'package:xta/plugins/booru/booru_post_media.dart';
import 'package:xta/plugins/booru/booru_post_pager.dart';
import 'package:xta/plugins/booru/booru_search_results.dart';
import 'package:xta/plugins/booru/booru_search_screen.dart';

/// Booru baseline scenarios S2 and S3 (docs/booru/audit.md, docs/perf-baseline.md).
///
/// Device-only, like integration_test/feed_scroll_perf_test.dart: it drives the
/// real app against live Danbooru, because the cost being measured is real
/// image decode, layout and paging. Run on a phone in profile mode:
///
///   fvm flutter drive \
///     --driver=test_driver/booru_perf_driver.dart \
///     --target=integration_test/booru_perf_test.dart \
///     --profile
///
/// Each scenario runs once as a warm-up, then [trials] times under
/// watchPerformance; the driver writes medians and p95 to
/// build/booru_perf_summary.json. The Booru site is switched to Danbooru for
/// the run and switched back afterwards.
const trials = 3;
const query = 'order:rank';
const gridPosts = 300;
const viewerSwipes = 20;

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  binding.framePolicy = LiveTestWidgetsFlutterBindingFramePolicy.benchmarkLive;

  testWidgets('booru S2 grid fling and S3 viewer swipes', (tester) async {
    app.main();
    await _waitFor(tester, find.byType(Navigator), const Duration(seconds: 60));
    final context = tester.element(find.byType(Navigator).first);
    await _useDanbooru(PrefService.of(context, listen: false));

    binding.reportData = {
      'env': {
        'refresh_rate': tester.view.display.refreshRate,
        'device_pixel_ratio': tester.view.devicePixelRatio,
        'os': Platform.operatingSystemVersion,
        'query': query,
        'grid_posts': gridPosts,
        'viewer_swipes': viewerSwipes,
        'trials': trials,
      },
    };

    tester
        .state<NavigatorState>(find.byType(Navigator).first)
        .push(MaterialPageRoute<void>(builder: (_) => const BooruSearchScreen(initialQuery: query)));
    await _waitFor(tester, find.byType(BooruPostTile), const Duration(seconds: 60));

    // S2: the warm-up pass loads the 300 posts; each trial flings back
    // through them from the top.
    await _flingThroughGrid(tester);
    for (var trial = 1; trial <= trials; trial++) {
      await _jumpToTop(tester);
      await binding.watchPerformance(() => _flingThroughGrid(tester), reportKey: 's2_trial_$trial');
    }

    // S3: open the first post, wait for its picture, swipe through 20 posts.
    // The warm-up pass loads them; trials reopen the same post.
    for (var run = 0; run <= trials; run++) {
      await _jumpToTop(tester);
      await tester.tap(find.byType(BooruPostTile).at(_firstPicture(tester)));
      final opened = Stopwatch()..start();
      await _waitForPicture(tester);
      opened.stop();
      Future<void> swipe() => _swipeViewer(tester);
      if (run == 0) {
        await swipe();
      } else {
        binding.reportData!['s3_open_ms_trial_$run'] = opened.elapsedMilliseconds;
        await binding.watchPerformance(swipe, reportKey: 's3_trial_$run');
      }
      tester.state<NavigatorState>(find.byType(Navigator).first).pop();
      await tester.pump(const Duration(seconds: 1));
    }
  }, timeout: const Timeout(Duration(minutes: 20)));
}

/// Points the Booru plugin at Danbooru for the run, restoring the reader's
/// site when the test ends.
Future<void> _useDanbooru(BasePrefService prefs) async {
  final danbooru = booruPresets.firstWhere((preset) => preset.id == 'danbooru');
  final keys = [optionPluginBooruEngine, optionPluginBooruHost, optionPluginBooruPreset];
  final saved = {for (final key in keys) key: prefs.get<String>(key)};
  addTearDown(() async {
    for (final MapEntry(:key, :value) in saved.entries) {
      if (value != null) await prefs.set(key, value);
    }
  });
  await prefs.set(optionPluginBooruEngine, danbooru.engine.id);
  await prefs.set(optionPluginBooruHost, danbooru.host);
  await prefs.set(optionPluginBooruPreset, danbooru.id);
}

Future<void> _waitFor(WidgetTester tester, Finder finder, Duration timeout) async {
  final deadline = DateTime.now().add(timeout);
  while (finder.evaluate().isEmpty) {
    if (DateTime.now().isAfter(deadline)) fail('timed out waiting for $finder');
    await tester.pump(const Duration(milliseconds: 200));
  }
}

Finder get _gridScrollable => find.descendant(of: find.byType(BooruPostGrid), matching: find.byType(Scrollable)).first;

int _loadedPosts(WidgetTester tester) =>
    tester.widget<BooruSearchResults>(find.byType(BooruSearchResults)).results.state.length;

/// Flings down until [gridPosts] posts are loaded and the last of them has
/// been scrolled past, or the host stops sending more.
Future<void> _flingThroughGrid(WidgetTester tester) async {
  for (var fling = 0; fling < 200; fling++) {
    final position = tester.state<ScrollableState>(_gridScrollable).position;
    final atEnd = position.pixels >= position.maxScrollExtent - 1;
    if (_loadedPosts(tester) >= gridPosts && atEnd) return;
    await tester.fling(_gridScrollable, const Offset(0, -900), 4000);
    await tester.pump(const Duration(milliseconds: 1200));
  }
}

Future<void> _jumpToTop(WidgetTester tester) async {
  tester.state<ScrollableState>(_gridScrollable).position.jumpTo(0);
  await tester.pump(const Duration(milliseconds: 500));
}

/// The first post in the grid that is a picture rather than a video, so S3
/// always measures an image post.
int _firstPicture(WidgetTester tester) {
  final tiles = tester.widgetList<BooruPostTile>(find.byType(BooruPostTile)).toList();
  final index = tiles.indexWhere((tile) => !tile.post.isVideo);
  if (index < 0) fail('no picture post on the first screen');
  return index;
}

/// Waits until the opened post's picture has decoded and painted.
Future<void> _waitForPicture(WidgetTester tester) async {
  final picture = find.descendant(of: find.byType(BooruPostMedia), matching: find.byType(ExtendedRawImage));
  final deadline = DateTime.now().add(const Duration(seconds: 30));
  while (!picture.evaluate().any((element) => (element.widget as ExtendedRawImage).image != null)) {
    if (DateTime.now().isAfter(deadline)) fail('the opened post never showed its picture');
    await tester.pump(const Duration(milliseconds: 50));
  }
}

Future<void> _swipeViewer(WidgetTester tester) async {
  final pager = find.descendant(of: find.byType(BooruPostPager), matching: find.byType(PageView));
  for (var swipe = 0; swipe < viewerSwipes; swipe++) {
    await tester.fling(pager, const Offset(-500, 0), 2000);
    await tester.pump(const Duration(milliseconds: 900));
  }
}
