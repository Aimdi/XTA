import 'dart:async';

import 'package:package_info_plus/package_info_plus.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/endpoints.dart';
import 'package:xta/client/headers.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/database_facts.dart';
import 'package:xta/settings/diagnostics_report.dart';
import 'package:xta/utils/read_activity.dart';

/// A report built from memory alone, for the error screen: when the database or the Diagnose page itself does not
/// answer, the request log and the signing state are still the facts that say which layer stalled.
DiagnosticsReport quickDiagnosticsReport({
  required String appVersion,
  required BasePrefService prefs,
  required DateTime now,
  required String keyState,
  required Object? xSetupFailure,
  required List<String> operations,
}) => DiagnosticsReport(
  appVersion: appVersion,
  accounts: const [],
  endpoints: XEndpoints.all.map(EndpointDiagnostics.of).toList(),
  registryEnabled: prefs.get<bool>(optionEndpointRegistryEnabled) != false,
  registryFetchedAt: DateTime.tryParse(prefs.get<String>(optionEndpointRegistryFetchedAt) ?? ''),
  generatedAt: now,
  operations: operations,
  xSetupFailure: xSetupFailure,
  probes: [
    const DiagnosticsProbe('database', failure: 'not probed (copied from the error screen)'),
    DiagnosticsProbe('database at launch', failure: DatabaseFacts.summary),
    DiagnosticsProbe('X signing key', failure: keyState),
  ],
);

/// The package lookup is a platform call, bounded so a copy never hangs behind it.
Future<String> quickDiagnosticsText(BasePrefService prefs) async {
  String version;
  try {
    final info = await PackageInfo.fromPlatform().timeout(const Duration(seconds: 2));
    version = 'v${info.version}+${info.buildNumber}';
  } catch (_) {
    version = 'unknown';
  }
  return quickDiagnosticsReport(
    appVersion: version,
    prefs: prefs,
    now: DateTime.now(),
    keyState: TwitterHeaders.describeKeyState(),
    xSetupFailure: TwitterHeaders.lastInitializationFailure,
    operations: ReadActivityLog.shared.snapshot(),
  ).toPlainText();
}
