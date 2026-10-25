import 'dart:async';

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:media_kit/media_kit.dart' as mk;
import 'package:media_kit_video/media_kit_video.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/main.dart';
import 'package:xta/tweet/_video.dart';
import 'package:xta/tweet/video_controller_pool.dart';

/// A player that never emits anything, and counts the pauses asked of it.
class _Player extends Fake implements mk.Player {
  int pauses = 0;

  @override
  final mk.PlayerState state = const mk.PlayerState();

  @override
  final mk.PlayerStream stream = _SilentStreams();

  @override
  mk.PlatformPlayer? get platform => null;

  @override
  Future<void> setVolume(double volume) async {}

  @override
  Future<void> play() async {}

  @override
  Future<void> pause() async {
    pauses++;
  }
}

/// Every [mk.PlayerStream] stream, empty.
class _SilentStreams implements mk.PlayerStream {
  @override
  dynamic noSuchMethod(Invocation invocation) => const Stream<Never>.empty();
}

/// A controller with no texture: [Video] draws nothing but its controls.
class _Controller extends Fake implements VideoController {
  @override
  final mk.Player player;

  @override
  final ValueNotifier<PlatformVideoController?> notifier = ValueNotifier(null);

  _Controller(this.player);

  @override
  Future<void> get waitUntilFirstFrameRendered => Future<void>.value();
}

const _channel = MethodChannel('com.alexmercerind/media_kit_video');

Widget _app(VideoControllerPool pool, ValueNotifier<bool> showTile) => PrefService(
  service: PrefServiceCache(
    defaults: {
      optionMediaDisableAutoload: false,
      optionMediaDefaultMute: true,
      optionMediaDefaultLoop: false,
      optionMediaDefaultAutoPlay: true,
      optionMediaBackgroundPlayback: false,
      optionMediaAllowBackgroundPlayOtherApps: false,
      optionMediaVideoQuality: 'large',
      optionDisableAnimations: false,
    },
  ),
  child: MultiProvider(
    providers: [
      Provider<VideoControllerPool>.value(value: pool),
      ChangeNotifierProvider(create: (_) => VideoContextState(true)),
    ],
    child: MaterialApp(
      localizationsDelegates: xtaLocalizationsDelegates,
      supportedLocales: L10n.delegate.supportedLocales,
      home: Scaffold(
        body: ValueListenableBuilder<bool>(
          valueListenable: showTile,
          builder: (context, show, _) => show
              ? TweetVideo(
                  username: 'reader',
                  loop: false,
                  tweetId: 'post',
                  metadata: TweetVideoMetadata(16 / 9, null, () => Completer<TweetVideoUrls>().future),
                )
              : const SizedBox.shrink(),
        ),
      ),
    ),
  ),
);

/// The controls also listen for a double tap, so a single tap lands only once
/// that could no longer be one.
Future<void> _tapControl(WidgetTester tester, IconData icon) async {
  await tester.tap(find.byIcon(icon));
  await tester.pump(kDoubleTapTimeout);
  await tester.pumpAndSettle();
}

/// A pool with room for one player, which a tile shows in full screen.
class _FullScreen {
  final pool = VideoControllerPool(maxSize: 1);
  final player = _Player();
  final showTile = ValueNotifier(true);

  bool get slotFree => pool.canAcquire('next:0');

  Future<void> open(WidgetTester tester) async {
    final video = PooledVideo(
      player: player,
      videoController: _Controller(player),
      downloadUrl: null,
      qualities: const [],
      currentStreamUrl: 'https://files.example/v.mp4',
      pausableByPolicy: true,
    );
    // Watched before, so the tile attaches to the pool's player.
    await pool.acquire('post:0', () async => video);
    pool.release('post:0');

    await tester.pumpWidget(_app(pool, showTile));
    await tester.pump(kVideoCreationSettleDelay + const Duration(milliseconds: 50));
    await tester.pump();
    await _tapControl(tester, Icons.fullscreen);
    expect(find.byIcon(Icons.fullscreen_exit), findsOneWidget);
  }

  /// Takes the tile away, the way an orientation change or a recycled list does.
  Future<void> removeTile(WidgetTester tester) async {
    showTile.value = false;
    await tester.pump();
    await tester.pump();
    expect(find.byType(TweetVideo), findsNothing);
  }

  /// Unmounts everything and outlives the controls' timers.
  Future<void> finish(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 13));
    showTile.dispose();
    expect(tester.takeException(), isNull);
  }
}

void main() {
  setUp(() {
    VisibilityDetectorController.instance.updateInterval = Duration.zero;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      _channel,
      (_) async => null,
    );
  });
  tearDown(() {
    VisibilityDetectorController.instance.updateInterval = const Duration(milliseconds: 500);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(_channel, null);
  });

  // Without semantics: the seek bar's slider has no increasedValue, which
  // Flutter asserts on, and that is not what these check.
  testWidgets(
    'a tile gone under its full-screen player gives the slot back once that closes',
    semanticsEnabled: false,
    (tester) async {
      final screen = _FullScreen();
      await screen.open(tester);

      await screen.removeTile(tester);
      expect(screen.slotFree, isFalse, reason: 'the full-screen route still shows the player');

      await _tapControl(tester, Icons.fullscreen_exit);
      expect(screen.slotFree, isTrue, reason: 'nothing shows the player any more');
      expect(screen.player.pauses, 1, reason: 'no other tile shows it either');

      await screen.finish(tester);
    },
  );

  testWidgets('a tile still there when full screen closes keeps its player until it goes', semanticsEnabled: false, (
    tester,
  ) async {
    final screen = _FullScreen();
    await screen.open(tester);

    await _tapControl(tester, Icons.fullscreen_exit);
    expect(find.byType(TweetVideo), findsOneWidget);
    expect(screen.slotFree, isFalse, reason: 'the tile plays it inline again');
    expect(screen.player.pauses, 0);

    await screen.removeTile(tester);
    expect(screen.slotFree, isTrue);
    expect(screen.player.pauses, 1, reason: 'given back once, by the tile');

    await screen.finish(tester);
  });
}
