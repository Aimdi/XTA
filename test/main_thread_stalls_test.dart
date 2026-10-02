import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/settings/main_thread_stalls.dart';

void main() {
  group('describeMainThreadStalls', () {
    test('an empty record means the thread never stalled', () {
      expect(describeMainThreadStalls(''), 'no stall recorded');
      expect(describeMainThreadStalls('\n  \n'), 'no stall recorded');
    });

    test('keeps the stall line, its frames and the resume line, indented under the probe', () {
      const record =
          '2026-10-02T12:40:39.100 main thread blocked for 5 s\n'
          '  at android.os.BinderProxy.transactNative(Native Method)\n'
          '  at com.example.Plugin.onMethodCall(Plugin.java:42)\n'
          '2026-10-02T12:42:25.300 main thread resumed after 111 s\n';
      final text = describeMainThreadStalls(record);
      expect(text, startsWith('\n  2026-10-02T12:40:39.100 main thread blocked for 5 s'));
      expect(text, contains('    at com.example.Plugin.onMethodCall(Plugin.java:42)'));
      expect(text, endsWith('  2026-10-02T12:42:25.300 main thread resumed after 111 s'));
    });

    test('only the newest lines survive a long record', () {
      final record = List.generate(200, (i) => 'line $i').join('\n');
      final text = describeMainThreadStalls(record);
      expect(text, isNot(contains('line 139\n')));
      expect(text, contains('line 140'));
      expect(text, endsWith('line 199'));
    });
  });

  group('mainThreadStallSummary', () {
    late Directory directory;
    setUp(() async => directory = await Directory.systemTemp.createTemp('xta_main_thread_stalls'));
    tearDown(() => directory.delete(recursive: true));

    test('reads the watchdog file from the files directory', () async {
      final file = File('${directory.path}/$mainThreadStallFile');
      await file.create(recursive: true);
      await file.writeAsString('2026-10-02T12:40:39.100 main thread blocked for 5 s\n');
      final text = await mainThreadStallSummary(filesDirectory: () async => directory);
      expect(text, contains('main thread blocked for 5 s'));
    });

    test('a missing file is no stall, and a directory lookup that never answers is bounded', () async {
      expect(await mainThreadStallSummary(filesDirectory: () async => directory), 'no stall recorded');
      final text = await mainThreadStallSummary(
        filesDirectory: () => Future<Directory>.delayed(const Duration(seconds: 5), () => directory),
        timeout: const Duration(milliseconds: 50),
      );
      expect(text, startsWith('unknown'));
    });
  });
}
