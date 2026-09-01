import 'package:flutter_triple/flutter_triple.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/accounts.dart';
import 'package:xta/client/endpoints.dart';
import 'package:xta/client/rate_limit_tracker.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/settings/diagnostics_report.dart';

class DiagnosticsModel extends Store<DiagnosticsReport> {
  final BasePrefService prefs;

  DiagnosticsModel(this.prefs) : super(DiagnosticsReport.empty);

  Future<void> load() async {
    await execute(() async {
      final now = DateTime.now();
      final packageInfo = await PackageInfo.fromPlatform();

      return DiagnosticsReport(
        appVersion: 'v${packageInfo.version}+${packageInfo.buildNumber}',
        accounts: (await getAccounts()).map((account) => _diagnose(account, now)).toList(),
        endpoints: XEndpoints.all.map(EndpointDiagnostics.of).toList(),
        registryEnabled: prefs.get<bool>(optionEndpointRegistryEnabled) != false,
        registryFetchedAt: DateTime.tryParse(prefs.get<String>(optionEndpointRegistryFetchedAt) ?? ''),
        generatedAt: now,
      );
    });
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
