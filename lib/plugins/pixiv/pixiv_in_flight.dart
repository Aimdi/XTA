import 'dart:async';

/// Loads and writes that can land after the sheet or screen that started them
/// has closed. The stores they write to are destroyed only once all of them
/// have landed, since a store written after it is destroyed throws.
class PixivInFlight {
  final _work = <Future<void>>{};

  /// Tracks [work] until it lands and hands it back.
  Future<T> track<T>(Future<T> work) {
    final settled = work.then<void>((_) {}, onError: (_) {});
    _work.add(settled);
    unawaited(settled.whenComplete(() => _work.remove(settled)));
    return work;
  }

  /// Runs [then] once everything tracked so far has landed.
  Future<void> whenSettled(FutureOr<void> Function() then) => Future.wait(_work.toList()).then((_) => then());
}
