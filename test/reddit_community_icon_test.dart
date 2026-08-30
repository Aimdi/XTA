import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';

http.Response _json(Object body) => http.Response(
      jsonEncode(body),
      200,
      headers: {'content-type': 'application/json'},
    );

Map<String, dynamic> _tokenBody() => {
      'access_token': 'tok_123',
      'token_type': 'bearer',
      'expires_in': 3600,
      'scope': '*',
    };

void main() {
  group('redditCommunityIconUrl', () {
    test('prefers community_icon, unescapes, and strips a signed thumb', () {
      expect(
        redditCommunityIconUrl(
          communityIcon:
              'https://styles.redditmedia.com/t5_x/styles/communityIcon.png?width=64&' 'amp;s=abc',
          iconImg: 'https://b.thumbs.redditmedia.com/tiny.png',
        ),
        'https://styles.redditmedia.com/t5_x/styles/communityIcon.png',
      );
    });

    test('falls back to icon_img when community_icon is empty', () {
      expect(
        redditCommunityIconUrl(
          communityIcon: '',
          iconImg: '//b.thumbs.redditmedia.com/logo.png',
        ),
        'https://b.thumbs.redditmedia.com/logo.png',
      );
    });

    test('rejects the site snoo and placeholder names', () {
      expect(
        redditCommunityIconUrl(
          communityIcon: 'https://www.redditstatic.com/icon.png',
          iconImg: 'default',
        ),
        isNull,
      );
    });
  });

  group('fetchSubredditIcon', () {
    test('reads community_icon from public about.json', () async {
      final client = RedditClient(
        httpClient: MockClient((request) async {
          expect(request.url.path, '/r/girlsfrontline2/about.json');
          return _json({
            'kind': 't5',
            'data': {
              'display_name': 'girlsfrontline2',
              'community_icon':
                  'https://styles.redditmedia.com/t5_gf2/styles/communityIcon.png',
              'icon_img': '',
            },
          });
        }),
      );

      expect(
        await client.fetchSubredditIcon('girlsfrontline2'),
        'https://styles.redditmedia.com/t5_gf2/styles/communityIcon.png',
      );
    });

    test('oauth about.json is used when a client id is set', () async {
      final asked = <Uri>[];
      final client = RedditClient(
        httpClient: MockClient((request) async {
          asked.add(request.url);
          if (request.url.path.contains('access_token')) {
            return _json(_tokenBody());
          }
          return _json({
            'kind': 't5',
            'data': {
              'display_name': 'novelai',
              'community_icon':
                  'https://styles.redditmedia.com/t5_nai/styles/communityIcon.png',
            },
          });
        }),
      );

      expect(
        await client.fetchSubredditIcon('novelai', clientId: 'id'),
        'https://styles.redditmedia.com/t5_nai/styles/communityIcon.png',
      );
      expect(asked.any((u) => u.path.contains('access_token')), isTrue);
      expect(
        asked.any((u) => u.path == '/r/novelai/about.json'),
        isTrue,
      );
    });

    test('guest about.json falls back to old.reddit when www is blocked', () async {
      final asked = <Uri>[];
      final client = RedditClient(
        httpClient: MockClient((request) async {
          asked.add(request.url);
          if (request.url.host == 'www.reddit.com') {
            return http.Response('blocked', 403);
          }
          if (request.url.host == 'old.reddit.com' &&
              request.url.path == '/r/girlsfrontline2/about.json') {
            return _json({
              'kind': 't5',
              'data': {
                'display_name': 'girlsfrontline2',
                'community_icon':
                    'https://styles.redditmedia.com/t5_gf2/styles/communityIcon.png?width=64&s=sig',
              },
            });
          }
          return http.Response('no', 404);
        }),
      );

      expect(
        await client.fetchSubredditIcon('girlsfrontline2'),
        'https://styles.redditmedia.com/t5_gf2/styles/communityIcon.png',
      );
      expect(
        asked.any((u) => u.host == 'www.reddit.com' && u.path.contains('about.json')),
        isTrue,
      );
      expect(
        asked.any((u) => u.host == 'old.reddit.com' && u.path.contains('about.json')),
        isTrue,
      );
    });
  });
}
