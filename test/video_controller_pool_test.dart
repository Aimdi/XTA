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


Widget _startupApp(VideoControllerPool pool, Future<TweetVideoUrls> Function() urls) => PrefService(
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
              tweetId: 'post',
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
}
