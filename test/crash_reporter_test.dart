import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:pref/pref.dart';
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/constants.dart';
import 'package:xta/utils/crash_reporter.dart';

void main() {
  test('uncaught isolate errors are marked handled', () {
    expect(handleUncaughtIsolateErrors, isTrue);
  });

  test('shouldReport skips synthetic and http account errors', () {
    expect(shouldReport(Exception('boom')), isTrue);
    expect(shouldReport(NoAccountAvailableException()), isFalse);
    expect(shouldReport(RateLimitedException()), isFalse);
    expect(shouldReport(NoWorkingAccountException()), isFalse);
  });

  test('issueApiUri validates owner/name', () {
    expect(
      issueApiUri('Aimdi/XTA-gamma')?.path,
      '/repos/Aimdi/XTA-gamma/issues',
    );
    expect(issueApiUri('bad'), isNull);
    expect(issueApiUri('/'), isNull);
  });

  test('prefsMapWithoutSecrets strips the GitHub token', () {
    final cleaned = prefsMapWithoutSecrets({
      optionCrashGithubToken: 'secret',
      optionCrashReportsEnabled: true,
    });
    expect(cleaned.containsKey(optionCrashGithubToken), isFalse);
    expect(cleaned[optionCrashReportsEnabled], isTrue);
  });

  test('every plugin credential is stripped from an export', () {
    // These all reach the file a reader shares and the document uploaded to
    // their WebDAV server. The redaction used to name two keys, so each
    // credential added after it was written left the device in the clear.
    final stripped = prefsMapWithoutSecrets({
      optionAiApiKey: 'ai',
      optionPluginDeepmarksApiKey: 'deepmarks',
      optionPluginDeepmarksSecretKey: 'deepmarks-secret',
      optionPluginImmichApiKey: 'immich',
      optionPluginKarakeepApiKey: 'karakeep',
      optionPluginRedditClientId: 'reddit-client',
      optionPluginRedditRefreshToken: 'reddit-refresh',
      optionPluginThreadsApiToken: 'threads',
      optionCrashGithubToken: 'github',
      optionWebDavPassword: 'hunter2',
      optionThemeMode: 'dark',
    });

    expect(stripped.keys, [
      optionThemeMode,
    ], reason: 'only the non-secret setting survives');
  });

  test('a credential-shaped key is stripped even when nobody declared it', () {
    final stripped = prefsMapWithoutSecrets({
      'plugin.notyetwritten.api_key': 'secret',
      'plugin.notyetwritten.refresh_token': 'secret',
      'plugin.notyetwritten.password': 'secret',
      'plugin.notyetwritten.server_url': 'https://example.org',
    });

    expect(stripped.keys, ['plugin.notyetwritten.server_url']);
  });

  test('buildIssueTitle stays compact', () {
    final title = buildIssueTitle(Exception('x' * 200));
    expect(title.startsWith('[crash]'), isTrue);
    expect(title.length <= 120, isTrue);
  });

  test('report posts to GitHub when enabled with token', () async {
    final prefs = PrefServiceCache(
      cache: {
        optionCrashReportsEnabled: true,
        optionCrashGithubRepo: 'Aimdi/XTA-gamma',
        optionCrashGithubToken: 'test-token',
      },
    );

    http.Request? seen;
    final client = MockClient((request) async {
      seen = request;
      return http.Response(
        '{"id":1}',
        201,
        headers: {'content-type': 'application/json'},
      );
    });

    final reporter = CrashReporter(
      prefs,
      httpClient: client,
      packageInfoLoader: () async => PackageInfo(
        appName: 'XTA',
        packageName: 'com.aimdi.xta',
        version: '4.12.0',
        buildNumber: '1',
      ),
    );
    final result = await reporter.report(
      Exception('unit-test-crash'),
      StackTrace.current,
      force: true,
    );

    expect(result, CrashReportResult.sent);
    expect(seen, isNotNull);
    expect(seen!.method, 'POST');
    expect(seen!.url.path, '/repos/Aimdi/XTA-gamma/issues');
    expect(seen!.headers['Authorization'], 'Bearer test-token');
  });

  test('report refuses to send without token', () async {
    final prefs = PrefServiceCache(
      cache: {
        optionCrashReportsEnabled: true,
        optionCrashGithubRepo: 'Aimdi/XTA-gamma',
        optionCrashGithubToken: '',
      },
    );
    final reporter = CrashReporter(
      prefs,
      httpClient: MockClient((_) async => http.Response('', 500)),
    );
    final result = await reporter.report(
      Exception('x'),
      StackTrace.current,
      force: true,
    );
    expect(result, CrashReportResult.missingToken);
  });
}
