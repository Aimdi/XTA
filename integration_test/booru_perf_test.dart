import 'dart:io';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/main.dart' as app;
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_grid.dart';
import 'package:xta/plugins/booru/booru_post_media.dart';
import 'package:xta/plugins/booru/booru_post_pager.dart';
import 'package:xta/plugins/booru/booru_screen.dart';
import 'package:xta/plugins/booru/booru_search_landing.dart';
import 'package:xta/plugins/booru/booru_search_results.dart';
import 'package:xta/plugins/booru/booru_search_screen.dart';
import 'package:xta/tweet/_video_controls.dart';
import 'package:xta/tweet/video_controller_pool.dart';

/// Booru baseline scenarios S1–S7 (docs/booru/audit.md, docs/perf-baseline.md).
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
/// Timed scenarios run once as a warm-up, then [trials] times; the driver
/// writes medians and p95 to build/booru_perf_summary.json. The Booru site is
/// switched to Danbooru, without credentials, for the run and switched back
/// afterwards. `--dart-define=BOORU_HOST=<url>` measures another
/// Danbooru-compatible host instead, such as a mirror.
const trials = 3;
const query = 'order:rank';
const videoQuery = 'animated';
const gridPosts = 300;
const viewerSwipes = 20;
const videoPosts = 10;
const warmUpPrefix = 'ar';
const suggestionPrefixes = ['bl', 'ha', 'lo', 'sm', 'ori'];
const hostOverride = String.fromEnvironment('BOORU_HOST');
const _mb = 1024 * 1024;

typedef Report = Map<String, dynamic>;

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  binding.framePolicy = LiveTestWidgetsFlutterBindingFramePolicy.benchmarkLive;

  testWidgets('booru baseline S1–S7', (tester) async {
    final launch = Stopwatch()..start();
    app.main();
    await _waitFor(tester, find.byType(Navigator), timeout: const Duration(seconds: 60));
    launch.stop();
    final context = tester.element(find.byType(Navigator).first);
    final host = await _useDanbooru(PrefService.of(context, listen: false));
    final pool = context.read<VideoControllerPool>();
    final Report report = {'env': _environment(tester, host), 's1_main_to_navigator_ms': launch.elapsedMilliseconds};
    binding.reportData = report;

    await _s1OpenBooru(tester, report);
    final results = _push(tester, const BooruSearchScreen(initialQuery: query));
    await _waitFor(tester, _in(results, find.byType(BooruPostTile)), timeout: const Duration(seconds: 60));
    report['s7_grid_screenful'] = await _screenfulBytes(tester, results);
    await _s2GridFling(binding, tester, results, report);
    await _s3Viewer(binding, tester, results, report);
    report['s6_after_s2_s3'] = _memory(pool);
    await _memoryPressure(tester);
    report['s6_after_memory_pressure'] = _memory(pool);
    await _pop(tester);
    await _s4Autocomplete(tester, report);
    await _s5Videos(tester, pool, report);
  }, timeout: const Timeout(Duration(minutes: 30)));
}

Map<String, Object> _environment(WidgetTester tester, String host) => {
  'refresh_rate': tester.view.display.refreshRate,
  'device_pixel_ratio': tester.view.devicePixelRatio,
  'os': Platform.operatingSystemVersion,
  'host': host,
  'query': query,
  'video_query': videoQuery,
  'grid_posts': gridPosts,
  'viewer_swipes': viewerSwipes,
  'video_posts': videoPosts,
  'trials': trials,
  'video_pool_limit': kVideoPoolSize,
  'image_cache_limit_mb': PaintingBinding.instance.imageCache.maximumSizeBytes / _mb,
};

/// Points the Booru plugin at Danbooru (or [hostOverride]) with no
/// credentials, so a key saved for another site is never sent to it, and
/// lets media load without a tap, so S5 starts each video with the player's
/// own button. The reader's settings come back when the test ends.
Future<String> _useDanbooru(BasePrefService prefs) async {
  final danbooru = booruPresets.firstWhere((preset) => preset.id == 'danbooru');
  final host = hostOverride.isEmpty ? danbooru.host : hostOverride;
  final run = <String, Object>{
    optionPluginBooruEngine: danbooru.engine.id,
    optionPluginBooruHost: host,
    optionPluginBooruPreset: danbooru.id,
    optionPluginBooruLogin: '',
    optionPluginBooruApiKey: '',
    optionMediaDisableAutoload: false,
  };
  final saved = {for (final key in run.keys) key: prefs.get<Object>(key)};
  addTearDown(() => _restore(prefs, saved));
  for (final MapEntry(:key, :value) in run.entries) {
    await prefs.set(key, value);
  }
  return host;
}

Future<void> _restore(BasePrefService prefs, Map<String, Object?> saved) async {
  for (final MapEntry(:key, :value) in saved.entries) {
    if (value == null) {
      await prefs.remove(key);
    } else {
      await prefs.set(key, value);
    }
  }
}

/// S1: open the Booru home until its first thumbnail paints. Outside Home
/// each open builds its own stores, so every run fetches the first page;
/// after the warm-up its pictures can come from the image caches.
Future<void> _s1OpenBooru(WidgetTester tester, Report report) async {
  for (var run = 0; run <= trials; run++) {
    final scroll = ScrollController();
    final opened = Stopwatch()..start();
    final home = _push(tester, BooruScreen(scrollController: scroll));
    await _waitUntil(tester, () => _in(home, find.byType(BooruPostTile)).evaluate().any(_decoded), 'a thumbnail');
    opened.stop();
    if (run > 0) report['s1_open_ms_trial_$run'] = opened.elapsedMilliseconds;
    await _pop(tester);
    scroll.dispose();
  }
}

/// S2: the warm-up pass loads [gridPosts] posts; each trial flings back
/// through them from the top.
Future<void> _s2GridFling(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
  Finder results,
  Report report,
) async {
  await _flingThroughGrid(tester, results);
  for (var trial = 1; trial <= trials; trial++) {
    await _jumpToTop(tester, results);
    await binding.watchPerformance(() => _flingThroughGrid(tester, results), reportKey: 's2_trial_$trial');
    report['s2_rss_mb_trial_$trial'] = ProcessInfo.currentRss / _mb;
  }
}

/// S3: open the first picture post, wait for its picture, swipe through
/// [viewerSwipes] posts. The warm-up run also weighs each picture it showed,
/// for S7.
Future<void> _s3Viewer(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
  Finder results,
  Report report,
) async {
  for (var run = 0; run <= trials; run++) {
    await _jumpToTop(tester, results);
    await tester.tap(_in(results, find.byType(BooruPostTile)).at(_firstPicture(tester, results)));
    final opened = Stopwatch()..start();
    await _waitUntil(tester, () => find.byType(BooruPostMedia).evaluate().any(_decoded), 'the opened picture');
    opened.stop();
    if (run == 0) {
      report['s7_viewer_posts'] = await _viewerBytes(tester);
    } else {
      report['s3_open_ms_trial_$run'] = opened.elapsedMilliseconds;
      await binding.watchPerformance(() => _swipeViewer(tester), reportKey: 's3_trial_$run');
    }
    await _pop(tester);
  }
}

/// S4: from a keystroke to the suggestions on screen, the store's debounce
/// included. The second pass repeats the prefixes, to show whether anything
/// is cached. A prefix whose suggestions never came is null.
Future<void> _s4Autocomplete(WidgetTester tester, Report report) async {
  final search = _push(tester, const BooruSearchScreen());
  await _waitFor(tester, _in(search, _tagInput));
  await _suggestionMs(tester, search, warmUpPrefix);
  for (final pass in ['first', 'repeat']) {
    report['s4_${pass}_ms'] = [for (final prefix in suggestionPrefixes) await _suggestionMs(tester, search, prefix)];
  }
  await _pop(tester);
}

Future<int?> _suggestionMs(WidgetTester tester, Finder search, String prefix) async {
  _type(tester, search, '');
  await tester.pump(const Duration(milliseconds: 500));
  final typed = Stopwatch()..start();
  _type(tester, search, prefix);
  final shown = await _waitUntil(tester, () => _suggestionsFor(search, prefix), prefix, required: false);
  return shown ? typed.elapsedMilliseconds : null;
}

/// S5: open and close [videoPosts] video posts, counting the players the
/// shared pool holds while each plays and after it closes.
Future<void> _s5Videos(WidgetTester tester, VideoControllerPool pool, Report report) async {
  final results = _push(tester, const BooruSearchScreen(initialQuery: videoQuery));
  await _waitFor(tester, _in(results, find.byType(BooruPostTile)), timeout: const Duration(seconds: 60));
  final before = _memory(pool);
  final opened = <String>[];
  final playing = <int>[];
  final closed = <int>[];
  for (
    var post = await _nextVideo(tester, results, opened);
    post != null;
    post = await _nextVideo(tester, results, opened)
  ) {
    opened.add(post);
    playing.add(await _playVideo(tester, results, post, pool));
    await _pop(tester);
    closed.add(pool.cachedKeys.length);
  }
  report['s5_videos'] = {
    'opened': opened.length,
    'players_while_playing': playing,
    'players_after_close': closed,
    'before': before,
    'after': _memory(pool),
  };
  await _memoryPressure(tester);
  report['s5_players_after_memory_pressure'] = pool.cachedKeys.length;
  await _pop(tester);
}

/// A video post in the grid not opened yet, flinging for more when none is
/// built. Null once [videoPosts] were opened or the host has no more.
Future<String?> _nextVideo(WidgetTester tester, Finder results, List<String> opened) async {
  if (opened.length >= videoPosts) return null;
  for (var fling = 0; fling < 20; fling++) {
    final next = tester
        .widgetList<BooruPostTile>(_in(results, find.byType(BooruPostTile)))
        .map((tile) => tile.post)
        .where((post) => post.videoUrl != null && !opened.contains(post.key))
        .firstOrNull;
    if (next != null) return next.key;
    await tester.fling(_gridScrollable(results), const Offset(0, -900), 4000);
    await tester.pump(const Duration(milliseconds: 1200));
  }
  return null;
}

/// Opens the post, starts its video when it waits for a tap, and returns how
/// many players the pool holds once it has played for two seconds.
Future<int> _playVideo(WidgetTester tester, Finder results, String key, VideoControllerPool pool) async {
  final tile = _in(results, find.byWidgetPredicate((widget) => widget is BooruPostTile && widget.post.key == key));
  await tester.ensureVisible(tile);
  await tester.pump(const Duration(milliseconds: 500));
  await tester.tap(tile);
  await tester.pump(const Duration(seconds: 2));
  final play = find.descendant(of: find.byType(BooruPostMedia), matching: find.byType(FritterCenterPlayButton));
  if (play.evaluate().isNotEmpty) await _tapVisible(tester, play.first);
  final id = key.substring(key.lastIndexOf(':') + 1);
  await _waitUntil(
    tester,
    () => pool.cachedKeys.any((cached) => cached.contains('-$id:')),
    'a player',
    required: false,
  );
  await tester.pump(const Duration(seconds: 2));
  return pool.cachedKeys.length;
}

/// Taps the middle of the part of [finder] that is on screen. The play
/// button covers the whole poster, which on a tall post is taller than the
/// screen, so its centre can be out of reach.
Future<void> _tapVisible(WidgetTester tester, Finder finder) async {
  final box = tester.renderObject<RenderBox>(finder);
  final screen = Offset.zero & tester.view.physicalSize / tester.view.devicePixelRatio;
  await tester.tapAt((box.localToGlobal(Offset.zero) & box.size).intersect(screen).center);
}

Map<String, num> _memory(VideoControllerPool pool) {
  final images = PaintingBinding.instance.imageCache;
  return {
    'rss_mb': ProcessInfo.currentRss / _mb,
    'image_cache_mb': images.currentSizeBytes / _mb,
    'images_cached': images.currentSize,
    'images_live': images.liveImageCount,
    'video_players': pool.cachedKeys.length,
  };
}

/// What Android's trim-memory callback asks of the framework: the decoded
/// image cache is cleared and observers are told, so the video pool lets idle
/// players go. Only `adb shell am send-trim-memory` also trims the engine and
/// the VM; docs/perf-baseline.md has that run.
Future<void> _memoryPressure(WidgetTester tester) async {
  await tester.binding.defaultBinaryMessenger.handlePlatformMessage(
    SystemChannels.system.name,
    SystemChannels.system.codec.encodeMessage(<String, Object>{'type': 'memoryPressure'}),
    (_) {},
  );
  await tester.pump(const Duration(seconds: 2));
}

/// S7: the pictures behind the first screenful of the grid, weighed in
/// extended_image's disk cache. API responses are not counted.
Future<Map<String, Object>> _screenfulBytes(WidgetTester tester, Finder results) async {
  final settled = await _waitUntil(
    tester,
    () => _visibleTiles(tester, results).every(_decoded),
    'the first screenful',
    timeout: const Duration(seconds: 60),
    required: false,
  );
  await tester.pump(const Duration(seconds: 1));
  final tiles = _visibleTiles(tester, results);
  return {'tiles': tiles.length, 'all_decoded': settled, 'sizes': await _cachedSizes(tiles.expand(_imageUrls))};
}

/// S7: the warm-up swipes, noting each post's picture, weighed in the disk
/// cache. With the default settings a tile shows the same sample as the
/// viewer, so the grid has often downloaded it already.
Future<Map<String, Object>> _viewerBytes(WidgetTester tester) async {
  final urls = <String>{};
  for (var swipe = 0; swipe <= viewerSwipes; swipe++) {
    urls.addAll(find.byType(BooruPostMedia).evaluate().expand(_imageUrls));
    if (swipe < viewerSwipes) await _swipeOnce(tester);
  }
  await tester.pump(const Duration(seconds: 2));
  return {'posts': viewerSwipes + 1, 'sizes': await _cachedSizes(urls)};
}

/// Sizes of the files extended_image saved for [urls]. A picture still
/// downloading, or never shown, has none.
Future<List<int>> _cachedSizes(Iterable<String> urls) async {
  final folder = path.join((await getTemporaryDirectory()).path, cacheImageFolderName);
  final files = urls.toSet().map((url) => File(path.join(folder, keyToMd5(url))));
  return [
    for (final file in files)
      if (file.existsSync()) file.lengthSync(),
  ];
}

Iterable<String> _imageUrls(Element root) => _below<ExtendedImage>(root).map((image) => _urlOf(image.image)).nonNulls;

String? _urlOf(ImageProvider provider) => switch (provider) {
  ExtendedResizeImage(:final imageProvider) => _urlOf(imageProvider),
  ExtendedNetworkImageProvider(:final url) => url,
  _ => null,
};

bool _decoded(Element root) => _below<ExtendedRawImage>(root).any((image) => image.image != null);

List<T> _below<T extends Widget>(Element root) {
  final found = <T>[];
  void visit(Element element) {
    if (element.widget case final T widget) found.add(widget);
    element.visitChildren(visit);
  }

  root.visitChildren(visit);
  return found;
}

List<Element> _visibleTiles(WidgetTester tester, Finder results) {
  final screen = Offset.zero & tester.view.physicalSize / tester.view.devicePixelRatio;
  return _in(results, find.byType(BooruPostTile)).evaluate().where((tile) {
    final box = tile.renderObject;
    return box is RenderBox && box.hasSize && screen.overlaps(box.localToGlobal(Offset.zero) & box.size);
  }).toList();
}

NavigatorState _navigator(WidgetTester tester) => tester.state<NavigatorState>(find.byType(Navigator).first);

/// Pushes [screen] and returns a finder for it, so lookups skip the screens
/// below it, such as a Booru tab on Home.
Finder _push(WidgetTester tester, Widget screen) {
  final key = UniqueKey();
  _navigator(tester).push(
    MaterialPageRoute<void>(
      builder: (_) => KeyedSubtree(key: key, child: screen),
    ),
  );
  return find.byKey(key);
}

Future<void> _pop(WidgetTester tester) async {
  _navigator(tester).pop();
  await tester.pump(const Duration(seconds: 1));
}

Finder _in(Finder scope, Finder matching) => find.descendant(of: scope, matching: matching);

Future<void> _waitFor(WidgetTester tester, Finder finder, {Duration timeout = const Duration(seconds: 30)}) =>
    _waitUntil(tester, () => finder.evaluate().isNotEmpty, '$finder', timeout: timeout);

/// Pumps until [done]. Past [timeout] the test fails, or when not
/// [required] this returns false.
Future<bool> _waitUntil(
  WidgetTester tester,
  bool Function() done,
  String what, {
  Duration timeout = const Duration(seconds: 30),
  bool required = true,
}) async {
  final deadline = DateTime.now().add(timeout);
  while (!done()) {
    if (DateTime.now().isAfter(deadline)) {
      if (required) fail('timed out waiting for $what');
      return false;
    }
    await tester.pump(const Duration(milliseconds: 16));
  }
  return true;
}

Finder _gridScrollable(Finder results) =>
    _in(results, find.descendant(of: find.byType(BooruPostGrid), matching: find.byType(Scrollable))).first;

int _loadedPosts(WidgetTester tester, Finder results) =>
    tester.widget<BooruSearchResults>(_in(results, find.byType(BooruSearchResults))).results.state.length;

/// Flings down until [gridPosts] posts are loaded and the last of them has
/// been scrolled past, or the host stops sending more.
Future<void> _flingThroughGrid(WidgetTester tester, Finder results) async {
  for (var fling = 0; fling < 200; fling++) {
    final position = tester.state<ScrollableState>(_gridScrollable(results)).position;
    final atEnd = position.pixels >= position.maxScrollExtent - 1;
    if (_loadedPosts(tester, results) >= gridPosts && atEnd) return;
    await tester.fling(_gridScrollable(results), const Offset(0, -900), 4000);
    await tester.pump(const Duration(milliseconds: 1200));
  }
}

Future<void> _jumpToTop(WidgetTester tester, Finder results) async {
  tester.state<ScrollableState>(_gridScrollable(results)).position.jumpTo(0);
  await tester.pump(const Duration(milliseconds: 500));
}

/// The first post in the grid that is a picture rather than a video, so S3
/// always measures an image post.
int _firstPicture(WidgetTester tester, Finder results) {
  final tiles = tester.widgetList<BooruPostTile>(_in(results, find.byType(BooruPostTile))).toList();
  final index = tiles.indexWhere((tile) => !tile.post.isVideo);
  if (index < 0) fail('no picture post on the first screen');
  return index;
}

Future<void> _swipeViewer(WidgetTester tester) async {
  for (var swipe = 0; swipe < viewerSwipes; swipe++) {
    await _swipeOnce(tester);
  }
}

Future<void> _swipeOnce(WidgetTester tester) async {
  final pager = find.descendant(of: find.byType(BooruPostPager), matching: find.byType(PageView));
  await tester.fling(pager, const Offset(-500, 0), 2000);
  await tester.pump(const Duration(milliseconds: 900));
}

Finder get _tagInput =>
    find.descendant(of: find.byKey(const ValueKey('booru-tag-input')), matching: find.byType(TextField));

/// Types into the tag field the way a keystroke does: the field's text
/// changes and its onChanged runs. Integration tests have no test keyboard.
void _type(WidgetTester tester, Finder search, String text) {
  final field = tester.widget<TextField>(_in(search, _tagInput));
  field.controller!.value = TextEditingValue(
    text: text,
    selection: TextSelection.collapsed(offset: text.length),
  );
  field.onChanged!(text);
}

bool _suggestionsFor(Finder search, String prefix) => _in(search, find.byType(BooruSearchLanding))
    .evaluate()
    .map((element) => (element.widget as BooruSearchLanding).state)
    .any((state) => state.input == prefix && state.suggestions.isNotEmpty);
