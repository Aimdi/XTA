import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/crash_log_screen.dart';
import 'package:xta/utils/breadcrumbs.dart';
import 'package:xta/utils/crash_log.dart';
import 'package:xta/utils/crash_log_entry.dart';

class _MemoryStorage implements CrashLogStorage {
  String log;
  String? session;

  _MemoryStorage({this.log = ''});

  @override
  Future<String> readLog() async => log;

  @override
  Future<void> writeLog(String contents) async => log = contents;

  @override
  Future<String?> readSession() async => session;

  @override
  Future<void> writeSession(String contents) async => session = contents;

  @override
  Future<void> clearSession() async => session = null;
}

Widget _app() => MaterialApp(
  locale: const Locale('en'),
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  home: const CrashLogScreen(),
);

CrashLogEntry _entry(CrashSource source, String error, {String? stack}) =>
    CrashLogEntry(
      firstSeen: DateTime.utc(2026, 8, 22, 10),
      lastSeen: DateTime.utc(2026, 8, 22, 10),
      source: source,
      error: error,
      stack: stack,
      breadcrumbs: const ['10:00:00 route: push /pixiv'],
      vitals: const {'rss': '512.0MB'},
    );

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    PackageInfo.setMockInitialValues(
      appName: 'XTA',
      packageName: 'com.example.xta',
      version: '4.12.0',
      buildNumber: '400001080',
      buildSignature: '',
    );
  });

  tearDown(() {
    CrashLog.instance?.dispose();
    CrashLog.instance = null;
  });

  Future<CrashLog> installLog(_MemoryStorage storage) async {
    final log = CrashLog(storage: storage, breadcrumbs: Breadcrumbs());
    CrashLog.instance = log;
    await log.start(appVersion: '4.12.0+400001080');
    return log;
  }

  testWidgets('an empty log explains what will show up here', (tester) async {
    final log = await installLog(_MemoryStorage());

    await tester.pumpWidget(_app());
    await tester.pumpAndSettle();

    expect(find.text(L10n.current.crash_log_empty), findsOneWidget);
    expect(find.byIcon(Icons.copy_all), findsOneWidget);

    log.dispose();
  });

  testWidgets('a recorded failure is listed and expands to its stack', (
    tester,
  ) async {
    final storage = _MemoryStorage(
      log: encodeCrashEntries([
        _entry(
          CrashSource.flutter,
          'RangeError (index): Invalid value',
          stack: '#0 PixivIllustGrid.build',
        ),
      ]),
    );
    final log = await installLog(storage);

    await tester.pumpWidget(_app());
    await tester.pumpAndSettle();

    expect(find.text(L10n.current.crash_log_source_interface), findsOneWidget);

    await tester.tap(find.text(L10n.current.crash_log_source_interface));
    await tester.pumpAndSettle();

    final body = tester
        .widget<SelectableText>(find.byType(SelectableText))
        .data!;
    expect(body, contains('RangeError'));
    expect(body, contains('#0 PixivIllustGrid.build'));
    expect(body, contains('push /pixiv'));
    expect(body, contains('rss=512.0MB'));

    log.dispose();
  });

  testWidgets('a native death is shown as an unexpected close', (tester) async {
    final storage = _MemoryStorage(
      log: encodeCrashEntries([
        _entry(CrashSource.nativeDeath, 'Previous session ended unexpectedly'),
      ]),
    );
    final log = await installLog(storage);

    await tester.pumpWidget(_app());
    await tester.pumpAndSettle();

    expect(find.text(L10n.current.crash_log_source_killed), findsOneWidget);

    log.dispose();
  });

  testWidgets('writing a test entry proves the log works end to end', (
    tester,
  ) async {
    final storage = _MemoryStorage();
    final log = await installLog(storage);

    await tester.pumpWidget(_app());
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.more_vert));
    await tester.pumpAndSettle();
    await tester.tap(find.text(L10n.current.crash_log_add_test_entry));
    await tester.pumpAndSettle();

    expect(find.text(L10n.current.crash_log_source_test), findsOneWidget);
    expect(decodeCrashEntries(storage.log), hasLength(1));

    log.dispose();
  });

  testWidgets('clearing empties both the screen and the file', (tester) async {
    final storage = _MemoryStorage(
      log: encodeCrashEntries([_entry(CrashSource.asyncError, 'boom')]),
    );
    final log = await installLog(storage);

    await tester.pumpWidget(_app());
    await tester.pumpAndSettle();
    expect(find.text(L10n.current.crash_log_source_background), findsOneWidget);

    await tester.tap(find.byIcon(Icons.more_vert));
    await tester.pumpAndSettle();
    await tester.tap(find.text(L10n.current.crash_log_clear));
    await tester.pumpAndSettle();

    expect(find.text(L10n.current.crash_log_empty), findsOneWidget);
    expect(decodeCrashEntries(storage.log), isEmpty);

    log.dispose();
  });
}
