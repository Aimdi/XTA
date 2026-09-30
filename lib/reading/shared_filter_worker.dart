import 'dart:async';
import 'dart:isolate';

class SharedFilterPattern {
  final String pattern;
  final bool caseSensitive;
  const SharedFilterPattern(this.pattern, this.caseSensitive);
}

/// A pattern check that did not finish in time, or a worker that could not run it.
class SharedFilterWorkerFailure implements Exception {
  final String reason;
  const SharedFilterWorkerFailure(this.reason);
  @override
  String toString() => 'SharedFilterWorkerFailure($reason)';
}

/// Checks patterns against texts somewhere a runaway pattern cannot freeze the reader.
abstract interface class SharedFilterRegexEvaluator {
  /// For each text, the indexes of the [patterns] it matches.
  Future<List<List<int>>> evaluate(List<SharedFilterPattern> patterns, List<String> texts);
  Future<void> close();
}

/// One owned background isolate. A batch that misses [deadline] kills it; the next batch starts a fresh one.
class IsolateRegexEvaluator implements SharedFilterRegexEvaluator {
  final Duration deadline;
  final Duration startup;
  Isolate? _isolate;
  SendPort? _send;
  ReceivePort? _receive;
  Future<void>? _starting;
  int _next = 0;
  final _waiting = <int, Completer<List<List<int>>>>{};

  IsolateRegexEvaluator({this.deadline = const Duration(milliseconds: 250), this.startup = const Duration(seconds: 2)});

  Future<void> _start() async {
    final receive = ReceivePort();
    final ready = Completer<SendPort>();
    _receive = receive;
    receive.listen((message) {
      if (message is SendPort) {
        if (!ready.isCompleted) ready.complete(message);
      } else if (message is List && message.length == 2 && message.first is int) {
        _answer(message.first as int, message.last);
      }
    });
    try {
      _isolate = await Isolate.spawn(_regexWorker, receive.sendPort, errorsAreFatal: false).timeout(startup);
      _send = await ready.future.timeout(startup);
    } catch (_) {
      _stop();
      throw const SharedFilterWorkerFailure('startup');
    }
  }

  void _answer(int id, Object? result) {
    final waiting = _waiting.remove(id);
    if (waiting == null || waiting.isCompleted) return;
    if (result is List) {
      waiting.complete([for (final row in result) (row as List).cast<int>()]);
    } else {
      waiting.completeError(const SharedFilterWorkerFailure('pattern'));
    }
  }

  @override
  Future<List<List<int>>> evaluate(List<SharedFilterPattern> patterns, List<String> texts) async {
    await (_starting ??= _start());
    final send = _send;
    if (send == null) throw const SharedFilterWorkerFailure('stopped');
    final id = ++_next;
    final waiting = Completer<List<List<int>>>();
    _waiting[id] = waiting;
    send.send([
      id,
      [
        for (final pattern in patterns) [pattern.pattern, pattern.caseSensitive],
      ],
      texts,
    ]);
    try {
      return await waiting.future.timeout(deadline);
    } on TimeoutException {
      // A backtracking pattern cannot be interrupted, only abandoned with its isolate.
      _stop();
      throw const SharedFilterWorkerFailure('deadline');
    }
  }

  void _stop() {
    _isolate?.kill(priority: Isolate.immediate);
    _receive?.close();
    for (final waiting in _waiting.values) {
      if (!waiting.isCompleted) waiting.completeError(const SharedFilterWorkerFailure('stopped'));
    }
    _waiting.clear();
    _isolate = null;
    _send = null;
    _receive = null;
    _starting = null;
  }

  @override
  Future<void> close() async => _stop();
}

void _regexWorker(SendPort reply) {
  final inbox = ReceivePort();
  final compiled = <String, RegExp>{};
  reply.send(inbox.sendPort);
  inbox.listen((message) {
    final request = message as List;
    final id = request[0] as int;
    try {
      final patterns = [
        for (final row in request[1] as List)
          compiled.putIfAbsent(
            '${row[1]}:${row[0]}',
            () => RegExp(row[0] as String, caseSensitive: row[1] as bool, unicode: true),
          ),
      ];
      reply.send([
        id,
        [
          for (final text in (request[2] as List).cast<String>())
            [
              for (var index = 0; index < patterns.length; index++)
                if (patterns[index].hasMatch(text)) index,
            ],
        ],
      ]);
    } catch (_) {
      reply.send([id, 'failed']);
    }
  });
}
