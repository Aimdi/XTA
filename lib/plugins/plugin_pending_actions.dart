import 'package:flutter_triple/flutter_triple.dart';

/// The keys whose action is on its way, so a control can show it is busy and
/// a second tap does not run the action twice.
class PluginPendingActions<K> extends Store<Set<K>> {
  var _closed = false;

  PluginPendingActions() : super(<K>{});

  /// Runs [action] for [key] unless one is already running; false when it was
  /// skipped or failed.
  Future<bool> run(K key, Future<void> Function() action) async {
    if (_closed || state.contains(key)) return false;
    update({...state, key});
    try {
      await action();
      return true;
    } catch (_) {
      return false;
    } finally {
      if (!_closed) update({...state}..remove(key));
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    return super.destroy();
  }
}
