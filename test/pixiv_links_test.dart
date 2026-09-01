import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';

void main() {
  group('parsePixivLink', () {
    test('treats bare numeric ids as artworks', () {
      final ref = parsePixivLink('123');

      expect(
        ref,
        isA<PixivArtworkLinkRef>().having((ref) => ref.id, 'id', 123),
      );
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
        expect(
          ref,
          isA<PixivArtworkLinkRef>().having((ref) => ref.id, 'id', 123),
        );
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
    });
  });
}
