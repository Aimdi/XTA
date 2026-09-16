import 'support/memory_json_store.dart';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/constants.dart';
import 'package:xta/utils/crash_reporter.dart';
import 'package:xta/utils/webdav_sync.dart';

const _config = WebDavConfig(
  url: 'https://cloud.example.org/dav/xta/backup.json',
  username: 'reader',
  password: 'hunter2',
);

void main() {
  group('WebDavConfig', () {
    test('is incomplete until every field is filled', () {
      expect(_config.isComplete, isTrue);
      expect(const WebDavConfig(url: '', username: 'a', password: 'b').isComplete, isFalse);
      expect(const WebDavConfig(url: 'https://x/y', username: '', password: 'b').isComplete, isFalse);
      expect(const WebDavConfig(url: '  ', username: 'a', password: 'b').isComplete, isFalse);
    });

    // The payload can carry X session tokens, so plaintext is refused outright.
    test('rejects anything that is not https', () {
      expect(const WebDavConfig(url: 'http://cloud/x.json', username: 'a', password: 'b').uri, isNull);
      expect(const WebDavConfig(url: 'not a url at all', username: 'a', password: 'b').uri, isNull);
      expect(_config.uri, isNotNull);
    });

    test('sends credentials as HTTP basic auth', () {
      final header = _config.authHeaders['authorization']!;

      expect(header, startsWith('Basic '));
      expect(utf8.decode(base64Decode(header.substring(6))), 'reader:hunter2');
    });
  });

  group('conditional upload', () {
    test('first upload uses If-None-Match and records the returned validator', () async {
      final storage = MemoryJsonStore();
      late http.Request seen;
      final sync = WebDavSync(
        storage: storage,
        client: MockClient((request) async {
          if (request.method == 'GET') return http.Response('', 404);
          seen = request;
          return http.Response('', 201, headers: {'etag': '"new"'});
        }),
      );
      expect((await sync.upload(_config, '{"hello":"world"}')).isSuccess, isTrue);
      expect(seen.headers['if-none-match'], '*');
      expect(jsonDecode(seen.body)['hello'], 'world');
      expect(storage.values.values, contains('"new"'));
    });
    test('missing parent collection retains the conditional header on retry', () async {
      var puts = 0;
      final sync = WebDavSync(
        storage: MemoryJsonStore(),
        client: MockClient((request) async {
          if (request.method == 'GET') return http.Response('', 404);
          if (request.method == 'MKCOL') return http.Response('', 201);
          expect(request.headers['if-none-match'], '*');
          return ++puts == 1 ? http.Response('', 409) : http.Response('', 201, headers: {'etag': '"new"'});
        }),
      );
      expect((await sync.upload(_config, '{}')).isSuccess, isTrue);
      expect(puts, 2);
    });
    test('an unseen remote version is never overwritten', () async {
      final calls = <String>[];
      final sync = WebDavSync(
        storage: MemoryJsonStore(),
        client: MockClient((request) async {
          calls.add(request.method);
          return http.Response('{}', 200, headers: {'etag': '"other-device"'});
        }),
      );
      expect((await sync.upload(_config, '{}')).outcome, WebDavOutcome.conflict);
      expect(calls, ['GET']);
    });
    test('another device changing the file during upload causes 412 after archiving', () async {
      final store = MemoryJsonStore();
      final calls = <http.Request>[];
      final sync = WebDavSync(
        storage: store,
        client: MockClient((request) async {
          calls.add(request);
          if (request.method == 'GET') return http.Response('{"old":true}', 200, headers: {'etag': '"old"'});
          if (request.url.path.contains('.versions/')) {
            expect(request.body, '{"old":true}');
            expect(request.headers['if-none-match'], '*');
            return http.Response('', 201, headers: {'etag': '"archive"'});
          }
          expect(request.headers['if-match'], '"old"');
          return http.Response('', 412);
        }),
      );
      await sync.acknowledge(_config, await sync.download(_config));
      expect((await sync.upload(_config, '{"new":true}')).outcome, WebDavOutcome.conflict);
      expect(store.values.values, contains('"old"'));
      expect(calls.where((r) => r.method == 'PUT'), hasLength(2));
    });
    test('successful replacement preserves a version that can be restored', () async {
      final store = MemoryJsonStore();
      final objects = <String, String>{'/dav/xta/backup.json': '{"old":true}'};
      var etag = '"old"';
      final sync = WebDavSync(
        storage: store,
        client: MockClient((request) async {
          if (request.method == 'GET') return http.Response(objects[request.url.path]!, 200, headers: {'etag': etag});
          objects[request.url.path] = request.body;
          if (!request.url.path.contains('.versions/')) etag = '"new"';
          return http.Response('', 201, headers: {'etag': etag});
        }),
      );
      await sync.acknowledge(_config, await sync.download(_config));
      expect((await sync.upload(_config, '{"new":true}')).isSuccess, isTrue);
      final current = await sync.download(_config);
      final versions = webDavVersions(current.body);
      expect(versions, hasLength(1));
      expect((await sync.downloadVersion(_config, versions.single)).body, '{"old":true}');
      expect(store.values.values, contains('"new"'));
    });
    test('weak or missing validators refuse to overwrite remote backups', () async {
      for (final headers in [
        <String, String>{},
        {'etag': 'W/"weak"'},
      ]) {
        final sync = WebDavSync(
          storage: MemoryJsonStore(),
          client: MockClient((_) async => http.Response('{}', 200, headers: headers)),
        );
        expect((await sync.upload(_config, '{}')).outcome, WebDavOutcome.unsafeServer);
      }
    });
    test('archive failure prevents replacing the current backup', () async {
      final sync = WebDavSync(
        storage: MemoryJsonStore(),
        client: MockClient((request) async {
          if (request.method == 'GET') return http.Response('{}', 200, headers: {'etag': '"old"'});
          expect(request.url.path, contains('.versions/'));
          return http.Response('', 507);
        }),
      );
      await sync.acknowledge(_config, await sync.download(_config));
      expect((await sync.upload(_config, '{}')).outcome, WebDavOutcome.serverError);
    });
    test('bad credentials and network failures remain distinct', () async {
      expect(
        (await WebDavSync(client: MockClient((_) async => http.Response('', 401))).upload(_config, '{}')).outcome,
        WebDavOutcome.unauthorized,
      );
      expect(
        (await WebDavSync(
          client: MockClient((_) async => throw const SocketExceptionStub()),
        ).upload(_config, '{}')).outcome,
        WebDavOutcome.networkError,
      );
    });
    test('no request is made for missing settings or insecure URLs', () async {
      final sync = WebDavSync(client: MockClient((_) async => throw StateError('must not contact')));
      expect(
        (await sync.upload(const WebDavConfig(url: '', username: '', password: ''), '{}')).outcome,
        WebDavOutcome.notConfigured,
      );
      expect(
        (await sync.upload(const WebDavConfig(url: 'http://cloud/x', username: 'a', password: 'b'), '{}')).outcome,
        WebDavOutcome.insecureUrl,
      );
    });
    test('history cannot redirect authenticated reads to another server or path', () {
      expect(
        webDavVersions(
          jsonEncode({
            '_xtaWebDavHistory': [
              {'file': '../other.json', 'at': '2026-01-01'},
              {'file': 'https://evil/x', 'at': '2026-01-01'},
            ],
          }),
        ),
        isEmpty,
      );
    });
  });

  group('download', () {
    test('returns the stored document', () async {
      final sync = WebDavSync(client: MockClient((_) async => http.Response('{"subscriptions":[]}', 200)));

      final result = await sync.download(_config);

      expect(result.isSuccess, isTrue);
      expect(result.body, '{"subscriptions":[]}');
    });

    test('a first sync with nothing uploaded yet reads as notFound', () async {
      final sync = WebDavSync(client: MockClient((_) async => http.Response('', 404)));

      final result = await sync.download(_config);

      expect(result.outcome, WebDavOutcome.notFound);
      expect(result.body, isNull);
    });

    test('a server failure carries the status for the error message', () async {
      final sync = WebDavSync(client: MockClient((_) async => http.Response('boom', 500)));

      final result = await sync.download(_config);

      expect(result.outcome, WebDavOutcome.serverError);
      expect(result.detail, contains('500'));
    });
  });

  // The synced document is written to the very server these credentials open,
  // so leaving the password in would store the server's own password on it.
  test('the exported settings never carry the sync password', () {
    final stripped = prefsMapWithoutSecrets({
      optionWebDavUrl: 'https://cloud.example.org/dav/xta/backup.json',
      optionWebDavUsername: 'reader',
      optionWebDavPassword: 'hunter2',
      optionCrashGithubToken: 'ghp_secret',
      optionThemeMode: 'dark',
    });

    expect(stripped.containsKey(optionWebDavPassword), isFalse);
    expect(stripped.containsKey(optionCrashGithubToken), isFalse);
    expect(stripped[optionWebDavUrl], isNotNull);
    expect(stripped[optionWebDavUsername], 'reader');
    expect(stripped[optionThemeMode], 'dark');
  });

  test('status mapping keeps 2xx apart from the failure classes', () {
    expect(outcomeForStatus(200), WebDavOutcome.success);
    expect(outcomeForStatus(204), WebDavOutcome.success);
    expect(outcomeForStatus(403), WebDavOutcome.unauthorized);
    expect(outcomeForStatus(404), WebDavOutcome.notFound);
    expect(outcomeForStatus(507), WebDavOutcome.serverError);
  });
}

class SocketExceptionStub implements Exception {
  const SocketExceptionStub();

  @override
  String toString() => 'Connection refused';
}
