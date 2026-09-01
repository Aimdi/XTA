import 'package:flutter_test/flutter_test.dart';
import 'package:xta/group/future_pool.dart';

void main() {
  test('mapWithConcurrency preserves order', () async {
    final out = await mapWithConcurrency([1, 2, 3, 4, 5], 2, (n) async {
      await Future<void>.delayed(Duration(milliseconds: 6 - n));
      return n * 10;
    });
    expect(out, [10, 20, 30, 40, 50]);
  });

  test('mapWithConcurrency caps in-flight work', () async {
    var inFlight = 0;
    var peak = 0;

    await mapWithConcurrency(List.generate(12, (i) => i), 3, (_) async {
      inFlight++;
      if (inFlight > peak) {
        peak = inFlight;
      }
      await Future<void>.delayed(const Duration(milliseconds: 20));
      inFlight--;
      return null;
    });

    expect(peak, lessThanOrEqualTo(3));
    expect(peak, greaterThan(0));
  });

  test('mapWithConcurrency handles empty input', () async {
    final out = await mapWithConcurrency<int, int>(const [], 4, (n) async => n);
    expect(out, isEmpty);
  });

  test('mapWithConcurrency treats non-positive concurrency as 1', () async {
    var peak = 0;
    var inFlight = 0;
    await mapWithConcurrency([1, 2, 3], 0, (_) async {
      inFlight++;
      peak = inFlight > peak ? inFlight : peak;
      await Future<void>.delayed(const Duration(milliseconds: 5));
      inFlight--;
      return null;
    });
    expect(peak, 1);
  });
}
