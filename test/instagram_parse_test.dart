import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/instagram/instagram_parse.dart';

const _profile = {
  'data': {
    'user': {
      'id': '25025320',
      'username': 'instagram',
      'full_name': 'Instagram',
      'biography': 'Discover',
      'profile_pic_url': 'https://scontent.cdninstagram.com/a.jpg',
      'profile_pic_url_hd': 'https://scontent.cdninstagram.com/hd.jpg',
      'is_private': false,
      'is_verified': true,
      'edge_followed_by': {'count': 100},
      'edge_follow': {'count': 10},
      'edge_owner_to_timeline_media': {
        'count': 1,
        'page_info': {'has_next_page': true, 'end_cursor': 'QWE'},
        'edges': [
          {
            'node': {
              'id': '1',
              'shortcode': 'ABC',
              'taken_at_timestamp': 1700000000,
              'display_url': 'https://scontent.cdninstagram.com/p.jpg',
              'is_video': false,
              'edge_liked_by': {'count': 5},
              'edge_media_to_comment': {'count': 1},
              'edge_media_to_caption': {
                'edges': [
                  {
                    'node': {'text': 'hello'},
                  },
                ],
              },
              'owner': {'username': 'instagram', 'full_name': 'Instagram'},
            },
          },
        ],
      },
    },
  },
};

void main() {
  test('normaliseInstagramHandle strips @ and URLs, rejects paths', () {
    expect(normaliseInstagramHandle('@Instagram'), 'instagram');
    expect(normaliseInstagramHandle(' some.user_1 '), 'some.user_1');
    expect(
      normaliseInstagramHandle('https://www.instagram.com/instagram/'),
      'instagram',
    );
    expect(
      normaliseInstagramHandle('https://www.instagram.com/p/ABC/'),
      isNull,
    );
    expect(
      normaliseInstagramHandle('https://www.instagram.com/reel/ABC/'),
      isNull,
    );
    expect(
      normaliseInstagramHandle('https://www.instagram.com/explore/'),
      isNull,
    );
    expect(normaliseInstagramHandle('not a handle!'), isNull);
    expect(normaliseInstagramHandle(''), isNull);
  });

  test('parseInstagramProfileJson reads web_profile_info', () {
    final profile = parseInstagramProfileJson(_profile);
    expect(profile, isNotNull);
    expect(profile!.username, 'instagram');
    expect(profile.id, '25025320');
    expect(profile.displayName, 'Instagram');
    expect(profile.followerCount, 100);
    expect(profile.followingCount, 10);
    expect(profile.mediaCount, 1);
    expect(profile.isVerified, isTrue);
    expect(profile.avatarUrl, contains('hd.jpg'));
  });

  test('parseInstagramProfileMedia reads the first page of posts', () {
    final page = parseInstagramProfileMedia(_profile);
    expect(page.posts, hasLength(1));
    expect(page.posts.single.shortcode, 'ABC');
    expect(page.posts.single.caption, 'hello');
    expect(page.posts.single.likeCount, 5);
    expect(page.cursor, 'QWE');
    expect(page.hasMore, isTrue);
    expect(page.posts.single.author.username, 'instagram');
    expect(page.posts.single.author.pk, '25025320');
  });

  test('parseInstagramMediaNode reads GraphQL sidecar slides', () {
    final page = parseInstagramProfileMedia({
      'data': {
        'user': {
          'id': '1',
          'username': 'natgeo',
          'full_name': 'Nat Geo',
          'edge_owner_to_timeline_media': {
            'edges': [
              {
                'node': {
                  'id': '9',
                  'shortcode': 'CAR',
                  'taken_at_timestamp': 1700000000,
                  'display_url': 'https://scontent.cdninstagram.com/a.jpg',
                  'owner': {'username': 'natgeo', 'id': '1'},
                  'edge_sidecar_to_children': {
                    'edges': [
                      {
                        'node': {
                          'display_url':
                              'https://scontent.cdninstagram.com/a.jpg',
                        },
                      },
                      {
                        'node': {
                          'display_url':
                              'https://scontent.cdninstagram.com/b.jpg',
                        },
                      },
                    ],
                  },
                },
              },
            ],
          },
        },
      },
    });
    expect(page.posts.single.carouselUrls, [
      'https://scontent.cdninstagram.com/a.jpg',
      'https://scontent.cdninstagram.com/b.jpg',
    ]);
    expect(page.posts.single.displayUrls, hasLength(2));
  });

  test('parseInstagramUserFeed reads items and video flags', () {
    final page = parseInstagramUserFeed({
      'items': [
        {
          'id': '2',
          'code': 'DEF',
          'taken_at': 1700000001,
          'media_type': 2,
          'product_type': 'clips',
          'caption': {'text': 'reel'},
          'like_count': 3,
          'image_versions2': {
            'candidates': [
              {'url': 'https://scontent.cdninstagram.com/c.jpg'},
            ],
          },
          'user': {'username': 'instagram', 'full_name': 'Instagram'},
        },
      ],
      'more_available': true,
      'next_max_id': 'cursor2',
    });
    expect(page.posts, hasLength(1));
    expect(page.posts.single.isVideo, isTrue);
    expect(page.posts.single.caption, 'reel');
    expect(page.cursor, 'cursor2');
    expect(page.hasMore, isTrue);
  });

  test('parseInstagramTopSearch dedupes users', () {
    final users = parseInstagramTopSearch({
      'users': [
        {
          'user': {
            'pk': '25025320',
            'username': 'instagram',
            'full_name': 'Instagram',
            'is_verified': true,
          },
        },
        {
          'user': {'pk': '1', 'username': 'Instagram', 'full_name': 'dup'},
        },
      ],
    });
    expect(users, hasLength(1));
    expect(users.single.username, 'instagram');
    expect(users.single.isVerified, isTrue);
  });

  test('missing fields do not throw', () {
    expect(parseInstagramProfileJson({'data': {}}), isNull);
    expect(parseInstagramProfileMedia({}).posts, isEmpty);
    expect(parseInstagramUserFeed(null).posts, isEmpty);
    expect(parseInstagramTopSearch('nope'), isEmpty);
  });

  test('instagramLoginRequired reads fail messages', () {
    expect(
      instagramLoginRequired({'status': 'fail', 'message': 'login_required'}),
      isTrue,
    );
    expect(instagramLoginRequired({'status': 'ok'}), isFalse);
  });
}
