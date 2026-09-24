import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/ui/read_failure_kind.dart';

HttpException _http(int status) => HttpException(http.Response('', status));

void main() {
  group('readFailureKind', () {
    test('separates connectivity and timeout failures', () {
      expect(
        readFailureKind(const SocketException('offline')),
        ReadFailureKind.connection,
      );
      expect(
        readFailureKind(http.ClientException('closed')),
        ReadFailureKind.connection,
      );
      expect(
        readFailureKind(TimeoutException('slow')),
        ReadFailureKind.timedOut,
      );
    });

    test('separates account, rate limit and endpoint failures', () {
      expect(
        readFailureKind(RateLimitedException()),
        ReadFailureKind.rateLimited,
      );
      expect(
        readFailureKind(_http(429)),
        ReadFailureKind.rateLimited,
      );
      expect(
        readFailureKind(NoWorkingAccountException()),
        ReadFailureKind.session,
      );
      expect(
        readFailureKind(_http(401)),
        ReadFailureKind.session,
      );
      expect(
        readFailureKind(EndpointRefusedException('HomeTimeline')),
        ReadFailureKind.endpointRefused,
      );
    });

    test('does not confuse raw 404 with a proven endpoint rotation', () {
      expect(readFailureKind(_http(404)), ReadFailureKind.unavailable);
      expect(readFailureKind(_http(403)), ReadFailureKind.unavailable);
    });

    test('server failures are distinct and automatically recoverable', () {
      for (final status in [500, 502, 503, 504]) {
        final error = _http(status);
        expect(
          readFailureKind(error),
          ReadFailureKind.serviceUnavailable,
          reason: 'HTTP $status',
        );
        expect(recoverableReadFailure(error), same(error));
      }
    });

    test('session and rate-limit failures never auto-retry', () {
      expect(recoverableReadFailure(_http(401)), isNull);
      expect(recoverableReadFailure(_http(429)), isNull);
      expect(
        recoverableReadFailure(EndpointRefusedException('SearchTimeline')),
        isNull,
      );
    });
  });
}
