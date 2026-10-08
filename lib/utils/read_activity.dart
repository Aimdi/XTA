enum ReadOperation { page, profile, cache, groupBatch, groupSearch, groupFallback, groupGap, snapshot }

enum ReadOutcome { pending, completed, failed, timedOut, cancelled }

class ReadActivity {
  final ReadOperation operation;
  final DateTime startedAt;
  final Stopwatch _watch = Stopwatch()..start();
  ReadOutcome outcome = ReadOutcome.pending;
  ReadActivity(this.operation) : startedAt = DateTime.now();
  Duration get elapsed => _watch.elapsed;
  void finish(ReadOutcome result) {
    if (outcome != ReadOutcome.pending) return;
    outcome = result;
    _watch.stop();
  }

  String describe() => '${startedAt.toIso8601String()} ${operation.name} ${outcome.name} ${elapsed.inMilliseconds}ms';
}

/// Process-local metadata only. No user content or exception text is retained.
class ReadActivityLog {
  static final shared = ReadActivityLog();
  final int capacity;
  final _entries = <ReadActivity>[];
  ReadActivityLog({this.capacity = 60});
  ReadActivity begin(ReadOperation operation) {
    final entry = ReadActivity(operation);
    if (capacity > 0) {
      _entries.add(entry);
      if (_entries.length > capacity) _entries.removeAt(0);
    }
    return entry;
  }

  List<String> snapshot() => _entries.map((e) => e.describe()).toList(growable: false);
}
