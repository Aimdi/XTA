import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/karakeep/karakeep_client.dart';

KarakeepClient _client(Future<http.Response> Function(http.Request) handler) =>
    KarakeepClient(httpClient: MockClient(handler));

void main() {
  group('parseKarakeepBaseUrl', () {
    test('assumes https when the scheme is left out', () {
      expect(parseKarakeepBaseUrl('karakeep.example.com'), Uri.parse('https://karakeep.example.com'));
    });

    test('keeps an explicit scheme, host and port', () {
      expect(parseKarakeepBaseUrl('http://192.168.1.10:3000'), Uri.parse('http://192.168.1.10:3000'));
    });

    test('drops a trailing slash and the api path copied from the docs', () {
      for (final input in [
        'https://k.example.com/',
        'https://k.example.com/api',
        'https://k.example.com/api/v1',
        'https://k.example.com/api/v1/',
      ]) {
        expect(parseKarakeepBaseUrl(input), Uri.parse('https://k.example.com'), reason: input);
      }
    });

    test('keeps a path prefix from a reverse proxy', () {
      expect(parseKarakeepBaseUrl('https://home.example.com/karakeep/api/v1'),
          Uri.parse('https://home.example.com/karakeep'));
    });

    test('rejects what is not a server address', () {
      for (final input in ['', '   ', 'ftp://k.example.com', 'not a url']) {
        expect(parseKarakeepBaseUrl(input), isNull, reason: input);
      }
    });
  });

  group('saveLink', () {
    test('posts a link bookmark with the API key', () async {
      http.Request? seen;
      final client = _client((request) async {
        seen = request;
        return http.Response(jsonEncode({'id': 'bm_1'}), 201, headers: {'content-type': 'application/json'});
      });

      final result = await client.saveLink(
        baseUrl: 'karakeep.example.com',
        apiKey: 'secret',
        url: 'https://x.com/jack/status/20',
        title: 'Jack (@jack): just setting up my twttr',
      );

      expect(result.outcome, KarakeepSaveOutcome.saved);
      expect(result.bookmarkId, 'bm_1');
      expect(seen!.url, Uri.parse('https://karakeep.example.com/api/v1/bookmarks'));
      expect(seen!.headers['Authorization'], 'Bearer secret');

      final body = jsonDecode(seen!.body) as Map<String, dynamic>;
      expect(body['type'], 'link');
      expect(body['url'], 'https://x.com/jack/status/20');
      expect(body['title'], 'Jack (@jack): just setting up my twttr');
    });

    test('a 200 means Karakeep already had the URL', () async {
      final client = _client((_) async =>
          http.Response(jsonEncode({'id': 'bm_1', 'alreadyExists': true}), 200,
              headers: {'content-type': 'application/json'}));

      final result = await client.saveLink(
        baseUrl: 'https://karakeep.example.com',
        apiKey: 'secret',
        url: 'https://example.com',
      );

      expect(result.outcome, KarakeepSaveOutcome.alreadySaved);
    });

    test('respects a reverse-proxy path prefix', () async {
      http.Request? seen;
      final client = _client((request) async {
        seen = request;
        return http.Response('{}', 201, headers: {'content-type': 'application/json'});
      });

      await client.saveLink(
        baseUrl: 'https://home.example.com/karakeep',
        apiKey: 'k',
        url: 'https://example.com',
      );

      expect(seen!.url, Uri.parse('https://home.example.com/karakeep/api/v1/bookmarks'));
    });

    test('omits an empty title rather than sending a blank one', () async {
      http.Request? seen;
      final client = _client((request) async {
        seen = request;
        return http.Response('{}', 201, headers: {'content-type': 'application/json'});
      });

      await client.saveLink(baseUrl: 'k.example.com', apiKey: 'k', url: 'https://example.com', title: '   ');

      expect((jsonDecode(seen!.body) as Map).containsKey('title'), isFalse);
    });

    test('clamps an over-long title to what Karakeep accepts', () async {
      http.Request? seen;
      final client = _client((request) async {
        seen = request;
        return http.Response('{}', 201, headers: {'content-type': 'application/json'});
      });

      await client.saveLink(
        baseUrl: 'k.example.com',
        apiKey: 'k',
        url: 'https://example.com',
        title: 'x' * 900,
      );

      expect((jsonDecode(seen!.body)['title'] as String).length, KarakeepClient.maxTitleLength);
    });

    test('reports missing configuration without making a request', () async {
      var called = false;
      final client = _client((_) async {
        called = true;
        return http.Response('{}', 201);
      });

      await expectLater(
        client.saveLink(baseUrl: '', apiKey: '', url: 'https://example.com'),
        throwsA(isA<KarakeepException>().having((e) => e.kind, 'kind', KarakeepErrorKind.notConfigured)),
      );
      expect(called, isFalse);
    });

    test('a rejected key is reported as such, not as a generic failure', () async {
      final client = _client((_) async => http.Response('{"error":"unauthorized"}', 401,
          headers: {'content-type': 'application/json'}));

      await expectLater(
        client.saveLink(baseUrl: 'k.example.com', apiKey: 'bad', url: 'https://example.com'),
        throwsA(isA<KarakeepException>().having((e) => e.kind, 'kind', KarakeepErrorKind.unauthorized)),
      );
    });

    test('an HTML page means the address is not a Karakeep', () async {
      final client = _client((_) async =>
          http.Response('<!doctype html><html><body>nginx</body></html>', 200, headers: {'content-type': 'text/html'}));

      await expectLater(
        client.saveLink(baseUrl: 'wrong.example.com', apiKey: 'k', url: 'https://example.com'),
        throwsA(isA<KarakeepException>().having((e) => e.kind, 'kind', KarakeepErrorKind.badServer)),
      );
    });

    test('a dead host is reported as a network problem', () async {
      final client = _client((_) async => throw http.ClientException('No route to host'));

      await expectLater(
        client.saveLink(baseUrl: 'k.example.com', apiKey: 'k', url: 'https://example.com'),
        throwsA(isA<KarakeepException>().having((e) => e.kind, 'kind', KarakeepErrorKind.network)),
      );
    });
  });

  group('verify', () {
    test('is true when the bookmarks endpoint answers', () async {
      http.Request? seen;
      final client = _client((request) async {
        seen = request;
        return http.Response('{"bookmarks":[]}', 200, headers: {'content-type': 'application/json'});
      });

      expect(await client.verify(baseUrl: 'k.example.com', apiKey: 'k'), isTrue);
      expect(seen!.url.path, '/api/v1/bookmarks');
    });

    test('surfaces a bad key', () async {
      final client = _client((_) async => http.Response('{}', 403, headers: {'content-type': 'application/json'}));

      await expectLater(
        client.verify(baseUrl: 'k.example.com', apiKey: 'nope'),
        throwsA(isA<KarakeepException>().having((e) => e.kind, 'kind', KarakeepErrorKind.unauthorized)),
      );
    });
  });
}
