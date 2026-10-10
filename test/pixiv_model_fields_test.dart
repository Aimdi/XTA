import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

Map<String, Object?> _work(int id, {Map<String, Object?> extra = const {}, Map<String, Object?> user = const {}}) => {
  'id': id,
  'title': 'Work $id',
  'type': 'illust',
  'image_urls': {'medium': 'https://i.pximg.net/c/540x540_70/img-master/$id.jpg'},
  'user': {'id': 7, 'name': 'Artist', 'account': 'artist', ...user},
  'x_restrict': 0,
  'sanity_level': 2,
  ...extra,
};

void main() {
  group('work fields later screens need', () {
    test('a full payload carries series, comments, follow, caption HTML and rating', () {
      final illust = pixivIllustFromJson(
        _work(
          1,
          extra: {
            'caption': 'Line one<br />see <a href="https://www.pixiv.net/users/7">me</a>',
            'total_comments': 12,
            'sanity_level': 4,
            'series': {'id': 55, 'title': ' Summer '},
          },
          user: {'is_followed': true},
        ),
      )!;

      expect(illust.series?.id, 55);
      expect(illust.series?.title, 'Summer');
      expect(illust.totalComments, 12);
      expect(illust.userIsFollowed, isTrue);
      expect(illust.sanityLevel, 4);
      expect(illust.captionHtml, contains('<a href="https://www.pixiv.net/users/7">me</a>'));
      expect(illust.caption, 'Line onesee me');
    });

    test('a payload without them falls back to quiet defaults', () {
      final illust = pixivIllustFromJson(_work(2))!;

      expect(illust.series, isNull);
      expect(illust.totalComments, 0);
      expect(illust.userIsFollowed, isFalse);
      expect(illust.captionHtml, isEmpty);
      expect(illust.sanityLevel, 2);
    });

    test('a reshaped payload neither throws nor invents values', () {
      final illust = pixivIllustFromJson(
        _work(
          3,
          extra: {'series': 'Summer', 'total_comments': 'many', 'sanity_level': null, 'caption': 7},
          user: {'is_followed': 'yes'},
        ),
      )!;

      expect(illust.series, isNull);
      expect(illust.totalComments, 0);
      expect(illust.sanityLevel, 0);
      expect(illust.userIsFollowed, isFalse);
      expect(illust.captionHtml, isEmpty);
      expect(
        pixivIllustFromJson(
          _work(
            4,
            extra: {
              'series': <String, Object?>{'id': 0, 'title': 'none'},
            },
          ),
        )!.series,
        isNull,
      );
    });

    test('copyWith keeps the new fields and can change the follow', () {
      final illust = pixivIllustFromJson(
        _work(
          5,
          extra: {
            'total_comments': 3,
            'series': {'id': 9, 'title': 'S'},
          },
        ),
      )!;
      final copy = illust.copyWith(isBookmarked: true, userIsFollowed: true);

      expect((copy.totalComments, copy.series?.id, copy.userIsFollowed, copy.isBookmarked), (3, 9, true, true));
    });
  });

  group('PixivAuthUser.fromJson', () {
    test('reads premium and a quoted id', () {
      final user = PixivAuthUser.fromJson({'id': '123', 'name': ' Reader ', 'account': 'r', 'is_premium': true});
      expect((user.id, user.name, user.isPremium), (123, 'Reader', true));
    });

    test('a missing or reshaped user is an unknown free account', () {
      for (final json in [
        null,
        'user',
        <String, Object?>{'is_premium': 'yes'},
      ]) {
        final user = PixivAuthUser.fromJson(json);
        expect((user.id, user.isPremium, user.displayName), (0, false, ''));
      }
    });
  });

  group('parsePixivUserPreviews', () {
    final payload = {
      'user_previews': [
        {
          'user': {'id': 1, 'name': 'One', 'account': 'one', 'is_followed': true},
          'illusts': [
            _work(10),
            _work(11, extra: {'x_restrict': 1}),
            _work(12, extra: {'illust_ai_type': 2}),
          ],
          'novels': [
            {'id': 900, 'title': 'A novel'},
          ],
          'is_muted': true,
        },
        {
          'user': {'id': 2, 'name': 'Two', 'account': 'two'},
          'illusts': 'reshaped',
        },
        {
          'user': {'name': 'No id'},
        },
      ],
      'next_url': null,
    };

    test('keeps follow, mute and the raw novels beside each creator', () {
      final previews = parsePixivUserPreviews(payload, includeR18: true);

      expect(previews.map((p) => p.user.id), [1, 2]);
      expect(previews.first.user.isFollowed, isTrue);
      expect(previews.first.isMuted, isTrue);
      expect(previews.first.novels.single['title'].string, 'A novel');
      expect(previews.first.illusts.map((i) => i.id), [10, 11, 12]);
      expect(previews.last.illusts, isEmpty);
      expect(previews.last.novels, isEmpty);
      expect(previews.last.isMuted, isFalse);
    });

    test('drops R-18 and AI previews the way the feeds drop them', () {
      final hidden = parsePixivUserPreviews(payload, includeR18: false, includeAi: false);
      expect(hidden.first.illusts.map((i) => i.id), [10]);

      final defaults = parsePixivUserPreviews(payload);
      expect(defaults.first.illusts.map((i) => i.id), [10, 12]);
    });

    test('a reshaped list is no creators', () {
      expect(parsePixivUserPreviews(null), isEmpty);
      expect(parsePixivUserPreviews({'user_previews': 'nope'}), isEmpty);
    });
  });
}
