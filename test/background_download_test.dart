import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/downloads/download_destination.dart';
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/downloads/download_repository.dart';
import 'package:xta/downloads/download_store.dart';
import 'package:xta/downloads/download_transfer.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_download.dart';
import 'package:xta/settings/_media.dart';
import 'package:xta/utils/download_directory.dart';
import 'package:xta/utils/downloads.dart';

const _native = MethodChannel('browser_resolver');
const _dialog = MethodChannel('flutter_file_dialog');
const _mediaUri = 'content://media/external_primary/images/media/7';

class _BytesClient extends http.BaseClient {
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async =>
      http.StreamedResponse(Stream.value([1, 2, 3]), 200, contentLength: 3);
}

class _MemoryHistory implements DownloadHistory {
  @override
  Future<List<DownloadEntry>> read() async => [];
  @override
  Future<void> write(List<DownloadEntry> entries) async {}
}

DownloadEntry _entry(String fileName, {String? treeUri, bool background = false}) => DownloadEntry(
  id: 'job-${fileName.hashCode.abs()}',
  uri: Uri.parse('https://example.org/$fileName'),
  fileName: fileName,
  treeUri: treeUri,
  background: background,
  createdAt: DateTime(2026),
);

PrefServiceCache _prefs(Map<String, dynamic> stored) => PrefServiceCache(cache: {...stored});

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final native = <MethodCall>[];
  final dialog = <MethodCall>[];
  late bool sharedStorage;
  late Future<Object?> Function(MethodCall call) onSave;
  late Directory directory;

  setUp(() async {
    native.clear();
    dialog.clear();
    sharedStorage = true;
    onSave = (_) async => _mediaUri;
    directory = await Directory.systemTemp.createTemp('xta-background');
    messenger.setMockMethodCallHandler(_native, (call) async {
      native.add(call);
      return switch (call.method) {
        'canSaveToSharedStorage' => sharedStorage,
        'saveFileToSharedStorage' || 'saveFileToDownloadDirectory' => onSave(call),
        _ => null,
      };
    });
    messenger.setMockMethodCallHandler(_dialog, (call) async {
      dialog.add(call);
      return '/storage/emulated/0/Download/picked.jpg';
    });
  });

  tearDown(() async {
    messenger.setMockMethodCallHandler(_native, null);
    messenger.setMockMethodCallHandler(_dialog, null);
    await directory.delete(recursive: true);
  });

  Future<String?> transfer(DownloadEntry entry, {DownloadCancellation? token, List<DownloadStatus>? phases}) =>
      DownloadTransfer(
        clientFactory: _BytesClient.new,
        temporaryDirectory: () async => directory,
      ).call(entry, token ?? DownloadCancellation(), (_, _) {}, (status) => phases?.add(status));

  List<String> methods() => native.map((call) => call.method).toList();
  Map arguments(String method) => native.firstWhere((call) => call.method == method).arguments as Map;

  group('download setting', () {
    test('a fresh install saves in the background', () async {
      final prefs = _prefs({});
      await prefs.setDefaultValues(downloadPrefDefaults);
      await migrateDownloadTypeDefault(prefs);
      expect(prefs.get(optionDownloadType), optionDownloadTypeAuto);
      expect(DownloadDestination.fromPrefs(prefs).background, isTrue);
    });

    test('resolves each mode, and a directory without a folder still asks', () {
      expect(DownloadDestination.fromPrefs(_prefs({})).background, isTrue);
      expect(DownloadDestination.fromPrefs(_prefs({optionDownloadType: optionDownloadTypeAsk})).asks, isTrue);
      final folder = DownloadDestination.fromPrefs(
        _prefs({optionDownloadType: optionDownloadTypeDirectory, optionDownloadTreeUri: 'content://tree/x'}),
      );
      expect((folder.treeUri, folder.background), ('content://tree/x', false));
      expect(DownloadDestination.fromPrefs(_prefs({optionDownloadType: optionDownloadTypeDirectory})).asks, isTrue);
      final auto = DownloadDestination.fromPrefs(
        _prefs({optionDownloadType: optionDownloadTypeAuto, optionDownloadTreeUri: 'content://tree/old'}),
      );
      expect((auto.treeUri, auto.background), (null, true));
    });

    test('moves the inherited ask default once and keeps deliberate choices', () async {
      final inherited = _prefs({optionDownloadType: optionDownloadTypeAsk, optionDownloadTreeUri: ''});
      await migrateDownloadTypeDefault(inherited);
      expect(inherited.get(optionDownloadType), optionDownloadTypeAuto);

      await inherited.set(optionDownloadType, optionDownloadTypeAsk);
      await migrateDownloadTypeDefault(inherited);
      expect(inherited.get(optionDownloadType), optionDownloadTypeAsk);

      final visited = _prefs({optionDownloadType: optionDownloadTypeAsk, optionDownloadTreeUri: 'content://tree/x'});
      await migrateDownloadTypeDefault(visited);
      expect(visited.get(optionDownloadType), optionDownloadTypeAsk);

      final directory = _prefs({optionDownloadType: optionDownloadTypeDirectory});
      await migrateDownloadTypeDefault(directory);
      expect(directory.get(optionDownloadType), optionDownloadTypeDirectory);
    });
  });

  group('shared folders', () {
    test('pictures, videos and everything else land in their own XTA folder', () {
      expect(SharedDownloadFolder.of('a.jpg'), SharedDownloadFolder.pictures);
      expect(SharedDownloadFolder.of('a.webp'), SharedDownloadFolder.pictures);
      expect(SharedDownloadFolder.of('a.mp4'), SharedDownloadFolder.movies);
      expect(SharedDownloadFolder.of('a.mp3'), SharedDownloadFolder.downloads);
      expect(SharedDownloadFolder.of('a.zip'), SharedDownloadFolder.downloads);
      expect(SharedDownloadFolder.values.map((folder) => folder.relativePath), [
        'Pictures/XTA',
        'Movies/XTA',
        'Download/XTA',
      ]);
    });

    test('the copy carries the staged path, type and folder, never the bytes', () async {
      await DownloadDirectory.saveFileToSharedStorage(
        fileName: 'clip.mp4',
        sourcePath: '/s/job.part',
        operationId: 'job',
      );
      final args = arguments('saveFileToSharedStorage');
      expect(args, {
        'fileName': 'clip.mp4',
        'mimeType': 'video/mp4',
        'collection': 'video',
        'relativePath': 'Movies/XTA',
        'sourcePath': '/s/job.part',
        'operationId': 'job',
      });
    });

    test('only a finished background save reports its folder', () {
      final saved = _entry('cat.jpg', background: true).copyWith(savedUri: _mediaUri);
      expect(sharedFolderOf(saved), 'Pictures/XTA');
      expect(sharedFolderOf(saved.copyWith(savedUri: '/storage/emulated/0/Download/cat.jpg')), isNull);
      expect(sharedFolderOf(_entry('cat.jpg').copyWith(savedUri: _mediaUri)), isNull);
    });
  });

  group('transfer', () {
    test('a background download is copied into its shared folder without a dialog', () async {
      final phases = <DownloadStatus>[];
      final entry = _entry('cat.jpg', background: true);
      expect(await transfer(entry, phases: phases), _mediaUri);
      expect(dialog, isEmpty);
      expect(phases, [DownloadStatus.saving]);
      final args = arguments('saveFileToSharedStorage');
      expect(args['operationId'], entry.id);
      expect(args['fileName'], 'cat.jpg');
      expect(args['mimeType'], 'image/jpeg');
      expect(args['collection'], 'images');
      expect(args['relativePath'], 'Pictures/XTA');
      expect(args['sourcePath'], endsWith('.part'));
      expect(args.containsKey('bytes'), isFalse);
    });

    test('Android 9 and older fall back to the save dialog', () async {
      sharedStorage = false;
      final phases = <DownloadStatus>[];
      expect(await transfer(_entry('cat.jpg', background: true), phases: phases), isNotNull);
      expect(dialog.single.method, 'saveFile');
      expect(methods(), isNot(contains('saveFileToSharedStorage')));
      expect(phases, [DownloadStatus.choosingLocation]);
    });

    test('an explicit ask still opens the dialog', () async {
      await transfer(_entry('cat.jpg'));
      expect(dialog.single.method, 'saveFile');
      expect(methods(), isEmpty);
    });

    test('a chosen folder still saves through its document tree', () async {
      onSave = (_) async => 'content://provider/document/1';
      expect(await transfer(_entry('cat.jpg', treeUri: 'content://tree/x', background: true)), isNotNull);
      expect(dialog, isEmpty);
      expect(methods(), ['saveFileToDownloadDirectory']);
      expect(arguments('saveFileToDownloadDirectory')['treeUri'], 'content://tree/x');
    });

    test('cancelling stops the native copy by operation id', () async {
      final started = Completer<void>();
      final release = Completer<Object?>();
      onSave = (_) {
        started.complete();
        return release.future;
      };
      final token = DownloadCancellation();
      final entry = _entry('clip.mp4', background: true);
      final result = transfer(entry, token: token);
      await started.future;
      token.cancel();
      release.complete(null);
      await expectLater(result, throwsA(isA<DownloadCancelled>()));
      expect(arguments('cancelDownloadSave')['operationId'], entry.id);
      expect(methods(), isNot(contains('deleteDownloadedDocument')));
    });

    test('an item finished after the cancel is deleted again', () async {
      final started = Completer<void>();
      final release = Completer<Object?>();
      onSave = (_) {
        started.complete();
        return release.future;
      };
      final token = DownloadCancellation();
      final result = transfer(_entry('cat.jpg', background: true), token: token);
      await started.future;
      token.cancel();
      release.complete(_mediaUri);
      await expectLater(result, throwsA(isA<DownloadCancelled>()));
      expect(arguments('deleteDownloadedDocument')['documentUri'], _mediaUri);
    });

    test('a failed copy surfaces as an error', () async {
      onSave = (_) async => throw PlatformException(code: 'SAVE_FAILED');
      await expectLater(transfer(_entry('cat.jpg', background: true)), throwsA(isA<PlatformException>()));
    });
  });

  group('queue', () {
    test('background is recorded and survives the history file, unless a folder is named', () async {
      final seen = <DownloadEntry>[];
      final store = DownloadStore(
        history: _MemoryHistory(),
        runner: (entry, _, _, _) async {
          seen.add(entry);
          return _mediaUri;
        },
      );
      addTearDown(store.destroy);
      final saved = await store.enqueue(
        uri: Uri.parse('https://example.org/a.jpg'),
        fileName: 'a.jpg',
        background: true,
      );
      await store.enqueue(
        uri: Uri.parse('https://example.org/b.jpg'),
        fileName: 'b.jpg',
        treeUri: 'content://tree/x',
        background: true,
      );
      expect(seen.map((entry) => entry.background), [true, false]);
      expect(DownloadEntry.fromJson(saved.toJson())!.background, isTrue);
      expect(DownloadEntry.fromJson(seen.last.toJson())!.background, isFalse);
    });

    test('a Pixiv batch in background mode never opens the folder picker', () async {
      final destination = await const PixivDownloader().batchDestination(
        _prefs({optionDownloadType: optionDownloadTypeAuto}),
      );
      expect(destination?.background, isTrue);
      expect(methods(), isNot(contains('pickDownloadDirectory')));
    });
  });

  testWidgets('settings offers background saving and explains where files go', (tester) async {
    final prefs = _prefs({...downloadPrefDefaults});
    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          locale: const Locale('en'),
          home: Scaffold(body: DownloadTypeSetting(prefs: prefs)),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Save in the background'), findsOneWidget);
    expect(find.textContaining('Pictures/XTA'), findsOneWidget);

    await tester.tap(find.text('Save in the background'));
    await tester.pumpAndSettle();
    expect(find.text('Always ask'), findsWidgets);
    expect(find.text('Save to directory'), findsWidgets);
    await tester.tap(find.text('Always ask').last);
    await tester.pumpAndSettle();
    expect(prefs.get(optionDownloadType), optionDownloadTypeAsk);
    expect(find.textContaining('Pictures/XTA'), findsNothing);
  });
}
