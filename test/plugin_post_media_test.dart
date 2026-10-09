import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/plugin_post_media.dart';

void main() {
  group('clampPluginMediaAspect', () {
    test('defaults when the ratio is missing or nonsense', () {
      expect(clampPluginMediaAspect(null), 16 / 9);
      expect(clampPluginMediaAspect(0), 16 / 9);
      expect(clampPluginMediaAspect(-1), 16 / 9);
      expect(clampPluginMediaAspect(double.nan), 16 / 9);
    });

    test('clamps extreme portraits and banners', () {
      expect(clampPluginMediaAspect(0.2), 0.45);
      expect(clampPluginMediaAspect(4), 2.4);
      expect(clampPluginMediaAspect(1.5), 1.5);
    });
  });

  group('pluginMediaAspectFrom', () {
    test('reads width/height and a precomputed aspect', () {
      expect(
        pluginMediaAspectFrom({'width': 2200, 'height': 1312}),
        closeTo(2200 / 1312, 0.001),
      );
      expect(pluginMediaAspectFrom({'aspect': 1.777}), closeTo(1.777, 0.001));
      expect(pluginMediaAspectFrom(null), isNull);
      expect(pluginMediaAspectFrom({'width': 0, 'height': 10}), isNull);
    });
  });

  test('pluginMediaItemsFrom pairs urls with aspect, video and alt metadata', () {
    final items = pluginMediaItemsFrom(
      urls: ['a.jpg', 'b.jpg'],
      aspects: [1.5],
      videos: [false, true],
      alts: ['A description'],
    );
    expect(items, hasLength(2));
    expect(items.first.aspectRatio, 1.5);
    expect(items.first.alt, 'A description');
    expect(items.last.isVideo, isTrue);
    expect(items.last.aspectRatio, isNull);
    expect(items.last.alt, isNull);
  });

  test('visiblePluginMedia keeps the tapped surviving item selected', () {
    final items = [
      const PluginMediaItem(url: ''),
      const PluginMediaItem(url: 'two.jpg'),
      const PluginMediaItem(url: 'three.jpg'),
    ];
    final selection = visiblePluginMedia(items, 2);
    expect(selection.items.map((item) => item.url), ['two.jpg', 'three.jpg']);
    expect(selection.initialIndex, 1);
  });

  test('plugin media filename strips query strings and prefixes the source', () {
    const item = PluginMediaItem(
      url: 'https://cdn.example/photo.jpg?width=1200',
    );
    expect(pluginMediaFileName(item, 'mastodon'), 'mastodon-photo.jpg');
  });

  group('plugin videos', () {
    test('pluginMediaItemsFrom carries the stream and GIF flag', () {
      final items = pluginMediaItemsFrom(
        urls: ['a.jpg', 'b.jpg'],
        videos: [false, true],
        videoUrls: [null, 'https://x.example/b.m3u8'],
        gifs: [false, true],
      );
      expect(items.first.isPlayable, isFalse);
      expect(items.last.isPlayable, isTrue);
      expect(items.last.isGif, isTrue);
    });

    test('a poster without a stream is not playable', () {
      const item = PluginMediaItem(url: 'p.jpg', isVideo: true);
      expect(item.isPlayable, isFalse);
    });

    test('an MP4 is offered for download, an HLS playlist is not', () async {
      const mp4 = PluginMediaItem(
        url: 'https://x.example/p.png',
        isVideo: true,
        videoUrl: 'https://x.example/v.mp4',
        aspectRatio: 0.1,
      );
      const hls = PluginMediaItem(url: '', isVideo: true, videoUrl: 'https://x.example/watch/playlist.m3u8?session=1');

      final mp4Meta = pluginVideoMetadata(mp4);
      final mp4Urls = await mp4Meta.streamUrlsBuilder();
      expect(mp4Meta.imageUrl, 'https://x.example/p.png');
      expect(mp4Meta.aspectRatio, clampPluginMediaAspect(0.1));
      expect(mp4Urls.streamUrl, 'https://x.example/v.mp4');
      expect(mp4Urls.downloadUrl, 'https://x.example/v.mp4');

      final hlsMeta = pluginVideoMetadata(hls);
      final hlsUrls = await hlsMeta.streamUrlsBuilder();
      expect(hlsMeta.imageUrl, isNull);
      expect(hlsUrls.streamUrl, hls.videoUrl);
      expect(hlsUrls.downloadUrl, isNull);
    });

    test('isHlsPlaylistUrl reads the path, not the query', () {
      expect(isHlsPlaylistUrl('https://a.example/x/playlist.M3U8'), isTrue);
      expect(isHlsPlaylistUrl('https://a.example/v.mp4?f=.m3u8'), isFalse);
    });
  });
}
