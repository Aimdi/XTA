import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';

void main() {
  group('parsePixivLink', () {
    test('treats bare numeric ids as artworks', () {
      final ref = parsePixivLink('123');

      expect(ref, isA<PixivArtworkLinkRef>().having((ref) => ref.id, 'id', 123));
    });

    test('parses artwork urls and paths', () {
      final refs = [
        parsePixivLink('https://www.pixiv.net/artworks/123'),
        parsePixivLink('https://www.pixiv.net/en/artworks/123'),
        parsePixivLink('www.pixiv.net/artworks/123'),
        parsePixivLink('/artworks/123'),
        parsePixivLink('artworks/123'),
        parsePixivLink('https://pixiv.me/member_illust.php?illust_id=123'),
      ];

      for (final ref in refs) {
        expect(ref, isA<PixivArtworkLinkRef>().having((ref) => ref.id, 'id', 123));
      }
    });

    test('parses user urls and paths', () {
      final refs = [
        parsePixivLink('https://www.pixiv.net/users/456'),
        parsePixivLink('https://www.pixiv.net/en/users/456'),
        parsePixivLink('users/456'),
        parsePixivLink('user/456'),
      ];

      for (final ref in refs) {
        expect(ref, isA<PixivUserLinkRef>().having((ref) => ref.id, 'id', 456));
      }
    });

    test('ignores unsupported links', () {
      expect(parsePixivLink('https://example.com/users/1'), isNull);
      expect(parsePixivLink('not pixiv'), isNull);
      expect(parsePixivLink('users/nope'), isNull);
      expect(parsePixivLink('https://www.pixiv.net/ranking.php'), isNull);
      expect(parsePixivLink('pixiv://account/login?code=abc'), isNull);
      expect(parsePixivLink('https://i.pximg.net/user-profile/img/2023/09/15/12/22/50/4004637_abc_170.jpg'), isNull);
      expect(parsePixivLink('https://www.pixiv.net/jump.php?%E0%A4%A'), isNull);
    });

    test('reads the short forms and the old PHP pages', () {
      expect(parsePixivLink('https://www.pixiv.net/i/123'), isA<PixivArtworkLinkRef>().having((r) => r.id, 'id', 123));
      expect(parsePixivLink('https://www.pixiv.net/u/456'), isA<PixivUserLinkRef>().having((r) => r.id, 'id', 456));
      expect(
        parsePixivLink('https://www.pixiv.net/member.php?id=456'),
        isA<PixivUserLinkRef>().having((r) => r.id, 'id', 456),
      );
      expect(
        parsePixivLink('https://www.pixiv.net/member_illust.php?id=456&type=all'),
        isA<PixivUserLinkRef>().having((r) => r.id, 'id', 456),
      );
      expect(
        parsePixivLink('https://www.pixiv.net/users/456/artworks'),
        isA<PixivUserLinkRef>().having((r) => r.id, 'id', 456),
      );
    });

    test('opens a tag page as its tag, decoded', () {
      for (final link in [
        'https://www.pixiv.net/tags/%E7%8C%AB/artworks?s_mode=s_tag',
        'https://www.pixiv.net/en/tags/猫',
        'tags/猫',
      ]) {
        expect(parsePixivLink(link), isA<PixivTagLinkRef>().having((r) => r.tag, 'tag', '猫'), reason: link);
      }
    });

    test('reads the work id from an image file name', () {
      for (final link in [
        'https://i.pximg.net/img-original/img/2026/07/01/00/00/00/123_p0.png',
        'https://i.pximg.net/c/600x1200_90/img-master/img/2026/07/01/00/00/00/123_p3_master1200.jpg',
        'i.pximg.net/img-zip-ugoira/img/2026/07/01/00/00/00/123_ugoira600x600.zip',
      ]) {
        expect(parsePixivLink(link), isA<PixivArtworkLinkRef>().having((r) => r.id, 'id', 123), reason: link);
      }
    });

    test("reads Pixiv's app links", () {
      expect(parsePixivLink('pixiv://illusts/123'), isA<PixivArtworkLinkRef>().having((r) => r.id, 'id', 123));
      expect(parsePixivLink('pixiv://users/456'), isA<PixivUserLinkRef>().having((r) => r.id, 'id', 456));
      expect(parsePixivLink('pixiv://novels/789'), isA<PixivNovelLinkRef>().having((r) => r.id, 'id', 789));
    });

    test('declares series, novels and pixivision with the page the browser opens', () {
      expect(
        parsePixivLink('https://www.pixiv.net/user/4004637/series/266067'),
        isA<PixivSeriesLinkRef>()
            .having((r) => r.id, 'id', 266067)
            .having((r) => r.webUrl, 'webUrl', 'https://www.pixiv.net/user/4004637/series/266067'),
      );
      expect(
        parsePixivLink('https://www.pixiv.net/novel/show.php?id=789'),
        isA<PixivNovelLinkRef>().having((r) => r.webUrl, 'webUrl', 'https://www.pixiv.net/novel/show.php?id=789'),
      );
      expect(parsePixivLink('https://www.pixiv.net/n/789'), isA<PixivNovelLinkRef>().having((r) => r.id, 'id', 789));
      expect(
        parsePixivLink('https://www.pixiv.net/novel/series/55'),
        isA<PixivNovelSeriesLinkRef>().having((r) => r.id, 'id', 55),
      );
      expect(
        parsePixivLink('https://www.pixivision.net/ja/a/9876'),
        isA<PixivisionLinkRef>()
            .having((r) => r.id, 'id', 9876)
            .having((r) => r.webUrl, 'webUrl', 'https://www.pixivision.net/ja/a/9876'),
      );
    });

    test('keeps a pixiv.me name for resolving, but reads its old PHP links at once', () {
      expect(parsePixivLink('https://pixiv.me/mika'), isA<PixivShortLinkRef>().having((r) => r.name, 'name', 'mika'));
      expect(parsePixivLink('pixiv.me/mika'), isA<PixivShortLinkRef>());
      expect(parsePixivLink('https://pixiv.me/a/b'), isNull);
    });
  });

  group('resolvePixivShortLink', () {
    test('follows one redirect, without following it automatically, to a pixiv.net page', () async {
      final requests = <http.BaseRequest>[];
      final client = MockClient((request) async {
        requests.add(request);
        return http.Response('', 302, headers: {'location': 'https://www.pixiv.net/users/456'});
      });
      final ref = await resolvePixivShortLink(const PixivShortLinkRef('mika'), client: client);
      expect(ref, isA<PixivUserLinkRef>().having((r) => r.id, 'id', 456));
      expect(requests.single.url.toString(), 'https://pixiv.me/mika');
      expect(requests.single.followRedirects, isFalse);
    });

    test('accepts only a pixiv.net Location', () async {
      for (final location in [
        'https://evil.example/users/456',
        'https://pixiv.net.evil.example/users/456',
        'pixiv://users/456',
        'https://pixiv.me/other',
      ]) {
        final client = MockClient((_) async => http.Response('', 302, headers: {'location': location}));
        expect(await resolvePixivShortLink(const PixivShortLinkRef('mika'), client: client), isNull, reason: location);
      }
    });

    test('is null when there is no redirect or the request fails', () async {
      final notFound = MockClient((_) async => http.Response('', 404));
      expect(await resolvePixivShortLink(const PixivShortLinkRef('mika'), client: notFound), isNull);
      final failing = MockClient((_) async => throw const SocketException('offline'));
      expect(await resolvePixivShortLink(const PixivShortLinkRef('mika'), client: failing), isNull);
    });
  });
}
