import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/endpoints.dart';
import 'package:xta/client/rate_limit_tracker.dart';
import 'package:xta/settings/diagnostics_report.dart';

DiagnosticsReport _report({
  List<AccountDiagnostics> accounts = const [],
  bool registryEnabled = true,
  DateTime? registryFetchedAt,
}) => DiagnosticsReport(
  appVersion: 'v4.12.0+400001040',
  accounts: accounts,
  endpoints: XEndpoints.all.map(EndpointDiagnostics.of).toList(),
  registryEnabled: registryEnabled,
  registryFetchedAt: registryFetchedAt,
  generatedAt: DateTime.utc(2026, 7, 25, 9, 30),
);

void main() {
  tearDown(XEndpoints.clearOverrides);

  group('AccountDiagnostics', () {
    test('an account with no flags is healthy', () {
      const account = AccountDiagnostics(id: '1', screenName: 'reader', rateLimited: {}, notFoundUntil: null);

      expect(account.isHealthy, isTrue);
    });

    test('a rate limit on one endpoint makes it unhealthy', () {
      final account = AccountDiagnostics(
        id: '1',
        screenName: 'reader',
        rateLimited: {'/i/api/graphql/x/SearchTimeline': DateTime.utc(2026, 7, 25, 10)},
        notFoundUntil: null,
      );

      expect(account.isHealthy, isFalse);
    });
  });

  group('report text', () {
    test('names the endpoint and account behind a failure', () {
      final text = _report(
        accounts: [
          AccountDiagnostics(
            id: 'abc',
            screenName: 'reader',
            rateLimited: {'/i/api/graphql/x/SearchTimeline': DateTime.utc(2026, 7, 25, 10)},
            notFoundUntil: DateTime.utc(2026, 7, 25, 15),
          ),
        ],
      ).toPlainText();

      expect(text, contains('@reader'));
      expect(text, contains('429 /i/api/graphql/x/SearchTimeline until 2026-07-25T10:00:00.000Z'));
      expect(text, contains('auth broken until 2026-07-25T15:00:00.000Z'));
    });

    test('says so plainly when there is no account at all', () {
      final text = _report().toPlainText();

      expect(text, contains('accounts (0):'));
      expect(text, contains('  none'));
    });

    test('lists every endpoint with the query id actually in force', () {
      XEndpoints.applyOverrides({XEndpoints.userTweets: 'ZZZZZZZZZZZZZZZZZZZZZZ'});

      final report = _report();
      final text = report.toPlainText();

      for (final endpoint in XEndpoints.all) {
        expect(text, contains(endpoint.name));
      }
      expect(text, contains('ZZZZZZZZZZZZZZZZZZZZZZ'));
      expect(text, contains('(overridden)'));
      expect(report.overriddenCount, 1);
    });

    test('records whether the registry was ever reached', () {
      expect(_report().toPlainText(), contains('last checked never'));
      expect(
        _report(registryFetchedAt: DateTime.utc(2026, 7, 25, 5)).toPlainText(),
        contains('last checked 2026-07-25T05:00:00.000Z'),
      );
      expect(_report(registryEnabled: false).toPlainText(), contains('endpoint registry: off'));
    });
  });

  group('RateLimitTracker.activeFor', () {
    test('reports only windows that have not elapsed', () {
      final now = DateTime.utc(2026, 7, 25, 9);
      RateLimitTracker.flag('acc', '/live', now.add(const Duration(minutes: 10)));
      RateLimitTracker.flag('acc', '/expired', now.subtract(const Duration(minutes: 10)));

      expect(RateLimitTracker.activeFor('acc', now).keys, ['/live']);

      RateLimitTracker.clear('acc', '/live');
      RateLimitTracker.clear('acc', '/expired');
    });

    test('an account that has never been limited reports nothing', () {
      expect(RateLimitTracker.activeFor('unknown', DateTime.utc(2026, 7, 25)), isEmpty);
    });
  });
}
