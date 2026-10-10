/// The [capacity] most recently used values; the least recently used one leaves first.
class PixivLruCache<K, V> {
  final int capacity;
  final _entries = <K, V>{};

  PixivLruCache(this.capacity) : assert(capacity > 0);

  int get length => _entries.length;

  Iterable<K> get keys => _entries.keys;

  V? operator [](K key) {
    final value = _entries.remove(key);
    if (value != null) _entries[key] = value;
    return value;
  }

  void operator []=(K key, V value) {
    _entries
      ..remove(key)
      ..[key] = value;
    while (_entries.length > capacity) {
      _entries.remove(_entries.keys.first);
    }
  }

  /// Drops [key] only while it still holds [value], so a newer entry survives an older one's cleanup.
  void removeIfHolds(K key, V value) {
    if (identical(_entries[key], value)) _entries.remove(key);
  }
}
