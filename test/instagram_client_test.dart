import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/instagram/instagram_client.dart';
import 'package:xta/plugins/instagram/instagram_discovery.dart';

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

  test('homepage HTML is accepted when warming the guest session', () async {
    final client = InstagramClient(
      prefs,
      httpClient: MockClient((request) async {
        if (request.url.path == '/') {
          return http.Response(
            '<html><body>ok</body></html>',
            200,
            headers: {'set-cookie': 'csrftoken=abc; Path=/, mid=mid1; Path=/'},
          );
        }
        return http.Response(jsonEncode(_profileJson()), 200);
      }),
    );

    await client.warmGuest();
    expect(client.cookieHeader, contains('csrftoken=abc'));
    expect(client.cookieHeader, contains('mid=mid1'));
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

  test('private empty profile is returned for the lock screen', () async {
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

    final profile = await client.profile('secret');
    expect(profile.username, 'secret');
    expect(profile.isPrivate, isTrue);
  });

  test('guestDiscover throws when every seed fails', () async {
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
        return http.Response('', 429);
      }),
    );

    expect(
      () => client.guestDiscover(),
      throwsA(
        isA<InstagramException>().having(
          (e) => e.kind,
          'kind',
          InstagramErrorKind.rateLimited,
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

  test('forYou uses explore_grid when the session answers', () async {
    await prefs.set(
      optionPluginInstagramCookies,
      'sessionid=s1; csrftoken=c1; ds_user_id=9; mid=m1; ig_did=d1',
    );
    final requested = <String>[];
    final client = InstagramClient(
      prefs,
      httpClient: MockClient((request) async {
        requested.add(request.url.path);
        if (request.url.path.endsWith('/discover/web/explore_grid/')) {
          return http.Response(
            jsonEncode({
              'more_available': false,
              'sectional_items': [
                {
                  'layout_content': {
                    'medias': [
                      {
                        'media': {
                          'id': '99',
                          'code': 'EXP',
                          'taken_at': 1700000000,
                          'user': {
                            'username': 'natgeo',
                            'full_name': 'Nat Geo',
                          },
                        },
                      },
                    ],
                  },
                },
              ],
            }),
            200,
          );
        }
        return http.Response('no', 404);
      }),
    );

    final page = await client.forYou();
    expect(page.posts.single.shortcode, 'EXP');
    expect(requested, contains('/api/v1/discover/web/explore_grid/'));
  });

  test('forYou skips Explore without a session', () async {
    final requested = <String>[];
    final client = InstagramClient(
      prefs,
      httpClient: MockClient((request) async {
        requested.add(request.url.path);
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
        if (request.url.path.contains('explore')) {
          return http.Response('should not hit explore', 500);
        }
        if (request.url.path == '/api/v1/users/web_profile_info/') {
          final user = request.url.queryParameters['username'] ?? 'x';
          return http.Response(jsonEncode(_profileJson(username: user)), 200);
        }
        return http.Response('no', 404);
      }),
    );

    final page = await client.forYou();
    expect(page.posts, isNotEmpty);
    expect(
      page.posts.every(
        (p) => kInstagramDiscoverHandles.contains(p.author.username),
      ),
      isTrue,
    );
    expect(requested.any((p) => p.contains('explore')), isFalse);
    expect(requested.any((p) => p.contains('web_profile_info')), isTrue);
  });

  test(
    'forYou falls back to public seeds when Explore needs a login',
    () async {
      await prefs.set(
        optionPluginInstagramCookies,
        'sessionid=s1; csrftoken=c1; ds_user_id=9; mid=m1; ig_did=d1',
      );
      final requested = <String>[];
      final client = InstagramClient(
        prefs,
        httpClient: MockClient((request) async {
          requested.add(request.url.path);
          if (request.url.path.contains('explore')) {
            return http.Response(
              jsonEncode({'status': 'fail', 'message': 'login_required'}),
              200,
            );
          }
          if (request.url.path == '/api/v1/users/web_profile_info/') {
            final user = request.url.queryParameters['username'] ?? 'x';
            return http.Response(jsonEncode(_profileJson(username: user)), 200);
          }
          return http.Response('no', 404);
        }),
      );

      final page = await client.forYou();
      expect(page.posts, isNotEmpty);
      expect(
        page.posts.every(
          (p) => kInstagramDiscoverHandles.contains(p.author.username),
        ),
        isTrue,
      );
      expect(requested.any((p) => p.contains('explore')), isTrue);
      expect(requested.any((p) => p.contains('web_profile_info')), isTrue);
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
