import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/reddit/reddit_auth.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_post_source.dart';
import 'package:xta/plugins/reddit/reddit_read_session.dart';

class _StalledTokenClient extends http.BaseClient {
  _StalledTokenClient({this.stallAt = 1, this.stallBody = false});

  final int stallAt;
  final bool stallBody;
  final requests = <http.BaseRequest>[];
  final pending = Completer<http.StreamedResponse>();
  final body = StreamController<List<int>>();
  bool aborted = false;
  bool closed = false;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    requests.add(request);
    if (requests.length != stallAt) {
      return Future.value(
        http.StreamedResponse(Stream.value(utf8.encode(jsonEncode({'access_token': 'token-${requests.length}'}))), 200),
      );
    }
    if (request is http.Abortable) {
      request.abortTrigger?.then((_) {
        aborted = true;
        final error = http.RequestAbortedException(request.url);
        if (stallBody) {
          body.addError(error);
          unawaited(body.close());
        } else {
          pending.completeError(error);
        }
      });
    }
    return stallBody ? Future.value(http.StreamedResponse(body.stream, 200)) : pending.future;
  }

  @override
  void close() => closed = true;
}

void main() {
  for (final stallBody in [false, true]) {
    testWidgets('stalled token ${stallBody ? 'body' : 'headers'} is aborted and a retry succeeds', (tester) async {
      final client = _StalledTokenClient(stallBody: stallBody);
      final auth = RedditAuth(httpClient: client);
      Object? failure;
      var finished = false;
      final read = auth
          .accessToken(clientId: 'client', refreshToken: 'refresh')
          .then<void>(
            (_) => finished = true,
            onError: (Object error) {
              failure = error;
              finished = true;
            },
          );

      await tester.pump();
      await tester.pump(const Duration(seconds: 16));

      expect(finished, isTrue, reason: 'A token refresh must not hold every Reddit surface indefinitely.');
      await read;
      expect(failure, isA<RedditException>().having((e) => e.kind, 'kind', RedditErrorKind.network));
      expect(client.aborted, isTrue, reason: 'A deadline must release the underlying request.');
      expect(client.closed, isFalse, reason: 'Other requests still share this client.');
      expect(await auth.accessToken(clientId: 'client', refreshToken: 'refresh'), 'token-2');
    });
  }

  testWidgets('a stalled renewal after the token expires releases every waiter and keeps sign-in', (tester) async {
    var now = DateTime(2026, 9, 25);
    final client = _StalledTokenClient(stallAt: 2);
    final prefs = PrefServiceCache(
      defaults: {optionPluginRedditClientId: 'client', optionPluginRedditRefreshToken: 'refresh'},
    );
    final source = RedditPostSource(
      RedditClient(httpClient: client),
      prefs,
      auth: RedditAuth(httpClient: client),
      clock: () => now,
    );
    expect(await source.userAccessTokenForTest(), 'token-1');
    now = now.add(kRedditUserTokenTtl + const Duration(seconds: 1));

    final results = <String?>[];
    final first = source.userAccessTokenForTest().then(results.add);
    final second = source.userAccessTokenForTest().then(results.add);
    await tester.pump();
    expect(client.requests, hasLength(2), reason: 'The two surfaces share one token renewal.');
    await tester.pump(const Duration(seconds: 16));

    expect(results, [null, null], reason: 'Both surfaces must regain their public fallback and retry.');
    await Future.wait([first, second]);
    expect(prefs.get<String>(optionPluginRedditRefreshToken), 'refresh');
    expect(client.aborted, isTrue);
    expect(await source.userAccessTokenForTest(), 'token-3');
    expect(client.requests, hasLength(3), reason: 'The stalled shared future must be cleared.');
  });

  testWidgets('a standalone reader keeps its sign-in when token renewal times out', (tester) async {
    final client = _StalledTokenClient();
    final auth = RedditAuth(httpClient: client);
    final prefs = PrefServiceCache(
      defaults: {optionPluginRedditClientId: 'client', optionPluginRedditRefreshToken: 'refresh'},
    );
    RedditReadSession? session;
    final read = RedditReadSession.resolve(prefs: prefs, auth: auth).then((value) => session = value);
    await tester.pump();
    await tester.pump(const Duration(seconds: 16));

    expect(session, isNotNull);
    await read;
    expect(session!.userToken, isNull);
    expect(prefs.get<String>(optionPluginRedditRefreshToken), 'refresh');
    expect((await RedditReadSession.resolve(prefs: prefs, auth: auth)).userToken, 'token-2');
  });
}
