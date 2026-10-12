import 'package:flutter_test/flutter_test.dart';

import '../test_driver/booru_perf_stats.dart';

void main() {
  test('nearest-rank percentiles', () {
    final values = [for (var i = 1; i <= 100; i++) i.toDouble()];
    expect(percentile(values, 50), 50);
    expect(percentile(values, 95), 95);
    expect(percentile(values, 100), 100);
    expect(percentile([7], 95), 7);
    expect(percentile(const [], 95), 0);
  });

  test('a frame is janky when build or raster misses the refresh budget', () {
    final summary = {
      // Microseconds, as watchPerformance reports them.
      'frame_build_times': [4000, 9000, 3000, 2000],
      'frame_rasterizer_times': [3000, 3000, 12000, 2000],
    };
    final at120 = trialStats(summary, refreshRate: 120);
    expect(at120['frames'], 4);
    expect(at120['budget_ms'], closeTo(8.333, 0.001));
    expect(at120['janky_pct'], 50, reason: 'frame 2 builds slowly, frame 3 rasters slowly');

    final at60 = trialStats(summary, refreshRate: 60);
    expect(at60['janky_pct'], 0);
  });

  test('an empty or missing summary reads as no frames', () {
    final stats = trialStats(const {}, refreshRate: 90);
    expect(stats['frames'], 0);
    expect(stats['janky_pct'], 0);
  });

  test('the median of each statistic across trials', () {
    final median = medianOfTrials([
      {'janky_pct': 1, 'build_p95_ms': 6},
      {'janky_pct': 3, 'build_p95_ms': 4},
      {'janky_pct': 2, 'build_p95_ms': 9},
    ]);
    expect(median, {'janky_pct': 2, 'build_p95_ms': 6});
  });
}
