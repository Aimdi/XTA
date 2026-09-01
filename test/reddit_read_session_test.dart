import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/reddit/reddit_auth.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_comments.dart';
import 'package:xta/plugins/reddit/reddit_read_session.dart';

void main() {
  group('RedditReadSession.resolve', () {
    test('public source never exchanges a refresh token', () async {
      var tokenCalls = 0;
      final auth = RedditAuth(
        httpClient: MockClient((request) async {
          tokenCalls++;
          return http.Response('{}', 500);
        }),
      );
      final prefs = PrefServiceCache(
        cache: {
          optionPluginRedditClientId: 'cid',
          optionPluginRedditSource: redditSourcePublic,
          optionPluginRedditRefreshToken: 'refresh_me',
        },
      );

      final session = await RedditReadSession.resolve(prefs: prefs, auth: auth);

      expect(session.preferPublic, isTrue);
      expect(session.userToken, isNull);
      expect(session.clientId, 'cid');
      expect(tokenCalls, 0);
      expect(prefs.get<String>(optionPluginRedditRefreshToken), 'refresh_me');
    });

    test(
      'auto source with a refresh token yields a user access token',
      () async {
        final auth = RedditAuth(
          httpClient: MockClient((request) async {
            expect(request.url.host, 'www.reddit.com');
            return http.Response(
              jsonEncode({
                'access_token': 'access_abc',
                'token_type': 'bearer',
                'expires_in': 3600,
              }),
              200,
              headers: {'content-type': 'application/json'},
            );
          }),
        );
        final prefs = PrefServiceCache(
          cache: {
            optionPluginRedditClientId: 'cid',
            optionPluginRedditSource: redditSourceAuto,
            optionPluginRedditRefreshToken: 'refresh_me',
          },
        );

        final session = await RedditReadSession.resolve(
          prefs: prefs,
          auth: auth,
        );

        expect(session.preferPublic, isFalse);
        expect(session.userToken, 'access_abc');
        expect(session.clientId, 'cid');
      },
    );

    test(
      'a dead refresh token is cleared and the session falls back',
      () async {
        final auth = RedditAuth(
          httpClient: MockClient(
            (request) async => http.Response('{"error":"invalid_grant"}', 401),
          ),
        );
        final prefs = PrefServiceCache(
          cache: {
            optionPluginRedditClientId: 'cid',
            optionPluginRedditSource: redditSourceAuto,
            optionPluginRedditRefreshToken: 'stale',
          },
        );

        final session = await RedditReadSession.resolve(
          prefs: prefs,
          auth: auth,
        );

        expect(session.userToken, isNull);
        expect(session.preferPublic, isFalse);
        expect(prefs.get<String>(optionPluginRedditRefreshToken), '');
      },
    );

    test(
      'auto without a refresh token keeps the client id for app-only',
      () async {
        final prefs = PrefServiceCache(
          cache: {
            optionPluginRedditClientId: 'cid',
            optionPluginRedditSource: redditSourceAuto,
          },
        );

        final session = await RedditReadSession.resolve(prefs: prefs);

        expect(session.clientId, 'cid');
        expect(session.userToken, isNull);
        expect(session.preferPublic, isFalse);
      },
    );
  });

  group('RedditReadSession.fetchSubreddit', () {
    test(
      'threads userToken into the client so oauth.reddit.com is used',
      () async {
        Uri? seen;
        final client = RedditClient(
          httpClient: MockClient((request) async {
            seen = request.url;
            return http.Response(
              jsonEncode({
                'kind': 'Listing',
                'data': {'after': null, 'children': []},
              }),
              200,
              headers: {'content-type': 'application/json'},
            );
          }),
        );
        const session = RedditReadSession(
          clientId: 'cid',
          preferPublic: false,
          userToken: 'user_tok',
        );

        await session.fetchSubreddit(client, 'dartlang', limit: 10);

        expect(seen?.host, 'oauth.reddit.com');
        expect(seen?.path, contains('/r/dartlang/'));
      },
    );

    test('passes top listing time filters through to the client', () async {
      Uri? seen;
      final client = RedditClient(
        httpClient: MockClient((request) async {
          seen = request.url;
          return http.Response(
            jsonEncode({
              'kind': 'Listing',
              'data': {'after': null, 'children': []},
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );
      const session = RedditReadSession(
        clientId: 'cid',
        preferPublic: false,
        userToken: 'user_tok',
      );

      await session.fetchSubreddit(
        client,
        'dartlang',
        sort: RedditSort.top,
        timeFilter: RedditTimeFilter.week,
      );

      expect(seen?.queryParameters['t'], 'week');
    });
  });

  group('RedditReadSession.fetchComments', () {
    test(
      'threads credentials so oauth.reddit.com serves the thread JSON',
      () async {
        http.BaseRequest? seen;
        final client = RedditClient(
          httpClient: MockClient((request) async {
            seen = request;
            return http.Response(
              jsonEncode([
                {
                  'kind': 'Listing',
                  'data': {
                    'children': [
                      {
                        'kind': 't3',
                        'data': {
                          'id': 'abc123',
                          'title': 'Hello',
                          'subreddit': 'dartlang',
                          'permalink': '/r/dartlang/comments/abc123/hello/',
                          'url': 'https://example.com/story',
                          'is_self': true,
                          'selftext': 'Post body',
                          'score': 1,
                          'num_comments': 1,
                          'created_utc': 1769000000,
                        },
                      },
                    ],
                  },
                },
                {
                  'kind': 'Listing',
                  'data': {
                    'children': [
                      {
                        'kind': 't1',
                        'data': {
                          'id': 'c1',
                          'author': 'reader',
                          'body': 'Nice',
                          'score': 3,
                          'created_utc': 1769000100,
                          'is_submitter': false,
                          'permalink': '/r/dartlang/comments/abc123/hello/c1/',
                          'replies': '',
                        },
                      },
                    ],
                  },
                },
              ]),
              200,
              headers: {'content-type': 'application/json'},
            );
          }),
        );
        const session = RedditReadSession(
          clientId: 'cid',
          preferPublic: false,
          userToken: 'user_tok',
        );

        final result = await session.fetchComments(
          client,
          '/r/dartlang/comments/abc123/hello/',
          sort: 'new',
        );

        expect(seen?.url.host, 'oauth.reddit.com');
        expect(seen?.url.path, '/r/dartlang/comments/abc123/hello.json');
        expect(seen?.url.queryParameters['raw_json'], '1');
        expect(seen?.url.queryParameters['sort'], 'new');
        expect(seen?.headers['authorization'], 'Bearer user_tok');
        expect(result.selfText, 'Post body');
        expect(result.postUrl, 'https://example.com/story');
        expect(result.comments, hasLength(1));
        expect(result.comments.single, isA<RedditComment>());
        expect(result.comments.single.body, 'Nice');
      },
    );
  });
}
