import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/instagram/instagram_client.dart';

Map<String, Object?> _profileJson({
  String username = 'instagram',
  bool private = false,
}) => {
  'data': {
    'user': {
      'id': '25025320',
      'username': username,
      'full_name': 'Instagram',
      'is_private': private,
      'media_count': private ? 0 : 1,
      'edge_owner_to_timeline_media': {
        'count': private ? 0 : 1,
        'page_info': {'has_next_page': false},
        'edges': [
          if (!private)
            {
              'node': {
                'id': '1',
                'shortcode': 'ABC',
                'taken_at_timestamp': 1700000000,
                'display_url': 'https://scontent.cdninstagram.com/p.jpg',
                'owner': {'username': username, 'full_name': 'Instagram'},
              },
            },
        ],
      },
    },
  },
};

void main() {
  late PrefServiceCache prefs;

  setUp(() {
    prefs = PrefServiceCache(cache: {});
  });

  test('profile warms the homepage then hits i.instagram.com', () async {
    final requested = <Uri>[];
    final client = InstagramClient(
      prefs,
      httpClient: MockClient((request) async {
        requested.add(request.url);
        if (request.url.path == '/') {
          return http.Response(
            '<html></html>',
            200,
            headers: {
              'set-cookie': 'csrftoken=abc; Path=/, mid=mid1; Path=/',
              'content-type': 'text/html',
            },
          );
        }
        if (request.url.host == 'i.instagram.com' &&
            request.url.path == '/api/v1/users/web_profile_info/') {
          expect(request.headers['x-ig-app-id'], instagramWebAppId);
          expect(request.headers['cookie'], contains('csrftoken=abc'));
          return http.Response(jsonEncode(_profileJson()), 200);
        }
        return http.Response('no', 404);
      }),
    );

    final profile = await client.profile('Instagram');
    expect(profile.username, 'instagram');
    expect(requested.map((u) => u.host), contains('www.instagram.com'));
    expect(requested.map((u) => u.host), contains('i.instagram.com'));
  });

  test('web_profile_info falls back to www after 429', () async {
    final requested = <Uri>[];
    final client = InstagramClient(
      prefs,
      httpClient: MockClient((request) async {
        requested.add(request.url);
        if (request.url.path == '/') {
          return http.Response(
            '<html></html>',
            200,
            headers: {'set-cookie': 'csrftoken=abc; Path=/, mid=mid1; Path=/'},
          );
        }
        if (request.url.host == 'i.instagram.com') {
          return http.Response('', 429);
        }
        if (request.url.host == 'www.instagram.com' &&
            request.url.path == '/api/v1/users/web_profile_info/') {
          return http.Response(jsonEncode(_profileJson()), 200);
        }
        return http.Response('no', 404);
      }),
    );

    final profile = await client.profile('instagram');
    expect(profile.id, '25025320');
    expect(
      requested
          .where((u) => u.path.contains('web_profile_info'))
          .map((u) => u.host),
      ['i.instagram.com', 'www.instagram.com'],
    );
  });

  test('HTML app shell is loginRequired, not a parse crash', () async {
    final client = InstagramClient(
      prefs,
      httpClient: MockClient((request) async {
        if (request.url.path == '/') {
          return http.Response(
            '<html></html>',
            200,
            headers: {'set-cookie': 'csrftoken=abc; Path=/, mid=mid1; Path=/'},
          );
        }
        return http.Response('<html><body>login</body></html>', 200);
      }),
    );

    expect(
      () => client.profile('instagram'),
      throwsA(
        isA<InstagramException>().having(
          (e) => e.kind,
          'kind',
          InstagramErrorKind.loginRequired,
        ),
      ),
    );
  });

  test('private empty profile is privateAccount', () async {
    final client = InstagramClient(
      prefs,
      httpClient: MockClient((request) async {
        if (request.url.path == '/') {
          return http.Response(
            '<html></html>',
            200,
            headers: {'set-cookie': 'csrftoken=abc; Path=/, mid=mid1; Path=/'},
          );
        }
        return http.Response(
          jsonEncode(_profileJson(username: 'secret', private: true)),
          200,
        );
      }),
    );

    expect(
      () => client.profile('secret'),
      throwsA(
        isA<InstagramException>().having(
          (e) => e.kind,
          'kind',
          InstagramErrorKind.privateAccount,
        ),
      ),
    );
  });

  test(
    'setCookies and importThreadsCookies share names, not a client',
    () async {
      await prefs.set(
        optionPluginThreadsDirectCookies,
        'sessionid=s1; csrftoken=c1; ds_user_id=9; mid=m1; ig_did=d1',
      );
      final client = InstagramClient(
        prefs,
        httpClient: MockClient((_) async {
          return http.Response('no', 404);
        }),
      );

      expect(client.hasSession, isFalse);
      await client.importThreadsCookies();
      expect(client.hasSession, isTrue);
      expect(client.cookieHeader, contains('sessionid=s1'));
      expect(
        prefs.get<String>(optionPluginInstagramCookies),
        contains('sessionid=s1'),
      );
    },
  );

  test('invalid handle is notFound before any request', () async {
    var hits = 0;
    final client = InstagramClient(
      prefs,
      httpClient: MockClient((_) async {
        hits += 1;
        return http.Response('no', 404);
      }),
    );

    expect(
      () => client.profile('https://www.instagram.com/p/ABC/'),
      throwsA(
        isA<InstagramException>().having(
          (e) => e.kind,
          'kind',
          InstagramErrorKind.notFound,
        ),
      ),
    );
    expect(hits, 0);
  });
}
