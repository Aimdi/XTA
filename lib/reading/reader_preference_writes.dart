import 'package:pref/pref.dart';

class _PreferenceQueue {
  Future<void>? tail;
}

/// One queue per preference service, shared by the reader tools.
class ReaderPreferenceWrites {
  static final _queues = Expando<_PreferenceQueue>();

  static Future<T> enqueue<T>(BasePrefService prefs, Future<T> Function() write) {
    final queue = _queues[prefs] ??= _PreferenceQueue();
    final result = (queue.tail ?? Future<void>.value()).then((_) => write());
    final tail = result.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    queue.tail = tail;
    tail.then((_) {
      if (identical(queue.tail, tail)) queue.tail = null;
    });
    return result;
  }

  /// Call inside enqueue: put always attempts persistence, even for an equal cache value.
  /// On failure restore the exact prior cache value/absence best effort; never claim durability.
  static Future<bool> putString(BasePrefService prefs, String key, String value) async {
    Object? previous;
    try {
      prefs.makeSecret(key);
      previous = prefs.get<Object>(key);
    } catch (_) {
      return false;
    }
    try {
      if (await prefs.put(key, value)) return true;
    } catch (_) {
      // SharedPreferences may already have changed its cache before the backend fails.
    }
    try {
      if (previous == null) {
        await prefs.remove(key);
      } else {
        await prefs.put(key, previous);
      }
    } catch (_) {
      // A failed rollback is still a failed primary write, never a successful save.
    }
    return false;
  }

  static bool isPending(BasePrefService prefs) => _queues[prefs]?.tail != null;

  static Future<void> drain(BasePrefService prefs) async {
    while (isPending(prefs)) {
      await _queues[prefs]!.tail;
    }
  }
}
