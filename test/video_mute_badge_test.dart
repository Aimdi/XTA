import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/tweet/_video.dart';
import 'package:xta/tweet/_video_controls.dart';
import 'package:xta/tweet/video_controller_pool.dart';
import 'package:xta/tweet/video_mute_badge.dart';

const _mp4 = 'https://files.example/v.mp4';
const _tileWidth = 320.0;

Future<TweetVideoUrls> _neverResolves() => Completer<TweetVideoUrls>().future;

TweetVideo _xVideo({bool gif = false}) => TweetVideo(
  username: 'reader',
  loop: gif,
  alwaysPlay: gif,
  disableControls: gif,
  tweetId: 'post',
  metadata: TweetVideoMetadata(16 / 9, null, _neverResolves),
);

Widget _app(
  Widget child, {
  required VideoControllerPool pool,
  bool autoPlay = true,
  TextDirection direction = TextDirection.ltr,
}) {
  return PrefService(
    service: PrefServiceCache(
      defaults: {
        optionMediaDisableAutoload: false,
        optionMediaDefaultMute: true,
        optionMediaDefaultLoop: false,
        optionMediaDefaultAutoPlay: autoPlay,
        optionMediaBackgroundPlayback: false,
        optionMediaAllowBackgroundPlayOtherApps: false,
        optionMediaVideoQuality: 'large',
        optionDisableAnimations: true,
      },
    ),
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
        home: Directionality(
          textDirection: direction,
          child: Scaffold(
            body: Center(
              child: SizedBox(width: _tileWidth, child: child),
            ),
          ),
        ),
      ),
    ),
  );
}

/// Pumps past the creation gate, so the tile is waiting on its player.
Future<void> _pumpLoading(WidgetTester tester, Widget app) async {
  await tester.pumpWidget(app);
  await tester.pump();
  await tester.pump(kVideoCreationSettleDelay + const Duration(milliseconds: 50));
  await tester.pump();
}

/// Unmounts, then outlives the startup timeouts so no timer is left pending.
Future<void> _unmount(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pump(const Duration(seconds: 13));
  expect(tester.takeException(), isNull);
}

bool _isMuted(WidgetTester tester) => tester.element(find.byType(VideoMuteBadge)).read<VideoContextState>().isMuted;

Finder get _disc => find.descendant(of: find.byType(VideoMuteBadge), matching: find.byType(ClipOval));

void main() {
  setUp(() => VisibilityDetectorController.instance.updateInterval = Duration.zero);
  tearDown(() => VisibilityDetectorController.instance.updateInterval = const Duration(milliseconds: 500));

  testWidgets('a loading inline video keeps its poster, with no spinner', (tester) async {
    await _pumpLoading(tester, _app(_xVideo(), pool: VideoControllerPool(maxSize: 1)));

    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.byType(XtaControls), findsNothing);
    expect(find.byType(VideoMuteBadge), findsOneWidget);

    await tester.pump(const Duration(seconds: 2));
    expect(find.byType(CircularProgressIndicator), findsNothing);
    await _unmount(tester);
  });

  testWidgets('a video waiting for a free player shows no spinner either', (tester) async {
    final pool = VideoControllerPool(maxSize: 1);
    final held = Completer<PooledVideo>();
    pool.acquire('other', () => held.future);

    await _pumpLoading(tester, _app(_xVideo(), pool: pool));

    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.byType(VideoMuteBadge), findsOneWidget);
    await _unmount(tester);
  });

  testWidgets('the mute badge toggles sound at once, without controls shown', (tester) async {
    await _pumpLoading(tester, _app(_xVideo(), pool: VideoControllerPool(maxSize: 1)));
    final before = _isMuted(tester);
    expect(find.byIcon(before ? Icons.volume_off : Icons.volume_up), findsOneWidget);

    await tester.tap(find.byType(VideoMuteBadge));
    await tester.pump();

    expect(_isMuted(tester), !before);
    expect(find.byIcon(before ? Icons.volume_up : Icons.volume_off), findsOneWidget);
    expect(tester.binding.hasScheduledFrame, isFalse, reason: 'the swap is instant');

    await tester.tap(find.byType(VideoMuteBadge));
    await tester.pump();
    expect(_isMuted(tester), before);
    await _unmount(tester);
  });

  testWidgets('the badge is a labelled toggle', (tester) async {
    final semantics = tester.ensureSemantics();
    await _pumpLoading(tester, _app(_xVideo(), pool: VideoControllerPool(maxSize: 1)));

    expect(
      tester.getSemantics(find.byType(VideoMuteBadge)),
      isSemantics(label: L10n.current.mute_videos, isButton: true, hasToggledState: true, hasTapAction: true),
    );
    semantics.dispose();
    await _unmount(tester);
  });

  testWidgets('the badge sits in the bottom-end corner with a full touch target', (tester) async {
    await _pumpLoading(tester, _app(_xVideo(), pool: VideoControllerPool(maxSize: 1)));

    final tile = tester.getRect(find.byType(TweetVideo));
    final target = tester.getRect(find.byType(VideoMuteBadge));
    final disc = tester.getRect(_disc);

    expect(target.width, greaterThanOrEqualTo(48));
    expect(target.height, greaterThanOrEqualTo(48));
    expect(disc.width, kVideoMuteDiscSize);
    expect(tile.right - disc.right, kVideoMuteCornerInset);
    expect(tile.bottom - disc.bottom, kVideoMuteCornerInset);
    expect(target.contains(disc.center), isTrue);
    await _unmount(tester);
  });

  testWidgets('right-to-left puts the badge in the bottom-left corner', (tester) async {
    await _pumpLoading(tester, _app(_xVideo(), pool: VideoControllerPool(maxSize: 1), direction: TextDirection.rtl));

    final tile = tester.getRect(find.byType(TweetVideo));
    final disc = tester.getRect(_disc);
    expect(disc.left - tile.left, kVideoMuteCornerInset);
    expect(tile.bottom - disc.bottom, kVideoMuteCornerInset);
    await _unmount(tester);
  });

  testWidgets('a GIF has no sound, so no badge and no spinner', (tester) async {
    await _pumpLoading(tester, _app(_xVideo(gif: true), pool: VideoControllerPool(maxSize: 1)));

    expect(find.byType(VideoMuteBadge), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    await _unmount(tester);
  });

  testWidgets('a tap-to-play poster keeps its play button and no badge', (tester) async {
    await _pumpLoading(tester, _app(_xVideo(), pool: VideoControllerPool(maxSize: 1), autoPlay: false));

    expect(find.byType(FritterCenterPlayButton), findsOneWidget);
    expect(find.byType(VideoMuteBadge), findsNothing);
    await _unmount(tester);
  });

  group('plugin posts share the badge', () {
    const video = PluginMediaItem(url: '', isVideo: true, videoUrl: _mp4);
    const gif = PluginMediaItem(url: '', isVideo: true, videoUrl: _mp4, isGif: true);

    /// Parks the plugin video's startup so the tile waits on it.
    VideoControllerPool stalledPool(PluginMediaItem item) {
      final pool = VideoControllerPool(maxSize: 1);
      final key = pluginVideoPoolKey(item);
      pool.release(key, acquisition: pool.acquire(key, () => Completer<PooledVideo>().future));
      return pool;
    }

    testWidgets('a Mastodon or Bluesky video loads without a spinner and shows the badge', (tester) async {
      await _pumpLoading(tester, _app(const PluginPostMedia(items: [video]), pool: stalledPool(video)));

      expect(find.byType(CircularProgressIndicator), findsNothing);
      expect(find.byType(VideoMuteBadge), findsOneWidget);
      await _unmount(tester);
    });

    testWidgets('a plugin GIF shows no badge', (tester) async {
      await _pumpLoading(tester, _app(const PluginPostMedia(items: [gif]), pool: stalledPool(gif)));

      expect(find.byType(VideoMuteBadge), findsNothing);
      expect(find.byType(CircularProgressIndicator), findsNothing);
      await _unmount(tester);
    });
  });
}
