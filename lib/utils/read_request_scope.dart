import 'dart:async';

/// Deadlines for reads owned by a screen. Cancelling only detaches the waiter;
/// it does not close shared HTTP or database clients used by other screens.
class ReadRequestScope {
  final _cancel = <void Function()>{};

  Future<T> run<T>(Future<T> source, {required Duration timeout}) {
    final result = Completer<T>();
    late Timer timer;
    late void Function() cancel;
    void finish() {
      timer.cancel();
      _cancel.remove(cancel);
    }

    void fail(Object error, [StackTrace? stack]) {
      if (result.isCompleted) return;
      finish();
      result.completeError(error, stack);
    }

    cancel = () => fail(const ReadCancelled());
    timer = Timer(timeout, () => fail(TimeoutException('Read deadline exceeded', timeout)));
    _cancel.add(cancel);
    source.then((value) {
      if (result.isCompleted) return;
      finish();
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
