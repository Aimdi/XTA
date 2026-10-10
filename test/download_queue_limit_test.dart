import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/downloads/download_repository.dart';
import 'package:xta/downloads/download_store.dart';
import 'package:xta/downloads/download_transfer.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/_media.dart';
import 'package:xta/settings/settings_search_index.dart';

class _MemoryHistory implements DownloadHistory {
  List<DownloadEntry> entries = const [];

  @override
  Future<List<DownloadEntry>> read() async => entries;

  @override
  Future<void> write(List<DownloadEntry> entries) async => this.entries = List.of(entries);
}

/// A runner whose transfers finish only when the test says so.
class _GatedRunner {
  final started = <String>[];
  final _gates = <String, Completer<String?>>{};

  Future<String?> call(
    DownloadEntry entry,
    DownloadCancellation token,
    DownloadProgress progress,
    DownloadPhase phase,
  ) {
    started.add(entry.fileName);
    return (_gates[entry.fileName] = Completer<String?>()).future;
  }

  void finish(String name) => _gates[name]!.complete('content://provider/document/$name');
}

Future<List<Future<DownloadEntry>>> _enqueue(DownloadStore store, List<String> names) async {
  final pending = <Future<DownloadEntry>>[];
  for (final name in names) {
    pending.add(store.enqueue(uri: Uri.parse('https://media.example/$name'), fileName: name));
    await pumpEventQueue();
  }
  return pending;
}

/// Waits on real I/O (staging files) until [done], or gives up after a second.
Future<void> _until(bool Function() done) async {
  for (var i = 0; i < 200 && !done(); i++) {
    await Future<void>.delayed(const Duration(milliseconds: 5));
  }
}

/// Real transfers whose save step stands in for the system save dialog, which
/// takes one request at a time; [overlaps] counts any second one opened.
class _AskMode {
  final dialogs = <String, Completer<String?>>{};
  final _open = <String>{};
  var overlaps = 0;
  late final DownloadStore store;

  _AskMode(Directory directory) {
    final transfer = DownloadTransfer(
      clientFactory: () => MockClient((_) async => http.Response.bytes([1, 2, 3], 200)),
      temporaryDirectory: () async => directory,
      save: (entry, file, token) async {
        if (_open.isNotEmpty) overlaps++;
        _open.add(entry.fileName);
        try {
          return await (dialogs[entry.fileName] = Completer<String?>()).future;
        } finally {
          _open.remove(entry.fileName);
        }
      },
    );
    store = DownloadStore(history: _MemoryHistory(), runner: transfer.call)..setConcurrency(downloadConcurrencyDefault);
  }

  DownloadEntry entry(String name) => store.state.entries.singleWhere((entry) => entry.fileName == name);

  /// The download that reached the dialog first, and the one left waiting.
  Future<(String, String)> firstOpen() async {
    await _until(() => dialogs.isNotEmpty);
    // Give the other download time to finish fetching and reach the dialog too.
    await _until(() => store.state.entries.every((entry) => entry.received == 3));
    await Future<void>.delayed(const Duration(milliseconds: 50));
    final first = dialogs.keys.single;
    return (first, first == 'a.jpg' ? 'b.jpg' : 'a.jpg');
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('concurrent downloads', () {
    test('a store left at its default runs one transfer at a time, oldest first', () async {
      final runner = _GatedRunner();
      final store = DownloadStore(history: _MemoryHistory(), runner: runner.call);
      addTearDown(store.destroy);

      await _enqueue(store, ['a', 'b', 'c']);
      expect(runner.started, ['a']);
      runner.finish('a');
      await pumpEventQueue();
      expect(runner.started, ['a', 'b']);
    });

    test('with a limit of two, two run and the third waits for a free slot', () async {
      final runner = _GatedRunner();
      final store = DownloadStore(history: _MemoryHistory(), runner: runner.call)..setConcurrency(2);
      addTearDown(store.destroy);

      final pending = await _enqueue(store, ['a', 'b', 'c']);
      expect(runner.started, ['a', 'b']);
      expect(store.state.entries.where((entry) => entry.status == DownloadStatus.downloading), hasLength(2));

      runner.finish('b');
      expect((await pending[1]).status, DownloadStatus.completed);
      await pumpEventQueue();
      expect(runner.started, ['a', 'b', 'c']);
    });

    test('raising the limit starts queued downloads at once; lowering it stops nothing', () async {
      final runner = _GatedRunner();
      final store = DownloadStore(history: _MemoryHistory(), runner: runner.call);
      addTearDown(store.destroy);

      await _enqueue(store, ['a', 'b', 'c', 'd']);
      store.setConcurrency(3);
      await pumpEventQueue();
      expect(runner.started, ['a', 'b', 'c']);

      store.setConcurrency(1);
      runner.finish('a');
      await pumpEventQueue();
      expect(runner.started, ['a', 'b', 'c'], reason: 'two still run, above the new limit of one');
      runner
        ..finish('b')
        ..finish('c');
      await pumpEventQueue();
      expect(runner.started, ['a', 'b', 'c', 'd']);
    });

    test('downloads without a folder fetch side by side but open the save dialog one at a time', () async {
      final directory = await Directory.systemTemp.createTemp('xta-ask-mode');
      addTearDown(() => directory.delete(recursive: true));
      final ask = _AskMode(directory);
      addTearDown(ask.store.destroy);

      final pending = await _enqueue(ask.store, ['a.jpg', 'b.jpg']);
      final (first, waiting) = await ask.firstOpen();
      expect(ask.entry(waiting).status, DownloadStatus.downloading, reason: 'fetched, waiting for the dialog');
      expect(ask.entry(waiting).canCancel, isTrue);

      ask.dialogs[first]!.complete('content://provider/document/$first');
      await _until(() => ask.dialogs.length == 2);
      ask.dialogs[waiting]!.complete('content://provider/document/$waiting');

      expect([for (final entry in pending) (await entry).status], [DownloadStatus.completed, DownloadStatus.completed]);
      expect(ask.overlaps, 0);
    });

    test('a download cancelled while it waits for the dialog never opens one', () async {
      final directory = await Directory.systemTemp.createTemp('xta-ask-cancel');
      addTearDown(() => directory.delete(recursive: true));
      final ask = _AskMode(directory);
      addTearDown(ask.store.destroy);

      final pending = await _enqueue(ask.store, ['a.jpg', 'b.jpg']);
      final (first, waiting) = await ask.firstOpen();
      await ask.store.cancel(ask.entry(waiting).id);
      ask.dialogs[first]!.complete('content://provider/document/$first');
      await ask.store.flush();
      await Future<void>.delayed(const Duration(milliseconds: 50));

      final statuses = {for (final entry in pending) (await entry).fileName: (await entry).status};
      expect(statuses, {first: DownloadStatus.completed, waiting: DownloadStatus.cancelled});
      expect(ask.dialogs.keys, [first]);
    });

    test('the limit stays within the offered choices', () {
      final store = DownloadStore(history: _MemoryHistory(), runner: _GatedRunner().call);
      addTearDown(store.destroy);
      store.setConcurrency(0);
      expect(store.concurrency, 1);
      store.setConcurrency(99);
      expect(store.concurrency, downloadConcurrencyChoices.last);
      expect(downloadConcurrencyChoices, contains(downloadConcurrencyDefault));
    });
  });

  testWidgets('the Simultaneous downloads setting stores the limit and applies it to the queue', (tester) async {
    addTearDown(() => DownloadStore.shared.setConcurrency(1));
    final prefs = PrefServiceCache(cache: {optionDownloadConcurrency: downloadConcurrencyDefault});
    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          home: const Scaffold(body: DownloadConcurrencySetting()),
        ),
      ),
    );
    expect(find.text('Simultaneous downloads'), findsOneWidget);

    await tester.tap(find.text('$downloadConcurrencyDefault'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('3').last);
    await tester.pumpAndSettle();

    expect(prefs.get<int>(optionDownloadConcurrency), 3);
    expect(DownloadStore.shared.concurrency, 3);
    final l10n = L10n.of(tester.element(find.byType(DownloadConcurrencySetting)));
    expect(searchSettingsControls(l10n, 'simultaneous').map((result) => result.target), [optionDownloadConcurrency]);
  });

  group('subfolders', () {
    test('safeDownloadFolder keeps a path inside the chosen folder', () {
      expect(safeDownloadFolder('R-18/Mika_42'), 'R-18/Mika_42');
      expect(safeDownloadFolder(r'..\..\R-18/./Mika:42/'), 'R-18/Mika_42');
      expect(safeDownloadFolder(' name. /  '), 'name');
      expect(safeDownloadFolder('.hidden/ .mika_42'), 'hidden/mika_42');
      expect(safeDownloadFolder('/../.'), isNull);
      expect(safeDownloadFolder('a/b/c/d/e/f'), 'a/b/c/d');
      expect(safeDownloadFolder('x' * 300), 'x' * 120);
    });

    test('an entry keeps its subfolder in history, and a tampered one is cleaned', () {
      final entry = DownloadEntry(
        id: 'a',
        uri: Uri.parse('https://media.example/a.png'),
        fileName: 'a.png',
        treeUri: 'content://tree/x',
        subfolder: 'R-18/Mika_42',
        createdAt: DateTime.utc(2026),
        status: DownloadStatus.completed,
      );
      expect(entry.toJson()['folder'], 'R-18/Mika_42');
      expect(DownloadEntry.fromJson(entry.toJson())!.subfolder, 'R-18/Mika_42');
      expect(DownloadEntry.fromJson({...entry.toJson(), 'folder': '../../etc'})!.subfolder, 'etc');
      expect(DownloadEntry.fromJson({...entry.toJson()}..remove('folder'))!.subfolder, isNull);
      expect(entry.copyWith(status: DownloadStatus.queued).subfolder, 'R-18/Mika_42');
      final plain = DownloadEntry(id: 'b', uri: entry.uri, fileName: 'b.png', createdAt: DateTime.utc(2026));
      expect(plain.toJson().containsKey('folder'), isFalse);
    });

    test('a queued request carries its cleaned subfolder to the transfer', () async {
      final seen = <String?>[];
      final store = DownloadStore(
        history: _MemoryHistory(),
        runner: (entry, token, progress, phase) async {
          seen.add(entry.subfolder);
          return 'content://provider/document/1';
        },
      );
      addTearDown(store.destroy);

      final batch = await store.enqueueBatch([
        DownloadRequest(uri: Uri.parse('https://media.example/1'), fileName: '1', subfolder: 'Mika_42/../R-18'),
        DownloadRequest(uri: Uri.parse('https://media.example/2'), fileName: '2'),
      ]);
      expect(batch.saved, 2);
      expect(seen, ['Mika_42/R-18', null]);
    });

    test('bytes made on the device are staged and copied, never sent through the channel', () async {
      final directory = await Directory.systemTemp.createTemp('xta-staged-bytes');
      addTearDown(() => directory.delete(recursive: true));
      final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      const pathProvider = MethodChannel('plugins.flutter.io/path_provider');
      messenger.setMockMethodCallHandler(pathProvider, (_) async => directory.path);
      addTearDown(() => messenger.setMockMethodCallHandler(pathProvider, null));
      const channel = MethodChannel('browser_resolver');
      final calls = <MethodCall>[];
      List<int>? staged;
      messenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call);
        staged = await File((call.arguments as Map)['sourcePath'] as String).readAsBytes();
        return 'content://provider/document/7';
      });
      addTearDown(() => messenger.setMockMethodCallHandler(channel, null));

      final saved = await DownloadTransfer.saveBytes(
        treeUri: 'content://tree/x',
        fileName: '120_p0.gif',
        bytes: [71, 73, 70],
        subfolder: 'R-18/Mika_42',
      );

      expect(saved, 'content://provider/document/7');
      final args = calls.single.arguments as Map;
      expect(calls.single.method, 'saveFileToDownloadDirectory');
      expect(args['subfolder'], 'R-18/Mika_42');
      expect(args['mimeType'], 'image/gif');
      expect(args.containsKey('bytes'), isFalse);
      expect(staged, [71, 73, 70]);
      expect(File(args['sourcePath'] as String).existsSync(), isFalse, reason: 'the staged copy is removed');
    });

    test('the transfer hands the subfolder to the platform save', () async {
      const channel = MethodChannel('browser_resolver');
      final calls = <MethodCall>[];
      final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call);
        return 'content://provider/document/9';
      });
      addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
      final directory = await Directory.systemTemp.createTemp('xta-subfolder-transfer');
      addTearDown(() => directory.delete(recursive: true));
      final transfer = DownloadTransfer(
        clientFactory: () => MockClient((_) async => http.Response.bytes([1, 2, 3], 200)),
        temporaryDirectory: () async => directory,
      );
      final entry = DownloadEntry(
        id: 'job',
        uri: Uri.parse('https://i.pximg.net/img-original/120_p0.png'),
        fileName: '120_p0.png',
        treeUri: 'content://tree/x',
        subfolder: 'R-18/Mika_42',
        createdAt: DateTime.utc(2026),
      );

      expect(await transfer.call(entry, DownloadCancellation(), (_, _) {}, (_) {}), 'content://provider/document/9');
      final save = calls.singleWhere((call) => call.method == 'saveFileToDownloadDirectory');
      expect((save.arguments as Map)['subfolder'], 'R-18/Mika_42');
      expect((save.arguments as Map)['fileName'], '120_p0.png');
    });
  });
}
