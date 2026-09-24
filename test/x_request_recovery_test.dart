import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/headers.dart';
import 'package:xta/client/http_client.dart';
import 'package:xta/client/rate_limit_tracker.dart';
import 'package:xta/client/transport.dart';
import 'package:xta/client/x_client_transaction_id/client_transaction.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/utils/request_budget.dart';

class _StallingClient extends http.BaseClient {
  final requests = <http.BaseRequest>[];
  final stalled = Completer<http.StreamedResponse>();

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    requests.add(request);
    return stalled.future;
  }
}

void main() {
  late Directory databaseDir;
  final uri = Uri.https('x.com', '/i/api/graphql/test/HomeTimeline');

  Future<void> seedAccounts(int count) async {
    final db = await Repository.writable();
    await db.delete(tableAccounts);
    for (var i = 0; i < count; i++) {
      final account = Account(
        id: 'account-$i',
        authHeader: '{"x-test-account":"account-$i"}',
        screenName: 'account$i',
      );
      await db.insert(tableAccounts, account.toMap());
    }
  }

  final account = Account(id: 'test', authHeader: '{}', screenName: 'test');
  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
    databaseDir = await Directory.systemTemp.createTemp(
      'xta_transport_recovery_test',
    );
    await databaseFactory.setDatabasesPath(databaseDir.path);
    await Repository().migrate();
  });

  tearDownAll(() async {
    if (databaseDir.existsSync()) {
      await databaseDir.delete(recursive: true);
    }
  });

  setUp(() async {
    final db = await Repository.writable();
    await db.delete(tableAccounts);
    TwitterHeaders.resetForTesting();
    TwitterHeaders.initializer = () async =>
        ClientTransaction.forTesting(keyBytes: [1, 2, 3, 4], animationKey: 'test-animation-key');
  });
  tearDown(() {
    xHttpClient = null;
    TwitterHeaders.resetForTesting();
    RateLimitTracker.clear(account.id, uri.path);
    for (var i = 0; i < 4; i++) {
      RateLimitTracker.clear('account-$i', uri.path);
    }
  });

  for (final status in [502, 503, 504]) {
    test('recovers from $status with one GET retry', () async {
      var calls = 0;
      xHttpClient = MockClient((request) async {
        expect(request.headers['x-test'], 'kept');
        return http.Response('body', ++calls == 1 ? status : 200);
      });
      expect((await getXResponse(uri, headers: {'x-test': 'kept'})).statusCode, 200);
      expect(calls, 2);
    });
  }
  for (final status in [401, 403, 404, 429, 500]) {
    test('does not retry HTTP $status', () async {
      var calls = 0;
      xHttpClient = MockClient((_) async {
        calls++;
        return http.Response('', status);
      });
      expect((await getXResponse(uri)).statusCode, status);
      expect(calls, 1);
    });
  }
  test('a dropped connection recovers and persistent failures retry only once', () async {
    var calls = 0;
    xHttpClient = MockClient((_) async {
      if (++calls == 1) throw http.ClientException('connection reset');
      return http.Response('ok', 200);
    });
    expect((await getXResponse(uri)).statusCode, 200);
    expect(calls, 2);
    calls = 0;
    xHttpClient = MockClient((_) async {
      calls++;
      throw http.ClientException('offline');
    });
    await expectLater(getXResponse(uri), throwsA(isA<http.ClientException>()));
    expect(calls, 2);
  });
  test('an exhausted budget never starts another request', () async {
    var calls = 0;
    await expectLater(RequestBudget(Duration.zero).run(() async => ++calls), throwsA(isA<TimeoutException>()));
    expect(calls, 0);
  });
  test('pinned HomeTimeline times out and aborts its own request', () async {
    final client = _StallingClient();
    xHttpClient = client;
    await expectLater(
      QuackerTwitterClient.fetchAs(account, uri, timeout: const Duration(milliseconds: 30)),
      throwsA(isA<TimeoutException>()),
    );
    expect(client.requests, hasLength(1));
    await (client.requests.single as http.Abortable).abortTrigger!.timeout(const Duration(seconds: 1));
    // A late network failure must not poison the next request or become unhandled.
    client.stalled.completeError(http.ClientException('late failure'));
    xHttpClient = MockClient((_) async => http.Response('recovered', 200));
    expect((await QuackerTwitterClient.fetchAs(account, uri)).body, 'recovered');
  });
  test('a stalled response body is bounded too', () async {
    final body = StreamController<List<int>>();
    xHttpClient = MockClient.streaming((request, _) async => http.StreamedResponse(body.stream, 200));
    await expectLater(getXResponse(uri, timeout: const Duration(milliseconds: 30)), throwsA(isA<TimeoutException>()));
    await body.close();
  });
  group('account-rotating fetch characterization', () {
    test('429 rotates to another account and remembers the limited one', () async {
      await seedAccounts(2);
      final attempted = <String>[];
      var calls = 0;
      xHttpClient = MockClient((request) async {
        final id = request.headers['x-test-account']!;
        attempted.add(id);
        calls++;
        return http.Response(
          calls == 1 ? '' : 'ok',
          calls == 1 ? 429 : 200,
          headers: calls == 1
              ? {
                  'x-rate-limit-reset':
                      '${DateTime.now().add(const Duration(minutes: 5)).millisecondsSinceEpoch ~/ 1000}',
                }
              : const {},
        );
      });

      final response = await QuackerTwitterClient.fetch(uri);

      expect(response.statusCode, 200);
      expect(attempted, hasLength(2));
      expect(attempted.toSet(), hasLength(2));
      expect(
        RateLimitTracker.isLimited(
          attempted.first,
          uri.path,
          DateTime.now(),
        ),
        isTrue,
      );
    });

    test('a 404 is blamed on an account only after another account succeeds', () async {
      await seedAccounts(2);
      final attempted = <String>[];
      var calls = 0;
      xHttpClient = MockClient((request) async {
        attempted.add(request.headers['x-test-account']!);
        calls++;
        return http.Response(calls == 1 ? '' : 'ok', calls == 1 ? 404 : 200);
      });

      expect((await QuackerTwitterClient.fetch(uri)).statusCode, 200);
      final db = await Repository.readOnly();
      final rows = await db.query(tableAccounts);
      final byId = {
        for (final row in rows) row['id'] as String: row,
      };

      expect(
        byId[attempted.first]!['consecutive_not_found'],
        1,
        reason:
            'the successful second account proves the first 404 was account-specific',
      );
      expect(
        byId[attempted.last]!['consecutive_not_found'],
        0,
      );
    });

    test('all-account 404s surface endpoint refusal without poisoning accounts', () async {
      await seedAccounts(2);
      xHttpClient = MockClient((_) async => http.Response('', 404));

      await expectLater(
        QuackerTwitterClient.fetch(uri),
        throwsA(isA<EndpointRefusedException>()),
      );

      final db = await Repository.readOnly();
      final rows = await db.query(tableAccounts);
      expect(
        rows.map((row) => row['consecutive_not_found']),
        everyElement(0),
      );
      expect(
        rows.map((row) => row['last_not_found_at']),
        everyElement(isNull),
      );
    });

    test('all-account 429s surface a rate limit instead of an empty feed', () async {
      await seedAccounts(2);
      final reset =
          DateTime.now().add(const Duration(minutes: 5)).millisecondsSinceEpoch ~/
          1000;
      xHttpClient = MockClient(
        (_) async => http.Response(
          '',
          429,
          headers: {'x-rate-limit-reset': '$reset'},
        ),
      );

      await expectLater(
        QuackerTwitterClient.fetch(uri),
        throwsA(isA<RateLimitedException>()),
      );
    });

    test('a successful fetch clears an old endpoint rate-limit flag', () async {
      await seedAccounts(1);
      RateLimitTracker.flag(
        'account-0',
        uri.path,
        DateTime.now().add(const Duration(minutes: 5)),
      );
      xHttpClient = MockClient((_) async => http.Response('ok', 200));

      expect((await QuackerTwitterClient.fetch(uri)).statusCode, 200);
      expect(
        RateLimitTracker.isLimited('account-0', uri.path, DateTime.now()),
        isFalse,
      );
    });
  });

  for (final reset in ['invalid', '9999999999999999999']) {
    test('malformed rate limit reset $reset preserves the rate limit error', () async {
      xHttpClient = MockClient((_) async => http.Response('', 429, headers: {'x-rate-limit-reset': reset}));
      await expectLater(QuackerTwitterClient.fetchAs(account, uri), throwsA(isA<RateLimitedException>()));
      expect(RateLimitTracker.isLimited(account.id, uri.path, DateTime.now()), isTrue);
    });
  }
}
