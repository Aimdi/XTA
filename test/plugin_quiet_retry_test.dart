import 'dart:convert';
import 'dart:io' show SocketException;

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/karakeep/karakeep_client.dart';
import 'package:xta/plugins/rss/rss_client.dart';
import 'package:xta/utils/read_request_scope.dart';

void main() {
  testWidgets('Bluesky recovers before returning a parsed page and its cursor', (tester) async {
    var calls = 0;
    final requests = <http.Request>[];
    final client = BlueskyClient(
      httpClient: MockClient((request) async {
        calls++;
        requests.add(request);
        if (calls == 1) throw const SocketException('connection changed');
        if (calls == 2) return http.Response('temporarily unavailable', 503);
        return http.Response(jsonEncode({'cursor': 'oldest', 'feed': []}), 200);
      }),
    );
    final result = client.getAuthorFeed('alice.bsky.social', cursor: 'older');
    final check = expectLater(result, completion(isNotNull));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump(const Duration(milliseconds: 1200));
    await check;
    expect((await result).cursor, 'oldest');
    expect(requests.map((request) => request.url.queryParameters['cursor']), everyElement('older'));
    expect(calls, 3);
  });

  test('RSS retains HTTP status so a containing read does not retry account or rate-limit failures', () async {
    for (final status in [401, 403, 404, 429]) {
      var calls = 0;
      final client = RssClient(
        httpClient: MockClient((_) async {
          calls++;
          return http.Response('unavailable', status);
        }),
      );
      await expectLater(
        ReadRequestScope().start(
          () => client.fetchChannel('https://example.org/feed.xml'),
          timeout: const Duration(seconds: 8),
        ),
        throwsA(isA<HttpException>().having((error) => error.statusCode, 'status', status)),
      );
      expect(calls, 1);
    }
  });

  testWidgets('saved-service connection checks retry safe GET requests', (tester) async {
    var calls = 0;
    final requests = <http.Request>[];
    final client = KarakeepClient(
      httpClient: MockClient((request) async {
        requests.add(request);
        return ++calls == 1
            ? http.Response('busy', 502)
            : http.Response('{}', 200, headers: {'content-type': 'application/json'});
      }),
    );
    final result = client.verify(baseUrl: 'https://example.org', apiKey: 'key');
    final check = expectLater(result, completion(isTrue));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await check;
    expect(calls, 2);
    expect(requests.map((request) => request.method), everyElement('GET'));
    expect(requests.map((request) => request.headers['Authorization']), everyElement('Bearer key'));
  });

  test('failed bookmark saves remain single-attempt', () async {
    for (final networkFailure in [false, true]) {
      var calls = 0;
      final client = KarakeepClient(
        httpClient: MockClient((request) async {
          expect(request.method, 'POST');
          calls++;
          if (networkFailure) throw const SocketException('response lost');
          return http.Response('busy', 503);
        }),
      );
      await expectLater(
        client.saveLink(baseUrl: 'https://example.org', apiKey: 'key', url: 'https://example.org/post'),
        throwsA(isA<KarakeepException>()),
      );
      expect(calls, 1);
    }
  });
}
