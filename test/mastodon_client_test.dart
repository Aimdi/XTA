import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';

void main() {
  group('MastodonClient', () {
    test('verify prefers /api/v2/instance', () async {
      final client = MastodonClient(
        httpClient: MockClient((request) async {
          expect(request.url.path, '/api/v2/instance');
          expect(request.url.host, 'mastodon.social');
          return http.Response(
            jsonEncode({'domain': 'mastodon.social'}),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      await client.verify('https://mastodon.social');
    });

    test('verify falls back to /api/v1/instance', () async {
      var calls = 0;
      final client = MastodonClient(
        httpClient: MockClient((request) async {
          calls++;
          if (request.url.path == '/api/v2/instance') {
            return http.Response('missing', 404);
          }
          expect(request.url.path, '/api/v1/instance');
          return http.Response(
            jsonEncode({'uri': 'https://old.example'}),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      await client.verify('old.example');
      expect(calls, 2);
    });

    test('lookup parses a public account payload', () async {
      final client = MastodonClient(
        httpClient: MockClient((request) async {
          expect(request.url.path, '/api/v1/accounts/lookup');
          expect(request.url.queryParameters['acct'], 'gargron');
          return http.Response(
            jsonEncode({
              'id': '1',
              'username': 'Gargron',
              'acct': 'Gargron',
              'display_name': 'Eugen',
              'note': '',
              'url': 'https://mastodon.social/@Gargron',
              'followers_count': 1,
              'following_count': 2,
              'statuses_count': 3,
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      final profile = await client.lookup('https://mastodon.social', 'Gargron');
      expect(profile.id, '1');
      expect(profile.acct, 'gargron@mastodon.social');
      expect(profile.displayName, 'Eugen');
    });

    test('getStatuses returns parsed posts', () async {
      final client = MastodonClient(
        httpClient: MockClient((request) async {
          expect(request.url.path, '/api/v1/accounts/1/statuses');
          expect(request.url.queryParameters['limit'], '5');
          return http.Response(
            jsonEncode([
              {
                'id': '9',
                'created_at': '2026-08-01T09:00:00.000Z',
                'content': '<p>Hi</p>',
                'url': 'https://mastodon.social/@a/9',
                'account': {
                  'id': '1',
                  'username': 'a',
                  'acct': 'a',
                  'display_name': 'A',
                  'note': '',
                  'url': 'https://mastodon.social/@a',
                },
                'media_attachments': [],
              },
            ]),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      final posts = await client.getStatuses(
        'https://mastodon.social',
        '1',
        limit: 5,
      );
      expect(posts, hasLength(1));
      expect(posts.first.text, 'Hi');
    });

    test('fetchThread resolves a status URL then loads context', () async {
      final client = MastodonClient(
        httpClient: MockClient((request) async {
          final path = request.url.path;
          if (path == '/api/v2/search') {
            expect(
              request.url.queryParameters['q'],
              'https://other.social/@b/22',
            );
            expect(request.url.queryParameters['resolve'], 'true');
            return http.Response(
              jsonEncode({
                'statuses': [
                  {
                    'id': '100',
                    'created_at': '2026-08-01T09:00:00.000Z',
                    'content': '<p>Root</p>',
                    'url': 'https://other.social/@b/22',
                    'replies_count': 1,
                    'account': {
                      'id': '2',
                      'username': 'b',
                      'acct': 'b@other.social',
                      'display_name': 'B',
                      'note': '',
                      'url': 'https://other.social/@b',
                    },
                    'media_attachments': [],
                  },
                ],
              }),
              200,
              headers: {'content-type': 'application/json'},
            );
          }
          if (path == '/api/v1/statuses/100/context') {
            return http.Response(
              jsonEncode({
                'ancestors': [],
                'descendants': [
                  {
                    'id': '101',
                    'created_at': '2026-08-01T10:00:00.000Z',
                    'content': '<p>Reply</p>',
                    'url': 'https://other.social/@c/101',
                    'account': {
                      'id': '3',
                      'username': 'c',
                      'acct': 'c@other.social',
                      'display_name': 'C',
                      'note': '',
                      'url': 'https://other.social/@c',
                    },
                    'media_attachments': [],
                  },
                ],
              }),
              200,
              headers: {'content-type': 'application/json'},
            );
          }
          return http.Response('unexpected ${request.url}', 500);
        }),
      );

      final seed = MastodonPost(
        id: '22',
        acct: 'b@other.social',
        authorName: 'B',
        text: 'Root',
        url: 'https://other.social/@b/22',
      );
      final thread = await client.fetchThread('https://mastodon.social', seed);
      expect(thread.status.id, '100');
      expect(thread.descendants, hasLength(1));
      expect(thread.descendants.first.text, 'Reply');
    });

    test('maps 404 and 429 to typed errors', () async {
      final notFound = MastodonClient(
        httpClient: MockClient((_) async => http.Response('missing', 404)),
      );
      await expectLater(
        notFound.lookup('https://mastodon.social', 'nobody'),
        throwsA(
          isA<MastodonException>().having(
            (e) => e.kind,
            'kind',
            MastodonErrorKind.notFound,
          ),
        ),
      );

      final limited = MastodonClient(
        httpClient: MockClient((_) async => http.Response('slow', 429)),
      );
      await expectLater(
        limited.getStatuses('https://mastodon.social', '1'),
        throwsA(
          isA<MastodonException>().having(
            (e) => e.kind,
            'kind',
            MastodonErrorKind.rateLimited,
          ),
        ),
      );
    });

    test(
      'fetchThread on origin skips a search 401 and uses the URL snowflake',
      () async {
        final client = MastodonClient(
          httpClient: MockClient((request) async {
            final path = request.url.path;
            if (path == '/api/v2/search') {
              return http.Response(
                '{"error":"Search queries that resolve remote resources are not allowed"}',
                401,
              );
            }
            if (path == '/api/v1/statuses/22') {
              return http.Response(
                jsonEncode(
                  _statusJson(
                    id: '22',
                    url: 'https://other.social/@b/22',
                    text: 'Root',
                  ),
                ),
                200,
                headers: {'content-type': 'application/json'},
              );
            }
            if (path == '/api/v1/statuses/22/context') {
              return http.Response(
                jsonEncode({
                  'ancestors': [],
                  'descendants': [
                    _statusJson(
                      id: '23',
                      url: 'https://other.social/@c/23',
                      text: 'Reply',
                      username: 'c',
                    ),
                  ],
                }),
                200,
                headers: {'content-type': 'application/json'},
              );
            }
            return http.Response('unexpected ${request.url}', 500);
          }),
        );

        // Card id is from a *different* host; the public URL still carries origin's id.
        final seed = MastodonPost(
          id: '999',
          acct: 'b@other.social',
          authorName: 'B',
          text: 'Root',
          url: 'https://other.social/@b/22',
        );
        final thread = await client.fetchThread('https://other.social', seed);
        expect(thread.status.id, '22');
        expect(thread.descendants.single.text, 'Reply');
      },
    );

    test(
      'fetchThreadAnywhere rediscovers a remote post via account statuses when search is closed',
      () async {
        final asked = <String>[];
        final client = MastodonClient(
          httpClient: MockClient((request) async {
            asked.add('${request.url.host}${request.url.path}');
            final host = request.url.host;
            final path = request.url.path;

            if (host == 'closed.social') {
              if (path == '/api/v2/search') {
                return http.Response('nope', 401);
              }
              return http.Response('gone', 404);
            }

            if (path == '/api/v2/search') {
              return http.Response('nope', 401);
            }
            if (path == '/api/v1/accounts/lookup') {
              return http.Response(
                jsonEncode({
                  'id': '7',
                  'username': 'b',
                  'acct': 'b@closed.social',
                  'display_name': 'B',
                  'note': '',
                  'url': 'https://closed.social/@b',
                }),
                200,
                headers: {'content-type': 'application/json'},
              );
            }
            if (path == '/api/v1/accounts/7/statuses') {
              return http.Response(
                jsonEncode([
                  _statusJson(
                    id: '100',
                    url: 'https://closed.social/@b/22',
                    text: 'Root',
                    acct: 'b@closed.social',
                  ),
                ]),
                200,
                headers: {'content-type': 'application/json'},
              );
            }
            if (path == '/api/v1/statuses/100/context') {
              return http.Response(
                jsonEncode({
                  'ancestors': [],
                  'descendants': [
                    _statusJson(
                      id: '101',
                      url: 'https://closed.social/@c/101',
                      text: 'Via open',
                      username: 'c',
                    ),
                  ],
                }),
                200,
                headers: {'content-type': 'application/json'},
              );
            }
            // Direct status ids from the seed are meaningless on the proxy.
            if (path.startsWith('/api/v1/statuses/')) {
              return http.Response('missing', 404);
            }
            return http.Response('unexpected ${request.url}', 500);
          }),
        );

        final seed = MastodonPost(
          id: '22',
          acct: 'b@closed.social',
          authorName: 'B',
          text: 'Root',
          url: 'https://closed.social/@b/22',
        );
        final thread = await client.fetchThreadAnywhere([
          'https://closed.social',
          'https://open.social',
        ], seed);

        expect(thread.status.id, '100');
        expect(thread.descendants.single.text, 'Via open');
        expect(asked.any((e) => e.startsWith('closed.social')), isTrue);
        expect(asked.any((e) => e.startsWith('open.social')), isTrue);
      },
    );

    test('getPublicTimeline reads /timelines/public?local=true', () async {
      final client = MastodonClient(
        httpClient: MockClient((request) async {
          expect(request.url.path, '/api/v1/timelines/public');
          expect(request.url.queryParameters['local'], 'true');
          return http.Response(
            jsonEncode([
              _statusJson(
                id: '3',
                url: 'https://mastodon.social/@a/3',
                text: 'local',
              ),
            ]),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );
      final posts = await client.getPublicTimeline(
        'https://mastodon.social',
        local: true,
      );
      expect(posts.single.text, 'local');
    });

    test('getPublicTimeline falls back to Misskey featured', () async {
      final asked = <String>[];
      final client = MastodonClient(
        httpClient: MockClient((request) async {
          asked.add('${request.method} ${request.url.path}');
          if (request.url.path == '/api/v1/timelines/public') {
            return http.Response('no', 404);
          }
          expect(request.method, 'POST');
          expect(request.url.path, '/api/notes/featured');
          return http.Response(
            jsonEncode([
              {
                'id': 'mk1',
                'text': 'from misskey',
                'user': {'username': 'neo', 'host': null},
              },
            ]),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );
      final posts = await client.getPublicTimeline('https://misskey.io');
      expect(posts.single.text, 'from misskey');
      expect(asked, contains('GET /api/v1/timelines/public'));
      expect(asked, contains('POST /api/notes/featured'));
    });

    test('getPublicTimeline pages with max_id', () async {
      final client = MastodonClient(
        httpClient: MockClient((request) async {
          expect(request.url.queryParameters['max_id'], '3');
          return http.Response(
            jsonEncode([
              _statusJson(
                id: '2',
                url: 'https://mastodon.social/@a/2',
                text: 'older',
              ),
            ]),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );
      final posts = await client.getPublicTimeline(
        'https://mastodon.social',
        maxId: '3',
      );
      expect(posts.single.id, '2');
    });

    test('getStatuses asks only_media and max_id', () async {
      final client = MastodonClient(
        httpClient: MockClient((request) async {
          expect(request.url.path, '/api/v1/accounts/1/statuses');
          expect(request.url.queryParameters['only_media'], 'true');
          expect(request.url.queryParameters['max_id'], '9');
          return http.Response(
            jsonEncode([
              _statusJson(
                id: '8',
                url: 'https://mastodon.social/@a/8',
                text: 'pic',
              ),
            ]),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );
      final posts = await client.getStatuses(
        'https://mastodon.social',
        '1',
        onlyMedia: true,
        maxId: '9',
      );
      expect(posts.single.text, 'pic');
    });

    test('Misskey local-timeline pages with untilId', () async {
      final client = MastodonClient(
        httpClient: MockClient((request) async {
          if (request.url.path == '/api/v1/timelines/public') {
            return http.Response('no', 404);
          }
          expect(request.url.path, '/api/notes/local-timeline');
          final body = jsonDecode(request.body) as Map<String, dynamic>;
          expect(body['untilId'], 'mk1');
          return http.Response(
            jsonEncode([
              {
                'id': 'mk0',
                'text': 'older note',
                'user': {'username': 'neo', 'host': null},
              },
            ]),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );
      final posts = await client.getPublicTimeline(
        'https://misskey.io',
        local: true,
        maxId: 'mk1',
      );
      expect(posts.single.text, 'older note');
    });

    test('getStatuses asks pinned=true', () async {
      final client = MastodonClient(
        httpClient: MockClient((request) async {
          expect(request.url.queryParameters['pinned'], 'true');
          return http.Response(
            jsonEncode([
              _statusJson(
                id: '1',
                url: 'https://mastodon.social/@a/1',
                text: 'pin',
              ),
            ]),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );
      final posts = await client.getStatuses(
        'https://mastodon.social',
        '1',
        pinned: true,
      );
      expect(posts.single.text, 'pin');
    });

    test('search reads accounts, statuses, and hashtags', () async {
      final client = MastodonClient(
        httpClient: MockClient((request) async {
          expect(request.url.path, '/api/v2/search');
          expect(request.url.queryParameters['q'], 'flutter');
          return http.Response(
            jsonEncode({
              'accounts': [],
              'statuses': [
                _statusJson(
                  id: '4',
                  url: 'https://mastodon.social/@a/4',
                  text: 'about flutter',
                ),
              ],
              'hashtags': [
                {'name': 'flutter', 'history': []},
              ],
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );
      final page = await client.search('https://mastodon.social', 'flutter');
      expect(page.posts.single.text, 'about flutter');
      expect(page.tags.single.name, 'flutter');
    });
  });
}

Map<String, dynamic> _statusJson({
  required String id,
  required String url,
  required String text,
  String username = 'b',
  String? acct,
}) {
  return {
    'id': id,
    'created_at': '2026-08-01T09:00:00.000Z',
    'content': '<p>$text</p>',
    'url': url,
    'replies_count': 1,
    'account': {
      'id': '2',
      'username': username,
      'acct': acct ?? username,
      'display_name': username.toUpperCase(),
      'note': '',
      'url': 'https://example.social/@$username',
    },
    'media_attachments': [],
  };
}
