import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/reddit/reddit_auth.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';

/// Reddit's authorization-code flow for installed apps — the same one RedReader
/// uses. Signing in is optional and gets the reader their own account's rate
/// limits, which is the most reliable route Reddit offers.
http.Response _json(Object body, int status) =>
    http.Response(jsonEncode(body), status, headers: {'content-type': 'application/json'});

void main() {
  group('authorizeUrl', () {
    final uri = RedditAuth.authorizeUrl(clientId: 'my_id', state: 'st4te');

    test('asks for a code against the registered app', () {
      expect(uri.host, 'www.reddit.com');
      expect(uri.path, '/api/v1/authorize.compact');
      expect(uri.queryParameters['client_id'], 'my_id');
      expect(uri.queryParameters['response_type'], 'code');
      expect(uri.queryParameters['redirect_uri'], RedditAuth.redirectUri);
      expect(uri.queryParameters['state'], 'st4te');
    });

    test('asks for a lasting session, or the reader would log in again hourly', () {
      expect(uri.queryParameters['duration'], 'permanent');
    });

    test('asks only for read scopes: XTA never posts, votes or subscribes', () {
      final scopes = uri.queryParameters['scope']!.split(',');

      expect(scopes, containsAll(<String>['read', 'identity']));
      for (final write in ['submit', 'vote', 'edit', 'subscribe', 'save', 'report']) {
        expect(scopes, isNot(contains(write)), reason: write);
      }
    });
  });

  group('reading the redirect', () {
    test('takes the code Reddit sent back', () {
      final uri = Uri.parse('${RedditAuth.redirectUri}?state=st4te&code=abc123');

      expect(RedditAuth.codeFrom(uri, expectedState: 'st4te'), 'abc123');
    });

    test('refuses a code whose state does not match, so none can be injected', () {
      final uri = Uri.parse('${RedditAuth.redirectUri}?state=somebody_else&code=abc123');

      expect(RedditAuth.codeFrom(uri, expectedState: 'st4te'), isNull);
    });

    test('ignores every other page the login walks through', () {
      for (final url in [
        'https://www.reddit.com/login',
        'https://www.reddit.com/api/v1/authorize.compact?client_id=my_id',
        'https://accounts.google.com/signin',
      ]) {
        expect(RedditAuth.codeFrom(Uri.parse(url), expectedState: 'st4te'), isNull, reason: url);
      }
    });

    test('a refusal is recognised, so declining closes quietly', () {
      final denied = Uri.parse('${RedditAuth.redirectUri}?state=st4te&error=access_denied');

      expect(RedditAuth.deniedIn(denied), isTrue);
      expect(RedditAuth.codeFrom(denied, expectedState: 'st4te'), isNull);
      expect(RedditAuth.deniedIn(Uri.parse('https://www.reddit.com/login')), isFalse);
    });
  });

  group('exchanging the code', () {
    test('posts the documented grant with the client id as basic auth', () async {
      late http.Request sent;
      final auth = RedditAuth(httpClient: MockClient((request) async {
        sent = request;
        return _json({'refresh_token': 'refresh_me', 'access_token': 'tok'}, 200);
      }));

      final token = await auth.exchangeCode(clientId: 'my_id', code: 'abc123');

      expect(token, 'refresh_me');
      expect(sent.url, Uri.parse('https://www.reddit.com/api/v1/access_token'));
      expect(sent.body, contains('grant_type=authorization_code'));
      expect(sent.body, contains('code=abc123'));
      // An installed app has no secret, so the password half stays empty.
      expect(sent.headers['Authorization'], 'Basic ${base64Encode(utf8.encode('my_id:'))}');
      expect(sent.headers['User-Agent'], RedditClient.userAgent);
    });

    test('a refresh token buys an access token without asking the reader again', () async {
      late http.Request sent;
      final auth = RedditAuth(httpClient: MockClient((request) async {
        sent = request;
        return _json({'access_token': 'fresh', 'expires_in': 3600}, 200);
      }));

      expect(await auth.accessToken(clientId: 'my_id', refreshToken: 'refresh_me'), 'fresh');
      expect(sent.body, contains('grant_type=refresh_token'));
      expect(sent.body, contains('refresh_token=refresh_me'));
    });

    test('signing in still needs a client id, and says so', () async {
      final auth = RedditAuth(httpClient: MockClient((_) async => _json({}, 200)));

      await expectLater(
        auth.exchangeCode(clientId: '  ', code: 'abc123'),
        throwsA(isA<RedditException>().having((e) => e.kind, 'kind', RedditErrorKind.notConfigured)),
      );
    });

    test('a rejected client id is reported as unauthorised, not as a broken response', () async {
      final auth = RedditAuth(httpClient: MockClient((_) async => _json({'error': 401}, 401)));

      await expectLater(
        auth.exchangeCode(clientId: 'wrong', code: 'abc123'),
        throwsA(isA<RedditException>().having((e) => e.kind, 'kind', RedditErrorKind.unauthorized)),
      );
    });

    test('a response without the token is a bad response rather than a crash', () async {
      final auth = RedditAuth(httpClient: MockClient((_) async => _json({'access_token': 'only_this'}, 200)));

      await expectLater(
        auth.exchangeCode(clientId: 'my_id', code: 'abc123'),
        throwsA(isA<RedditException>().having((e) => e.kind, 'kind', RedditErrorKind.badResponse)),
      );
    });
  });

  group('the signed-in read', () {
    test('a user token goes to the authenticated host and is used verbatim', () async {
      final requests = <http.Request>[];
      final client = RedditClient(httpClient: MockClient((request) async {
        requests.add(request);
        return _json({
          'kind': 'Listing',
          'data': {'after': null, 'children': const []},
        }, 200);
      }));

      await client.fetchSubreddit('dartlang', clientId: 'my_id', userToken: 'user_tok');

      // No token request: the caller already holds one, so app-only auth is
      // skipped entirely.
      expect(requests, hasLength(1));
      expect(requests.single.url.host, 'oauth.reddit.com');
      expect(requests.single.headers['Authorization'], 'Bearer user_tok');
    });
  });
}
