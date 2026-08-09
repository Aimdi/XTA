import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/reddit/reddit_auth.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_post_source.dart';

/// A signed-in reader, an expired access token, and a token endpoint that
/// answers [answer]. What happens to the stored refresh token?
Future<BasePrefService> _mintAgainst(Future<http.Response> Function() answer) async {
  final prefs = PrefServiceCache(
    defaults: {optionPluginRedditClientId: 'client123', optionPluginRedditRefreshToken: 'refresh_abc'},
  );
  final auth = RedditAuth(httpClient: MockClient((_) => answer()));
  final source = RedditPostSource(RedditClient(), prefs, auth: auth);

  await source.userAccessTokenForTest();
  return prefs;
}

void main() {
  group('what a failed token refresh does to the sign-in', () {
    // The bug: any RedditException dropped the stored refresh token — so being
    // offline at the wrong moment signed the reader out for good, silently.
    test('a network error keeps the session', () async {
      final prefs = await _mintAgainst(() => throw http.ClientException('offline'));

      expect(prefs.get<String>(optionPluginRedditRefreshToken), 'refresh_abc');
    });

    test('a 500 from the token endpoint keeps the session', () async {
      final prefs = await _mintAgainst(() async => http.Response('server error', 500));

      expect(prefs.get<String>(optionPluginRedditRefreshToken), 'refresh_abc');
    });

    test('a rate limit keeps the session', () async {
      final prefs = await _mintAgainst(() async => http.Response('too many', 429));

      expect(prefs.get<String>(optionPluginRedditRefreshToken), 'refresh_abc');
    });

    // A 200 that is not JSON is a block page, not a verdict on the token.
    test('a block page keeps the session, as a Reddit error rather than a crash', () async {
      final prefs = await _mintAgainst(() async => http.Response('<html>checking your browser</html>', 200));

      expect(prefs.get<String>(optionPluginRedditRefreshToken), 'refresh_abc');
    });

    // Only Reddit actually refusing the grant means the session is over.
    test('a rejected client id ends the session', () async {
      final prefs = await _mintAgainst(() async => http.Response('unauthorized', 401));

      expect(prefs.get<String>(optionPluginRedditRefreshToken), '');
    });

    test('invalid_grant ends the session', () async {
      final prefs = await _mintAgainst(() async => http.Response('{"error": "invalid_grant"}', 400));

      expect(prefs.get<String>(optionPluginRedditRefreshToken), '');
    });
  });
}
