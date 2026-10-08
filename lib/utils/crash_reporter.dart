import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:logging/logging.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:pref/pref.dart';
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/constants.dart';

/// Opt-in crash reporter that opens GitHub Issues via a user-supplied token.
///
/// Never embeds a PAT. Default is off. Synthetic/account errors are ignored.
class CrashReporter {
  static final log = Logger('CrashReporter');
  static CrashReporter? instance;

  final BasePrefService prefs;
  final http.Client httpClient;
  final Future<PackageInfo> Function() packageInfoLoader;
  final Set<String> _recentFingerprints = {};
  DateTime _windowStart = DateTime.fromMillisecondsSinceEpoch(0);
  int _reportsInWindow = 0;
  bool _installed = false;

  CrashReporter(
    this.prefs, {
    http.Client? httpClient,
    Future<PackageInfo> Function()? packageInfoLoader,
  }) : httpClient = httpClient ?? http.Client(),
       packageInfoLoader = packageInfoLoader ?? PackageInfo.fromPlatform;

  static CrashReporter install(BasePrefService prefs) {
    final reporter = CrashReporter(prefs);
    instance = reporter;
    reporter._attachHandlers();
    return reporter;
  }

  bool get enabled => prefs.get(optionCrashReportsEnabled) == true;

  String get repository =>
      (prefs.get(optionCrashGithubRepo) as String?)?.trim().isNotEmpty == true
      ? (prefs.get(optionCrashGithubRepo) as String).trim()
      : defaultCrashGithubRepo;

  String get token =>
      (prefs.get(optionCrashGithubToken) as String?)?.trim() ?? '';

  void _attachHandlers() {
    if (_installed) return;
    _installed = true;

    final previousFlutter = FlutterError.onError;
    FlutterError.onError = (details) {
      unawaited(
        report(
          details.exception,
          details.stack,
          context: details.context?.toString(),
        ),
      );
      previousFlutter?.call(details);
    };

    PlatformDispatcher.instance.onError = (error, stack) {
      unawaited(report(error, stack));
      // Handled: an uncaught async error must not abort the isolate. Returning
      // false is how a widget or compute failure became "XTA has stopped".
      return handleUncaughtIsolateErrors;
    };
  }

  Future<CrashReportResult> report(
    Object error,
    StackTrace? stack, {
    String? context,
    bool force = false,
  }) async {
    if (!force && !enabled) return CrashReportResult.disabled;
    if (!force && !shouldReport(error)) return CrashReportResult.ignored;
    if (token.isEmpty) return CrashReportResult.missingToken;

    final fingerprint = fingerprintOf(error, stack);
    if (!force && _recentFingerprints.contains(fingerprint)) {
      return CrashReportResult.duplicate;
    }
    if (!force && !_consumeRateBudget()) return CrashReportResult.rateLimited;

    final info = await packageInfoLoader();
    final body = buildIssueBody(
      error: error,
      stack: stack,
      context: context,
      appVersion: '${info.version}+${info.buildNumber}',
      packageName: info.packageName,
    );
    final title = buildIssueTitle(error);

    try {
      final uri = issueApiUri(repository);
      if (uri == null) return CrashReportResult.invalidRepo;

      final response = await httpClient.post(
        uri,
        headers: {
          'Accept': 'application/vnd.github+json',
          'Authorization': 'Bearer $token',
          'X-GitHub-Api-Version': '2022-11-28',
          'Content-Type': 'application/json',
        },
        body: jsonEncode({'title': title, 'body': body}),
      );

      if (response.statusCode == 201) {
        _recentFingerprints.add(fingerprint);
        log.info('Crash report filed for $fingerprint');
        return CrashReportResult.sent;
      }

      log.warning(
        'GitHub issue create failed: ${response.statusCode} ${response.body}',
      );
      if (response.statusCode == 401 || response.statusCode == 403) {
        return CrashReportResult.authFailed;
      }
      return CrashReportResult.failed;
    } catch (e, st) {
      log.warning('Unable to send crash report', e, st);
      return CrashReportResult.failed;
    }
  }

  Future<CrashReportResult> sendTestReport() {
    return report(
      Exception('XTA crash-report test'),
      StackTrace.current,
      context: 'manual test from Settings',
      force: true,
    );
  }

  bool _consumeRateBudget() {
    final now = DateTime.now();
    if (now.difference(_windowStart) > const Duration(hours: 1)) {
      _windowStart = now;
      _reportsInWindow = 0;
    }
    if (_reportsInWindow >= maxCrashReportsPerHour) return false;
    _reportsInWindow++;
    return true;
  }
}

enum CrashReportResult {
  sent,
  disabled,
  ignored,
  missingToken,
  duplicate,
  rateLimited,
  invalidRepo,
  authFailed,
  failed,
}

const maxCrashReportsPerHour = 5;

/// [PlatformDispatcher.onError] must return true so a widget or isolate
/// failure does not abort the process with "XTA has stopped".
const handleUncaughtIsolateErrors = true;

bool shouldReport(Object error) {
  if (error is SyntheticException) return false;
  if (error is HttpException) return false;
  return true;
}

String fingerprintOf(Object error, StackTrace? stack) {
  final firstFrames = (stack?.toString() ?? '')
      .split('\n')
      .where((line) => line.trim().isNotEmpty)
      .take(4)
      .join('|');
  return '${error.runtimeType}|${error.toString()}|$firstFrames';
}

String buildIssueTitle(Object error) {
  final raw = error.toString().replaceAll('\n', ' ').trim();
  final clipped = raw.length > 80 ? '${raw.substring(0, 77)}...' : raw;
  return '[crash] ${error.runtimeType}: $clipped';
}

String buildIssueBody({
  required Object error,
  required StackTrace? stack,
  required String? context,
  required String appVersion,
  required String packageName,
}) {
  final buffer = StringBuffer()
    ..writeln('## Crash report (auto)')
    ..writeln()
    ..writeln('- App: `$packageName` `$appVersion`')
    ..writeln(
      '- OS: `${Platform.operatingSystem} ${Platform.operatingSystemVersion}`',
    )
    ..writeln(
      '- Mode: `${kReleaseMode ? 'release' : (kProfileMode ? 'profile' : 'debug')}`',
    );
  if (context != null && context.isNotEmpty) {
    buffer.writeln('- Context: `$context`');
  }
  buffer
    ..writeln()
    ..writeln('### Error')
    ..writeln('```')
    ..writeln(error.toString())
    ..writeln('```')
    ..writeln()
    ..writeln('### Stack trace')
    ..writeln('```')
    ..writeln(stack?.toString() ?? '(none)')
    ..writeln('```');
  return buffer.toString();
}

Uri? issueApiUri(String repo) {
  final parts = repo.split('/');
  if (parts.length != 2 || parts[0].isEmpty || parts[1].isEmpty) return null;
  return Uri.https('api.github.com', '/repos/${parts[0]}/${parts[1]}/issues');
}

/// Strip secrets before settings export / backup.
///
/// The WebDAV password is stripped for two reasons: a backup file is shared and
/// inspected, and the synced document is itself written to that server — leaving
/// the password in would store the server's own credentials on it in plaintext.
Map<String, dynamic> prefsMapWithoutSecrets(Map<String, dynamic> prefs) {
  final copy = Map<String, dynamic>.from(prefs);
  copy.removeWhere((key, _) => isSecretPrefKey(key));
  return copy;
}
