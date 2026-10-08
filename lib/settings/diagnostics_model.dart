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
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/settings/diagnostics_report.dart';
import 'package:xta/settings/main_thread_stalls.dart';

class DiagnosticsModel extends Store<DiagnosticsReport> {
  final BasePrefService prefs;

  /// Every local step is bounded, so a stuck database or platform thread shows up as a named line instead of a
  /// Diagnose page that never finishes loading.
  static const probeTimeout = Duration(seconds: 5);

  DiagnosticsModel(this.prefs) : super(DiagnosticsReport.empty);

  Future<void> load() async {
    await execute(() async {
      final now = DateTime.now();
      final probes = <DiagnosticsProbe>[];
      final packageInfo = await _probe(probes, 'android platform call', PackageInfo.fromPlatform);
      final accounts = await _probe(probes, 'database read', getAccounts);
      // A transaction that changes nothing: it needs the same write lock a like or a group change needs.
      await _probe(probes, 'database save', () async {
        await (await Repository.writable()).transaction((txn) => txn.rawQuery('SELECT 1'));
      });
      probes.add(DiagnosticsProbe('android main thread', failure: await mainThreadStallSummary()));

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

  /// Runs [step] under [probeTimeout]; a timeout or failure is recorded by category only and yields null.
  static Future<T?> _probe<T>(List<DiagnosticsProbe> probes, String name, Future<T> Function() step) async {
    final watch = Stopwatch()..start();
    try {
      final value = await step().timeout(probeTimeout);
      probes.add(DiagnosticsProbe(name, elapsed: watch.elapsed));
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
