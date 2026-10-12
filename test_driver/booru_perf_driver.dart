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
      final env = Map<String, dynamic>.from(data['env'] as Map? ?? const {});
      final refreshRate = (env['refresh_rate'] as num?)?.toDouble() ?? 60;
      env.addAll(await _hostEnvironment());

      Map<String, Object> scenario(String prefix) {
        final trials = [
          for (var trial = 1; data['${prefix}_trial_$trial'] is Map; trial++)
            trialStats(Map<String, dynamic>.from(data['${prefix}_trial_$trial'] as Map), refreshRate: refreshRate),
        ];
        return {'trials': trials, 'median': medianOfTrials(trials)};
      }

      final openTimes = [
        for (var trial = 1; data['s3_open_ms_trial_$trial'] is num; trial++)
          (data['s3_open_ms_trial_$trial'] as num).toDouble(),
      ];
      final report = {
        'env': env,
        's2_grid_fling': scenario('s2'),
        's3_viewer_swipes': scenario('s3'),
        's3_open_to_picture_ms': {'trials': openTimes, 'median': median(openTimes), 'p95': percentile(openTimes, 95)},
      };

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
