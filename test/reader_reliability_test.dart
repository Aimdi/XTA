import 'dart:async';
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/group/batch_read_store.dart';
import 'package:xta/group/future_pool.dart';
import 'package:xta/ui/reader_failure.dart';
import 'package:xta/utils/read_activity.dart';
import 'package:xta/utils/read_recovery.dart';
import 'package:xta/utils/read_request_scope.dart';

void main() {
  test('completed batch is published before a slower batch finishes', () async {
    final store = BatchReadStore<int>();
    addTearDown(store.destroy);
    final slow = Completer<int>();
    final load = store.load(
      ['fast', 'slow'],
      (key) => key == 'slow' ? slow.future : Future.value(1),
      onError: (_) => -1,
      failed: (value) => value < 0,
    );
    await Future<void>.delayed(Duration.zero);
    expect(store.state.results, {'fast': 1});
    expect(store.state.loading, isTrue);
    slow.complete(2);
    expect(await load, [1, 2]);
    expect(store.state.loading, isFalse);
  });

  test('selective retry does not refetch successful batches', () async {
    final store = BatchReadStore<int>();
    addTearDown(store.destroy);
    final calls = <String>[];
    var failing = true;
    Future<int> fetch(String key) async {
      calls.add(key);
      if (key == 'bad' && failing) throw const SocketException('offline');
      return key == 'good' ? 1 : 2;
    }

    await store.load(['good', 'bad'], fetch, onError: (_) => -1, failed: (value) => value < 0);
    failing = false;
    await store.load(['good', 'bad'], fetch, onError: (_) => -1, failed: (value) => value < 0, retryFailed: true);
    expect(calls, ['good', 'bad', 'bad']);
    expect(store.state.failed, isEmpty);
    expect(store.state.results, {'good': 1, 'bad': 2});
  });

  test('abandoned batch cannot publish or start queued work', () async {
    final store = BatchReadStore<int>();
    addTearDown(store.destroy);
    final first = Completer<int>();
    final started = <String>[];
    final load = store.load(
      ['one', 'two', 'three'],
      (key) {
        started.add(key);
        return first.future;
      },
      concurrency: 1,
      onError: (_) => -1,
      failed: (value) => value < 0,
    );
    final assertion = expectLater(load, throwsA(isA<ReadCancelled>()));
    await Future<void>.delayed(Duration.zero);
    store.cancel();
    await assertion;
    first.complete(1);
    await Future<void>.delayed(Duration.zero);
    expect(started, ['one']);
    expect(store.state.results, isEmpty);
  });

  test('timed out batch releases its slot and ignores late completion', () async {
    final store = BatchReadStore<int>(batchTimeout: const Duration(milliseconds: 10));
    addTearDown(store.destroy);
    final late = Completer<int>();
    await store.load(
      ['late', 'next'],
      (key) => key == 'late' ? late.future : Future.value(2),
      concurrency: 1,
      onError: (_) => -1,
      failed: (value) => value < 0,
    );
    expect(store.state.results, {'late': -1, 'next': 2});
    late.complete(100);
    await Future<void>.delayed(Duration.zero);
    expect(store.state.results['late'], -1);
  });

  test('read cancellation prevents subsequent fanout after late completion', () async {
    final reads = ReadRequestScope();
    final pending = Completer<int>();
    final started = <int>[];
    final load = reads.start(
      () => mapWithConcurrency([1, 2, 3], 1, (value) {
        started.add(value);
        return pending.future;
      }),
      timeout: const Duration(seconds: 5),
    );
    final assertion = expectLater(load, throwsA(isA<ReadCancelled>()));
    reads.cancel();
    await assertion;
    pending.complete(1);
    await Future<void>.delayed(Duration.zero);
    expect(started, [1]);
  });

  test('recovery excludes non-network failures and coalesces repeated signals', () {
    final gate = RecoveryGate();
    final error = const SocketException('offline');
    final now = DateTime(2026);
    expect(recoverableReadFailure(StateError('bad data')), isNull);
    expect(gate.take(recoverableReadFailure(error), now, event: 1), isTrue);
    expect(gate.take(error, now.add(const Duration(seconds: 1)), event: 1), isFalse);
    expect(gate.take(TimeoutException('slow'), now.add(const Duration(seconds: 2)), event: 2), isFalse);
    expect(gate.take(TimeoutException('slow'), now.add(const Duration(seconds: 11)), event: 2), isTrue);
    expect(gate.take(TimeoutException('new failure'), now.add(const Duration(seconds: 50)), event: 2), isFalse);
  });

  test('diagnostic timings are bounded and do not retain exception messages', () async {
    final log = ReadActivityLog(capacity: 2);
    log.begin(ReadOperation.cache).finish(ReadOutcome.completed);
    log.begin(ReadOperation.profile).finish(ReadOutcome.timedOut);
    log.begin(ReadOperation.page).finish(ReadOutcome.cancelled);
    expect(log.snapshot(), hasLength(2));
    expect(log.snapshot().join(), contains('timedOut'));
    final reads = ReadRequestScope();
    await expectLater(
      reads.start(() async => throw Exception('secret-token-and-content'), timeout: const Duration(seconds: 1)),
      throwsException,
    );
    expect(ReadActivityLog.shared.snapshot().join(), isNot(contains('secret-token-and-content')));
  });
}
