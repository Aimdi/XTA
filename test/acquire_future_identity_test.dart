import 'package:flutter_test/flutter_test.dart';

/// Documents the Dart evaluation order that broke video playback in aimdi108.
///
/// `_acquireFuture ??= _acquire()` assigns `_acquire()`'s *own* future after
/// `_acquire` returns — which is at its first `await`. Anything `_acquire`
/// wrote into `_acquireFuture` before that await is overwritten. Comparing
/// `_acquireFuture` to `pool.acquire`'s inner future then always fails, so
/// the tile released the player, skipped first-frame listeners, and the
/// poster never lifted.
void main() {
  test('async ??= overwrites a field the function set before awaiting', () async {
    Future<String>? held;
    Future<String>? inner;

    Future<String> acquire() async {
      inner = Future.value('inner');
      held = inner;
      await inner;
      return 'outer';
    }

    held ??= acquire();
    expect(await held, 'outer');
    expect(identical(held, inner), isFalse);
  });
}
