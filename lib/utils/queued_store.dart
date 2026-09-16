import 'package:flutter_triple/flutter_triple.dart';

/// Local writes must finish in order; Store.execute cancels earlier work.
mixin QueuedStore<State> on Store<State> {
  Future<void>? _pending;

  Duration get snapshotTimeout => const Duration(seconds: 15);

  /// Bound the read after a write, not the write itself. Letting a timed-out
  /// mutation continue behind a newer mutation would break their ordering.
  Future<State> readSnapshot(Future<State> Function() read) => Future.sync(read).timeout(snapshotTimeout);

  // flutter_triple's error selector retains the old error after update().
  @override
  dynamic get error => triple.error;

  Future<bool> executeQueued(Future<State> Function() action) {
    final result = (_pending ?? Future<void>.value()).then((_) async {
      setLoading(true);
      try {
        final next = await action();
        update(next, force: true);
        return true;
      } catch (error) {
        setError(error, force: true);
        return false;
      } finally {
        setLoading(false, force: true);
      }
    });
    final pending = result.then((_) {});
    _pending = pending;
    pending.then((_) {
      if (identical(_pending, pending)) _pending = null;
    });
    return result;
  }

  @override
  Future destroy() async {
    final pending = _pending;
    if (pending != null) await pending;
    await super.destroy();
  }
}
