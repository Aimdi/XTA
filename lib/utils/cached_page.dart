import 'dart:async';

/// Optional storage must never hold a successful page, or its retry, hostage.
Future<T> loadCachedPage<T>({
  required Future<T?> Function() readFresh,
  required Future<T?> Function() readStale,
  required Future<T> Function() fetch,
  required Future<void> Function(T) write,
  bool bypassCache = false,
  Duration cacheTimeout = const Duration(seconds: 1),
}) async {
  Future<T?> read(Future<T?> Function() action) async {
    try {
      return await Future.sync(action).timeout(cacheTimeout);
    } catch (_) {
      return null;
    }
  }

  if (!bypassCache) {
    final cached = await read(readFresh);
    if (cached != null) return cached;
  }
  try {
    final page = await fetch();
    unawaited(Future.sync(() => write(page)).catchError((Object _) {}));
    return page;
  } catch (_) {
    final stale = await read(readStale);
    if (stale != null) return stale;
    rethrow;
  }
}
