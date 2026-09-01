import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/deepmarks/deepmarks_client.dart';
import 'package:xta/plugins/deepmarks/nostr_event.dart';

const _secretHex = '67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa';
const _publicHex = '7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e';

DeepmarksClient _client(Future<http.Response> Function(http.Request) handler) =>
    DeepmarksClient(httpClient: MockClient(handler));

NostrEvent _event() => signWebBookmark(
      secretKeyHex: _secretHex,
      url: 'https://x.com/jack/status/20',
      title: 'Jack (@jack): just setting up my twttr',
      now: DateTime.utc(2026, 7, 25),
    );

http.Response _json(Object body, int status) =>
    http.Response(jsonEncode(body), status, headers: {'content-type': 'application/json'});

void main() {
  group('publishBookmark', () {
    test('posts the signed event to the documented endpoint', () async {
      http.Request? seen;
      final client = _client((request) async {
        seen = request;
        return _json({'eventId': 'abc', 'publishedTo': ['wss://relay.deepmarks.org'], 'failedRelays': []}, 200);
      });

      final event = _event();
      final result = await client.publishBookmark(baseUrl: '', apiKey: 'dmk_live_x', event: event);

      expect(result.eventId, 'abc');
      expect(result.publishedTo, ['wss://relay.deepmarks.org']);
      expect(result.reachedNoRelay, isFalse);

      expect(seen!.url, Uri.parse('https://api.deepmarks.org/api/v1/bookmarks'));
      expect(seen!.headers['Authorization'], 'Bearer dmk_live_x');

      // The body must be the complete signed event the API requires.
      final body = jsonDecode(seen!.body) as Map<String, dynamic>;
      expect(body.keys.toSet(), {'id', 'pubkey', 'created_at', 'kind', 'tags', 'content', 'sig'});
      expect(body['kind'], 39701);
      expect(body['pubkey'], _publicHex);
      expect((body['tags'] as List).first, ['d', 'https://x.com/jack/status/20']);
    });

    test('honours a custom API base for self-hosted or local instances', () async {
      http.Request? seen;
      final client = _client((request) async {
        seen = request;
        return _json({'eventId': 'abc'}, 200);
      });

      await client.publishBookmark(baseUrl: 'http://localhost:4000', apiKey: 'k', event: _event());

      expect(seen!.url, Uri.parse('http://localhost:4000/api/v1/bookmarks'));
    });

    test('flags an event that reached the API but no relay', () async {
      final client = _client((_) async =>
          _json({'eventId': 'abc', 'publishedTo': [], 'failedRelays': ['wss://relay.deepmarks.org']}, 200));

      final result = await client.publishBookmark(baseUrl: '', apiKey: 'k', event: _event());

      expect(result.reachedNoRelay, isTrue);
    });

    test('does not call out when there is no API key', () async {
      var called = false;
      final client = _client((_) async {
        called = true;
        return _json({}, 200);
      });

      await expectLater(
        client.publishBookmark(baseUrl: '', apiKey: '   ', event: _event()),
        throwsA(isA<DeepmarksException>()
            .having((e) => e.kind, 'kind', DeepmarksErrorKind.notConfigured)),
      );
      expect(called, isFalse);
    });

    test('maps each documented status to something actionable', () async {
      final cases = {
        400: DeepmarksErrorKind.rejected,
        401: DeepmarksErrorKind.unauthorized,
        402: DeepmarksErrorKind.notLifetimeMember,
        403: DeepmarksErrorKind.keyMismatch,
        429: DeepmarksErrorKind.rateLimited,
        503: DeepmarksErrorKind.badServer,
      };

      for (final entry in cases.entries) {
        final client = _client((_) async => _json({'error': 'nope'}, entry.key));

        await expectLater(
          client.publishBookmark(baseUrl: '', apiKey: 'k', event: _event()),
          throwsA(isA<DeepmarksException>().having((e) => e.kind, 'kind', entry.value)),
          reason: 'HTTP ${entry.key}',
        );
      }
    });

    test('an HTML page is a wrong address, not a success', () async {
      final client = _client((_) async => http.Response('<html>nginx</html>', 200, headers: {'content-type': 'text/html'}));

      await expectLater(
        client.publishBookmark(baseUrl: 'https://wrong.example.com', apiKey: 'k', event: _event()),
        throwsA(isA<DeepmarksException>().having((e) => e.kind, 'kind', DeepmarksErrorKind.badServer)),
      );
    });

    test('an unreachable host is a network failure', () async {
      final client = _client((_) async => throw http.ClientException('No route to host'));

      await expectLater(
        client.publishBookmark(baseUrl: '', apiKey: 'k', event: _event()),
        throwsA(isA<DeepmarksException>().having((e) => e.kind, 'kind', DeepmarksErrorKind.network)),
      );
    });
  });

  group('verify', () {
    test('returns the account pubkey when a bookmark reveals it', () async {
      http.Request? seen;
      final client = _client((request) async {
        seen = request;
        return _json({'bookmarks': [{'id': 'e1', 'pubkey': _publicHex}], 'count': 1, 'mode': 'list'}, 200);
      });

      expect(await client.verify(baseUrl: '', apiKey: 'k'), _publicHex);
      expect(seen!.url.path, '/api/v1/bookmarks');
      expect(seen!.url.queryParameters['limit'], '1');
    });

    test('returns null — not a mismatch — for an account with no bookmarks yet', () async {
      final client = _client((_) async => _json({'bookmarks': [], 'count': 0, 'mode': 'list'}, 200));

      expect(await client.verify(baseUrl: '', apiKey: 'k'), isNull);
    });

    test('surfaces the lifetime-tier requirement', () async {
      final client = _client((_) async => _json({'error': 'api access is available to lifetime-tier members'}, 402));

      await expectLater(
        client.verify(baseUrl: '', apiKey: 'k'),
        throwsA(isA<DeepmarksException>()
            .having((e) => e.kind, 'kind', DeepmarksErrorKind.notLifetimeMember)),
      );
    });
  });
}
