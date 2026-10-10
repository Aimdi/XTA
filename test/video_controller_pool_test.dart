import 'dart:async';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/_video.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/tweet/video_controller_pool.dart';
import 'package:dart_twitter_api/api/media/data/media.dart';
import 'package:xta/profile/media_grid/gif_playback_gate.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';

class _Video extends Fake implements PooledVideo {
  int disposals = 0;
  final Completer<void>? stopping;
  _Video({this.stopping});
  @override
  bool get isDisposed => disposals > 0;
  @override
  void suppressQualityResume() {}
  @override
  Future<void> dispose() async {
    disposals++;
    await stopping?.future;
  }
}

Future<void> _settle() => Future<void>.delayed(Duration.zero);


Widget _startupApp(VideoControllerPool pool, Future<TweetVideoUrls> Function() urls, {String? tweetId = 'post'}) =>
    PrefService(
  service: PrefServiceCache(defaults: {
    optionMediaDefaultLoop: false,
    optionMediaDefaultAutoPlay: false,
    optionMediaBackgroundPlayback: false,
    optionMediaAllowBackgroundPlayOtherApps: false,
    optionMediaVideoQuality: 'large',
    optionDisableAnimations: true,
  }),
  child: MultiProvider(
    providers: [
      Provider<VideoControllerPool>.value(value: pool),
      ChangeNotifierProvider(create: (_) => VideoContextState(true)),
    ],
    child: MaterialApp(
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      home: Scaffold(
        body: Center(
          child: SizedBox(
            width: 320,
            child: TweetVideo(
              username: 'reader',
              loop: false,
              tweetId: tweetId,
              metadata: TweetVideoMetadata(1, null, urls),
            ),
          ),
        ),
      ),
    ),
  ),
);

void main() {
  setUp(() => VisibilityDetectorController.instance.updateInterval = Duration.zero);
  tearDown(() => VisibilityDetectorController.instance.updateInterval = const Duration(milliseconds: 500));

  testWidgets('stalled URL resolution ends the spinner and offers manual restart', (tester) async {
    final pool = VideoControllerPool(maxSize: 1);
    var calls = 0;
    await tester.pumpWidget(_startupApp(pool, () {
      calls++;
      return Completer<TweetVideoUrls>().future;
    }));
    await tester.tap(find.byType(TweetVideo));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    await tester.pump();
    expect(calls, 1);
    await tester.pump(const Duration(seconds: 9));
    await tester.pump();
    expect(find.text(L10n.current.restart_video_player), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(pool.canAcquire('another'), isTrue);
    await tester.tap(find.text(L10n.current.restart_video_player));
    await tester.pump();
    await tester.pump();
    expect(calls, 2);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 13));
    expect(tester.takeException(), isNull);
  });

  testWidgets('stalled native startup exposes retry and retains capacity until disposal', (tester) async {
    final pending = Completer<PooledVideo>();
    final pool = VideoControllerPool(maxSize: 1);
    final startup = pool.acquire('post:0', () => pending.future);
    pool.release('post:0');
    await tester.pumpWidget(_startupApp(pool, () async => throw StateError('must share startup')));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    await tester.pump();
    await tester.pump(const Duration(seconds: 13));
    await tester.pump();
    expect(find.text(L10n.current.restart_video_player), findsOneWidget);
    expect(pool.canAcquire('another'), isFalse);
    await tester.pumpWidget(const SizedBox.shrink());
    final late = _Video();
    pending.complete(late);
    await startup;
    await tester.pump();
    expect(late.disposals, 1);
    expect(pool.canAcquire('another'), isTrue);
    expect(tester.takeException(), isNull);
  });

  test('eviction retains capacity until the native player is actually disposed', () async {
    final stopped = Completer<void>();
    final first = _Video(stopping: stopped);
    final pool = VideoControllerPool(maxSize: 1);
    await pool.acquire('a', () async => first);
    pool.release('a');
    await expectLater(pool.acquire('b', () async => _Video()), throwsA(isA<VideoPoolFullException>()));
    expect(first.disposals, 1);
    expect(pool.canAcquire('b'), isFalse);
    stopped.complete();
    await _settle();
    await pool.acquire('b', () async => _Video());
    expect(pool.contains('b'), isTrue);
  });

  test('abandoned startup cannot allocate more decoders and late completion is disposed', () async {
    final pending = Completer<PooledVideo>();
    final pool = VideoControllerPool(maxSize: 1);
    final acquisition = pool.acquire('a', () => pending.future);
    pool.release('a', acquisition: acquisition);
    pool.discardIfUnused('a', acquisition);
    var creates = 0;
    for (var attempt = 0; attempt < 3; attempt++) {
      await expectLater(
        pool.acquire('a', () async {
          creates++;
          return _Video();
        }),
        throwsA(isA<VideoPoolFullException>()),
      );
    }
    expect(creates, 0);
    final late = _Video();
    pending.complete(late);
    await acquisition;
    await _settle();
    expect(late.disposals, 1);
    expect(pool.canAcquire('a'), isTrue);
  });

  test('a late release cannot release a replacement with the same key', () async {
    final pool = VideoControllerPool(maxSize: 1);
    final old = pool.acquire('a', () async => _Video());
    await old;
    pool.release('a', acquisition: old);
    pool.discardIfUnused('a', old);
    await _settle();
    final replacement = _Video();
    await pool.acquire('a', () async => replacement);
    pool.release('a', acquisition: old);
    pool.discardIfUnused('a', old);
    pool.releaseUnused();
    expect(pool.contains('a'), isTrue);
    expect(replacement.disposals, 0);
    await expectLater(pool.acquire('b', () async => _Video()), throwsA(isA<VideoPoolFullException>()));
  });

  test('reattaching to a cached key shares one acquisition', () async {
    final pool = VideoControllerPool(maxSize: 1);
    final first = pool.acquire('a', () async => _Video());
    await first;
    pool.release('a');
    final second = pool.acquire('a', () async => throw StateError('must reuse'));
    expect(identical(first, second), isTrue);
    await second;
    pool.release('a');
    pool.releaseUnused();
    await _settle();
  });

  group('players nothing can re-attach to', () {
    // The widget shares a startup seeded under its own key, which finishes
    // after the widget is gone — the usual way a scrolled-past video ends.
    Future<(_Video, String)> startThenDisposeBeforeReady(
      WidgetTester tester,
      VideoControllerPool pool,
      String? tweetId,
    ) async {
      await tester.pumpWidget(
        _startupApp(pool, () async => throw StateError('shares the seeded startup'), tweetId: tweetId),
      );
      final state = tester.state(find.byType(TweetVideo));
      final key = tweetId == null ? 'local:${identityHashCode(state)}' : '$tweetId:0';
      final pending = Completer<PooledVideo>();
      final startup = pool.acquire(key, () => pending.future);
      pool.release(key, acquisition: startup);

      await tester.tap(find.byType(TweetVideo));
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
      await tester.pump();
      await tester.pumpWidget(const SizedBox.shrink());

      final video = _Video();
      pending.complete(video);
      await tester.pump();
      await tester.pump();
      return (video, key);
    }

    testWidgets('a player keyed to its widget alone goes with the widget', (tester) async {
      final pool = VideoControllerPool(maxSize: 3);
      final (video, _) = await startThenDisposeBeforeReady(tester, pool, null);

      expect(pool.cachedKeys, isEmpty,
          reason: 'cached under a key no widget will ask for again, it held a slot until the pool needed it');
      expect(video.disposals, 1);
      expect(tester.takeException(), isNull);
    });

    testWidgets('a player keyed to its post stays cached for the next screen', (tester) async {
      final pool = VideoControllerPool(maxSize: 3);
      final (video, key) = await startThenDisposeBeforeReady(tester, pool, 'post');

      expect(pool.cachedKeys, [key]);
      expect(video.disposals, 0);
      expect(tester.takeException(), isNull);
    });
  });

  group('profile media grid GIFs', () {
    testWidgets('play no more at once than the pool can host', (tester) async {
      late GifPlaybackGate pooled;
      late GifPlaybackGate standalone;
      await tester.pumpWidget(Column(children: [
        Provider<VideoControllerPool>.value(
          value: VideoControllerPool(maxSize: 2),
          child: Builder(builder: (context) {
            pooled = GifPlaybackGate.sizedToPool(context);
            return const SizedBox.shrink();
          }),
        ),
        Builder(builder: (context) {
          standalone = GifPlaybackGate.sizedToPool(context);
          return const SizedBox.shrink();
        }),
      ]));

      expect(pooled.maxConcurrent, 2);
      expect(standalone.maxConcurrent, 5, reason: 'without a shared pool the gate keeps its own cap');
      pooled.dispose();
      standalone.dispose();
    });

    testWidgets('are keyed like the same GIF in a post', (tester) async {
      await tester.pumpWidget(const SizedBox.shrink());
      final item = GifGridItem(
        tweetId: '42',
        username: 'someone',
        thumbnailUrl: 'https://pbs.twimg.com/tweet_video_thumb/a.jpg',
        aspectRatio: 1,
        mediaIndex: 1,
        media: Media.fromJson({
          'type': 'animated_gif',
          'media_url_https': 'https://pbs.twimg.com/tweet_video_thumb/a.jpg',
          'video_info': {
            'aspect_ratio': [1, 1],
            'variants': [
              {'bitrate': 0, 'content_type': 'video/mp4', 'url': 'https://video.twimg.com/tweet_video/a.mp4'},
            ],
          },
        }),
      );

      final video = (item.toWidget(tester.element(find.byType(SizedBox))) as IgnorePointer).child as TweetVideo;
      expect((video.tweetId, video.mediaIndex), ('42', 1),
          reason: 'a cell granted playback again re-attaches to its cached player instead of starting one');
    });
  });
}
