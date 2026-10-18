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

  test('a timing without a measurement is missing, not zero', () {
    final timing = spread([120, null, 80, 100]);
    expect(timing['trials'], [120, 80, 100]);
    expect(timing['median'], 100);
    expect(timing['p95'], 120);
    expect(timing['missing'], 1);
  });

  test('disk-cache sizes become count, total, median and p95', () {
    final bytes = byteStats({
      'tiles': 6,
      'sizes': [100, 300, 200],
    });
    expect(bytes['tiles'], 6, reason: 'other fields pass through');
    expect(bytes.containsKey('sizes'), isFalse);
    expect(bytes['images'], 3);
    expect(bytes['total_bytes'], 600);
    expect(bytes['median_bytes'], 200);
    expect(bytes['p95_bytes'], 300);
  });

  test('the summary reads numbered trials and keeps raw S5 and S6 data', () {
    final summary = booruSummary({
      'env': {'refresh_rate': 120},
      's1_open_ms_trial_1': 900,
      's1_open_ms_trial_2': 700,
      's1_open_ms_trial_3': 800,
      's2_trial_1': {
        'frame_build_times': [4000],
        'frame_rasterizer_times': [9000],
      },
      's4_first_ms': [400, null],
      's5_videos': {'opened': 10},
      's5_players_after_memory_pressure': 0,
      's6_after_s2_s3': {'rss_mb': 300},
    });
    expect((summary['s1_open_to_thumbnail_ms'] as Map)['median'], 800);
    final grid = summary['s2_grid_fling'] as Map;
    expect((grid['median'] as Map)['janky_pct'], 100, reason: 'a 9 ms raster misses the 8.3 ms budget');
    expect(((summary['s4_keystroke_to_suggestions_ms'] as Map)['first'] as Map)['missing'], 1);
    expect(summary['s5_videos'], {'opened': 10, 'players_after_memory_pressure': 0});
    expect((summary['s6_memory'] as Map)['after_s2_s3'], {'rss_mb': 300});
    expect((summary['s7_grid_screenful'] as Map)['images'], 0, reason: 'absent data reads as nothing weighed');
  });
}
