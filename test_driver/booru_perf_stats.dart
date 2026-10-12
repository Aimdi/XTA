/// Frame statistics for the booru benchmark (integration_test/booru_perf_test.dart).
///
/// Pure so `flutter test` can check it without a device. Input is the summary
/// `IntegrationTestWidgetsFlutterBinding.watchPerformance` reports, whose
/// `frame_build_times` and `frame_rasterizer_times` are microseconds per frame.
library;

/// The [p]th percentile (0–100) by nearest rank; 0 when there are no values.
double percentile(List<double> values, double p) {
  if (values.isEmpty) return 0;
  final sorted = [...values]..sort();
  final rank = (p / 100 * sorted.length).ceil().clamp(1, sorted.length);
  return sorted[rank - 1];
}

double median(List<double> values) => percentile(values, 50);

List<double> _millis(Object? micros) => [
  for (final value in micros is List ? micros : const [])
    if (value is num) value / 1000,
];

/// One trial's numbers. A frame is janky when its build or its raster time
/// exceeds the frame budget, 1000 ms / [refreshRate].
Map<String, double> trialStats(Map<String, dynamic> summary, {required double refreshRate}) {
  final build = _millis(summary['frame_build_times']);
  final raster = _millis(summary['frame_rasterizer_times']);
  final budget = 1000 / (refreshRate > 0 ? refreshRate : 60);
  final frames = build.length < raster.length ? build.length : raster.length;
  var janky = 0;
  for (var i = 0; i < frames; i++) {
    if (build[i] > budget || raster[i] > budget) janky++;
  }
  return {
    'frames': frames.toDouble(),
    'budget_ms': budget,
    'janky_pct': frames == 0 ? 0 : janky * 100 / frames,
    'build_p50_ms': percentile(build, 50),
    'build_p95_ms': percentile(build, 95),
    'build_p99_ms': percentile(build, 99),
    'raster_p50_ms': percentile(raster, 50),
    'raster_p95_ms': percentile(raster, 95),
    'raster_p99_ms': percentile(raster, 99),
  };
}

/// The median of each statistic across [trials].
Map<String, double> medianOfTrials(List<Map<String, double>> trials) => {
  for (final key in trials.isEmpty ? const <String>[] : trials.first.keys)
    key: median([for (final trial in trials) trial[key] ?? 0]),
};
