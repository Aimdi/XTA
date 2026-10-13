/// Statistics for the booru benchmark (integration_test/booru_perf_test.dart).
///
/// Pure so `flutter test` can check it without a device. Frame input is the
/// summary `IntegrationTestWidgetsFlutterBinding.watchPerformance` reports,
/// whose `frame_build_times` and `frame_rasterizer_times` are microseconds per
/// frame.
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

/// Trials of one timing with their median and p95. A trial that measured
/// nothing (null) is counted as missing rather than averaged in.
Map<String, Object> spread(List<num?> values) {
  final measured = [
    for (final value in values)
      if (value != null) value.toDouble(),
  ];
  return {
    'trials': measured,
    'median': median(measured),
    'p95': percentile(measured, 95),
    'missing': values.length - measured.length,
  };
}

/// Image files weighed in the disk cache: `sizes` becomes their count, total,
/// median and p95; other fields pass through.
Map<String, Object?> byteStats(Map<String, dynamic> weighed) {
  final sizes = [
    for (final size in weighed['sizes'] as List? ?? const [])
      if (size is num) size.toDouble(),
  ];
  return {
    for (final MapEntry(:key, :value) in weighed.entries)
      if (key != 'sizes') key: value,
    'images': sizes.length,
    'total_bytes': sizes.fold<double>(0, (sum, size) => sum + size),
    'median_bytes': median(sizes),
    'p95_bytes': percentile(sizes, 95),
  };
}

/// Frame statistics for each `<prefix>_trial_<n>` watchPerformance summary.
Map<String, Object> frameTrials(Map<String, dynamic> data, String prefix, {required double refreshRate}) {
  final trials = [
    for (var trial = 1; data['${prefix}_trial_$trial'] is Map; trial++)
      trialStats(Map<String, dynamic>.from(data['${prefix}_trial_$trial'] as Map), refreshRate: refreshRate),
  ];
  return {'trials': trials, 'median': medianOfTrials(trials)};
}

/// The report docs/perf-baseline.md reads, from what the test sent.
Map<String, Object?> booruSummary(Map<String, dynamic> data) {
  final env = Map<String, dynamic>.from(data['env'] as Map? ?? const {});
  final refreshRate = (env['refresh_rate'] as num?)?.toDouble() ?? 60;
  List<num?> numbered(String prefix) => [
    for (var trial = 1; data['$prefix$trial'] is num; trial++) data['$prefix$trial'] as num,
  ];
  List<num?> listed(String key) => [for (final value in data[key] as List? ?? const []) value as num?];
  Map<String, dynamic> map(String key) => Map<String, dynamic>.from(data[key] as Map? ?? const {});
  return {
    'env': env,
    's1_main_to_navigator_ms': data['s1_main_to_navigator_ms'],
    's1_open_to_thumbnail_ms': spread(numbered('s1_open_ms_trial_')),
    's2_grid_fling': frameTrials(data, 's2', refreshRate: refreshRate),
    's2_rss_mb_after_trial': numbered('s2_rss_mb_trial_'),
    's3_viewer_swipes': frameTrials(data, 's3', refreshRate: refreshRate),
    's3_open_to_picture_ms': spread(numbered('s3_open_ms_trial_')),
    's4_keystroke_to_suggestions_ms': {
      'first': spread(listed('s4_first_ms')),
      'repeat': spread(listed('s4_repeat_ms')),
    },
    's5_videos': {...map('s5_videos'), 'players_after_memory_pressure': data['s5_players_after_memory_pressure']},
    's6_memory': {'after_s2_s3': map('s6_after_s2_s3'), 'after_memory_pressure': map('s6_after_memory_pressure')},
    's7_grid_screenful': byteStats(map('s7_grid_screenful')),
    's7_viewer_post': byteStats(map('s7_viewer_posts')),
  };
}
