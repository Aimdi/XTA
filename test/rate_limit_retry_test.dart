import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/ui/rate_limit_retry.dart';

void main() {
  test('countdown uses the earliest relevant account reset, never an unrelated endpoint', () {
    final now = DateTime.utc(2026);
    final error = RateLimitedException(operations: ['SearchTimeline']);
    final accounts = [
      {
        '/graphql/a/SearchTimeline': now.add(const Duration(minutes: 3)),
        '/graphql/b/UserTweets': now.add(const Duration(seconds: 1)),
      },
      {'/graphql/c/SearchTimeline': now.add(const Duration(minutes: 1))},
    ];
    expect(knownRateLimitReset(error, accounts, now), now.add(const Duration(minutes: 1)));
    expect(knownRateLimitReset(error, [...accounts, <String, DateTime>{}], now), isNull);
    expect(knownRateLimitReset(RateLimitedException(), accounts, now), isNull);
  });
  test('read context reaches parameterless rate-limit exceptions through async work', () async {
    try {
      await withRateLimitOperations(['UserTweets'], () async {
        await Future<void>.value();
        throw RateLimitedException();
      });
      fail('expected rate limit');
    } on RateLimitedException catch (error) {
      expect(error.operations, ['UserTweets']);
    }
  });
  test('HTTP retry headers expose their actual reset', () {
    final now = DateTime.utc(2026);
    final error = HttpException(http.Response('', 429, headers: {'retry-after': '45'}));
    expect(knownRateLimitReset(error, const [], now), now.add(const Duration(seconds: 45)));
  });
  testWidgets('retry is disabled until reset and disposal cancels its timer', (tester) async {
    var calls = 0;
    var now = DateTime.utc(2026);
    final deadline = now.add(const Duration(seconds: 2));
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: const [L10n.delegate],
        home: Scaffold(
          body: RateLimitRetryButton(
            error: RateLimitedException(),
            clock: () => now,
            lookup: (_) async => deadline,
            onRetry: () => calls++,
          ),
        ),
      ),
    );
    await tester.pump();
    expect(tester.widget<TextButton>(find.byType(TextButton)).onPressed, isNull);
    now = now.add(const Duration(seconds: 3));
    await tester.pump(const Duration(seconds: 1));
    expect(tester.widget<TextButton>(find.byType(TextButton)).onPressed, isNotNull);
    await tester.tap(find.byType(TextButton));
    await tester.pumpWidget(const SizedBox());
    expect(calls, 1);
  });
}
