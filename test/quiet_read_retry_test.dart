import 'dart:async';
import 'dart:io' show SocketException;

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/utils/read_activity.dart';
import 'package:xta/utils/read_request_scope.dart';

void main() {
  testWidgets('temporary read failures recover before publishing an error', (tester) async {
    final scope = ReadRequestScope();
    var calls = 0;
    final result = scope.start(() async {
      if (++calls < 3) throw const SocketException('temporary failure');
      return ['post'];
    }, timeout: const Duration(seconds: 10));
    final check = expectLater(result, completion(['post']));
    await tester.pump();
    expect(calls, 1);
    await tester.pump(const Duration(milliseconds: 400));
    expect(calls, 2);
    await tester.pump(const Duration(milliseconds: 1200));
    await check;
    expect(calls, 3);
  });

  testWidgets('persistent failures stop after two quiet retries and preserve the cause', (tester) async {
    final scope = ReadRequestScope();
    final error = TimeoutException('temporary failure');
    var calls = 0;
    final check = expectLater(
      scope.start(() async {
        calls++;
        throw error;
      }, timeout: const Duration(seconds: 10)),
      throwsA(same(error)),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump(const Duration(milliseconds: 1200));
    await check;
    await tester.pump(const Duration(minutes: 1));
    expect(calls, 3);
  });

  testWidgets('cancelling during backoff prevents another request', (tester) async {
    final scope = ReadRequestScope();
    var calls = 0;
    final check = expectLater(
      scope.start(() async {
        calls++;
        throw const SocketException('temporary');
      }, timeout: const Duration(seconds: 10)),
      throwsA(isA<ReadCancelled>()),
    );
    await tester.pump();
    scope.cancel();
    await check;
    await tester.pump();
    expect(calls, 1);
  });

  testWidgets('nested read scopes share their quiet retry budget', (tester) async {
    final outer = ReadRequestScope();
    final inner = ReadRequestScope();
    var calls = 0;
    final error = const SocketException('temporary');
    final check = expectLater(
      outer.start(
        () => inner.start(() async {
          calls++;
          throw error;
        }, timeout: const Duration(seconds: 10)),
        timeout: const Duration(seconds: 10),
      ),
      throwsA(same(error)),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump(const Duration(milliseconds: 1200));
    await check;
    expect(calls, 3);
  });

  testWidgets('deadline still stops a slow read without starting a parallel retry', (tester) async {
    final pending = Completer<String>();
    var calls = 0;
    final check = expectLater(
      ReadRequestScope().start(() {
        calls++;
        return pending.future;
      }, timeout: const Duration(seconds: 1)),
      throwsA(isA<TimeoutException>()),
    );
    await tester.pump(const Duration(seconds: 1));
    await check;
    pending.complete('late');
    await tester.pump(const Duration(seconds: 5));
    expect(calls, 1);
  });

  test('account, rate-limit, unavailable, parser and cancellation failures are not retried', () async {
    for (final error in <Object>[
      for (final code in [400, 401, 403, 404, 429, 501]) HttpException(http.Response('', code)),
      RateLimitedException(),
      NoWorkingAccountException(),
      EndpointRefusedException('UserTweets'),
      const FormatException('shape'),
      StateError('programming failure'),
      const ReadCancelled(),
      http.RequestAbortedException(),
      TransactionIdUnavailableException(const FormatException('bootstrap changed')),
      TransactionIdUnavailableException(HttpException(http.Response('', 429))),
    ]) {
      var calls = 0;
      await expectLater(
        ReadRequestScope().start(() async {
          calls++;
          throw error;
        }, timeout: const Duration(seconds: 10)),
        throwsA(same(error)),
      );
      expect(calls, 1, reason: '$error');
    }
  });

  testWidgets('a temporary X bootstrap failure can recover without an account error', (tester) async {
    var calls = 0;
    final result = ReadRequestScope().start(() async {
      if (++calls == 1) throw TransactionIdUnavailableException(const SocketException('temporary'));
      return 'profile';
    }, timeout: const Duration(seconds: 10));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(await result, 'profile');
    expect(calls, 2);
  });

  test('cache and post-write snapshot failures remain single-attempt', () async {
    for (final operation in [ReadOperation.cache, ReadOperation.snapshot]) {
      var calls = 0;
      await expectLater(
        ReadRequestScope().start(
          () async {
            calls++;
            throw TimeoutException('local read');
          },
          timeout: const Duration(seconds: 10),
          operation: operation,
        ),
        throwsA(isA<TimeoutException>()),
      );
      expect(calls, 1);
    }
  });
}
