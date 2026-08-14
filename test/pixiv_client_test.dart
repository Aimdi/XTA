import 'dart:convert';

import 'package:crypto/crypto.dart';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

void main() {
  late PrefServiceCache prefs;

  setUp(() async {
    prefs = PrefServiceCache(
      cache: {
        optionPluginPixivRefreshToken: 'refresh-me',
        optionPluginPixivAccessToken: '',
        optionPluginPixivAccessExpiresAt: '',
        optionPluginPixivShowR18: false,
      },
    );
  });

  group('PixivClient', () {
    test('refreshAccessToken stores tokens and returns the user', () async {
      final client = PixivClient(
        prefs,
        httpClient: MockClient((request) async {
          expect(request.url.host, 'oauth.secure.pixiv.net');
          expect(request.body, contains('grant_type=refresh_token'));
          expect(request.body, contains('refresh_token=refresh-me'));
          return http.Response(
            jsonEncode({
              'access_token': 'access-1',
              'refresh_token': 'refresh-2',
              'expires_in': 3600,
              'user': {'id': '123', 'name': 'Reader', 'account': 'reader'},
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      final user = await client.refreshAccessToken();
      expect(user.name, 'Reader');
      expect(user.id, 123);
      expect(prefs.get<String>(optionPluginPixivAccessToken), 'access-1');
      expect(prefs.get<int>(optionPluginPixivUserId), 123);
      expect(client.storedUserId, 123);
      expect(prefs.get<String>(optionPluginPixivRefreshToken), 'refresh-2');
    });

    test('following uses the access token and parses illusts', () async {
      await prefs.set(optionPluginPixivAccessToken, 'access-1');
      await prefs.set(
        optionPluginPixivAccessExpiresAt,
        DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
      );

      final client = PixivClient(
        prefs,
        httpClient: MockClient((request) async {
          expect(request.url.path, '/v2/illust/follow');
          expect(request.headers['Authorization'], 'Bearer access-1');
          return http.Response(
            jsonEncode({
              'illusts': [
                {
                  'id': 1,
                  'title': 'Hi',
                  'caption': '',
                  'type': 'illust',
                  'image_urls': {'square_medium': 'https://i.pximg.net/a.jpg'},
                  'user': {
                    'id': 2,
                    'name': 'A',
                    'account': 'a',
                    'profile_image_urls': {},
                  },
                  'page_count': 1,
                  'x_restrict': 0,
                  'sanity_level': 2,
                },
              ],
              'next_url': null,
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      final page = await client.following();
      expect(page.illusts, hasLength(1));
      expect(page.illusts.first.title, 'Hi');
    });

    test('missing refresh token is notConfigured', () async {
      await prefs.set(optionPluginPixivRefreshToken, '');
      final client = PixivClient(
        prefs,
        httpClient: MockClient((_) async => http.Response('', 500)),
      );
      await expectLater(
        client.verify(),
        throwsA(
          isA<PixivException>().having(
            (e) => e.kind,
            'kind',
            PixivErrorKind.notConfigured,
          ),
        ),
      );
    });

    // Pixiv's token endpoint checks a signed timestamp on every request — the
    // official app always sends the pair, and without it a perfectly valid
    // refresh token is refused. That is exactly what "the right token doesn't
    // connect" looks like from the outside.
    group('client time signature', () {
      test(
        'every request carries the time and its hash, and they agree',
        () async {
          final requests = <http.Request>[];
          final client = PixivClient(
            prefs,
            // A fixed instant, so the expected header values are knowable.
            clock: () => DateTime.utc(2026, 8, 6, 1, 2, 3),
            httpClient: MockClient((request) async {
              requests.add(request);
              return _json({
                'access_token': 'access-1',
                'expires_in': 3600,
                'user': {'id': '123', 'name': 'Reader', 'account': 'reader'},
              }, 200);
            }),
          );

          await client.verify();

          final headers = requests.single.headers;
          const time = '2026-08-06T01:02:03+00:00';
          expect(headers['X-Client-Time'], time);
          expect(
            headers['X-Client-Hash'],
            md5
                .convert(utf8.encode('$time${PixivClient.clientHashSalt}'))
                .toString(),
            reason: 'the hash must be of exactly the time string that was sent',
          );
        },
      );

      test('the API requests are signed the same way', () async {
        await prefs.set(optionPluginPixivAccessToken, 'access-1');
        await prefs.set(
          optionPluginPixivAccessExpiresAt,
          DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
        );

        final requests = <http.Request>[];
        final client = PixivClient(
          prefs,
          httpClient: MockClient((request) async {
            requests.add(request);
            return _json({'illusts': [], 'next_url': null}, 200);
          }),
        );

        await client.following();

        final headers = requests.single.headers;
        expect(headers['X-Client-Time'], isNotNull);
        expect(
          headers['X-Client-Hash'],
          md5
              .convert(
                utf8.encode(
                  '${headers['X-Client-Time']}${PixivClient.clientHashSalt}',
                ),
              )
              .toString(),
        );
      });
    });

    test(
      'ranking and search hit the expected paths and query parameters',
      () async {
        await prefs.set(optionPluginPixivAccessToken, 'access-1');
        await prefs.set(
          optionPluginPixivAccessExpiresAt,
          DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
        );

        final requests = <http.Request>[];
        final client = PixivClient(
          prefs,
          httpClient: MockClient((request) async {
            requests.add(request);
            if (request.url.path.contains('search/user')) {
              return _json({
                'user_previews': [
                  {
                    'user': {
                      'id': 9,
                      'name': 'N',
                      'account': 'n',
                      'comment': '',
                      'profile_image_urls': {},
                    },
                  },
                ],
              }, 200);
            }
            if (request.url.path.contains('illust/detail')) {
              return _json({
                'illust': {
                  'id': 5,
                  'title': 'T',
                  'caption': '',
                  'type': 'illust',
                  'image_urls': {'square_medium': 'https://i.pximg.net/a.jpg'},
                  'user': {
                    'id': 1,
                    'name': 'A',
                    'account': 'a',
                    'profile_image_urls': {},
                  },
                  'page_count': 1,
                  'x_restrict': 0,
                  'sanity_level': 2,
                },
              }, 200);
            }
            return _json({'illusts': [], 'next_url': null}, 200);
          }),
        );

        await client.ranking(mode: 'week');
        await client.searchIllust(
          'cat',
          searchTarget: 'exact_match_for_tags',
          sort: 'popular_desc',
        );
        final users = await client.searchUsers('artist');
        final detail = await client.illustDetail(5);
        await client.related(5);
        await client.bookmarks(userId: 123, restrict: 'private');

        final paths = requests.map((request) => request.url.path).toList();
        expect(paths, contains('/v1/illust/ranking'));
        expect(paths, contains('/v1/search/illust'));
        expect(paths, contains('/v1/search/user'));
        expect(paths, contains('/v1/illust/detail'));
        expect(paths, contains('/v2/illust/related'));
        expect(paths, contains('/v1/user/bookmarks/illust'));
        final search = requests.singleWhere(
          (request) => request.url.path == '/v1/search/illust',
        );
        expect(
          search.url.queryParameters['search_target'],
          'exact_match_for_tags',
        );
        expect(search.url.queryParameters['sort'], 'popular_desc');
        final bookmarks = requests.singleWhere(
          (request) => request.url.path == '/v1/user/bookmarks/illust',
        );
        expect(bookmarks.url.queryParameters['restrict'], 'private');
        expect(users.users.single.id, 9);
        expect(detail.id, 5);
      },
    );

    test('followUser posts to the follow-add endpoint', () async {
      http.Request? follow;
      final client = PixivClient(
        prefs,
        httpClient: MockClient((request) async {
          if (request.url.host == 'oauth.secure.pixiv.net') {
            return http.Response(
              jsonEncode({
                'access_token': 'access-1',
                'refresh_token': 'refresh-2',
                'expires_in': 3600,
                'user': {'id': '123', 'name': 'Reader', 'account': 'reader'},
              }),
              200,
              headers: {'content-type': 'application/json'},
            );
          }
          follow = request;
          return http.Response(
            '{}',
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      await client.followUser(42);
      expect(follow!.method, 'POST');
      expect(follow!.url.path, '/v1/user/follow/add');
      expect(follow!.body, contains('user_id=42'));
      expect(follow!.body, contains('restrict=public'));
    });

    test('addBookmark posts to the bookmark-add endpoint', () async {
      http.Request? bookmark;
      final client = PixivClient(
        prefs,
        httpClient: MockClient((request) async {
          if (request.url.host == 'oauth.secure.pixiv.net') {
            return http.Response(
              jsonEncode({
                'access_token': 'access-1',
                'refresh_token': 'refresh-2',
                'expires_in': 3600,
                'user': {'id': '123', 'name': 'Reader', 'account': 'reader'},
              }),
              200,
              headers: {'content-type': 'application/json'},
            );
          }
          bookmark = request;
          return http.Response(
            '{}',
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      await client.addBookmark(99);
      expect(bookmark!.method, 'POST');
      expect(bookmark!.url.path, '/v2/illust/bookmark/add');
      expect(bookmark!.body, contains('illust_id=99'));
      expect(bookmark!.body, contains('restrict=public'));
    });

    test('deleteBookmark posts to the bookmark-delete endpoint', () async {
      http.Request? bookmark;
      final client = PixivClient(
        prefs,
        httpClient: MockClient((request) async {
          if (request.url.host == 'oauth.secure.pixiv.net') {
            return http.Response(
              jsonEncode({
                'access_token': 'access-1',
                'refresh_token': 'refresh-2',
                'expires_in': 3600,
                'user': {'id': '123', 'name': 'Reader', 'account': 'reader'},
              }),
              200,
              headers: {'content-type': 'application/json'},
            );
          }
          bookmark = request;
          return http.Response(
            '{}',
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      await client.deleteBookmark(99);
      expect(bookmark!.method, 'POST');
      expect(bookmark!.url.path, '/v1/illust/bookmark/delete');
      expect(bookmark!.body, contains('illust_id=99'));
    });

    test('a refused token surfaces what Pixiv actually said', () async {
      final client = PixivClient(
        prefs,
        httpClient: MockClient(
          (_) async => _json({
            'has_error': true,
            'errors': {
              'system': {'message': 'Invalid refresh token', 'code': 1508},
            },
          }, 403),
        ),
      );

      await expectLater(
        client.verify(),
        throwsA(
          isA<PixivException>()
              .having((e) => e.kind, 'kind', PixivErrorKind.unauthorized)
              .having(
                (e) => e.message,
                'message',
                contains('Invalid refresh token'),
              ),
        ),
      );
    });

    test(
      'concurrent refreshAccessToken calls share one network round-trip',
      () async {
        var tokenHits = 0;
        final client = PixivClient(
          prefs,
          httpClient: MockClient((request) async {
            if (request.url.host.contains('oauth')) {
              tokenHits++;
              await Future<void>.delayed(const Duration(milliseconds: 40));
              return _json({
                'access_token': 'access-shared',
                'refresh_token': 'refresh-2',
                'expires_in': 3600,
                'user': {'id': '7', 'name': 'A', 'account': 'a'},
              }, 200);
            }
            return _json({'illusts': []}, 200);
          }),
        );

        final results = await Future.wait([
          client.refreshAccessToken(),
          client.refreshAccessToken(),
          client.ensureUserId(),
        ]);

        expect(tokenHits, 1);
        expect(results[0], isA<PixivAuthUser>());
        expect(results[2], 7);
        expect(client.storedUserId, 7);
      },
    );

    test(
      'ensureUserId reuses a stored id without forcing another refresh',
      () async {
        await prefs.set(optionPluginPixivAccessToken, 'access-1');
        await prefs.set(
          optionPluginPixivAccessExpiresAt,
          DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
        );
        await prefs.set(optionPluginPixivUserId, 42);
        var tokenHits = 0;
        final client = PixivClient(
          prefs,
          httpClient: MockClient((request) async {
            if (request.url.host.contains('oauth')) {
              tokenHits++;
            }
            return _json({'illusts': []}, 200);
          }),
        );

        expect(await client.ensureUserId(), 42);
        expect(tokenHits, 0);
      },
    );

    test('related keeps R-18 when the opened illust is R-18', () async {
      await prefs.set(optionPluginPixivAccessToken, 'access-1');
      await prefs.set(
        optionPluginPixivAccessExpiresAt,
        DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
      );

      final client = PixivClient(
        prefs,
        httpClient: MockClient((request) async {
          expect(request.url.path, '/v2/illust/related');
          return _json({
            'illusts': [
              {
                'id': 1,
                'title': 'SFW',
                'caption': '',
                'type': 'illust',
                'image_urls': {'medium': 'https://i.pximg.net/sfw.jpg'},
                'user': {
                  'id': 1,
                  'name': 'A',
                  'account': 'a',
                  'profile_image_urls': {},
                },
                'x_restrict': 0,
                'sanity_level': 2,
              },
              {
                'id': 2,
                'title': 'R18',
                'caption': '',
                'type': 'illust',
                'image_urls': {'medium': 'https://i.pximg.net/r18.jpg'},
                'user': {
                  'id': 1,
                  'name': 'A',
                  'account': 'a',
                  'profile_image_urls': {},
                },
                'x_restrict': 1,
                'sanity_level': 6,
              },
            ],
            'next_url': 'https://app-api.pixiv.net/v2/illust/related?offset=30',
          }, 200);
        }),
      );

      final hidden = await client.related(9);
      expect(hidden.illusts.map((e) => e.id), [1]);

      final kept = await client.related(9, includeR18: true);
      expect(kept.illusts.map((e) => e.id), [1, 2]);
      expect(kept.nextUrl, contains('offset=30'));
    });
  });
}

http.Response _json(Object body, int status) => http.Response(
  jsonEncode(body),
  status,
  headers: {'content-type': 'application/json'},
);
