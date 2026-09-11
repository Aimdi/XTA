import 'package:flutter_triple/flutter_triple.dart';

/// Local writes must finish in order; Store.execute cancels earlier work.
mixin QueuedStore<State> on Store<State> {
  Future<void> _pending = Future.value();

  Future<bool> executeQueued(Future<State> Function() action) {
    final result = _pending.then((_) async {
      setLoading(true);
      try {
        final next = await action();
        setError(null);
        update(next, force: true);
        return true;
      } catch (error) {
        setError(error, force: true);
        return false;
      } finally {
        setLoading(false, force: true);
      }
    });
    _pending = result.then((_) {});
    return result;
  }

  @override
  Future destroy() async {
    await _pending;
    await super.destroy();
  }
}
