/// A snapshot of why the app is or is not talking to X.
///
/// Pure data with no Flutter or database dependency, so the whole thing can be
/// built in a test and rendered to text without a device. [toPlainText] is what
/// the copy button puts on the clipboard — a bug report that answers "which
/// endpoint, which account, which query id" without a screenshot.
library;

import 'package:xta/client/endpoints.dart';

class AccountDiagnostics {
  final String id;
  final String? screenName;

  /// Endpoint path to the time X said the limit lifts.
  final Map<String, DateTime> rateLimited;

  /// Set once the account has returned `notFoundThreshold` consecutive 404s,
  /// which usually means its authentication has stopped working.
  final DateTime? notFoundUntil;

  const AccountDiagnostics({
    required this.id,
    required this.screenName,
    required this.rateLimited,
    required this.notFoundUntil,
  });

  bool get isHealthy => rateLimited.isEmpty && notFoundUntil == null;
}

class EndpointDiagnostics {
  final String name;
  final String host;
  final String queryId;
  final bool isOverridden;

  const EndpointDiagnostics({
    required this.name,
    required this.host,
    required this.queryId,
    required this.isOverridden,
  });

  factory EndpointDiagnostics.of(XEndpoint endpoint) => EndpointDiagnostics(
    name: endpoint.name,
    host: endpoint.host,
    queryId: XEndpoints.queryId(endpoint.name),
    isOverridden: XEndpoints.isOverridden(endpoint.name),
  );
}

class DiagnosticsReport {
  final String appVersion;
  final List<AccountDiagnostics> accounts;
  final List<EndpointDiagnostics> endpoints;
  final bool registryEnabled;
  final DateTime? registryFetchedAt;
  final DateTime generatedAt;

  const DiagnosticsReport({
    required this.appVersion,
    required this.accounts,
    required this.endpoints,
    required this.registryEnabled,
    required this.registryFetchedAt,
    required this.generatedAt,
  });

  static final empty = DiagnosticsReport(
    appVersion: '',
    accounts: const [],
    endpoints: const [],
    registryEnabled: true,
    registryFetchedAt: null,
    generatedAt: DateTime.fromMillisecondsSinceEpoch(0),
  );

  int get overriddenCount => endpoints.where((e) => e.isOverridden).length;

  /// Deliberately not localised: this is pasted into issues read by whoever
  /// maintains the fork, and a report in a language they cannot read is worse
  /// than no report.
  String toPlainText() {
    final lines = <String>[
      'XTA diagnostics',
      'app: $appVersion',
      'generated: ${generatedAt.toIso8601String()}',
      '',
      'accounts (${accounts.length}):',
    ];

    if (accounts.isEmpty) {
      lines.add('  none');
    }
    for (final account in accounts) {
      final state = [
        if (account.notFoundUntil != null) 'auth broken until ${account.notFoundUntil!.toIso8601String()}',
        for (final entry in account.rateLimited.entries) '429 ${entry.key} until ${entry.value.toIso8601String()}',
      ];
      lines.add('  @${account.screenName ?? account.id}: ${state.isEmpty ? 'ok' : state.join('; ')}');
    }

    lines
      ..add('')
      ..add(
        'endpoint registry: ${registryEnabled ? 'on' : 'off'}, '
        'last checked ${registryFetchedAt?.toIso8601String() ?? 'never'}, '
        '$overriddenCount override(s)',
      )
      ..add('')
      ..add('endpoints:');

    for (final endpoint in endpoints) {
      lines.add(
        '  ${endpoint.name.padRight(22)} ${endpoint.host.padRight(12)} ${endpoint.queryId}'
        '${endpoint.isOverridden ? ' (overridden)' : ''}',
      );
    }

    return lines.join('\n');
  }
}
