import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/errors.dart';
import 'package:xta/ui/read_failure_kind.dart';

HttpException _http(int status) => HttpException(http.Response('', status));

void main() {
  group('readFailureKind', () {
    test('separates connectivity and timeout failures', () {
      expect(readFailureKind(const SocketException('offline')), ReadFailureKind.connection);
      expect(readFailureKind(http.ClientException('closed')), ReadFailureKind.connection);
      expect(readFailureKind(TimeoutException('slow')), ReadFailureKind.timedOut);
    });

    test('separates account, rate limit and endpoint failures', () {
      expect(readFailureKind(RateLimitedException()), ReadFailureKind.rateLimited);
      expect(readFailureKind(_http(429)), ReadFailureKind.rateLimited);
      expect(readFailureKind(NoWorkingAccountException()), ReadFailureKind.session);
      expect(readFailureKind(_http(401)), ReadFailureKind.session);
      expect(readFailureKind(EndpointRefusedException('HomeTimeline')), ReadFailureKind.endpointRefused);
      expect(
        readFailureKind(TransactionIdUnavailableException(Exception('shape'))),
        ReadFailureKind.transactionUnavailable,
      );
    });

    test('does not confuse raw 404 with a proven endpoint rotation', () {
      expect(readFailureKind(_http(404)), ReadFailureKind.unavailable);
      expect(readFailureKind(_http(403)), ReadFailureKind.unavailable);
    });

    test('server failures are distinct and automatically recoverable', () {
      for (final status in [500, 502, 503, 504]) {
        final error = _http(status);
        expect(readFailureKind(error), ReadFailureKind.serviceUnavailable, reason: 'HTTP $status');
        expect(recoverableReadFailure(error), same(error));
      }
    });

    test('session and endpoint failures never auto-retry; rate limits wait for their reset', () {
      expect(recoverableReadFailure(_http(401)), isNull);
      expect(recoverableReadFailure(NoAccountAvailableException()), isNull);
      expect(recoverableReadFailure(EndpointRefusedException('SearchTimeline')), isNull);
      expect(readRetryOf(_http(429)), ReadRetry.atReset);
      expect(readRetryOf(RateLimitedException()), ReadRetry.atReset);
      expect(recoverableReadFailure(_http(429)), isNotNull);
    });

    test('every other failure is transient and retried on a backoff', () {
      for (final error in <Object>[
        const SocketException('offline'),
        TimeoutException('slow'),
        _http(503),
        _http(403),
        _http(400),
        TransactionIdUnavailableException(Exception('shape')),
        const FormatException('html instead of json'),
        TwitterError(uri: 'x', code: 131, message: 'Internal error'),
      ]) {
        expect(readRetryOf(error), ReadRetry.backoff, reason: '$error');
        expect(recoverableReadFailure(error), same(error));
      }
      expect(recoverableReadFailure(null), isNull);
    });

    test("X's definitive answers about an account are not retried", () {
      for (final code in [-1, 22, 34, 50, 63, 200]) {
        expect(
          readRetryOf(TwitterError(uri: 'x', code: code, message: '')),
          ReadRetry.manual,
          reason: '$code',
        );
      }
    });
  });
}
