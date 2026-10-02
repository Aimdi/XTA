import 'dart:async';

import 'package:xta/utils/read_activity.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/accounts.dart';
import 'package:xta/client/endpoints.dart';
import 'package:xta/client/headers.dart';
import 'package:xta/client/rate_limit_tracker.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/database_facts.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/settings/diagnostics_report.dart';
import 'package:xta/settings/main_thread_stalls.dart';

class DiagnosticsModel extends Store<DiagnosticsReport> {
  final BasePrefService prefs;

  DiagnosticsModel(this.prefs) : super(DiagnosticsReport.empty);

  /// Every local step is bounded: a report that says the database is stuck beats a spinner that never ends.
  static const probeTimeout = Duration(seconds: 5);

  Future<void> load() async {
    await execute(() async {
      final now = DateTime.now();
      final probes = <DiagnosticsProbe>[];
      final packageInfo = await _probe(probes, 'package info', PackageInfo.fromPlatform);
      final accounts = await _probe(probes, 'database (read-only connection)', getAccounts);
      await _probe(probes, 'database (writable connection)', () async {
        await (await Repository.writable()).rawQuery('SELECT 1');
      });
      await _probe(probes, 'feed cache', _feedCacheSize, detail: (size) => size);
      probes.add(DiagnosticsProbe('database at launch', failure: DatabaseFacts.summary));
      probes.add(DiagnosticsProbe('X signing key', failure: TwitterHeaders.describeKeyState()));
      probes.add(DiagnosticsProbe('Android main thread', failure: await mainThreadStallSummary()));

      return DiagnosticsReport(
        appVersion: packageInfo == null ? 'unknown' : 'v${packageInfo.version}+${packageInfo.buildNumber}',
        accounts: (accounts ?? const []).map((account) => _diagnose(account, now)).toList(),
        endpoints: XEndpoints.all.map(EndpointDiagnostics.of).toList(),
        registryEnabled: prefs.get<bool>(optionEndpointRegistryEnabled) != false,
        registryFetchedAt: DateTime.tryParse(prefs.get<String>(optionEndpointRegistryFetchedAt) ?? ''),
        generatedAt: now,
        operations: ReadActivityLog.shared.snapshot(),
        xSetupFailure: TwitterHeaders.lastInitializationFailure,
        probes: probes,
      );
    });
  }

  /// How much the Following fan-out has to do: subscriptions, groups and the cached chunk rows it reads and writes.
  static Future<String> _feedCacheSize() async {
    final database = await Repository.readOnly();
    Future<int> count(String sql) async => (await database.rawQuery(sql)).first.values.first as int? ?? 0;
    final subscriptions = await count('SELECT COUNT(*) FROM $tableSubscription');
    final searches = await count('SELECT COUNT(*) FROM $tableSearchSubscription');
    final groups = await count('SELECT COUNT(*) FROM $tableSubscriptionGroup');
    final chunkRows = await count('SELECT COUNT(*) FROM $tableFeedGroupChunk');
    final chunkBytes = await count('SELECT COALESCE(SUM(LENGTH(response)), 0) FROM $tableFeedGroupChunk');
    return '$subscriptions subscriptions, $searches searches, $groups groups, '
        '$chunkRows cached chunk rows (${chunkBytes ~/ 1024} KB)';
  }

  /// Runs [step] under [probeTimeout]; a timeout or failure is recorded by category only and yields null.
  static Future<T?> _probe<T>(
    List<DiagnosticsProbe> probes,
    String name,
    Future<T> Function() step, {
    String Function(T value)? detail,
  }) async {
    final watch = Stopwatch()..start();
    try {
      final value = await step().timeout(probeTimeout);
      probes.add(DiagnosticsProbe(name, elapsed: watch.elapsed, detail: detail?.call(value)));
      return value;
    } on TimeoutException {
      probes.add(DiagnosticsProbe(name, failure: 'still waiting after ${probeTimeout.inSeconds}s'));
    } catch (error) {
      probes.add(DiagnosticsProbe(name, failure: 'failed (${error.runtimeType})'));
    }
    return null;
  }

  AccountDiagnostics _diagnose(Account account, DateTime now) {
    final notFoundUntil = account.lastNotFoundAt?.add(notFoundCooldown);

    return AccountDiagnostics(
      id: account.id,
      screenName: account.screenName,
      rateLimited: RateLimitTracker.activeFor(account.id, now),
      notFoundUntil: notFoundUntil != null && notFoundUntil.isAfter(now) ? notFoundUntil : null,
    );
  }
}
