import 'dart:async';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:uuid/uuid.dart';
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/downloads/download_repository.dart';
import 'package:xta/downloads/download_transfer.dart';

class DownloadCenterState {
  final List<DownloadEntry> entries;
  final bool ready;
  final bool storageError;
  const DownloadCenterState({this.entries = const [], this.ready = false, this.storageError = false});
}

class DownloadStore extends Store<DownloadCenterState> {
  static final shared = DownloadStore();
  final DownloadHistory history;
  final DownloadRunner runner;
  final Future<void> Function()? cleanup;
  Future<void>? _initializing;
  bool _loading = false;
  bool _historyLoaded = false;
  final _attempts = <String, int>{};
  Future<void> _writes = Future.value();
  final _completion = <String, Completer<DownloadEntry>>{};
  final _cancellation = <String, DownloadCancellation>{};
  bool _running = false;
  bool _closed = false;
  DateTime _lastProgress = DateTime.fromMillisecondsSinceEpoch(0);

  DownloadStore({DownloadHistory? history, DownloadRunner? runner, Future<void> Function()? cleanup})
      : history = history ?? FileDownloadHistory(), runner = runner ?? DownloadTransfer().call,
        cleanup = cleanup ?? (runner == null ? DownloadTransfer.clearInterruptedFiles : null),
        super(const DownloadCenterState());

  Future<void> initialize() => _initializing ??= _load();

  Future<void> _load() async {
    _loading = true;
    try {
      final entries = await history.read();
      try { await cleanup?.call(); } catch (_) {}
      _historyLoaded = true;
      if (!_closed) update(DownloadCenterState(entries: entries, ready: true));
    } catch (_) {
      if (!_closed) update(const DownloadCenterState(ready: true, storageError: true));
    } finally {
      _loading = false;
    }
  }

  Future<void> retryHistory() async {
    if (_closed) return;
    if (_historyLoaded) {
      await _persistQuietly();
    } else if (_loading) {
      await initialize();
    } else {
      _initializing = null;
      await initialize();
    }
  }

  Future<DownloadEntry> enqueue({required Uri uri, required String fileName, String? treeUri}) async {
    await initialize();
    if (_closed) throw StateError('Downloads store is closed');
    if (!_historyLoaded) await retryHistory();
    if (!_historyLoaded) throw StateError('Download history could not be read');
    if (!['http', 'https'].contains(uri.scheme) || uri.host.isEmpty || uri.toString().length > 16000) {
      throw ArgumentError.value(uri, 'uri', 'Expected a web media URL');
    }
    if (state.entries.where((entry) => entry.active).length >= 100) throw StateError('Download queue is full');
    final entry = DownloadEntry(id: const Uuid().v4(), uri: uri, fileName: safeDownloadName(fileName),
      treeUri: treeUri == null || treeUri.isEmpty ? null : treeUri, createdAt: DateTime.now());
    final completion = Completer<DownloadEntry>();
    _completion[entry.id] = completion;
    _publish([entry, ...state.entries]);
    try {
      await _persist();
      unawaited(_pump());
    } catch (_) {
      _finish(entry.copyWith(status: DownloadStatus.failed));
    }
    return completion.future;
  }

  Future<DownloadBatchResult> enqueueBatch(List<DownloadRequest> requests) async {
    final completed = await Future.wait(requests.map((request) async {
      try {
        final entry = await enqueue(uri: request.uri, fileName: request.fileName, treeUri: request.treeUri);
        return entry.status == DownloadStatus.completed;
      } catch (_) { return false; }
    }));
    return DownloadBatchResult(saved: completed.where((saved) => saved).length, total: requests.length);
  }

  Future<void> flush() => _writes;

  Future<void> retry(String id) async {
    await initialize();
    final entry = _find(id);
    if (_closed || entry == null || !entry.canRetry) return;
    _attempts[id] = (_attempts[id] ?? 0) + 1;
    _replace(entry.copyWith(status: DownloadStatus.queued, reset: true));
    try {
      await _persist();
      unawaited(_pump());
    } catch (_) {
      _replace(entry.copyWith(status: DownloadStatus.failed));
    }
  }

  Future<void> cancel(String id) async {
    final entry = _find(id);
    if (_closed || entry == null || !entry.canCancel) return;
    _cancellation[id]?.cancel();
    _finish(entry.copyWith(status: DownloadStatus.cancelled));
    await _persistQuietly();
  }

  Future<void> clearFinished() async {
    _publish(state.entries.where((entry) => entry.active || entry.canRetry).toList());
    await _persistQuietly();
  }

  Future<void> _pump() async {
    if (_running || _closed) return;
    _running = true;
    try {
      while (!_closed) {
        final queued = state.entries.reversed.where((entry) => entry.status == DownloadStatus.queued);
        if (queued.isEmpty) break;
        await _run(queued.first);
      }
    } finally {
      _running = false;
    }
  }

  Future<void> _run(DownloadEntry entry) async {
    final token = DownloadCancellation();
    final attempt = _attempts[entry.id] ?? 0;
    bool currentAttempt() => (_attempts[entry.id] ?? 0) == attempt;
    _cancellation[entry.id] = token;
    _replace(entry.copyWith(status: DownloadStatus.downloading));
    try {
      await _persist();
      token.check();
      final savedUri = await runner(entry, token,
        (received, total) { if (currentAttempt()) _progress(entry.id, received, total); },
        (status) { final current = _find(entry.id); if (current != null && !token.cancelled && currentAttempt()) _replace(current.copyWith(status: status)); });
      token.check();
      if (!currentAttempt()) return;
      final current = _find(entry.id) ?? entry;
      _finish(current.copyWith(status: savedUri == null ? DownloadStatus.cancelled : DownloadStatus.completed, savedUri: savedUri));
    } catch (_) {
      if (!currentAttempt()) return;
      final current = _find(entry.id) ?? entry;
      _finish(current.copyWith(status: token.cancelled ? DownloadStatus.cancelled : DownloadStatus.failed));
    } finally {
      if (identical(_cancellation[entry.id], token)) _cancellation.remove(entry.id);
      await _persistQuietly();
    }
  }

  void _progress(String id, int received, int? total) {
    final current = _find(id);
    if (_closed || current == null || current.status != DownloadStatus.downloading) return;
    final now = DateTime.now();
    if (received != total && now.difference(_lastProgress).inMilliseconds < 120) return;
    _lastProgress = now;
    _replace(current.copyWith(received: received, total: total));
  }

  DownloadEntry? _find(String id) {
    for (final entry in state.entries) { if (entry.id == id) return entry; }
    return null;
  }

  void _replace(DownloadEntry entry) => _publish(state.entries.map((old) => old.id == entry.id ? entry : old).toList());

  void _publish(List<DownloadEntry> entries) {
    if (_closed) return;
    final active = entries.where((entry) => entry.active).toList();
    final finished = entries.where((entry) => !entry.active).take(200 - active.length).toList();
    final retained = {...active.map((entry) => entry.id), ...finished.map((entry) => entry.id)};
    _attempts.removeWhere((id, _) => !retained.contains(id));
    update(DownloadCenterState(entries: List.unmodifiable(entries.where((entry) => retained.contains(entry.id))),
      ready: true, storageError: state.storageError));
  }

  void _finish(DownloadEntry entry) {
    _replace(entry);
    final completion = _completion.remove(entry.id);
    if (completion != null && !completion.isCompleted) completion.complete(entry);
  }

  Future<void> _persist() {
    if (!_historyLoaded) return Future.error(StateError('Cannot overwrite unread download history'));
    final snapshot = state.entries;
    final write = _writes.then((_) async {
      await history.write(snapshot);
      if (!_closed && state.storageError) update(DownloadCenterState(entries: state.entries, ready: true));
    });
    _writes = write.catchError((Object _) {
      if (!_closed) update(DownloadCenterState(entries: state.entries, ready: true, storageError: true));
    });
    return write;
  }

  Future<void> _persistQuietly() async { try { await _persist(); } catch (_) {} }

  @override
  Future<void> destroy() async {
    _closed = true;
    for (final token in _cancellation.values) { token.cancel(); }
    for (final entry in state.entries.where((entry) => entry.active)) {
      final completion = _completion.remove(entry.id);
      completion?.complete(entry.copyWith(status: DownloadStatus.interrupted));
    }
    await _writes;
    await super.destroy();
  }
}
