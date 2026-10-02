enum ReadOperation { page, profile, cache, groupBatch, groupSearch, groupFallback, groupGap, snapshot, db }

enum ReadOutcome { pending, completed, failed, timedOut, cancelled }

class ReadActivity {
  final ReadOperation operation;
  final String? label;
  final DateTime startedAt;
  final Stopwatch _watch = Stopwatch()..start();
  ReadOutcome outcome = ReadOutcome.pending;
  ReadActivity(this.operation, {this.label}) : startedAt = DateTime.now();
  Duration get elapsed => _watch.elapsed;
  void finish(ReadOutcome result) {
    if (outcome != ReadOutcome.pending) return;
    outcome = result;
    _watch.stop();
  }

  String describe() =>
      '${startedAt.toIso8601String()} ${operation.name} ${outcome.name} ${elapsed.inMilliseconds}ms'
      '${label == null ? '' : ' $label'}';
}

/// Process-local metadata only. No user content or exception text is retained.
class ReadActivityLog {
  static final shared = ReadActivityLog();
  final int capacity;
  final _entries = <ReadActivity>[];
  ReadActivityLog({this.capacity = 100});
  ReadActivity begin(ReadOperation operation, {String? label}) {
    final entry = ReadActivity(operation, label: label);
    if (capacity > 0) {
      _entries.add(entry);
      if (_entries.length > capacity) _entries.removeAt(0);
    }
    return entry;
  }

  /// Statements short enough to be uninteresting leave the log again, so a hung or slow one stands out.
  static const traceThreshold = Duration(milliseconds: 100);

  /// Times one local step under [label]. While it runs it is visible as pending; once done it stays only when
  /// it took at least [traceThreshold] or failed.
  Future<T> trace<T>(String label, Future<T> Function() step) async {
    final entry = begin(ReadOperation.db, label: label);
    try {
      final value = await step();
      entry.finish(ReadOutcome.completed);
      if (entry.elapsed < traceThreshold) _entries.remove(entry);
      return value;
    } catch (_) {
      entry.finish(ReadOutcome.failed);
      rethrow;
    }
  }

  List<String> snapshot() => _entries.map((e) => e.describe()).toList(growable: false);
}
