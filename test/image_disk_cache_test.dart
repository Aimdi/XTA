import 'dart:io';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/image_cache_setting.dart';
import 'package:xta/utils/byte_size.dart';
import 'package:xta/utils/image_disk_cache.dart';

/// Points the image disk cache at a fresh folder and fills it with [files],
/// each aged by its value.
Future<Directory> _cache(Map<String, Duration> files) async {
  final temporary = await Directory.systemTemp.createTemp('xta_image_cache');
  addTearDown(() => temporary.delete(recursive: true));
  const pathProvider = MethodChannel('plugins.flutter.io/path_provider');
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(pathProvider, (_) async => temporary.path);
  addTearDown(() => messenger.setMockMethodCallHandler(pathProvider, null));
  final folder = await Directory('${temporary.path}/$cacheImageFolderName').create();
  final now = DateTime.now();
  for (final MapEntry(key: name, value: age) in files.entries) {
    final file = File('${folder.path}/$name');
    await file.writeAsBytes(List.filled(2048, 7));
    await file.setLastModified(now.subtract(age));
  }
  return folder;
}

Future<List<String>> _names(Directory folder) async =>
    await folder.exists() ? [for (final entity in await folder.list().toList()) entity.uri.pathSegments.last] : [];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('a launch removes pictures cached more than 30 days ago and keeps the rest', () async {
    final folder = await _cache({'old': const Duration(days: 40), 'recent': const Duration(days: 2)});

    await trimImageDiskCache();

    expect(await _names(folder), ['recent']);
  });

  test('the store measures the cache, and clearing empties it', () async {
    await _cache({'a': Duration.zero, 'b': Duration.zero});
    final store = ImageDiskCacheStore();
    addTearDown(store.destroy);

    await store.load();
    expect(store.state, 4096);

    await store.clear();
    expect(store.state, 0);
  });

  test('sizes read as bytes, kilobytes, megabytes and gigabytes', () {
    expect(formatByteSize(512), '512 B');
    expect(formatByteSize(2048), '2.0 KB');
    expect(formatByteSize(5 * 1048576), '5.0 MB');
    expect(formatByteSize(3 * 1073741824 ~/ 2), '1.5 GB');
  });

  testWidgets('settings show the size, and clear the cache once asked twice', (tester) async {
    var bytes = 2048;
    var emptied = 0;
    final store = ImageDiskCacheStore(
      measure: () async => bytes,
      empty: () async {
        emptied++;
        bytes = 0;
      },
    );
    addTearDown(store.destroy);
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
        ],
        home: Scaffold(body: ImageCacheSetting(store: store)),
      ),
    );
    await tester.pumpAndSettle();
    final l10n = await L10n.load(const Locale('en'));
    expect(find.text(l10n.image_cache_size('2.0 KB', 30)), findsOneWidget);

    await tester.tap(find.text(l10n.image_cache_title));
    await tester.pumpAndSettle();
    await tester.tap(find.text(l10n.cancel));
    await tester.pumpAndSettle();
    expect(emptied, 0, reason: 'cancelling keeps the cache');

    await tester.tap(find.text(l10n.image_cache_title));
    await tester.pumpAndSettle();
    await tester.tap(find.text(l10n.image_cache_clear));
    await tester.pumpAndSettle();

    expect(emptied, 1);
    expect(find.text(l10n.image_cache_size('0 B', 30)), findsOneWidget);
  });
}
