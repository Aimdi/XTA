import 'dart:typed_data';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/utils/download_directory.dart';

const MethodChannel _channel = MethodChannel('browser_resolver');

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final calls = <MethodCall>[];
  Object? Function(MethodCall call) handler = (_) => null;

  setUp(() {
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_channel, (call) async {
      calls.add(call);
      return handler(call);
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(_channel, null);
    handler = (_) => null;
  });

  group('mimeTypeFor', () {
    test('recognises the media X serves', () {
      expect(mimeTypeFor('cat.jpg'), 'image/jpeg');
      expect(mimeTypeFor('cat.JPEG'), 'image/jpeg');
      expect(mimeTypeFor('cat.png'), 'image/png');
      expect(mimeTypeFor('cat.gif'), 'image/gif');
      expect(mimeTypeFor('clip.mp4'), 'video/mp4');
    });

    test('falls back to a generic type rather than guessing', () {
      // Android stores the type with the document, and the gallery uses it to
      // decide whether the file shows up at all.
      expect(mimeTypeFor('mystery'), 'application/octet-stream');
      expect(mimeTypeFor('archive.zip'), 'application/octet-stream');
    });
  });

  group('displayName', () {
    test('shows the folder, not the whole tree URI', () {
      expect(
        DownloadDirectory.displayName(
            'content://com.android.externalstorage.documents/tree/primary%3APictures%2FXTA'),
        'Pictures/XTA',
      );
    });

    test('copes with an SD card volume and a bare id', () {
      expect(
        DownloadDirectory.displayName('content://com.android.externalstorage.documents/tree/1234-5678%3ADownload'),
        'Download',
      );
      expect(DownloadDirectory.displayName('content://provider/tree/downloads'), 'downloads');
    });
  });

  group('save', () {
    test('sends the tree, name, bytes and type to the platform', () async {
      handler = (_) => 'content://provider/document/42';

      final saved = await DownloadDirectory.save(
        treeUri: 'content://provider/tree/primary%3APictures',
        fileName: 'cat.jpg',
        bytes: Uint8List.fromList([1, 2, 3]),
      );

      expect(saved, 'content://provider/document/42');
      expect(calls.single.method, 'saveToDownloadDirectory');
      final args = calls.single.arguments as Map;
      expect(args['treeUri'], 'content://provider/tree/primary%3APictures');
      expect(args['fileName'], 'cat.jpg');
      expect(args['mimeType'], 'image/jpeg');
      expect((args['bytes'] as Uint8List).toList(), [1, 2, 3]);
    });

    test('a lost grant surfaces as an error the caller can report', () async {
      handler = (_) => throw PlatformException(code: 'PERMISSION_LOST', message: 'gone');

      await expectLater(
        DownloadDirectory.save(
            treeUri: 'content://provider/tree/x', fileName: 'cat.jpg', bytes: Uint8List(0)),
        throwsA(isA<PlatformException>().having((e) => e.code, 'code', 'PERMISSION_LOST')),
      );
    });
  });

  group('hasAccess', () {
    test('is false without asking the platform when nothing is stored', () async {
      expect(await DownloadDirectory.hasAccess(null), isFalse);
      expect(await DownloadDirectory.hasAccess(''), isFalse);
      expect(calls, isEmpty);
    });

    test('reports what the platform says about a stored tree', () async {
      handler = (_) => true;
      expect(await DownloadDirectory.hasAccess('content://provider/tree/x'), isTrue);

      handler = (_) => false;
      expect(await DownloadDirectory.hasAccess('content://provider/tree/x'), isFalse);

      expect(calls.map((c) => c.method), everyElement('hasDownloadDirectoryAccess'));
    });
  });

  group('pick', () {
    test('returns the chosen tree, and null when the user backs out', () async {
      handler = (_) => 'content://provider/tree/primary%3APictures';
      expect(await DownloadDirectory.pick(), 'content://provider/tree/primary%3APictures');

      handler = (_) => null;
      expect(await DownloadDirectory.pick(), isNull);
    });
  });
}
