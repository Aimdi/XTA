import 'dart:convert';
import 'dart:io';

import 'package:integration_test/integration_test_driver.dart';

/// Turns integration_test/substack_perf_test.dart's report into the numbers
/// docs/perf-baseline.md asks for: per trial, median and p95 build and raster
/// time and the share of frames over the display's own frame budget, plus the
/// medians across trials. Written to build/substack_perf_summary.json.
Future<void> main() {
  return integrationDriver(
    responseDataCallback: (data) async {
      if (data == null) return;
      final environment = {...?data['environment'] as Map<String, dynamic>?, ...await _device()};
      final budget = 1000 / ((environment['refresh_rate_hz'] as num?)?.toDouble() ?? 60);
      final trials = {
        for (final key in data.keys.where((key) => key.contains('_trial_')))
          key: _frames(data[key] as Map<String, dynamic>, budget),
      };
      final summary = {
        'environment': {...environment, 'frame_budget_ms': budget},
        's2': data['s2'],
        's3': data['s3'],
        'trials': trials,
        's2_fling_median_of_trials': _medians(trials, 's2_fling'),
        's3_scroll_median_of_trials': _medians(trials, 's3_scroll'),
      };
      const pretty = JsonEncoder.withIndent('  ');
      // ignore: avoid_print
      print('\nsubstack perf summary:\n${pretty.convert(summary)}\n');
      await Directory('build').create(recursive: true);
      await File('build/substack_perf_summary.json').writeAsString(pretty.convert(summary));
    },
  );
}

double _percentile(List<double> sorted, double q) => sorted.isEmpty ? 0 : sorted[(q * (sorted.length - 1)).round()];

Map<String, double> _frames(Map<String, dynamic> watched, double budget) {
  final build = [for (final us in watched['frame_build_times'] as List) (us as num) / 1000];
  final raster = [for (final us in watched['frame_rasterizer_times'] as List) (us as num) / 1000];
  final janky = [for (var i = 0; i < build.length; i++) build[i] > budget || raster[i] > budget].where((late) => late);
  build.sort();
  raster.sort();
  return {
    'frames': build.length.toDouble(),
    'build_p50_ms': _percentile(build, 0.5),
    'build_p95_ms': _percentile(build, 0.95),
    'raster_p50_ms': _percentile(raster, 0.5),
    'raster_p95_ms': _percentile(raster, 0.95),
    'janky_pct': build.isEmpty ? 0 : 100 * janky.length / build.length,
  };
}

Map<String, double> _medians(Map<String, Map<String, double>> trials, String scenario) {
  final runs = [
    for (final entry in trials.entries)
      if (entry.key.startsWith(scenario)) entry.value,
  ];
  if (runs.isEmpty) return const {};
  return {
    for (final metric in runs.first.keys) metric: _percentile([for (final run in runs) run[metric]!]..sort(), 0.5),
  };
}

/// adb follows ANDROID_SERIAL when more than one device is attached.
Future<Map<String, String>> _device() async {
  Future<String> property(String name) async {
    try {
      return '${(await Process.run('adb', ['shell', 'getprop', name])).stdout}'.trim();
    } on ProcessException {
      return '';
    }
  }

  return {
    'device_model': await property('ro.product.model'),
    'android_release': await property('ro.build.version.release'),
  };
}
