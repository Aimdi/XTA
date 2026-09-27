import 'dart:async';
import 'read_activity.dart';
import 'read_retry.dart';

/// Cooperative cancellation carried across asynchronous application stages.
/// It never closes shared network or database clients.
class ReadWork {
  static final _zoneKey = Object();
  static ReadWork? get current => Zone.current[_zoneKey] as ReadWork?;
  final ReadWork? parent;
  bool _cancelled = false;
  final _onCancel = <void Function()>{};
  ReadWork({this.parent});
  bool get cancelled => _cancelled || (parent?.cancelled ?? false);
  void cancel() {
    if (_cancelled) return;
    _cancelled = true;
    for (final callback in _onCancel.toList()) {
      callback();
    }
    _onCancel.clear();
  }

  void Function() onCancel(void Function() callback) {
    if (cancelled) {
      callback();
      return () {};
    }
    _onCancel.add(callback);
    final removeParent = parent?.onCancel(callback);
    return () {
      _onCancel.remove(callback);
      removeParent?.call();
    };
  }

  Future<void> pause(Duration duration) {
    check();
    final done = Completer<void>();
    final timer = Timer(duration, done.complete);
    final remove = onCancel(() {
      timer.cancel();
      if (!done.isCompleted) done.completeError(const ReadCancelled());
    });
    return done.future.whenComplete(remove);
  }

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
    final elapsed = Stopwatch()..start();
    final retry = operation != ReadOperation.cache && operation != ReadOperation.snapshot;
    return _wait(
      work.start(
        () => retry
            ? retryRead(source, checkpoint: work.check, pause: work.pause, remaining: () => timeout - elapsed.elapsed)
            : source(),
      ),
      timeout: timeout,
      work: work,
      operation: operation,
    );
  }

  Future<T> run<T>(Future<T> source, {required Duration timeout, ReadOperation operation = ReadOperation.page}) =>
      _wait(source, timeout: timeout, operation: operation);

  Future<T> _wait<T>(Future<T> source, {required Duration timeout, ReadWork? work, required ReadOperation operation}) {
    final activity = ReadActivityLog.shared.begin(operation);
    final result = Completer<T>();
    late Timer timer;
    late void Function() cancel;
    void Function()? removeCancellation;
    void finish(ReadOutcome outcome) {
      timer.cancel();
      _cancel.remove(cancel);
      removeCancellation?.call();
      activity.finish(outcome);
    }

    void fail(Object error, [StackTrace? stack]) {
      if (result.isCompleted) return;
      finish(
        error is ReadCancelled
            ? ReadOutcome.cancelled
            : error is TimeoutException
            ? ReadOutcome.timedOut
            : ReadOutcome.failed,
      );
      result.completeError(error, stack);
      work?.cancel();
    }

    cancel = () => fail(const ReadCancelled());
    timer = Timer(timeout, () => fail(TimeoutException('Read deadline exceeded', timeout)));
    _cancel.add(cancel);
    removeCancellation = work?.onCancel(cancel);
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
