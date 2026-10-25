// ignore_for_file: avoid_print

import 'dart:io';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:dart_twitter_api/twitter_api.dart' show User;
import 'package:extended_image/extended_image.dart' show ExtendedNetworkImageProvider;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/main.dart' show xtaLocalizationsDelegates;
import 'package:xta/profile/profile_chrome.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/tweet/focal_post.dart';
import 'package:xta/tweet/ticker/ticker_chart.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/ui/reorder_lift.dart';
import 'package:xta/ui/x_look_theme.dart';

/// Renders the second pass of X's motion frame by frame, for review:
///   fvm flutter test test/x_motion_render_test.dart --dart-define=RENDER_X_MOTION=true
/// The frames land in review-artifacts/renders/x-motion/.
const _render = bool.fromEnvironment('RENDER_X_MOTION');
const _shot = ValueKey('x-motion-shot');

Future<void> _frame(WidgetTester tester, String name) =>
    expectLater(find.byKey(_shot), matchesGoldenFile('../review-artifacts/renders/x-motion/$name.png'));

/// The whole screen is the shot, overlay included: a dragged row is drawn
/// there, above the list it came from.
Widget _app(Widget child) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: xLookLightsOutTheme(null),
  localizationsDelegates: xtaLocalizationsDelegates,
  supportedLocales: const [Locale('en')],
  builder: (context, app) => RepaintBoundary(key: _shot, child: app),
  home: Scaffold(body: child),
);

/// A phone-wide screen [height] points tall.
void _screen(WidgetTester tester, double height) {
  tester.view.physicalSize = Size(412, height);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

TweetWithCard _post({required int replies, required int likes, required int views}) => TweetWithCard()
  ..idStr = '1'
  ..fullText = 'Just a minute ago, my X forced a new interface design and motion graphics.'
  ..createdAt = DateTime.utc(2026, 6, 10, 14, 32)
  ..user = (User()
    ..idStr = '9'
    ..name = 'Ice Universe'
    ..screenName = 'UniverseIce')
  ..replyCount = replies
  ..retweetCount = 0
  ..quoteCount = 0
  ..favoriteCount = likes
  ..viewCount = views;

/// A wide, colourful stand-in banner, served from loopback so the bar loads
/// it the way the app does.
Future<Uint8List> _bannerPng() async {
  final recorder = ui.PictureRecorder();
  final canvas = Canvas(recorder);
  const size = Size(1500, 500);
  canvas.drawRect(
    Offset.zero & size,
    Paint()
      ..shader = ui.Gradient.linear(Offset.zero, const Offset(1500, 500), [
        const Color(0xFF7FD3E6),
        const Color(0xFFF2A65A),
      ]),
  );
  for (final (x, colour) in [(250.0, 0xFFE4572E), (750.0, 0xFF29335C), (1250.0, 0xFFF3A712)]) {
    canvas.drawCircle(Offset(x, 260), 170, Paint()..color = Color(colour));
  }
  final image = await recorder.endRecording().toImage(1500, 500);
  final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
  return bytes!.buffer.asUint8List();
}

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    for (final weight in ['Regular', 'Medium', 'SemiBold', 'Bold']) {
      await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-$weight.ttf'))).load();
    }
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  testWidgets('counts roll from the copy to X\'s numbers', skip: !_render, (tester) async {
    final likes = LikedTweetModel();
    final saves = SavedTweetModel();
    addTearDown(likes.destroy);
    addTearDown(saves.destroy);
    _screen(tester, 96);
    final copy = _post(replies: 1, likes: 35, views: 624);
    final answer = _post(replies: 2, likes: 36, views: 692);
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          Provider<LikedTweetModel>.value(value: likes),
          Provider<SavedTweetModel>.value(value: saves),
        ],
        child: PrefService(
          service: PrefServiceCache(cache: {optionZenMode: false, optionCalmMode: false}),
          child: _app(
            CountRollOrigin(
              tweet: copy,
              child: Column(
                children: [
                  TweetFocalMetaLine(createdAt: answer.createdAt, views: answer.viewCount, fromViews: copy.viewCount),
                  TweetFooterBar(
                    tweet: answer,
                    tweetText: answer.fullText!,
                    shareBaseUrl: '',
                    locale: const Locale('en'),
                    showViews: false,
                    onOpenTweet: () {},
                    onCaptureImage: () async => null,
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
    for (final ms in [0, 60, 120, 180, 240, 300]) {
      if (ms > 0) await tester.pump(const Duration(milliseconds: 60));
      await _frame(tester, 'count-$ms');
    }
  });

  testWidgets('the profile bar takes the banner as the header scrolls away', skip: !_render, (tester) async {
    _screen(tester, 56);
    HttpOverrides.global = null;
    // The banner image caches itself on disk, in the platform's temporary folder.
    final temp = Directory.systemTemp.createTempSync('x-motion-');
    addTearDown(() => temp.deleteSync(recursive: true));
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      const MethodChannel('plugins.flutter.io/path_provider'),
      (_) async => temp.path,
    );
    final png = (await tester.runAsync(_bannerPng))!;
    // Bound and answered on the real clock: the test's fake one never runs them.
    final server = (await tester.runAsync(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) {
        request.response.headers.contentType = ContentType('image', 'png');
        request.response.add(png);
        request.response.close();
      });
      return server;
    }))!;
    addTearDown(() => tester.runAsync(server.close));
    final banner = 'http://127.0.0.1:${server.port}/banner.png';

    Widget bar(bool scrolled) => _app(
      SizedBox(
        height: 56,
        child: CustomScrollView(
          slivers: [
            ProfileTopBar(
              name: 'Ice Universe',
              username: 'UniverseIce',
              bannerUrl: banner,
              scrolled: scrolled,
              actions: [IconButton(onPressed: () {}, tooltip: 'Search', icon: const Icon(Icons.search))],
            ),
          ],
        ),
      ),
    );
    await tester.pumpWidget(bar(false));
    // Loaded outside the test's fake clock, into the cache the bar reads.
    await tester.runAsync(
      () => precacheImage(ExtendedNetworkImageProvider(banner, cache: true), tester.element(find.byType(Scaffold))),
    );
    await tester.pumpWidget(bar(true));
    for (final ms in [0, 45, 90, 135, 180]) {
      if (ms > 0) await tester.pump(const Duration(milliseconds: 45));
      await _frame(tester, 'bar-$ms');
    }
    // The image cache's own timers end with the tree.
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 2));
  });

  testWidgets('a dragged row lifts', skip: !_render, (tester) async {
    _screen(tester, 4 * 56);
    final rows = ['Soccer', 'Stocks & Economy', 'Politics', 'Sports'];
    await tester.pumpWidget(
      _app(
        SizedBox(
          height: 4 * 56,
          child: ReorderableListView(
            proxyDecorator: xtaReorderLift,
            buildDefaultDragHandles: false,
            onReorderItem: (_, _) {},
            children: [
              for (final (index, row) in rows.indexed)
                ListTile(
                  key: ValueKey(row),
                  leading: const Icon(Icons.sports_soccer),
                  title: Text(row),
                  trailing: ReorderableDragStartListener(index: index, child: const Icon(Icons.drag_indicator)),
                ),
            ],
          ),
        ),
      ),
    );
    final gesture = await tester.startGesture(tester.getCenter(find.byIcon(Icons.drag_indicator).at(2)));
    await tester.pump(const Duration(milliseconds: 20));
    await gesture.moveBy(const Offset(0, -20));
    for (final ms in [0, 80, 160, 250]) {
      if (ms > 0) await tester.pump(const Duration(milliseconds: 80));
      await _frame(tester, 'lift-$ms');
    }
    await gesture.up();
    await tester.pumpAndSettle();
  });

  testWidgets('the price line draws itself in', skip: !_render, (tester) async {
    final quote = TickerQuote(
      symbol: 'TSLA',
      currency: 'USD',
      price: 381.23,
      previousClose: 321.66,
      points: [
        for (var i = 0; i < 60; i++)
          TickerPoint(
            at: DateTime.utc(2026, 7, 17, 9, 30).add(Duration(minutes: i * 6)),
            close: 330 + 40 * (i / 60) + 9 * math.sin(i / 4) + 4 * math.sin(i / 1.7),
          ),
      ],
    );
    _screen(tester, 200);
    await tester.pumpWidget(_app(TickerChart(quote: quote, drawKey: 'day')));
    for (final ms in [0, 175, 350, 525, 700]) {
      if (ms > 0) await tester.pump(const Duration(milliseconds: 175));
      await _frame(tester, 'chart-$ms');
    }
  });
}
