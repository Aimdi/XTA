import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/booru/booru_request_gate.dart';

/// A clock that moves only when the gate waits, so every wait is recorded
/// and none takes real time.
class _Clock {
  var now = DateTime.utc(2026, 10, 13, 12);
  final waits = <Duration>[];

  Future<void> wait(Duration duration) async {
    waits.add(duration);
    now = now.add(duration);
  }
}

BooruRequestGate _gate(_Clock clock, {int perSecond = 10, Duration maxPause = const Duration(seconds: 32)}) =>
    BooruRequestGate(perSecond: perSecond, maxPause: maxPause, now: () => clock.now, wait: clock.wait);

void main() {
  test('the eleventh request in a second waits for the window to move on', () async {
    final clock = _Clock();
    final gate = _gate(clock);
    for (var i = 0; i < 10; i++) {
      await gate.admit();
    }
    expect(clock.waits, isEmpty);

    await gate.admit();

    expect(clock.waits, [const Duration(seconds: 1)]);
  });

  test('requests wait their turn in the order they asked', () async {
    final clock = _Clock();
    final gate = _gate(clock, perSecond: 1);
    final order = <int>[];

    await Future.wait([for (var i = 0; i < 3; i++) gate.admit().then((_) => order.add(i))]);

    expect(order, [0, 1, 2]);
    expect(clock.waits, [const Duration(seconds: 1), const Duration(seconds: 1)]);
  });

  test('a 429 or 5xx pauses the host, longer each time in a row, until an answer succeeds', () async {
    final clock = _Clock();
    final gate = _gate(clock);

    for (final status in [429, 503, 500]) {
      gate.answered(status);
      await gate.admit();
    }
    gate.answered(200);
    gate.answered(429);
    await gate.admit();

    expect(clock.waits, const [Duration(seconds: 1), Duration(seconds: 2), Duration(seconds: 4), Duration(seconds: 1)]);
  });

  test('the pause stops growing at its cap, and Retry-After overrides it', () async {
    final clock = _Clock();
    final gate = _gate(clock, maxPause: const Duration(seconds: 4));
    for (var i = 0; i < 5; i++) {
      gate.answered(429);
    }
    await gate.admit();
    gate.answered(429, retryAfter: const Duration(seconds: 30));
    await gate.admit();

    expect(clock.waits, const [Duration(seconds: 4), Duration(seconds: 30)]);
  });

  test('other refusals do not pause the host', () async {
    final clock = _Clock();
    final gate = _gate(clock);
    for (final status in [401, 403, 404, 410, 422]) {
      gate.answered(status);
      await gate.admit();
    }
    expect(clock.waits, isEmpty);
  });

  test('Retry-After in seconds is read; a date is left to the doubling pause', () {
    expect(booruRetryAfter('7'), const Duration(seconds: 7));
    expect(booruRetryAfter(' 0 '), Duration.zero);
    expect(booruRetryAfter('Wed, 21 Oct 2026 07:28:00 GMT'), isNull);
    expect(booruRetryAfter(null), isNull);
  });
}
