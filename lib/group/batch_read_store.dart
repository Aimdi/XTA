import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/group/future_pool.dart';
import 'package:xta/utils/read_activity.dart';
import 'package:xta/utils/read_request_scope.dart';

class BatchReadState<T> {
  final Map<String, T> results;
  final Set<String> failed;
  final int total;
  final bool loading;
  const BatchReadState({this.results = const {}, this.failed = const {}, this.total = 0, this.loading = false});
}

/// Successful batches survive a selective retry; stale completions are ignored.
class BatchReadStore<T> extends Store<BatchReadState<T>> {
  final _reads = ReadRequestScope();
  final Duration batchTimeout;
  int _generation = 0;
  bool _closed = false;
  BatchReadStore({this.batchTimeout = const Duration(seconds: 35)}) : super(BatchReadState<T>());

  void cancel() {
    _generation++;
    _reads.cancel();
    if (!_closed && state.loading)
      update(BatchReadState(results: state.results, failed: state.failed, total: state.total), force: true);
  }

  void reset() {
    cancel();
    if (!_closed) update(BatchReadState<T>(), force: true);
  }

  Future<List<T>> load(
    Iterable<String> keys,
    Future<T> Function(String) fetch, {
    required T Function(Object) onError,
    required bool Function(T) failed,
    void Function(List<T>)? onProgress,
    bool retryFailed = false,
    int concurrency = 3,
  }) async {
    cancel();
    final generation = _generation;
    final all = keys.toSet();
    final results = retryFailed
        ? (Map<String, T>.of(state.results)..removeWhere((key, _) => !all.contains(key)))
        : <String, T>{};
    final errors = retryFailed ? state.failed.intersection(all) : <String>{};
    final pending = all.where((key) => !results.containsKey(key) || errors.contains(key)).toList();
    bool current() => !_closed && generation == _generation;
    void publish(bool loading) {
      if (!current()) return;
      update(
        BatchReadState(
          results: Map.unmodifiable(results),
          failed: Set.unmodifiable(errors),
          total: all.length,
          loading: loading,
        ),
        force: true,
      );
    }

    publish(true);
    try {
      await _reads.start(
        () => mapWithConcurrency(pending, concurrency, (key) async {
          T value;
          try {
            value = await _reads.start(() => fetch(key), timeout: batchTimeout, operation: ReadOperation.groupBatch);
          } on ReadCancelled {
            rethrow;
          } catch (error) {
            value = onError(error);
          }
          ReadWork.checkpoint();
          if (!current()) throw const ReadCancelled();
          results[key] = value;
          failed(value) ? errors.add(key) : errors.remove(key);
          onProgress?.call(results.values.toList());
          publish(true);
        }),
        timeout: const Duration(minutes: 2),
      );
      return results.values.toList();
    } finally {
      publish(false);
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    cancel();
    return super.destroy();
  }
}
