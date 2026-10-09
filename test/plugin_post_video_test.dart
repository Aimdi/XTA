import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/tweet/_video.dart';

const _mp4 = 'https://files.example/v.mp4';
const _hls = 'https://video.bsky.app/watch/a/b/playlist.m3u8';

/// No poster URL, so nothing in these tests reaches for the network.
const _video = PluginMediaItem(url: '', isVideo: true, videoUrl: _mp4);
const _gif = PluginMediaItem(url: '', isVideo: true, videoUrl: _mp4, isGif: true);

PrefServiceCache _prefs({bool manualLoad = false}) => PrefServiceCache(
  defaults: {
    optionMediaDisableAutoload: manualLoad,
    optionMediaDefaultMute: true,
    optionMediaDefaultLoop: false,
    optionMediaDefaultAutoPlay: false,
    optionMediaBackgroundPlayback: true,
    optionMediaAllowBackgroundPlayOtherApps: false,
  },
);

Widget _app(Widget child, PrefServiceCache prefs) {
  return PrefService(
    service: prefs,
    child: MaterialApp(
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      home: Scaffold(body: SingleChildScrollView(child: child)),
    ),
  );
}

/// Pumps, then unmounts at teardown so the player's visibility timers stop.
Future<void> _pump(WidgetTester tester, Widget child, {bool manual = false}) {
  addTearDown(() => tester.pumpWidget(const SizedBox()));
  return tester.pumpWidget(_app(child, _prefs(manualLoad: manual)));
}

void main() {
  setUpAll(() {
    VisibilityDetectorController.instance.updateInterval = Duration.zero;
  });

  group('PluginPostMedia', () {
    testWidgets('a video with a stream builds the X video player', (tester) async {
      await _pump(tester, const PluginPostMedia(items: [_video], sourceName: 'mastodon'));

      final player = tester.widget<TweetVideo>(find.byType(TweetVideo));
      expect(player.alwaysPlay, isFalse);
      expect(player.loop, isFalse);
      expect(player.disableControls, isFalse);
      expect(player.username, 'mastodon');
      expect(player.tweetId, pluginVideoPoolKey(_video));
      expect(tester.takeException(), isNull);
    });

    testWidgets('a GIF autoplays and loops without controls', (tester) async {
      await _pump(tester, const PluginPostMedia(items: [_gif]));

      final player = tester.widget<TweetVideo>(find.byType(TweetVideo));
      expect(player.alwaysPlay, isTrue);
      expect(player.loop, isTrue);
      expect(player.disableControls, isTrue);
    });

    testWidgets('videos in a multi-item pager play too', (tester) async {
      await _pump(
        tester,
        const PluginPostMedia(
          items: [
            _video,
            PluginMediaItem(url: ''),
          ],
        ),
      );

      expect(find.byType(TweetVideo), findsOneWidget);
    });

    testWidgets('a poster without a stream stays a thumbnail', (tester) async {
      await _pump(tester, const PluginPostMedia(items: [PluginMediaItem(url: '', isVideo: true)]));

      expect(find.byType(TweetVideo), findsNothing);
      expect(find.byIcon(Icons.play_arrow_rounded), findsOneWidget);
    });

    testWidgets('manual loading waits for a tap', (tester) async {
      await _pump(tester, const PluginPostMedia(items: [_video]), manual: true);

      expect(find.byType(TweetVideo), findsNothing);
      await tester.tap(find.byType(PluginPostVideo));
      await tester.pump();
      expect(find.byType(TweetVideo), findsOneWidget);
    });
  });

  testWidgets('a Mastodon card plays its video attachment', (tester) async {
    await _pump(
      tester,
      const MastodonPostCard(
        post: MastodonPost(
          id: '1',
          acct: 'a@example.social',
          authorName: 'A',
          text: 'clip',
          url: 'https://example.social/@a/1',
          images: [''],
          imageIsVideo: [true],
          imageDownloadUrls: [_mp4],
          imageIsGif: [true],
        ),
      ),
    );

    final player = tester.widget<TweetVideo>(find.byType(TweetVideo));
    expect(player.alwaysPlay, isTrue);
    expect(player.tweetId, 'plugin-$_mp4');
    expect(tester.takeException(), isNull);
  });

  testWidgets('a Bluesky card plays its HLS video', (tester) async {
    final prefs = _prefs();
    final accounts = BlueskyAccountsStore();
    final likes = BlueskyLikesStore(prefs);
    addTearDown(() {
      accounts.destroy();
      likes.destroy();
    });

    await _pump(
      tester,
      MultiProvider(
        providers: [
          Provider<BlueskyAccountsStore>.value(value: accounts),
          Provider<BlueskyLikesStore>.value(value: likes),
        ],
        child: const BlueskyPostCard(
          post: BlueskyPost(
            uri: 'at://did:plc:a/app.bsky.feed.post/v',
            cid: 'c',
            handle: 'alice.bsky.social',
            did: 'did:plc:a',
            authorName: 'Alice',
            text: 'Watch',
            url: 'https://bsky.app/profile/alice.bsky.social/post/v',
            images: [''],
            imageIsVideo: [true],
            videoUrls: [_hls],
          ),
        ),
      ),
    );

    final player = tester.widget<TweetVideo>(find.byType(TweetVideo));
    expect(player.username, 'bluesky');
    expect(player.tweetId, 'plugin-$_hls');
    expect(tester.takeException(), isNull);
  });
}
