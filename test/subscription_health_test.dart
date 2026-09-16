import 'dart:async';
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/errors.dart';
import 'package:xta/subscriptions/subscription_health.dart';

TwitterError failure(int code) => TwitterError(uri: '', code: code, message: 'fixture');
void main() {
  test('name connection failure does not run the destructive missing-account fallback', () async {
    var called = false;
    final result = await checkSubscriptionHealth(
      byName: () async => throw const SocketException('offline'),
      byId: () async {
        called = true;
        return 'valid';
      },
    );
    expect(result.kind, SubscriptionHealthKind.unreachable);
    expect(called, isFalse);
  });
  for (final error in [const SocketException('offline'), TimeoutException('slow'), StateError('shape'), failure(-1)]) {
    test('ID fallback keeps uncertain failures out of deletion candidates: ${error.runtimeType}', () async {
      final result = await checkSubscriptionHealth<String>(
        byName: () async => throw failure(50),
        byId: () async => throw error,
      );
      expect(result.kind, SubscriptionHealthKind.unreachable);
    });
  }
  test('confirmed missing, suspended and renamed accounts stay distinguishable', () async {
    for (final row in [(50, SubscriptionHealthKind.missing), (63, SubscriptionHealthKind.suspended)]) {
      final result = await checkSubscriptionHealth<String>(
        byName: () async => throw failure(50),
        byId: () async => throw failure(row.$1),
      );
      expect(result.kind, row.$2);
    }
    final renamed = await checkSubscriptionHealth(byName: () async => throw failure(50), byId: () async => 'new-name');
    expect(renamed.kind, SubscriptionHealthKind.renamed);
    expect(renamed.profile, 'new-name');
  });
  test('rate limits and hung lookups are not deletions', () async {
    final limited = await checkSubscriptionHealth<String>(
      byName: () async => throw failure(50),
      byId: () async => throw RateLimitedException(),
    );
    expect(limited.kind, SubscriptionHealthKind.rateLimited);
    final hung = await checkSubscriptionHealth<String>(
      byName: () => Completer<String>().future,
      byId: () async => 'unused',
      timeout: const Duration(milliseconds: 5),
    );
    expect(hung.kind, SubscriptionHealthKind.unreachable);
  });
}
