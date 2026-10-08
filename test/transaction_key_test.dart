import 'dart:async';
import 'dart:io' show SocketException;

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/headers.dart';
import 'package:xta/client/x_client_transaction_id/client_transaction.dart';
import 'package:xta/constants.dart';

/// `_initFuture ??= ClientTransaction.initialize()` cached a *rejected* future
/// as happily as a successful one. Deriving the key does two network requests
/// and parses X's HTML, so a single blip left that failure in the static for
/// the life of the process: every later request threw the same error and only a
/// force-stop cleared it. Since X answers an unsigned request with 404, the app
/// looked completely broken.
void main() {
  setUp(TwitterHeaders.resetForTesting);
  tearDown(TwitterHeaders.resetForTesting);

  final uri = Uri.https('x.com', '/i/api/graphql/abc/UserTweets');

  ClientTransaction fakeTransaction() =>
      ClientTransaction.forTesting(keyBytes: const [1, 2, 3, 4], animationKey: 'test-animation-key');

  group('transactionKeyUsable', () {
    final now = DateTime.utc(2026, 7, 25, 12);

    test('a key that has never been derived is not usable', () {
      expect(transactionKeyUsable(derivedAt: null, now: now, lifetime: const Duration(hours: 6)), isFalse);
    });

    test('a fresh key is usable', () {
      expect(
        transactionKeyUsable(
          derivedAt: now.subtract(const Duration(minutes: 5)),
          now: now,
          lifetime: const Duration(hours: 6),
        ),
        isTrue,
      );
    });

    // X rotates the home page and on-demand bundle the key is built from.
    test('a key older than its lifetime is re-derived', () {
      expect(
        transactionKeyUsable(
          derivedAt: now.subtract(const Duration(hours: 7)),
          now: now,
          lifetime: const Duration(hours: 6),
        ),
        isFalse,
      );
    });

    test('the shipped lifetime is well short of a day', () {
      expect(transactionKeyLifetime, lessThan(const Duration(days: 1)));
    });
  });

  group('key caching', () {
    testWidgets('a stalled setup expires and retry recovers without restarting', (tester) async {
      final stalled = Completer<ClientTransaction>();
      var attempts = 0;
      var now = DateTime.utc(2026, 9, 15);
      TwitterHeaders.clock = () => now;
      TwitterHeaders.initializer = () {
        attempts++;
        return attempts == 1 ? stalled.future : Future.value(fakeTransaction());
      };
      final first = expectLater(TwitterHeaders.getXClientTransactionIdHeader(uri), throwsA(isA<TimeoutException>()));
      final second = expectLater(TwitterHeaders.getXClientTransactionIdHeader(uri), throwsA(isA<TimeoutException>()));
      await tester.pump(TwitterHeaders.initializationTimeout + const Duration(seconds: 1));
      await Future.wait([first, second]);
      expect(attempts, 1);
      expect((await TwitterHeaders.getXClientTransactionIdHeader(uri))?['x-client-transaction-id'], isNotEmpty);
      expect(attempts, 2);
      stalled.completeError(Exception('obsolete setup finally failed'));
      await tester.pump();
      await TwitterHeaders.getXClientTransactionIdHeader(uri);
      expect(attempts, 2);
    });

    for (final failure in <Object>[
      const SocketException('offline'),
      http.ClientException('connection closed'),
      TimeoutException('network stalled'),
    ]) {
      test('${failure.runtimeType} keeps its type and the next concurrent requests recover immediately', () async {
        final pending = Completer<ClientTransaction>();
        var attempts = 0;
        TwitterHeaders.initializer = () {
          attempts++;
          return attempts == 1 ? pending.future : Future.value(fakeTransaction());
        };
        final first = expectLater(TwitterHeaders.getHeaders(uri, null), throwsA(same(failure)));
        final second = expectLater(TwitterHeaders.getHeaders(uri, null), throwsA(same(failure)));
        pending.completeError(failure);
        await Future.wait([first, second]);
        expect(attempts, 1);
        expect(TwitterHeaders.lastInitializationFailure, same(failure));

        final recovered = await Future.wait([
          TwitterHeaders.getHeaders(uri, null),
          TwitterHeaders.getHeaders(uri, null),
        ]);
        expect(recovered.map((headers) => headers['x-client-transaction-id']), everyElement(isNotEmpty));
        expect(attempts, 2);
        expect(TwitterHeaders.lastInitializationFailure, isNull);
      });
    }

    test('synchronous setup failures also respect the retry cooldown', () async {
      var attempts = 0;
      TwitterHeaders.initializer = () {
        attempts++;
        throw StateError('bad initialization');
      };
      for (var i = 0; i < 2; i++) {
        await expectLater(
          TwitterHeaders.getXClientTransactionIdHeader(uri),
          throwsA(isA<TransactionIdUnavailableException>()),
        );
      }
      expect(attempts, 1);
    });

    test('a successful derivation is reused rather than repeated per request', () async {
      var derivations = 0;
      TwitterHeaders.initializer = () async {
        derivations++;
        return fakeTransaction();
      };

      await TwitterHeaders.getXClientTransactionIdHeader(uri);
      await TwitterHeaders.getXClientTransactionIdHeader(uri);
      await TwitterHeaders.getXClientTransactionIdHeader(uri);

      expect(derivations, 1);
    });

    // The regression this file exists for: a failure must not be latched for
    // the life of the process. It is rate-limited (see the cooldown tests
    // below), but it always recovers without a force-stop.
    test('a failed derivation is not cached, so a later request retries', () async {
      var attempts = 0;
      var now = DateTime.utc(2026, 7, 25, 12);
      TwitterHeaders.clock = () => now;
      TwitterHeaders.initializer = () async {
        attempts++;
        if (attempts == 1) {
          throw Exception('transient network blip');
        }
        return fakeTransaction();
      };

      await expectLater(
        TwitterHeaders.getXClientTransactionIdHeader(uri),
        throwsA(isA<TransactionIdUnavailableException>()),
      );
      now = now.add(transactionKeyRetryCooldown + const Duration(seconds: 1));

      final header = await TwitterHeaders.getXClientTransactionIdHeader(uri);

      expect(attempts, 2);
      expect(header?['x-client-transaction-id'], isNotNull);
    });

    // Forgetting the failure must not mean re-deriving on every request:
    // deriving costs two requests to x.com, so a derivation that is outright
    // broken (X reshaped its HTML) would turn one feed load into twenty extra
    // hits on X.
    test('a persistent failure is retried on a cooldown, not on every request', () async {
      var attempts = 0;
      var now = DateTime.utc(2026, 7, 25, 12);
      TwitterHeaders.clock = () => now;
      TwitterHeaders.initializer = () async {
        attempts++;
        throw Exception('X reshaped its HTML');
      };

      for (var i = 0; i < 5; i++) {
        await expectLater(
          TwitterHeaders.getXClientTransactionIdHeader(uri),
          throwsA(isA<TransactionIdUnavailableException>()),
        );
      }

      expect(attempts, 1, reason: 'the cooldown should have suppressed the retries');
    });

    test('the caller still sees the failure while the cooldown suppresses retries', () async {
      var now = DateTime.utc(2026, 7, 25, 12);
      TwitterHeaders.clock = () => now;
      TwitterHeaders.initializer = () async => throw Exception('X reshaped its HTML');

      await expectLater(
        TwitterHeaders.getXClientTransactionIdHeader(uri),
        throwsA(isA<TransactionIdUnavailableException>()),
      );

      // Suppressed, but still an error rather than a silently missing header.
      await expectLater(
        TwitterHeaders.getXClientTransactionIdHeader(uri),
        throwsA(isA<TransactionIdUnavailableException>()),
      );
    });

    test('a retry happens once the cooldown has elapsed', () async {
      var attempts = 0;
      var now = DateTime.utc(2026, 7, 25, 12);
      TwitterHeaders.clock = () => now;
      TwitterHeaders.initializer = () async {
        attempts++;
        if (attempts == 1) {
          throw Exception('transient');
        }
        return fakeTransaction();
      };

      await expectLater(
        TwitterHeaders.getXClientTransactionIdHeader(uri),
        throwsA(isA<TransactionIdUnavailableException>()),
      );
      now = now.add(transactionKeyRetryCooldown + const Duration(seconds: 1));

      expect((await TwitterHeaders.getXClientTransactionIdHeader(uri))?['x-client-transaction-id'], isNotNull);
      expect(attempts, 2);
    });

    test('a success clears the cooldown, so a later failure retries promptly', () async {
      var attempts = 0;
      var now = DateTime.utc(2026, 7, 25, 12);
      TwitterHeaders.clock = () => now;
      TwitterHeaders.initializer = () async {
        attempts++;
        if (attempts == 1) {
          throw Exception('transient');
        }
        return fakeTransaction();
      };

      await expectLater(
        TwitterHeaders.getXClientTransactionIdHeader(uri),
        throwsA(isA<TransactionIdUnavailableException>()),
      );
      now = now.add(transactionKeyRetryCooldown + const Duration(seconds: 1));
      await TwitterHeaders.getXClientTransactionIdHeader(uri);

      // The key has expired and derivation fails again; because the last
      // outcome was a success, this is a fresh attempt rather than a suppressed
      // one.
      now = now.add(transactionKeyLifetime + const Duration(minutes: 1));
      TwitterHeaders.initializer = () async {
        attempts++;
        throw Exception('down again');
      };

      await expectLater(
        TwitterHeaders.getXClientTransactionIdHeader(uri),
        throwsA(isA<TransactionIdUnavailableException>()),
      );
      expect(attempts, 3);
    });

    test('the key is re-derived once its lifetime has passed', () async {
      var derivations = 0;
      var now = DateTime.utc(2026, 7, 25, 12);
      TwitterHeaders.clock = () => now;
      TwitterHeaders.initializer = () async {
        derivations++;
        return fakeTransaction();
      };

      await TwitterHeaders.getXClientTransactionIdHeader(uri);
      now = now.add(transactionKeyLifetime + const Duration(minutes: 1));
      await TwitterHeaders.getXClientTransactionIdHeader(uri);

      expect(derivations, 2);
    });

    test('concurrent first requests share one derivation', () async {
      var derivations = 0;
      TwitterHeaders.initializer = () async {
        derivations++;
        await Future<void>.delayed(const Duration(milliseconds: 10));
        return fakeTransaction();
      };

      await Future.wait([
        TwitterHeaders.getXClientTransactionIdHeader(uri),
        TwitterHeaders.getXClientTransactionIdHeader(uri),
        TwitterHeaders.getXClientTransactionIdHeader(uri),
      ]);

      expect(derivations, 1);
    });

    test('anonymous failure cooldown does not block an authenticated context', () async {
      var attempts = 0;
      TwitterHeaders.initializer = () async {
        attempts++;
        if (attempts == 1) throw const FormatException('anonymous page has no signer');
        return fakeTransaction();
      };

      await expectLater(TwitterHeaders.getHeaders(uri, null), throwsA(isA<TransactionIdUnavailableException>()));
      final headers = await TwitterHeaders.getHeaders(uri, {'Cookie': 'auth_token=account-a'});
      expect(headers['x-client-transaction-id'], isNotEmpty);
      await expectLater(TwitterHeaders.getHeaders(uri, null), throwsA(isA<TransactionIdUnavailableException>()));
      expect(attempts, 2);
    });

    test('cookie contexts isolate failures and reuse keys with any Cookie header casing', () async {
      var attempts = 0;
      TwitterHeaders.initializer = () async {
        attempts++;
        if (attempts == 1) throw const FormatException('account A page has no signer');
        return fakeTransaction();
      };

      await expectLater(
        TwitterHeaders.getHeaders(uri, {'Cookie': 'auth_token=account-a'}),
        throwsA(isA<TransactionIdUnavailableException>()),
      );
      await Future.wait([
        TwitterHeaders.getHeaders(uri, {'cookie': 'auth_token=account-b'}),
        TwitterHeaders.getHeaders(uri, {'COOKIE': 'auth_token=account-b'}),
      ]);
      await expectLater(
        TwitterHeaders.getHeaders(uri, {'cookie': 'auth_token=account-a'}),
        throwsA(isA<TransactionIdUnavailableException>()),
      );
      await TwitterHeaders.getHeaders(uri, {'Cookie': 'auth_token=account-b'});
      expect(attempts, 2);
    });

    test('changing the session cookie derives a fresh key', () async {
      var attempts = 0;
      TwitterHeaders.initializer = () async {
        attempts++;
        return fakeTransaction();
      };
      await TwitterHeaders.getHeaders(uri, {'Cookie': 'auth_token=old'});
      await TwitterHeaders.getHeaders(uri, {'Cookie': 'auth_token=new'});
      await TwitterHeaders.getHeaders(uri, {'Cookie': 'auth_token=new'});
      expect(attempts, 2);
    });

    test('many session contexts evict old keys without evicting the most recently used one', () async {
      var attempts = 0;
      TwitterHeaders.initializer = () async {
        attempts++;
        return fakeTransaction();
      };
      for (var index = 0; index < 17; index++) {
        await TwitterHeaders.getHeaders(uri, {'Cookie': 'auth_token=session-$index'});
      }
      expect(attempts, 17);
      await TwitterHeaders.getHeaders(uri, {'Cookie': 'auth_token=session-16'});
      expect(attempts, 17);
      await TwitterHeaders.getHeaders(uri, {'Cookie': 'auth_token=session-0'});
      expect(attempts, 18);
    });

    test('no header is asked for when there is no uri', () async {
      TwitterHeaders.initializer = () async => fail('should not derive a key');

      expect(await TwitterHeaders.getXClientTransactionIdHeader(null), isNull);
    });

    test('reset clears diagnostics and a late previous initialization cannot restore its error', () async {
      final pending = Completer<ClientTransaction>();
      TwitterHeaders.initializer = () => pending.future;
      final first = expectLater(TwitterHeaders.getHeaders(uri, null), throwsA(isA<TransactionIdUnavailableException>()));
      TwitterHeaders.resetForTesting();
      pending.completeError(const FormatException('old page'));
      await first;
      expect(TwitterHeaders.lastInitializationFailure, isNull);
    });
  });
}
