import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' show ClientException;
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/tweet/stale_feed_preview.dart';

void main() {
  final boom = Exception('boom');

  group('staleFeedReasonOf', () {
    test('separates the failures a reader would act on differently', () {
      expect(staleFeedReasonOf(const SocketException('no route')), StaleFeedReason.offline);
      expect(staleFeedReasonOf(ClientException('closed')), StaleFeedReason.offline);
      expect(staleFeedReasonOf(TimeoutException('slow')), StaleFeedReason.timedOut);
      expect(staleFeedReasonOf(RateLimitedException()), StaleFeedReason.rateLimited);
      expect(staleFeedReasonOf(NoWorkingAccountException()), StaleFeedReason.noWorkingAccount);
      expect(staleFeedReasonOf(NoAccountAvailableException()), StaleFeedReason.noAccount);
      expect(staleFeedReasonOf(EndpointRefusedException('Search')), StaleFeedReason.endpointRefused);
    });

    test('anything else stays unknown rather than being guessed at', () {
      expect(staleFeedReasonOf(boom), StaleFeedReason.unknown);
      expect(staleFeedReasonOf(null), StaleFeedReason.unknown);
    });
  });

  group('shouldShowStalePreview', () {
    test('a failed first page with cached posts shows them', () {
      expect(shouldShowStalePreview(error: boom, items: null, preview: const ['a']), isTrue);
    });

    test('no error means the normal list, not the cache', () {
      expect(shouldShowStalePreview(error: null, items: null, preview: const ['a']), isFalse);
    });

    test('an empty or absent cache leaves the error page in place', () {
      expect(shouldShowStalePreview(error: boom, items: null, preview: const []), isFalse);
      expect(shouldShowStalePreview(error: boom, items: null, preview: null), isFalse);
    });

    test('a later page failing does not replace the posts already loaded', () {
      expect(shouldShowStalePreview(error: boom, items: const ['x'], preview: const ['a']), isFalse);
      // Even a successful but empty first page counts as loaded.
      expect(shouldShowStalePreview(error: boom, items: const [], preview: const ['a']), isFalse);
    });
  });

  group('sameCalendarDay', () {
    test('compares the day, not the elapsed time', () {
      expect(sameCalendarDay(DateTime(2026, 8, 4, 0, 5), DateTime(2026, 8, 4, 23, 55)), isTrue);
      expect(sameCalendarDay(DateTime(2026, 8, 3, 23, 55), DateTime(2026, 8, 4, 0, 5)), isFalse);
      expect(sameCalendarDay(DateTime(2025, 8, 4), DateTime(2026, 8, 4)), isFalse);
    });
  });
}
