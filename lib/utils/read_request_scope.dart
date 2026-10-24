import 'dart:async';
import 'package:xta/ui/read_failure_kind.dart';
import 'read_activity.dart';

/// Cooperative cancellation carried across asynchronous application stages.
/// It never closes shared network or database clients.
class ReadWork {
  static final _zoneKey = Object();
  static ReadWork? get current => Zone.current[_zoneKey] as ReadWork?;
  final ReadWork? parent;
  bool _cancelled = false;
  ReadWork({this.parent});
  bool get cancelled => _cancelled || (parent?.cancelled ?? false);
  void cancel() => _cancelled = true;
  void check() {
    if (cancelled) throw const ReadCancelled();
  }

  static void checkpoint() => current?.check();
  Future<T> start<T>(Future<T> Function() work) => runZoned(
    () => Future.sync(() {
      check();
      return work();
    }),
    zoneValues: {_zoneKey: this},
  );
}

class ReadRequestScope {
  final _cancel = <void Function()>{};

  Future<T> start<T>(
    Future<T> Function() source, {
    required Duration timeout,
    ReadOperation operation = ReadOperation.page,
  }) {
    final work = ReadWork(parent: ReadWork.current);
    return _wait(work.start(source), timeout: timeout, work: work, operation: operation);
  }

  Future<T> run<T>(Future<T> source, {required Duration timeout, ReadOperation operation = ReadOperation.page}) =>
      _wait(source, timeout: timeout, operation: operation);

  Future<T> _wait<T>(Future<T> source, {required Duration timeout, ReadWork? work, required ReadOperation operation}) {
    final activity = ReadActivityLog.shared.begin(operation);
    final result = Completer<T>();
    late Timer timer;
    late void Function() cancel;
    void finish(ReadOutcome outcome, {String? failure}) {
      timer.cancel();
      _cancel.remove(cancel);
      activity.finish(outcome, failure: failure);
    }

    void fail(Object error, [StackTrace? stack]) {
      if (result.isCompleted) return;
      work?.cancel();
      if (error is ReadCancelled) {
        finish(ReadOutcome.cancelled);
      } else if (error is TimeoutException) {
        finish(ReadOutcome.timedOut);
      } else {
        finish(ReadOutcome.failed, failure: readFailureLabel(error));
      }
      result.completeError(error, stack);
    }

    cancel = () => fail(const ReadCancelled());
    timer = Timer(timeout, () => fail(TimeoutException('Read deadline exceeded', timeout)));
    _cancel.add(cancel);
    source.then((value) {
      if (result.isCompleted) return;
      finish(ReadOutcome.completed);
      result.complete(value);
    }, onError: fail);
    return result.future;
  }

  void cancel() {
    for (final cancel in _cancel.toList()) {
      cancel();
    }
  }
}

class ReadCancelled implements Exception {
  const ReadCancelled();
}
