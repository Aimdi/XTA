import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:logging/logging.dart';
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
    expect(issueApiUri('Aimdi/XTA-gamma')?.path, '/repos/Aimdi/XTA-gamma/issues');
    expect(issueApiUri('bad'), isNull);
    expect(issueApiUri('/'), isNull);
  });

  test('prefsMapWithoutSecrets strips the GitHub token', () {
    final cleaned = prefsMapWithoutSecrets({optionCrashGithubToken: 'secret', optionCrashReportsEnabled: true});
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

    expect(stripped.keys, [optionThemeMode], reason: 'only the non-secret setting survives');
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
      return http.Response('{"id":1}', 201, headers: {'content-type': 'application/json'});
    });

    final reporter = CrashReporter(
      prefs,
      httpClient: client,
      packageInfoLoader: () async =>
          PackageInfo(appName: 'XTA', packageName: 'com.aimdi.xta', version: '4.12.0', buildNumber: '1'),
    );
    final result = await reporter.report(Exception('unit-test-crash'), StackTrace.current, force: true);

    expect(result, CrashReportResult.sent);
    expect(seen, isNotNull);
    expect(seen!.method, 'POST');
    expect(seen!.url.path, '/repos/Aimdi/XTA-gamma/issues');
    expect(seen!.headers['Authorization'], 'Bearer test-token');
  });

  test('report refuses to send without token', () async {
    final prefs = PrefServiceCache(
      cache: {optionCrashReportsEnabled: true, optionCrashGithubRepo: 'Aimdi/XTA-gamma', optionCrashGithubToken: ''},
    );
    final reporter = CrashReporter(prefs, httpClient: MockClient((_) async => http.Response('', 500)));
    final result = await reporter.report(Exception('x'), StackTrace.current, force: true);
    expect(result, CrashReportResult.missingToken);
  });

  test('outbound issue omits private messages, context and local paths but retains code locations', () async {
    final requests = <http.Request>[];
    final reporter = _reporter(
      MockClient((request) async {
        requests.add(request);
        return http.Response('{}', 201);
      }),
    );
    final result = await reporter.report(
      const FormatException('Cookie: auth_token=private-cookie', 'private-post-body'),
      StackTrace.fromString(
        '#0 Feed.load (package:xta/home/feed_model.dart:42:7)\n'
        '#1 private-reader (file:///data/user/0/private-account/private-file.dart:1:2)\n'
        'https://private-host/article?token=private-query\n'
        '#2 _Timer._runTimers (dart:isolate-patch/timer_impl.dart:430:19)',
      ),
      context: 'Authorization: Bearer private-token; private-search',
    );

    expect(result, CrashReportResult.sent);
    expect(requests, hasLength(1));
    final payload = jsonDecode(requests.single.body) as Map<String, dynamic>;
    expect(payload['title'], contains('FormatException'));
    expect(payload['body'], contains('package:xta/home/feed_model.dart:42:7'));
    expect(payload['body'], contains('dart:isolate-patch/timer_impl.dart:430:19'));
    expect(payload['body'], contains('4.12.0+1'));
    expect(requests.single.body, isNot(contains('private-')));
    expect(requests.single.body, isNot(contains('auth_token')));
    expect(requests.single.headers['Authorization'], 'Bearer test-token');
  });

  test('disabled, ignored, duplicate and rate-limited errors do not send additional requests', () async {
    var sends = 0;
    final client = MockClient((_) async {
      sends++;
      return http.Response('{}', 201);
    });
    final disabled = _reporter(client, enabled: false);
    expect(await disabled.report(Exception('private-error'), null), CrashReportResult.disabled);
    final reporter = _reporter(client);
    expect(await reporter.report(NoAccountAvailableException(), null), CrashReportResult.ignored);
    expect(await reporter.report(Exception('private-first'), null), CrashReportResult.sent);
    expect(await reporter.report(Exception('private-first'), null), CrashReportResult.duplicate);
    for (var i = 0; i < 4; i++) {
      expect(await reporter.report(Exception('private-unique-$i'), null), CrashReportResult.sent);
    }
    expect(await reporter.report(Exception('private-over-limit'), null), CrashReportResult.rateLimited);
    expect(sends, 5);
    expect(await disabled.sendTestReport(), CrashReportResult.sent);
    expect(sends, 6);
  });

  test('report delivery logs do not disclose response bodies or plaintext fingerprints', () async {
    final records = <LogRecord>[];
    final oldLevel = Logger.root.level;
    Logger.root.level = Level.ALL;
    final subscription = Logger.root.onRecord.listen(records.add);
    addTearDown(() async {
      await subscription.cancel();
      Logger.root.level = oldLevel;
    });
    final success = _reporter(MockClient((_) async => http.Response('{}', 201)));
    await success.report(Exception('private-message'), null);
    final failure = _reporter(MockClient((_) async => http.Response('private-response', 403)));
    expect(await failure.report(Exception('private-message'), null), CrashReportResult.authFailed);
    expect(records.map((e) => e.message).join('\n'), isNot(contains('private-')));
    expect(records.map((e) => e.message).join('\n'), contains('403'));
  });
}

CrashReporter _reporter(http.Client client, {bool enabled = true}) => CrashReporter(
  PrefServiceCache(cache: {optionCrashReportsEnabled: enabled, optionCrashGithubToken: 'test-token'}),
  httpClient: client,
  packageInfoLoader: () async =>
      PackageInfo(appName: 'XTA', packageName: 'com.aimdi.xta', version: '4.12.0', buildNumber: '1'),
);
