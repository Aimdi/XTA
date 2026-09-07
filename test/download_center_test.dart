import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:path/path.dart' as p;
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/downloads/download_repository.dart';
import 'package:xta/downloads/download_store.dart';
import 'package:xta/downloads/download_transfer.dart';

DownloadEntry entry(String id, {DownloadStatus status = DownloadStatus.queued}) => DownloadEntry(
  id: id, uri: Uri.parse('https://media.example/$id.jpg'), fileName: '$id.jpg',
  treeUri: 'content://provider/tree/photos', createdAt: DateTime.utc(2026), status: status);

class MemoryHistory implements DownloadHistory {
  List<DownloadEntry> entries;
  bool failWrites = false;
  MemoryHistory([this.entries = const []]);
  @override
  Future<List<DownloadEntry>> read() async => entries;
  @override
  Future<void> write(List<DownloadEntry> entries) async {
    if (failWrites) throw const FileSystemException('No space');
    this.entries = List.of(entries);
  }
}

class ReadFailureHistory extends MemoryHistory {
  bool failReads = true;
  int writes = 0;
  ReadFailureHistory(super.entries);
  @override
  Future<List<DownloadEntry>> read() async {
    if (failReads) throw const FileSystemException('Storage unavailable');
    return super.read();
  }
  @override
  Future<void> write(List<DownloadEntry> entries) async {
    writes++;
    await super.write(entries);
  }
}

class StreamingClient extends http.BaseClient {
  final Future<http.StreamedResponse> Function(http.BaseRequest request) respond;
  bool closed = false;
  StreamingClient(this.respond);
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) => respond(request);
  @override
  void close() { closed = true; }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('only the X image host receives original-image normalization', () {
    expect(originalDownloadUri(Uri.parse('https://pbs.twimg.com/media/a.jpg')).toString(),
      'https://pbs.twimg.com/media/a.jpg:orig');
    expect(originalDownloadUri(Uri.parse('https://pbs.twimg.com/media/a?format=png&name=small')).queryParameters,
      {'format': 'png', 'name': 'orig'});
    for (final url in ['https://mastodon.social/media/a.jpg?token=abc', 'https://i.redd.it/a.jpg',
        'https://pbs.twimg.com.evil.example/a.jpg']) {
      expect(originalDownloadUri(Uri.parse(url)).toString(), url);
    }
  });

  test('file names cannot escape their chosen destination', () {
    expect(safeDownloadName('../../photo.jpg?token=x'), 'photo.jpg');
    expect(safeDownloadName(r'C:\folder\photo.jpg'), 'photo.jpg');
    expect(safeDownloadName('..'), 'xta-media');
    expect(safeDownloadName('a:b.jpg'), 'a_b.jpg');
  });

  test('history is atomic and active work becomes retryable after restart', () async {
    final directory = await Directory.systemTemp.createTemp('xta-history-test');
    addTearDown(() => directory.delete(recursive: true));
    final history = FileDownloadHistory(directory: () async => directory);
    await history.write([entry('pending'), entry('complete', status: DownloadStatus.completed)]);
    final loaded = await FileDownloadHistory(directory: () async => directory).read();
    expect(loaded.first.status, DownloadStatus.interrupted);
    expect(loaded.first.canRetry, isTrue);
    expect(loaded.last.status, DownloadStatus.completed);
    expect(await File(p.join(directory.path, 'history-v1.json.tmp')).exists(), isFalse);
    await File(p.join(directory.path, 'history-v1.json.tmp')).writeAsString('interrupted write');
    expect((await history.read()).length, 2);
  });

  test('history rejects oversized or malformed metadata and bounds retained entries', () async {
    final directory = await Directory.systemTemp.createTemp('xta-history-bounds');
    addTearDown(() => directory.delete(recursive: true));
    final history = FileDownloadHistory(directory: () async => directory);
    await history.write(List.generate(250, (i) => entry('$i', status: DownloadStatus.completed)));
    expect((await history.read()).length, 200);
    final file = File(p.join(directory.path, 'history-v1.json'));
    await file.writeAsString(jsonEncode({'version': 1, 'entries': [null, {}, entry('valid').toJson()]}));
    expect((await history.read()).single.id, 'valid');
    await file.writeAsString('x' * 2000001);
    expect(await history.read(), isEmpty);
  });

  test('streams chunks to disk, reports progress and deletes the staging file', () async {
    final directory = await Directory.systemTemp.createTemp('xta-stream-test');
    addTearDown(() => directory.delete(recursive: true));
    final client = StreamingClient((_) async => http.StreamedResponse(Stream.fromIterable([[1, 2], [3, 4, 5]]),
      200, contentLength: 5));
    final progress = <int>[];
    String? stagedPath;
    final transfer = DownloadTransfer(clientFactory: () => client, temporaryDirectory: () async => directory,
      save: (entry, file, token) async {
        stagedPath = file.path;
        expect(await file.readAsBytes(), [1, 2, 3, 4, 5]);
        return 'content://provider/document/42';
      });
    final saved = await transfer.call(entry('stream'), DownloadCancellation(), (received, _) => progress.add(received), (_) {});
    expect(saved, 'content://provider/document/42');
    expect(progress, [0, 2, 5]);
    expect(client.closed, isTrue);
    expect(await File(stagedPath!).exists(), isFalse);
  });

  test('a short response never writes an incomplete media file to the destination', () async {
    final directory = await Directory.systemTemp.createTemp('xta-short-test');
    addTearDown(() => directory.delete(recursive: true));
    var saves = 0;
    final transfer = DownloadTransfer(temporaryDirectory: () async => directory,
      clientFactory: () => StreamingClient((_) async => http.StreamedResponse(Stream.value([1, 2]), 200, contentLength: 4)),
      save: (_, _, _) async { saves++; return 'saved'; });
    await expectLater(transfer.call(entry('short'), DownloadCancellation(), (_, _) {}, (_) {}), throwsA(isA<HttpException>()));
    expect(saves, 0);
    expect(await Directory(p.join(directory.path, 'xta-download-staging')).list().toList(), isEmpty);
  });

  test('cancellation closes HTTP and refuses a late response before any destination write', () async {
    final directory = await Directory.systemTemp.createTemp('xta-cancel-test');
    addTearDown(() => directory.delete(recursive: true));
    final started = Completer<void>();
    final response = Completer<http.StreamedResponse>();
    final client = StreamingClient((_) { started.complete(); return response.future; });
    var saves = 0;
    final transfer = DownloadTransfer(temporaryDirectory: () async => directory, clientFactory: () => client,
      save: (_, _, _) async { saves++; return 'saved'; });
    final cancellation = DownloadCancellation();
    final result = transfer.call(entry('cancel'), cancellation, (_, _) {}, (_) {});
    final assertion = expectLater(result, throwsA(isA<DownloadCancelled>()));
    await started.future;
    cancellation.cancel();
    expect(client.closed, isTrue);
    response.complete(http.StreamedResponse(Stream.value([1, 2]), 200));
    await assertion;
    expect(saves, 0);
    expect(await Directory(p.join(directory.path, 'xta-download-staging')).list().toList(), isEmpty);
  });

  test('server errors never reach the file picker or document provider', () async {
    final directory = await Directory.systemTemp.createTemp('xta-server-test');
    addTearDown(() => directory.delete(recursive: true));
    var saves = 0;
    final transfer = DownloadTransfer(temporaryDirectory: () async => directory,
      clientFactory: () => StreamingClient((_) async => http.StreamedResponse(const Stream.empty(), 503)),
      save: (_, _, _) async { saves++; return 'saved'; });
    await expectLater(transfer.call(entry('error'), DownloadCancellation(), (_, _) {}, (_) {}), throwsA(isA<HttpException>()));
    expect(saves, 0);
  });

  test('queued cancellation never starts HTTP and active cancellation cannot finish as success', () async {
    final started = Completer<void>();
    final release = Completer<String?>();
    final calls = <String>[];
    final store = DownloadStore(history: MemoryHistory(), runner: (entry, token, progress, phase) {
      calls.add(entry.fileName);
      started.complete();
      return release.future;
    });
    final first = store.enqueue(uri: Uri.parse('https://media.example/first'), fileName: 'first');
    await started.future;
    final second = store.enqueue(uri: Uri.parse('https://media.example/second'), fileName: 'second');
    await Future<void>.delayed(Duration.zero);
    await store.cancel(store.state.entries.firstWhere((entry) => entry.fileName == 'second').id);
    expect((await second).status, DownloadStatus.cancelled);
    await store.cancel(store.state.entries.firstWhere((entry) => entry.fileName == 'first').id);
    release.complete('content://provider/late-success');
    expect((await first).status, DownloadStatus.cancelled);
    await Future<void>.delayed(Duration.zero);
    expect(calls, ['first']);
    expect(store.state.entries.every((entry) => entry.status == DownloadStatus.cancelled), isTrue);
    await store.destroy();
  });

  test('retry uses the same history entry and batch results count only saved destinations', () async {
    var attempts = 0;
    final store = DownloadStore(history: MemoryHistory(), runner: (entry, token, progress, phase) async {
      attempts++;
      if (attempts == 1) throw const HttpException('offline');
      return 'content://provider/${entry.id}';
    });
    final failed = await store.enqueue(uri: Uri.parse('https://media.example/photo'), fileName: 'photo');
    expect(failed.status, DownloadStatus.failed);
    await store.retry(failed.id);
    await Future<void>.delayed(Duration.zero);
    await store.flush();
    expect(store.state.entries.single.id, failed.id);
    expect(store.state.entries.single.status, DownloadStatus.completed);
    await store.destroy();

    final batchStore = DownloadStore(history: MemoryHistory(), runner: (entry, token, progress, phase) async {
      if (int.parse(entry.fileName) >= 7) throw const HttpException('failed');
      return 'content://provider/${entry.id}';
    });
    final batch = await batchStore.enqueueBatch(List.generate(10, (i) => DownloadRequest(
      uri: Uri.parse('https://media.example/$i'), fileName: '$i')));
    expect(batch.saved, 7);
    expect(batch.total, 10);
    expect(batchStore.state.entries.where((entry) => entry.status == DownloadStatus.failed).length, 3);
    await batchStore.destroy();
  });

  test('immediate retry survives the cancelled attempt finishing late', () async {
    final started = Completer<void>();
    final oldResponse = Completer<String?>();
    final retried = Completer<void>();
    var requests = 0;
    final store = DownloadStore(history: MemoryHistory(), runner: (entry, token, progress, phase) {
      requests++;
      if (requests == 1) {
        started.complete();
        return oldResponse.future;
      }
      retried.complete();
      return Future.value('content://provider/retried');
    });
    final first = store.enqueue(uri: Uri.parse('https://media.example/photo'), fileName: 'photo');
    await started.future;
    final id = store.state.entries.single.id;
    await store.cancel(id);
    expect((await first).status, DownloadStatus.cancelled);
    await store.retry(id);
    expect(store.state.entries.single.status, DownloadStatus.queued);
    oldResponse.complete('content://provider/stale-response');
    await retried.future;
    await Future<void>.delayed(Duration.zero);
    await store.flush();
    expect(requests, 2);
    expect(store.state.entries.single.status, DownloadStatus.completed);
    expect(store.state.entries.single.savedUri, 'content://provider/retried');
    await store.destroy();
  });

  test('a failed history read cannot erase existing records and can be retried', () async {
    final history = ReadFailureHistory([entry('existing', status: DownloadStatus.completed)]);
    var requests = 0;
    final store = DownloadStore(history: history, runner: (_, _, _, _) async { requests++; return 'saved'; });
    await store.initialize();
    expect(store.state.storageError, isTrue);
    await expectLater(store.enqueue(uri: Uri.parse('https://media.example/new'), fileName: 'new'), throwsStateError);
    await store.clearFinished();
    expect(history.writes, 0);
    expect(history.entries.single.id, 'existing');
    expect(requests, 0);
    history.failReads = false;
    await store.retryHistory();
    expect(store.state.entries.single.id, 'existing');
    expect(store.state.storageError, isFalse);
    await store.enqueue(uri: Uri.parse('https://media.example/new'), fileName: 'new');
    await store.flush();
    expect(store.state.entries.length, 2);
    expect(history.entries.any((entry) => entry.id == 'existing'), isTrue);
    await store.destroy();
  });

  test('retrying metadata write preserves the current failed download entry', () async {
    final history = MemoryHistory()..failWrites = true;
    final store = DownloadStore(history: history, runner: (_, _, _, _) async => 'saved');
    final failed = await store.enqueue(uri: Uri.parse('https://media.example/photo'), fileName: 'photo');
    history.failWrites = false;
    await store.retryHistory();
    expect(store.state.entries.single.id, failed.id);
    expect(history.entries.single.id, failed.id);
    expect(store.state.storageError, isFalse);
    await store.destroy();
  });

  test('network does not start when the queued work cannot be persisted', () async {
    final history = MemoryHistory()..failWrites = true;
    var requests = 0;
    final store = DownloadStore(history: history, runner: (_, _, _, _) async { requests++; return 'saved'; });
    final failed = await store.enqueue(uri: Uri.parse('https://media.example/photo'), fileName: 'photo');
    expect(failed.status, DownloadStatus.failed);
    expect(requests, 0);
    expect(store.state.storageError, isTrue);
    await store.destroy();
  });
}
