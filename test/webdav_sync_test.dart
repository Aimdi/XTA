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

  group('upload', () {
    test('PUTs the body to the configured document', () async {
      late http.Request seen;
      final sync = WebDavSync(
        client: MockClient((request) async {
          seen = request;
          return http.Response('', 201);
        }),
      );

      final result = await sync.upload(_config, '{"hello":"world"}');

      expect(result.isSuccess, isTrue);
      expect(seen.method, 'PUT');
      expect(seen.url.toString(), _config.url);
      expect(seen.body, '{"hello":"world"}');
      expect(seen.headers['authorization'], isNotNull);
    });

    // The normal state of a first sync, not a failure worth showing.
    test('creates the parent collection when it is missing, then retries', () async {
      final calls = <String>[];
      final sync = WebDavSync(
        client: MockClient((request) async {
          calls.add('${request.method} ${request.url.path}');
          if (request.method == 'MKCOL') {
            return http.Response('', 201);
          }
          return http.Response('', calls.where((c) => c.startsWith('PUT')).length == 1 ? 409 : 201);
        }),
      );

      final result = await sync.upload(_config, '{}');

      expect(result.isSuccess, isTrue);
      expect(calls, ['PUT /dav/xta/backup.json', 'MKCOL /dav/xta', 'PUT /dav/xta/backup.json']);
    });

    test('a collection that already exists is not an error', () async {
      var puts = 0;
      final sync = WebDavSync(
        client: MockClient((request) async {
          if (request.method == 'MKCOL') {
            return http.Response('', 405);
          }
          return http.Response('', ++puts == 1 ? 404 : 201);
        }),
      );

      expect((await sync.upload(_config, '{}')).isSuccess, isTrue);
    });

    test('bad credentials are reported as such, not as a generic failure', () async {
      final sync = WebDavSync(client: MockClient((_) async => http.Response('', 401)));

      expect((await sync.upload(_config, '{}')).outcome, WebDavOutcome.unauthorized);
    });

    test('an unreachable server is a network error, not a server error', () async {
      final sync = WebDavSync(client: MockClient((_) async => throw const SocketExceptionStub()));

      final result = await sync.upload(_config, '{}');

      expect(result.outcome, WebDavOutcome.networkError);
      expect(result.detail, isNotNull);
    });

    test('nothing is sent when the target is not configured or not https', () async {
      var called = false;
      final sync = WebDavSync(
        client: MockClient((_) async {
          called = true;
          return http.Response('', 200);
        }),
      );

      expect(
        (await sync.upload(const WebDavConfig(url: '', username: '', password: ''), '{}')).outcome,
        WebDavOutcome.notConfigured,
      );
      expect(
        (await sync.upload(const WebDavConfig(url: 'http://cloud/x.json', username: 'a', password: 'b'), '{}')).outcome,
        WebDavOutcome.insecureUrl,
      );
      expect(called, isFalse);
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
