import 'dart:typed_data';

import 'package:extended_image/extended_image.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_download.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_image_source.dart';
import 'package:xta/plugins/pixiv/pixiv_lru_cache.dart';

import 'support/pixiv_reader_harness.dart';

const _master = 'https://i.pximg.net/img-master/img/2026/07/01/00/00/00/120_p0_master1200.jpg';

void main() {
  group('rewrite', () {
    test('Pixiv\'s own server or nothing picked leaves the URL alone', () {
      expect(pixivImageUrl(_master, pixivImageHost), _master);
      expect(pixivImageUrl(_master, ''), _master);
      expect(pixivImageUrl(_master, '  '), _master);
    });

    test('the mirror swaps only the host and keeps the path', () {
      expect(
        pixivImageUrl(_master, pixivMirrorHost),
        'https://i.pixiv.re/img-master/img/2026/07/01/00/00/00/120_p0_master1200.jpg',
      );
    });

    test('a custom server keeps its port and path prefix', () {
      expect(
        pixivImageUrl(_master, 'https://example.com:8443/pixiv/'),
        'https://example.com:8443/pixiv/img-master/img/2026/07/01/00/00/00/120_p0_master1200.jpg',
      );
      expect(pixivImageUrl(_master, 'img.example.com'), startsWith('https://img.example.com/img-master/'));
      expect(pixivImageUrl('$_master?x=1', 'img.example.com'), endsWith('_master1200.jpg?x=1'));
    });

    test('an address with spaces or of another kind is refused and changes nothing', () {
      for (final host in [
        'img example.com',
        'ftp://example.com',
        'http://example.com',
        'https://user:pw@example.com',
        'https://',
        '?x',
      ]) {
        expect(parsePixivImageHost(host), isNull, reason: host);
        expect(pixivImageUrl(_master, host), _master, reason: host);
      }
      expect(parsePixivImageHost(' img.example.com '), isNotNull);
    });

    test('the static host and other servers are never mirrored', () {
      const avatar = 'https://s.pximg.net/common/images/no_profile.png';
      expect(pixivImageUrl(avatar, pixivMirrorHost), avatar);
      expect(pixivImageUrl('https://example.org/a.jpg', pixivMirrorHost), 'https://example.org/a.jpg');
      expect(pixivImageUrl('not a url', pixivMirrorHost), 'not a url');
    });

    test('a stored host reads as Pixiv, the mirror or a custom server', () {
      expect(pixivImageHostChoice(''), PixivImageHostChoice.pixiv);
      expect(pixivImageHostChoice(pixivImageHost), PixivImageHostChoice.pixiv);
      expect(pixivImageHostChoice(' i.pixiv.re '), PixivImageHostChoice.mirror);
      expect(pixivImageHostChoice('img.example.com'), PixivImageHostChoice.custom);
      expect(pixivImageHostSetting(null), pixivImageHost);
      expect(pixivImageHostSetting(PrefServiceCache(cache: {optionPluginPixivImageHost: 'x.test'})), 'x.test');
    });
  });

  group('where the mirror applies', () {
    test('downloads fetch from the picked server under Pixiv\'s file names', () {
      final requests = pixivPageRequests(pixivWork(pages: 2), 'content://tree/x', imageHost: pixivMirrorHost);
      expect(requests.map((request) => request.uri.host).toSet(), {pixivMirrorHost});
      expect(requests.map((request) => request.fileName), ['120_p0.png', '120_p1.png']);
      final media = pixivPageMedia(pixivWork(), 1, imageHost: pixivMirrorHost);
      expect(
        [Uri.parse(media.url).host, Uri.parse(media.resolvedDownloadUrl).host],
        [pixivMirrorHost, pixivMirrorHost],
      );
      expect(downloadRequestHeaders(requests.first.uri), isEmpty);
    });

    test('a single picture, such as an avatar or one in a novel, shows and saves from the picked server', () {
      final avatar = pixivImageMedia('https://i.pximg.net/user-profile/img/1.jpg', imageHost: pixivMirrorHost);
      expect(avatar.resolvedDownloadUrl, 'https://i.pixiv.re/user-profile/img/1.jpg');
      final upload = pixivImageMedia(
        'https://i.pximg.net/novel/9_1200.jpg',
        downloadUrl: 'https://i.pximg.net/novel/9.jpg',
        imageHost: pixivMirrorHost,
      );
      expect(
        [upload.url, upload.resolvedDownloadUrl],
        ['https://i.pixiv.re/novel/9_1200.jpg', 'https://i.pixiv.re/novel/9.jpg'],
      );
      expect(pixivImageMedia('https://s.pximg.net/common/a.png', imageHost: pixivMirrorHost).url, contains('s.pximg'));
    });

    test('an ugoira archive comes from the mirror once, then from memory', () async {
      final asked = <Uri>[];
      final client = PixivClient(
        PrefServiceCache(cache: {optionPluginPixivImageHost: pixivMirrorHost}),
        httpClient: MockClient((request) async {
          asked.add(request.url);
          return http.Response.bytes([1, 2, 3], 200);
        }),
      );
      const zip = 'https://i.pximg.net/img-zip-ugoira/img/9_ugoira600x600.zip';
      expect(await client.ugoiraArchive(zip), Uint8List.fromList([1, 2, 3]));
      expect(await client.ugoiraArchive(zip), Uint8List.fromList([1, 2, 3]));
      expect(asked, [Uri.parse('https://i.pixiv.re/img-zip-ugoira/img/9_ugoira600x600.zip')]);
    });

    test('a failed archive is fetched again next time', () async {
      var status = 503;
      var requests = 0;
      final client = PixivClient(
        PrefServiceCache(),
        httpClient: MockClient((_) async {
          requests++;
          return http.Response.bytes([7], status);
        }),
      );
      const zip = 'https://i.pximg.net/z.zip';
      await expectLater(client.ugoiraArchive(zip), throwsA(isA<PixivException>()));
      status = 200;
      expect(await client.ugoiraArchive(zip), [7]);
      expect(requests, 2);
    });

    test('an image server\'s refusal is never blamed on the Pixiv token', () async {
      for (final (status, kind) in [
        (403, PixivErrorKind.badResponse),
        (404, PixivErrorKind.notFound),
        (429, PixivErrorKind.rateLimited),
      ]) {
        final client = PixivClient(
          PrefServiceCache(),
          httpClient: MockClient((_) async => http.Response.bytes([], status)),
        );
        await expectLater(
          client.ugoiraArchive('https://i.pximg.net/z$status.zip'),
          throwsA(isA<PixivException>().having((e) => e.kind, 'kind', kind)),
        );
      }
    });

    testWidgets('images on screen load from the picked server', (tester) async {
      await pumpPixiv(
        tester,
        const PixivNetworkImage(url: _master, cacheWidth: 100),
        client: (prefs) {
          prefs.set(optionPluginPixivImageHost, pixivMirrorHost);
          return FakePixivClient(prefs);
        },
      );
      final image = tester.widget<ExtendedImage>(find.byType(ExtendedImage)).image;
      final provider = (image as ExtendedResizeImage).imageProvider as ExtendedNetworkImageProvider;
      expect(Uri.parse(provider.url).host, pixivMirrorHost);
      await disposePixiv(tester);
    });
  });

  group('archive memory', () {
    test('keeps the most recently used values up to its capacity', () {
      final cache = PixivLruCache<String, int>(2)
        ..['a'] = 1
        ..['b'] = 2;
      expect(cache['a'], 1);
      cache['c'] = 3;
      expect(cache.keys, ['a', 'c']);
      expect(cache['b'], isNull);
    });

    test('a cleanup only drops the value it was meant for', () {
      final cache = PixivLruCache<String, Object>(2);
      final older = Object();
      final newer = Object();
      cache['a'] = older;
      cache['a'] = newer;
      cache.removeIfHolds('a', older);
      expect(cache['a'], same(newer));
      cache.removeIfHolds('a', newer);
      expect(cache.length, 0);
    });
  });
}
