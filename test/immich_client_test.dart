import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/immich/immich_client.dart';

final _bytes = Uint8List.fromList([1, 2, 3]);

ImmichClient _client(MockClient mock) => ImmichClient(httpClient: mock);

Future<ImmichUploadResult> _upload(ImmichClient client) => client.upload(
      baseUrl: 'http://immich.local:2283',
      apiKey: 'key',
      bytes: _bytes,
      fileName: 'photo.jpg',
      deviceAssetId: 'media-1',
      createdAt: DateTime.utc(2026, 3, 4, 5, 6, 7),
    );

void main() {
  group('reading the address someone pasted', () {
    test('a bare LAN address is http, because that is what those serve', () {
      final uri = parseImmichBaseUrl('192.168.1.10:2283')!;

      expect(uri.scheme, 'http');
      expect(uri.host, '192.168.1.10');
      expect(uri.port, 2283);
    });

    test('a bare name is assumed to be reachable properly', () {
      expect(parseImmichBaseUrl('photos.example.org')!.scheme, 'https');
    });

    test('a scheme that was typed is kept', () {
      expect(parseImmichBaseUrl('http://photos.example.org')!.scheme, 'http');
      expect(parseImmichBaseUrl('https://192.168.1.10:2283')!.scheme, 'https');
    });

    // Immich's own settings screen shows the URL with /api on the end, so that is
    // what gets copied. The endpoints add it themselves.
    test('an /api copied from Immich is dropped rather than doubled', () {
      expect(parseImmichBaseUrl('https://photos.example.org/api')!.pathSegments.where((s) => s.isNotEmpty), isEmpty);
    });

    test('a reverse-proxy prefix is kept', () {
      final uri = parseImmichBaseUrl('https://home.example.org/immich/api')!;

      expect(uri.pathSegments.where((s) => s.isNotEmpty), ['immich']);
    });

    test('a trailing slash changes nothing', () {
      expect(parseImmichBaseUrl('https://photos.example.org/'), parseImmichBaseUrl('https://photos.example.org'));
    });

    test('nothing usable is null rather than a guess', () {
      expect(parseImmichBaseUrl(''), isNull);
      expect(parseImmichBaseUrl('   '), isNull);
      expect(parseImmichBaseUrl('not a url'), isNull);
      expect(parseImmichBaseUrl('ftp://photos.example.org'), isNull);
    });
  });

  group('uploading an asset', () {
    test('goes to /api/assets with the key and the fields Immich needs', () async {
      late http.BaseRequest sent;
      final client = _client(MockClient((request) async {
        sent = request;
        return http.Response(jsonEncode({'id': 'asset-1', 'status': 'created'}), 201,
            headers: {'content-type': 'application/json'});
      }));

      final result = await _upload(client);

      expect(sent.url.path, '/api/assets');
      expect(sent.headers['x-api-key'], 'key');
      expect(result.outcome, ImmichUploadOutcome.created);
      expect(result.assetId, 'asset-1');
    });

    test('a file Immich already had is a success, not a failure', () async {
      final client = _client(MockClient((_) async => http.Response(
            jsonEncode({'id': 'asset-1', 'status': 'duplicate'}),
            200,
            headers: {'content-type': 'application/json'},
          )));

      expect((await _upload(client)).outcome, ImmichUploadOutcome.duplicate);
    });

    // An instance a release or two behind only answers on the old path.
    test('falls back to the older endpoint when the current one is not there', () async {
      final paths = <String>[];
      final client = _client(MockClient((request) async {
        paths.add(request.url.path);
        if (request.url.path == '/api/assets') {
          return http.Response('{"message":"Not found"}', 404, headers: {'content-type': 'application/json'});
        }
        return http.Response(jsonEncode({'id': 'a', 'status': 'created'}), 201,
            headers: {'content-type': 'application/json'});
      }));

      final result = await _upload(client);

      expect(paths, ['/api/assets', '/api/asset/upload']);
      expect(result.outcome, ImmichUploadOutcome.created);
    });

    test('a rejected key is reported as unauthorized', () async {
      final client = _client(MockClient((_) async => http.Response('{}', 401)));

      await expectLater(
        _upload(client),
        throwsA(isA<ImmichException>().having((e) => e.kind, 'kind', ImmichErrorKind.unauthorized)),
      );
    });

    test('no address or key at all never reaches the network', () async {
      var called = false;
      final client = _client(MockClient((_) async {
        called = true;
        return http.Response('{}', 200);
      }));

      await expectLater(
        client.upload(
            baseUrl: '',
            apiKey: '',
            bytes: _bytes,
            fileName: 'a.jpg',
            deviceAssetId: 'm',
            createdAt: DateTime.utc(2026)),
        throwsA(isA<ImmichException>().having((e) => e.kind, 'kind', ImmichErrorKind.notConfigured)),
      );
      expect(called, isFalse);
    });
  });

  group('checking the details', () {
    test('a server that knows the user is a pass', () async {
      final client = _client(MockClient((request) async {
        expect(request.url.path, '/api/users/me');
        return http.Response('{"email":"me@example.org"}', 200, headers: {'content-type': 'application/json'});
      }));

      expect(await client.verify(baseUrl: 'http://immich.local:2283', apiKey: 'key'), isTrue);
    });

    test('an older instance answering on the previous path still passes', () async {
      final paths = <String>[];
      final client = _client(MockClient((request) async {
        paths.add(request.url.path);
        if (request.url.path == '/api/users/me') {
          return http.Response('{}', 404, headers: {'content-type': 'application/json'});
        }
        return http.Response('{"email":"me@example.org"}', 200, headers: {'content-type': 'application/json'});
      }));

      expect(await client.verify(baseUrl: 'http://immich.local:2283', apiKey: 'key'), isTrue);
      expect(paths, ['/api/users/me', '/api/user/me']);
    });

    // Something is listening, but it is not Immich — a reverse proxy or a router
    // page. Reporting that as a server error would send the user looking in the
    // wrong place.
    test('an HTML page from something that is not Immich is a bad address', () async {
      final client = _client(MockClient((_) async => http.Response('<html>Router</html>', 200)));

      await expectLater(
        client.verify(baseUrl: 'http://192.168.1.1', apiKey: 'key'),
        throwsA(isA<ImmichException>().having((e) => e.kind, 'kind', ImmichErrorKind.badServer)),
      );
    });

    test('an unreachable server is a network problem, not a wrong key', () async {
      final client = _client(MockClient((_) async => throw http.ClientException('no route to host')));

      await expectLater(
        client.verify(baseUrl: 'http://immich.local:2283', apiKey: 'key'),
        throwsA(isA<ImmichException>().having((e) => e.kind, 'kind', ImmichErrorKind.network)),
      );
    });
  });

  group('albums', () {
    test('an album that exists is used rather than made twice', () async {
      final methods = <String>[];
      final client = _client(MockClient((request) async {
        methods.add(request.method);
        return http.Response(jsonEncode([{'id': 'album-1', 'albumName': 'Art'}]), 200,
            headers: {'content-type': 'application/json'});
      }));

      expect(await client.ensureAlbum(baseUrl: 'http://immich.local:2283', apiKey: 'key', name: 'Art'), 'album-1');
      expect(methods, ['GET']);
    });

    test('an album that does not exist is created', () async {
      final client = _client(MockClient((request) async {
        if (request.method == 'GET') {
          return http.Response('[]', 200, headers: {'content-type': 'application/json'});
        }
        return http.Response(jsonEncode({'id': 'album-2'}), 201, headers: {'content-type': 'application/json'});
      }));

      expect(await client.ensureAlbum(baseUrl: 'http://immich.local:2283', apiKey: 'key', name: 'New'), 'album-2');
    });

    // The assets are already in the library by this point. An album that could
    // not be resolved must not turn a finished upload into a failure.
    test('a server that will not talk about albums gives null, not a throw', () async {
      final client = _client(MockClient((_) async => http.Response('nope', 500)));

      expect(await client.ensureAlbum(baseUrl: 'http://immich.local:2283', apiKey: 'key', name: 'Art'), isNull);
    });

    test('filing nothing does not call the server', () async {
      var called = false;
      final client = _client(MockClient((_) async {
        called = true;
        return http.Response('{}', 200);
      }));

      expect(
        await client.addToAlbum(baseUrl: 'http://immich.local:2283', apiKey: 'key', albumId: 'a', assetIds: const []),
        isTrue,
      );
      expect(called, isFalse);
    });

    test('filing assets sends their ids', () async {
      late String body;
      final client = _client(MockClient((request) async {
        body = request.body;
        return http.Response('[]', 200, headers: {'content-type': 'application/json'});
      }));

      await client.addToAlbum(
          baseUrl: 'http://immich.local:2283', apiKey: 'key', albumId: 'a', assetIds: const ['x', 'y']);

      expect(jsonDecode(body), {'ids': ['x', 'y']});
    });
  });
}
