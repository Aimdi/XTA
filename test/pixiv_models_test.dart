import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

void main() {
  group('parsePixivIllustList', () {
    final sample = {
      'illusts': [
        {
          'id': 42,
          'title': 'Cat',
          'caption': 'meow',
          'type': 'illust',
          'image_urls': {
            'square_medium':
                'https://i.pximg.net/c/360x360_70/img-master/cat.jpg',
            'medium': 'https://i.pximg.net/c/540x540_70/img-master/cat.jpg',
            'large': 'https://i.pximg.net/c/600x1200_90/img-master/cat.jpg',
          },
          'user': {
            'id': 7,
            'name': 'Artist',
            'account': 'artist',
            'profile_image_urls': {'medium': 'https://i.pximg.net/user.jpg'},
          },
          'create_date': '2026-08-01T09:00:00+09:00',
          'page_count': 2,
          'total_bookmarks': 10,
          'total_view': 100,
          'is_bookmarked': true,
          'x_restrict': 0,
          'sanity_level': 2,
        },
        {
          'id': 99,
          'title': 'R18',
          'caption': '',
          'type': 'illust',
          'image_urls': {'square_medium': 'https://i.pximg.net/r18.jpg'},
          'user': {
            'id': 1,
            'name': 'X',
            'account': 'x',
            'profile_image_urls': {},
          },
          'page_count': 1,
          'x_restrict': 1,
          'sanity_level': 6,
        },
      ],
      'next_url': 'https://app-api.pixiv.net/v2/illust/follow?offset=30',
    };

    test('reads fields and filters R-18 by default', () {
      final posts = parsePixivIllustList(sample);

      expect(posts, hasLength(1));
      expect(posts.first.id, 42);
      expect(posts.first.title, 'Cat');
      expect(posts.first.userName, 'Artist');
      expect(posts.first.pageCount, 2);
      expect(posts.first.url, 'https://www.pixiv.net/artworks/42');
      expect(posts.first.thumbnailUrl, contains('540x540'));
      expect(posts.first.caption, 'meow');
      expect(posts.first.isBookmarked, isTrue);
      expect(posts.first.totalBookmarks, 10);
    });

    test('reads tags, size and manga page urls', () {
      final posts = parsePixivIllustList({
        'illusts': [
          {
            'id': 1,
            'title': 'Manga',
            'caption': '<p>hi <b>there</b></p>',
            'type': 'manga',
            'width': 800,
            'height': 1200,
            'image_urls': {
              'square_medium': 'https://i.pximg.net/sq.jpg',
              'large': 'https://i.pximg.net/large0.jpg',
            },
            'meta_pages': [
              {
                'image_urls': {
                  'original': 'https://i.pximg.net/p0.jpg',
                  'large': 'https://i.pximg.net/l0.jpg',
                },
              },
              {
                'image_urls': {'original': 'https://i.pximg.net/p1.jpg'},
              },
            ],
            'tags': [
              {'name': '猫', 'translated_name': 'cat'},
              {'name': 'オリジナル'},
            ],
            'user': {
              'id': 2,
              'name': 'A',
              'account': 'a',
              'profile_image_urls': {},
            },
            'page_count': 2,
            'x_restrict': 0,
            'sanity_level': 2,
          },
        ],
      });

      expect(posts, hasLength(1));
      final illust = posts.first;
      expect(illust.caption, 'hi there');
      expect(illust.width, 800);
      expect(illust.height, 1200);
      expect(illust.aspectRatio, closeTo(800 / 1200, 0.001));
      // Prefer large over original so the viewer stays light (Pixez-style).
      expect(illust.pageUrls, [
        'https://i.pximg.net/l0.jpg',
        'https://i.pximg.net/p1.jpg',
      ]);
      expect(illust.viewerUrls, hasLength(2));
      expect(illust.tags.map((t) => t.displayName), ['cat', 'オリジナル']);
      expect(illust.isManga, isTrue);
    });

    test('keeps R-18 when asked', () {
      expect(parsePixivIllustList(sample, includeR18: true), hasLength(2));
    });

    test('drops deleted-or-private placeholder stubs', () {
      final posts = parsePixivIllustList({
        'illusts': [
          {
            'id': 1,
            'title': 'Gone',
            'visible': false,
            'image_urls': {
              'medium':
                  'https://s.pximg.net/common/images/limit_unknown_360.png',
            },
            'user': {
              'id': 1,
              'name': 'X',
              'account': 'x',
              'profile_image_urls': {},
            },
            'x_restrict': 0,
            'total_bookmarks': 0,
          },
          {
            'id': 2,
            'title': 'Ok',
            'visible': true,
            'image_urls': {
              'medium': 'https://i.pximg.net/c/540x540_70/img-master/ok.jpg',
            },
            'user': {
              'id': 1,
              'name': 'X',
              'account': 'x',
              'profile_image_urls': {},
            },
            'x_restrict': 0,
          },
        ],
      }, includeR18: true);

      expect(posts.map((p) => p.id), [2]);
    });

    test('a reshaped payload yields nothing rather than throwing', () {
      expect(parsePixivIllustList(null), isEmpty);
      expect(parsePixivIllustList({'illusts': 'nope'}), isEmpty);
    });
  });

  group('PixivUser.fromDetailJson', () {
    test('reads the nested user and profile objects', () {
      final user = PixivUser.fromDetailJson({
        'user': {
          'id': 11,
          'name': 'Name',
          'account': 'acct',
          'comment': 'hi',
          'is_followed': true,
          'profile_image_urls': {'medium': 'https://i.pximg.net/a.jpg'},
        },
        'profile': {'total_illusts': 5, 'total_follower': 9},
      });

      expect(user.id, 11);
      expect(user.name, 'Name');
      expect(user.illustsCount, 5);
      expect(user.followersCount, 9);
      expect(user.isFollowed, isTrue);
    });
  });
}
