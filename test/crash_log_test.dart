import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/utils/breadcrumb_http.dart';
import 'package:xta/utils/breadcrumbs.dart';
import 'package:xta/utils/crash_log.dart';
import 'package:xta/utils/crash_log_entry.dart';
import 'package:xta/utils/crash_session.dart';

/// Stands in for the two files on the device.
class _MemoryStorage implements CrashLogStorage {
  String log = '';
  String? session;
  int logWrites = 0;

  @override
  Future<String> readLog() async => log;

  @override
  Future<void> writeLog(String contents) async {
    logWrites++;
    log = contents;
  }

  @override
  Future<String?> readSession() async => session;

  @override
  Future<void> writeSession(String contents) async => session = contents;

  @override
  Future<void> clearSession() async => session = null;
}

CrashLogEntry _entry(
  String error, {
  String? stack,
  CrashSource source = CrashSource.flutter,
  DateTime? at,
}) {
  final when = at ?? DateTime.utc(2026, 8, 22, 10);
  return CrashLogEntry(
    firstSeen: when,
    lastSeen: when,
    source: source,
    error: error,
    stack: stack,
  );
}

void main() {
  group('breadcrumbs', () {
    test('a busy category cannot evict another one', () {
      final crumbs = Breadcrumbs();
      crumbs.drop(Crumb.route, 'push /profile');
      for (var i = 0; i < 40; i++) {
        crumbs.drop(Crumb.fetch, 'pixiv GET /v1/illust/$i');
      }

      final lines = crumbs.lines;
      expect(lines.where((line) => line.contains('/profile')), hasLength(1));
      expect(
        lines.where((line) => line.contains('fetch')),
        hasLength(Breadcrumbs.maxPerCategory),
      );
    });

    test('an immediate repeat is collapsed into a count', () {
      final crumbs = Breadcrumbs();
      crumbs
        ..drop(Crumb.fetch, 'booru GET /posts.json')
        ..drop(Crumb.fetch, 'booru GET /posts.json')
        ..drop(Crumb.fetch, 'booru GET /posts.json');

      expect(crumbs.trail, hasLength(1));
      expect(crumbs.trail.single.count, 3);
      expect(crumbs.lines.single, contains('(x3)'));
    });

    test('the trail is chronological across categories', () {
      var tick = DateTime.utc(2026, 8, 22, 10);
      final crumbs = Breadcrumbs()
        ..now = () => tick = tick.add(const Duration(seconds: 1));

      crumbs
        ..drop(Crumb.route, 'push /group')
        ..drop(Crumb.fetch, 'threads GET /graphql')
        ..drop(Crumb.route, 'push /status');

      expect(crumbs.lines.map((line) => line.split(': ').last).toList(), [
        'push /group',
        'threads GET /graphql',
        'push /status',
      ]);
    });
  });

  group('breadcrumb http client', () {
    test('records host and path but never the query string', () async {
      final crumbs = Breadcrumbs();
      final client = BreadcrumbHttpClient(
        'pixiv',
        breadcrumbs: crumbs,
        inner: MockClient((_) async => http.Response('{}', 200)),
      );

      await client.get(
        Uri.parse('https://app-api.pixiv.net/v1/search?word=secret&token=abc'),
      );

      expect(
        crumbs.lines.single,
        contains('pixiv GET app-api.pixiv.net/v1/search'),
      );
      expect(crumbs.lines.single, isNot(contains('secret')));
      expect(crumbs.lines.single, isNot(contains('abc')));
    });
  });

  group('entries', () {
    test('the same failure twice is one entry with a count', () {
      final first = _entry('RangeError', stack: '#0 tile\n#1 grid');
      final again = _entry(
        'RangeError',
        stack: '#0 tile\n#1 grid',
        at: DateTime.utc(2026, 8, 22, 10, 5),
      );

      final entries = appendCrashEntry(appendCrashEntry([], first), again);

      expect(entries, hasLength(1));
      expect(entries.single.count, 2);
      expect(entries.single.firstSeen, first.firstSeen);
      expect(entries.single.lastSeen, again.lastSeen);
    });

    test('different top frames are different entries', () {
      final entries = appendCrashEntry(
        appendCrashEntry([], _entry('RangeError', stack: '#0 tile')),
        _entry('RangeError', stack: '#0 masonry'),
      );

      expect(entries, hasLength(2));
    });

    test('the oldest entries go when the count cap is passed', () {
      var entries = <CrashLogEntry>[];
      for (var i = 0; i < kCrashLogMaxEntries + 5; i++) {
        entries = trimCrashEntries(
          appendCrashEntry(entries, _entry('boom $i')),
        );
      }

      expect(entries, hasLength(kCrashLogMaxEntries));
      expect(entries.first.error, 'boom 5');
      expect(entries.last.error, 'boom ${kCrashLogMaxEntries + 4}');
    });

    test('the byte cap wins over the entry cap', () {
      final huge = 'x' * 20000;
      var entries = <CrashLogEntry>[];
      for (var i = 0; i < 30; i++) {
        entries = trimCrashEntries(
          appendCrashEntry(entries, _entry('boom $i', stack: huge)),
          maxBytes: 100 * 1024,
        );
      }

      expect(entries.length, lessThan(30));
      expect(encodeCrashEntries(entries).length, lessThanOrEqualTo(100 * 1024));
      expect(entries.last.error, 'boom 29');
    });

    test('one entry over the cap is still kept', () {
      final entries = trimCrashEntries([
        _entry('boom', stack: 'x' * 5000),
      ], maxBytes: 100);

      expect(entries, hasLength(1));
    });

    test('a round trip through the file format keeps every field', () {
      final entry = CrashLogEntry(
        firstSeen: DateTime.utc(2026, 8, 22, 10),
        lastSeen: DateTime.utc(2026, 8, 22, 10, 1),
        source: CrashSource.nativeDeath,
        error: 'killed',
        stack: '#0 nowhere',
        context: 'app 4.12.0',
        breadcrumbs: const ['10:00:00 route: push /group'],
        vitals: const {'rss': '412.0MB'},
        count: 3,
      );

      final decoded = decodeCrashEntries(encodeCrashEntries([entry])).single;

      expect(decoded.source, CrashSource.nativeDeath);
      expect(decoded.error, 'killed');
      expect(decoded.stack, '#0 nowhere');
      expect(decoded.context, 'app 4.12.0');
      expect(decoded.breadcrumbs, entry.breadcrumbs);
      expect(decoded.vitals, entry.vitals);
      expect(decoded.count, 3);
      expect(decoded.lastSeen, entry.lastSeen);
    });

    test('a truncated or garbage file reads as an empty log', () {
      expect(decodeCrashEntries('[{"error": "half'), isEmpty);
      expect(decodeCrashEntries('not json at all'), isEmpty);
      expect(decodeCrashEntries('{"error": "not a list"}'), isEmpty);
      expect(decodeCrashEntries(''), isEmpty);
    });

    test('an entry with no error field is skipped, the rest survive', () {
      expect(
        decodeCrashEntries('[{"nope": 1}, {"error": "real"}]').single.error,
        'real',
      );
    });

    test('the formatted entry carries the trail and the vitals', () {
      final text = CrashLogEntry(
        firstSeen: DateTime.utc(2026, 8, 22, 10),
        lastSeen: DateTime.utc(2026, 8, 22, 10),
        source: CrashSource.flutter,
        error: 'RangeError',
        stack: '#0 tile',
        breadcrumbs: const ['10:00:00 route: push /pixiv'],
        vitals: const {'rss': '412.0MB'},
      ).format();

      expect(text, contains('--- flutter @ 2026-08-22T10:00:00.000Z'));
      expect(text, contains('RangeError'));
      expect(text, contains('rss=412.0MB'));
      expect(text, contains('push /pixiv'));
      expect(text, contains('#0 tile'));
    });
  });

  group('session marker', () {
    test('a marker left behind in the foreground becomes an entry', () {
      final entry = unexpectedExitEntry(
        CrashSession(
          startedAt: DateTime.utc(2026, 8, 22, 10),
          updatedAt: DateTime.utc(2026, 8, 22, 10, 2),
          appVersion: '4.12.0+400001080',
          lifecycle: 'resumed',
          breadcrumbs: const ['10:01:00 route: push /pixiv'],
          vitals: const {'rss': '512.0MB'},
        ),
      );

      expect(entry, isNotNull);
      expect(entry!.source, CrashSource.nativeDeath);
      expect(entry.breadcrumbs, contains('10:01:00 route: push /pixiv'));
      expect(entry.context, contains('120s'));
    });

    test('a run already in the background is not reported', () {
      expect(
        unexpectedExitEntry(
          CrashSession(
            startedAt: DateTime.utc(2026, 8, 22, 10),
            updatedAt: DateTime.utc(2026, 8, 22, 10, 2),
            appVersion: '4.12.0',
            lifecycle: 'paused',
          ),
        ),
        isNull,
      );
    });

    test('no marker means the last run shut down cleanly', () {
      expect(unexpectedExitEntry(null), isNull);
    });

    test('a half-written marker decodes to nothing rather than throwing', () {
      expect(CrashSession.decode('{"startedAt": "2026-08'), isNull);
      expect(CrashSession.decode('{}'), isNull);
      expect(CrashSession.decode(null), isNull);
    });
  });

  group('the log service', () {
    late _MemoryStorage storage;
    late CrashLog log;
    late DateTime clock;

    setUp(() {
      clock = DateTime.utc(2026, 8, 22, 10);
      storage = _MemoryStorage();
      log = CrashLog(
        storage: storage,
        breadcrumbs: Breadcrumbs(),
        readVitals: () => const {'rss': '128.0MB'},
        now: () => clock,
      );
    });

    tearDown(() => log.dispose());

    test('start reports a leftover foreground session, then re-arms', () async {
      storage.session = CrashSession(
        startedAt: DateTime.utc(2026, 8, 22, 9),
        updatedAt: DateTime.utc(2026, 8, 22, 9, 30),
        appVersion: '4.12.0',
        lifecycle: 'resumed',
      ).encode();

      await log.start(appVersion: '4.12.0+400001080');

      expect(log.entries.single.source, CrashSource.nativeDeath);
      expect(decodeCrashEntries(storage.log), hasLength(1));
      expect(
        CrashSession.decode(storage.session)!.appVersion,
        '4.12.0+400001080',
      );
    });

    test('an error recorded before start is kept, not overwritten', () async {
      storage.log = encodeCrashEntries([_entry('older')]);

      log.record(
        Exception('during startup'),
        null,
        source: CrashSource.flutter,
      );
      expect(storage.logWrites, 0, reason: 'nothing may be written yet');

      await log.start(appVersion: '4.12.0');

      expect(log.entries.map((entry) => entry.error), [
        'older',
        'Exception: during startup',
      ]);
    });

    test(
      'a storm of the same error writes once and stores one entry',
      () async {
        await log.start(appVersion: '4.12.0');
        final writesAfterStart = storage.logWrites;

        for (var i = 0; i < 60; i++) {
          log.record(
            Exception('rebuild loop'),
            StackTrace.fromString('#0 build'),
            source: CrashSource.flutter,
          );
        }
        await log.flush();

        expect(log.entries, hasLength(1));
        expect(log.entries.single.count, 60);
        expect(storage.logWrites - writesAfterStart, lessThanOrEqualTo(2));
      },
    );

    test('a recorded entry carries the trail and the vitals', () async {
      log.breadcrumbs.drop(Crumb.route, 'push /pixiv');
      await log.start(appVersion: '4.12.0');

      log.record(Exception('boom'), null, source: CrashSource.asyncError);
      await log.flush();

      final stored = decodeCrashEntries(storage.log).single;
      expect(stored.vitals['rss'], '128.0MB');
      expect(stored.breadcrumbs.single, contains('push /pixiv'));
    });

    test('a clean shutdown removes the marker', () async {
      await log.start(appVersion: '4.12.0');
      expect(storage.session, isNotNull);

      await log.markCleanShutdown();

      expect(storage.session, isNull);
    });

    test('clear empties the file as well as the screen', () async {
      await log.start(appVersion: '4.12.0');
      log.record(Exception('boom'), null, source: CrashSource.flutter);
      await log.flush();

      await log.clear();

      expect(log.entries, isEmpty);
      expect(decodeCrashEntries(storage.log), isEmpty);
    });

    test('recording never throws, even when vitals do', () async {
      final broken = CrashLog(
        storage: storage,
        breadcrumbs: Breadcrumbs(),
        readVitals: () => throw StateError('no binding'),
        now: () => clock,
      );
      await broken.start(appVersion: '4.12.0');

      expect(
        () =>
            broken.record(Exception('boom'), null, source: CrashSource.flutter),
        returnsNormally,
      );
      expect(broken.entries.single.vitals, isEmpty);
      broken.dispose();
    });
  });
}
