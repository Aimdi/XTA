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
import 'package:xta/database/entities.dart';
import 'package:xta/main.dart' show xtaLocalizationsDelegates;
import 'package:xta/profile/profile_chrome.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/tweet/_card.dart';
import 'package:xta/tweet/focal_post.dart';
import 'package:xta/tweet/poll_fill.dart';
import 'package:xta/tweet/progressive_feed_store.dart';
import 'package:xta/tweet/progressive_feed_view.dart';
import 'package:xta/tweet/ticker/ticker_chart.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/tweet/tweet_skeleton.dart';
import 'package:xta/ui/floating_notice.dart';
import 'package:xta/ui/image_fade_in.dart';
import 'package:xta/ui/loading_hairline.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/reorder_lift.dart';
import 'package:xta/ui/x_look_theme.dart';

/// Renders XTA's motion frame by frame, for review: the second pass of X's
/// motion and the two polish passes after it.
///   fvm flutter test test/x_motion_render_test.dart --dart-define=RENDER_X_MOTION=true
/// The frames land in review-artifacts/renders/x-motion/.
const _render = bool.fromEnvironment('RENDER_X_MOTION');
const _shot = ValueKey('x-motion-shot');

Future<void> _frame(WidgetTester tester, String name) =>
    expectLater(find.byKey(_shot), matchesGoldenFile('../review-artifacts/renders/x-motion/$name.png'));

/// Renders [name]-0 and on, [step] apart, over [frames] frames.
Future<void> _frames(WidgetTester tester, String name, {required int step, required int frames}) async {
  for (var frame = 0; frame < frames; frame++) {
    if (frame > 0) await tester.pump(Duration(milliseconds: step));
    await _frame(tester, '$name-${frame * step}');
  }
}

/// The whole screen is the shot, overlay included: a dragged row is drawn
/// there, above the list it came from.
Widget _app(Widget child, {Widget? floatingActionButton}) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: xLookLightsOutTheme(null),
  localizationsDelegates: xtaLocalizationsDelegates,
  supportedLocales: const [Locale('en')],
  builder: (context, app) => RepaintBoundary(key: _shot, child: app),
  home: Scaffold(body: child, floatingActionButton: floatingActionButton),
);

/// Rows of a feed for a notice to float over.
Widget _rows() => ListView(
  children: [
    for (final row in ['Soccer', 'Stocks & Economy', 'Politics', 'Sports'])
      ListTile(leading: const Icon(Icons.tag), title: Text(row)),
  ],
);

typedef _PostModels = ({LikedTweetModel likes, SavedTweetModel saves});

/// [child] with the models and settings a post's footer reads, ended with
/// the test.
Widget _withPostModels(_PostModels models, Widget child) {
  addTearDown(models.likes.destroy);
  addTearDown(models.saves.destroy);
  return MultiProvider(
    providers: [
      Provider<LikedTweetModel>.value(value: models.likes),
      Provider<SavedTweetModel>.value(value: models.saves),
    ],
    child: PrefService(
      service: PrefServiceCache(cache: {optionZenMode: false, optionCalmMode: false}),
      child: child,
    ),
  );
}

Widget _footer(TweetWithCard post) => TweetFooterBar(
  tweet: post,
  tweetText: post.fullText!,
  shareBaseUrl: '',
  locale: const Locale('en'),
  showViews: false,
  onOpenTweet: () {},
  onCaptureImage: () async => null,
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
    _screen(tester, 96);
    final copy = _post(replies: 1, likes: 35, views: 624);
    final answer = _post(replies: 2, likes: 36, views: 692);
    await tester.pumpWidget(
      _withPostModels(
        (likes: LikedTweetModel(), saves: SavedTweetModel()),
        _app(
          CountRollOrigin(
            tweet: copy,
            child: Column(
              children: [
                TweetFocalMetaLine(createdAt: answer.createdAt, views: answer.viewCount, fromViews: copy.viewCount),
                _footer(answer),
              ],
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

  testWidgets('a notice drops in and lifts away', skip: !_render, (tester) async {
    _screen(tester, 4 * 56);
    Widget page({required bool notice}) => _app(
      FloatingNotice(
        notice: notice
            ? NoticePill(
                child: TextButton.icon(onPressed: () {}, icon: const Icon(Icons.refresh), label: const Text('Retry')),
              )
            : null,
        child: _rows(),
      ),
    );
    await tester.pumpWidget(page(notice: false));
    await tester.pumpWidget(page(notice: true));
    await _frames(tester, 'notice-in', step: 45, frames: 5);
    await tester.pumpAndSettle();
    await tester.pumpWidget(page(notice: false));
    await _frames(tester, 'notice-out', step: 40, frames: 4);
  });

  testWidgets('new posts drop in over the feed, above its chips', skip: !_render, (tester) async {
    _screen(tester, 4 * 56);
    final store = ProgressiveFeedStore();
    await tester.pumpWidget(_app(ProgressiveFeedView(store: store, builder: (_) => _rows())));
    store.update(
      ProgressiveFeedState(
        pending: {'x': []},
        sources: {'substack': FeedSourceState(error: Exception('offline'))},
      ),
    );
    await _frames(tester, 'new-posts', step: 45, frames: 5);
    await tester.pumpWidget(const SizedBox());
    await store.destroy();
  });

  testWidgets('an opened post\'s poll fills in', skip: !_render, (tester) async {
    _screen(tester, 172);
    final post = TweetWithCard()
      ..idStr = '1'
      ..card = {
        'name': 'poll3choice_text_only',
        'binding_values': {
          for (final (index, (label, votes)) in [('Android', '412'), ('iPhone', '233'), ('Neither', '57')].indexed) ...{
            'choice${index + 1}_label': {'string_value': label},
            'choice${index + 1}_count': {'string_value': votes},
          },
        },
      };
    await tester.pumpWidget(
      PrefService(
        service: PrefServiceCache(cache: const {}),
        child: _app(
          FocalPostScope(
            id: '1',
            child: FocalPollFill(
              child: TweetCard(tweet: post, card: post.card),
            ),
          ),
        ),
      ),
    );
    // The fill starts after the frame that asked for it.
    await tester.pump();
    await _frames(tester, 'poll', step: 140, frames: 7);
  });

  testWidgets('saving pops the bookmark', skip: !_render, (tester) async {
    _screen(tester, 48);
    final models = (likes: LikedTweetModel(), saves: SavedTweetModel());
    final post = _post(replies: 2, likes: 36, views: 692);
    await tester.pumpWidget(_withPostModels(models, _app(_footer(post))));
    models.saves.update([SavedTweet(id: '1', user: '9', content: '{}')], force: true);
    await tester.pump();
    await _frames(tester, 'save', step: 60, frames: 6);
  });

  testWidgets('scroll to top grows in and shrinks away', skip: !_render, (tester) async {
    _screen(tester, 4 * 56);
    Widget page({required bool visible}) => _app(
      _rows(),
      floatingActionButton: XtaGrowIn(
        visible: visible,
        child: FloatingActionButton(onPressed: () {}, child: const Icon(Icons.arrow_upward)),
      ),
    );
    await tester.pumpWidget(page(visible: false));
    await tester.pumpWidget(page(visible: true));
    await _frames(tester, 'top-in', step: 45, frames: 5);
    // Shown a while, as when the reader scrolls back up.
    await tester.pumpAndSettle();
    await tester.pumpWidget(page(visible: false));
    await _frames(tester, 'top-out', step: 40, frames: 4);
  });

  testWidgets('a photo fades in over its placeholder', skip: !_render, (tester) async {
    _screen(tester, 170);
    final photo = MemoryImage((await tester.runAsync(_bannerPng))!);
    Widget card({required bool fetched}) => _app(
      Builder(
        builder: (context) {
          final placeholder = ColoredBox(color: skeletonBoneColor(context, 0));
          return Padding(
            padding: const EdgeInsets.all(16),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(16),
              child: AspectRatio(
                aspectRatio: 3,
                child: fetched
                    ? ImageFadeIn(
                        placeholder: placeholder,
                        image: Image(image: photo, fit: BoxFit.cover),
                      )
                    : placeholder,
              ),
            ),
          );
        },
      ),
    );
    await tester.pumpWidget(card(fetched: false));
    // Decoded on the real clock, as a fetched photo is before it is shown.
    await tester.runAsync(() => precacheImage(photo, tester.element(find.byType(Scaffold))));
    await tester.pumpWidget(card(fetched: true));
    await _frames(tester, 'photo', step: 45, frames: 5);
  });

  testWidgets('a translation fades in as the post eases to its height', skip: !_render, (tester) async {
    _screen(tester, 190);
    final post = _post(replies: 2, likes: 36, views: 692);
    final translated = ValueNotifier(false);
    addTearDown(translated.dispose);
    await tester.pumpWidget(
      _withPostModels(
        (likes: LikedTweetModel(), saves: SavedTweetModel()),
        _app(
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ValueListenableBuilder(
                  valueListenable: translated,
                  builder: (context, translated, _) => XtaSwap(
                    swap: translated,
                    child: Text(
                      translated
                          ? 'Just a minute ago, my X forced a new interface design and motion graphics on me. '
                                'Every tap now moves.'
                          : 'Gerade eben hat mir X ein neues Design aufgezwungen.',
                    ),
                  ),
                ),
                _footer(post),
              ],
            ),
          ),
        ),
      ),
    );
    translated.value = true;
    await tester.pump();
    await _frames(tester, 'translate', step: 45, frames: 5);
  });

  testWidgets('the loading line glides, fills and fades', skip: !_render, (tester) async {
    _screen(tester, 2 * 56);
    Widget feed({required bool loading, required double value}) =>
        _app(LoadingHairline(loading: loading, value: value, child: _rows()));
    await tester.pumpWidget(feed(loading: true, value: 0.3));
    await tester.pumpAndSettle();
    await tester.pumpWidget(feed(loading: true, value: 0.7));
    await _frames(tester, 'line-glide', step: 40, frames: 4);
    await tester.pumpAndSettle();
    await tester.pumpWidget(feed(loading: false, value: 0.7));
    await _frames(tester, 'line-end', step: 60, frames: 5);
    await tester.pumpAndSettle();
  });
}
