import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_comment_models.dart';

Map<String, Object?> _user(int id, {String name = 'Mika', String account = 'mika'}) => {
  'id': id,
  'name': name,
  'account': account,
  'profile_image_urls': {'medium': 'https://i.pximg.net/user-profile/img/$id.jpg'},
};

const _full = {
  'id': 501,
  'comment': 'Lovely colours (heart)',
  'date': '2026-07-01T12:30:00+09:00',
  'user': {
    'id': 42,
    'name': 'Mika',
    'account': 'mika',
    'profile_image_urls': {'medium': 'https://i.pximg.net/user-profile/img/42.jpg'},
  },
  'has_replies': true,
  'stamp': null,
};

void main() {
  group('pixivCommentFromJson', () {
    test('reads a full comment', () {
      final comment = pixivCommentFromJson(_full)!;
      expect(comment.id, 501);
      expect(comment.text, 'Lovely colours (heart)');
      expect(comment.date, DateTime.utc(2026, 7, 1, 3, 30).toLocal());
      expect(
        (comment.user?.id, comment.user?.name, comment.user?.avatarUrl),
        (42, 'Mika', 'https://i.pximg.net/user-profile/img/42.jpg'),
      );
      expect(comment.hasReplies, isTrue);
      expect(comment.stampUrl, isNull);
      expect(comment.replyTo, isNull);
    });

    test('reads a sticker comment with no text', () {
      final comment = pixivCommentFromJson({
        'id': 7,
        'comment': '',
        'user': _user(3),
        'stamp': {'stamp_id': 301, 'stamp_url': 'https://s.pximg.net/common/images/stamp/generated-stamps/301_s.jpg'},
      })!;
      expect(comment.text, isEmpty);
      expect(comment.stampUrl, 'https://s.pximg.net/common/images/stamp/generated-stamps/301_s.jpg');
    });

    test('keeps a comment whose author is missing or withdrawn', () {
      final missing = pixivCommentFromJson({'id': 8, 'comment': 'hi'})!;
      final withdrawn = pixivCommentFromJson({
        'id': 9,
        'comment': 'hi',
        'user': {'id': 0, 'name': ''},
      })!;
      expect(missing.user, isNull);
      expect(withdrawn.user, isNull);
      expect(pixivCommentAuthorName(missing.user), isNull);
    });

    test('reads whom a reply answers from parent_comment', () {
      final reply = pixivCommentFromJson({
        'id': 10,
        'comment': 'Thanks!',
        'user': _user(42),
        'parent_comment': {'id': 501, 'comment': 'Lovely', 'user': _user(77, name: 'Rin')},
      })!;
      final noParent = pixivCommentFromJson({'id': 11, 'comment': 'x', 'parent_comment': <String, Object?>{}})!;
      expect(reply.replyTo?.name, 'Rin');
      expect(noParent.replyTo, isNull);
    });

    test('survives a reshaped payload without throwing', () {
      final odd = pixivCommentFromJson({
        'id': '12',
        'comment': 5,
        'date': 'yesterday',
        'user': 'Mika',
        'parent_comment': [],
        'has_replies': 'yes',
        'stamp': {'stamp_url': '  '},
      })!;
      expect(
        (odd.id, odd.text, odd.date, odd.user, odd.replyTo, odd.hasReplies, odd.stampUrl),
        (12, '', null, null, null, false, null),
      );
      expect(pixivCommentFromJson({'comment': 'no id'}), isNull);
      expect(pixivCommentFromJson('not a map'), isNull);
      expect(pixivCommentFromJson(null), isNull);
    });
  });

  group('parsePixivCommentPage', () {
    test('reads the comments, the total and the next page', () {
      final page = parsePixivCommentPage({
        'total_comments': 41,
        'comments': [
          _full,
          {'comment': 'dropped: no id'},
          {'id': 502, 'comment': 'Second'},
        ],
        'next_url': 'https://app-api.pixiv.net/v3/illust/comments?illust_id=1&offset=30',
      });
      expect(page.items.map((comment) => comment.id), [501, 502]);
      expect(page.total, 41);
      expect(page.nextUrl, 'https://app-api.pixiv.net/v3/illust/comments?illust_id=1&offset=30');
    });

    test('an empty or reshaped page is simply empty', () {
      for (final json in [
        null,
        'error',
        <String, Object?>{},
        {'comments': 'none', 'next_url': 4},
      ]) {
        final page = parsePixivCommentPage(json);
        expect(page.items, isEmpty);
        expect((page.nextUrl, page.total), (null, null));
      }
    });
  });

  group('pixivCommentAuthorName', () {
    test('prefers the name, then the account', () {
      final named = pixivCommentFromJson({'id': 1, 'user': _user(1, name: 'Rin')})!;
      final unnamed = pixivCommentFromJson({'id': 2, 'user': _user(2, name: '', account: 'rin_art')})!;
      expect(pixivCommentAuthorName(named.user), 'Rin');
      expect(pixivCommentAuthorName(unnamed.user), '@rin_art');
    });
  });

  group('pixivTextLinksOutside', () {
    test('Pixiv links and plain text pass', () {
      for (final text in [
        'No links here (heart)',
        'See https://www.pixiv.net/artworks/120 and https://pixiv.me/mika',
        'Booth: https://mika.booth.pm/items/1 fanbox https://www.fanbox.cc/@mika',
        'Mirror https://i.pximg.net/img-original/1.png. Thanks',
        'Article: https://www.pixivision.net/en/a/1',
        'pixiv.netで見ました',
      ]) {
        expect(pixivTextLinksOutside(text), isFalse, reason: text);
      }
    });

    test('a link anywhere else is caught, however it is written', () {
      for (final text in [
        'Free coins https://bit.ly/abc',
        'visit www.example.com now',
        'HTTP://EVIL.example/x',
        'https://pixiv.net.example.com/fake',
        'https://notpixiv.net/a',
        'https://例え.jp/x',
        'Nice! https://www.pixiv.net/artworks/1 and also http://spam.example',
      ]) {
        expect(pixivTextLinksOutside(text), isTrue, reason: text);
      }
    });
  });

  test('each target asks its own endpoints', () {
    const illust = PixivCommentTarget.illust(120);
    const novel = PixivCommentTarget.novel(9);
    expect(
      (illust.work.listPath, illust.work.idField, illust.work.repliesPath),
      ('/v3/illust/comments', 'illust_id', '/v2/illust/comment/replies'),
    );
    expect(
      (novel.work.listPath, novel.work.idField, novel.work.repliesPath),
      ('/v3/novel/comments', 'novel_id', '/v2/novel/comment/replies'),
    );
    expect(const PixivCommentTarget.illust(120), illust);
    expect(const PixivCommentTarget.novel(120), isNot(illust));
  });
}
