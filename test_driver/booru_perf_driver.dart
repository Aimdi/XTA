import 'dart:convert';
import 'dart:io';

import 'package:integration_test/integration_test_driver.dart';

import 'booru_perf_stats.dart';

/// Driver for integration_test/booru_perf_test.dart. Records the environment
/// the run needs to be comparable (commit, device, Android version, refresh
/// rate) and writes per-trial and median numbers to
/// build/booru_perf_summary.json for docs/perf-baseline.md.
Future<void> main() {
  return integrationDriver(
    responseDataCallback: (data) async {
      if (data == null) return;
      final report = booruSummary(data);
      (report['env'] as Map<String, dynamic>).addAll(await _hostEnvironment());
      final pretty = const JsonEncoder.withIndent('  ').convert(report);
      // ignore: avoid_print
      print('\nbooru perf summary:\n$pretty\n');
      await Directory('build').create(recursive: true);
      await File('build/booru_perf_summary.json').writeAsString(pretty);
    },
  );
}

/// What only the host can see: the commit under test and the attached phone.
Future<Map<String, String>> _hostEnvironment() async {
  Future<String> run(String executable, List<String> arguments) async {
    try {
      final result = await Process.run(executable, arguments);
      return result.exitCode == 0 ? (result.stdout as String).trim() : '';
    } on ProcessException {
      return '';
    }
  }

  return {
    'commit': await run('git', ['rev-parse', '--short', 'HEAD']),
    'device': await run('adb', ['shell', 'getprop', 'ro.product.model']),
    'android': await run('adb', ['shell', 'getprop', 'ro.build.version.release']),
    'network': 'record by hand: Wi-Fi or mobile, and its speed',
  };
}
