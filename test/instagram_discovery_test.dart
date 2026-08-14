import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/instagram/instagram_discovery.dart';
import 'package:xta/plugins/instagram/instagram_models.dart';
import 'package:xta/plugins/instagram/instagram_parse.dart';

InstagramPost _post(String id, String handle) {
  return InstagramPost(
    id: id,
    shortcode: id,
    caption: id,
    createdAt: DateTime.fromMillisecondsSinceEpoch(0),
    author: InstagramAuthor(username: handle, fullName: handle),
  );
}

void main() {
  test('interleaveInstagramDiscover round-robins and dedupes', () {
    final mixed = interleaveInstagramDiscover([
      [_post('a1', 'instagram'), _post('a2', 'instagram')],
      [_post('b1', 'natgeo'), _post('a1', 'instagram')],
      [_post('c1', 'nasa')],
    ]);
    expect(mixed.map((e) => e.id), ['a1', 'b1', 'c1', 'a2']);
  });

  test('instagramSearchOpensHandle is guest-only for a real handle', () {
    expect(
      instagramSearchOpensHandle(hasSession: false, query: '@NatGeo'),
      isTrue,
    );
    expect(
      instagramSearchOpensHandle(hasSession: true, query: '@NatGeo'),
      isFalse,
    );
    expect(
      instagramSearchOpensHandle(hasSession: false, query: 'some person'),
      isFalse,
    );
  });

  test('peopleToFollowFromInstagram skips followed authors', () {
    final people = peopleToFollowFromInstagram(
      posts: [
        _post('1', 'instagram'),
        _post('2', 'natgeo'),
        _post('3', 'instagram'),
        _post('4', 'nasa'),
      ],
      alreadyFollows: (handle) => handle == 'instagram',
    );
    expect(people.map((e) => e.username), ['natgeo', 'nasa']);
  });

  test('parseInstagramExplore walks sectional media grids', () {
    final page = parseInstagramExplore({
      'more_available': true,
      'next_max_id': 'cur1',
      'sectional_items': [
        {
          'layout_content': {
            'fill_items': [
              {
                'media': {
                  'id': '11',
                  'code': 'AAA',
                  'taken_at': 1700000000,
                  'user': {'username': 'natgeo', 'full_name': 'Nat Geo'},
                  'image_versions2': {
                    'candidates': [
                      {'url': 'https://scontent.cdninstagram.com/a.jpg'},
                    ],
                  },
                },
              },
            ],
          },
        },
        {
          'layout_content': {
            'medias': [
              {
                'media': {
                  'pk': 12,
                  'code': 'BBB',
                  'taken_at': 1700000001,
                  'caption': {'text': 'reel'},
                  'media_type': 2,
                  'user': {'username': 'nasa', 'full_name': 'NASA'},
                },
              },
            ],
          },
        },
      ],
    });
    expect(page.posts.map((e) => e.shortcode), ['AAA', 'BBB']);
    expect(page.posts.last.isVideo, isTrue);
    expect(page.cursor, 'cur1');
    expect(page.hasMore, isTrue);
  });

  test('parseInstagramExplore ignores a reshaped empty payload', () {
    expect(parseInstagramExplore({'sectional_items': []}).posts, isEmpty);
    expect(parseInstagramExplore(null).posts, isEmpty);
  });
}
