import 'dart:async';
import 'dart:io';
import 'dart:isolate';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:logging/logging.dart';
import 'package:path_provider/path_provider.dart';
import 'package:xta/utils/breadcrumbs.dart';
import 'package:xta/utils/crash_log_entry.dart';
import 'package:xta/utils/crash_session.dart';

/// An on-device record of everything that went wrong, so a reader can hand over
/// the one thing nobody has been able to get: what the app was actually doing
/// when it died.
///
/// Nothing leaves the device. The log is a file in the app's own storage, and
/// the only way it reaches anyone is the reader tapping share on the Crash log
/// screen.
class CrashLog with WidgetsBindingObserver {
  static final _log = Logger('CrashLog');

  static CrashLog? instance;

  /// A first entry must reach the disk before the process can die, but a
  /// rebuild loop throwing every frame must not rewrite the file 60 times a
  /// second. Writes are immediate, then rate limited to this.
  static const Duration minWriteInterval = Duration(milliseconds: 500);

  /// How often the "still alive" marker is refreshed while the app is on
  /// screen. It is a couple of kilobytes, and the cost of a stale one is a
  /// vaguer breadcrumb trail on a native kill.
  static const Duration sessionHeartbeat = Duration(seconds: 15);

  final CrashLogStorage storage;
  final Breadcrumbs breadcrumbs;
  final Map<String, String> Function() readVitals;
  DateTime Function() now;

  List<CrashLogEntry> _entries = const [];
  CrashSession? _session;
  Timer? _pendingWrite;
  Timer? _heartbeat;
  DateTime _lastWrite = DateTime.fromMillisecondsSinceEpoch(0);
  Future<void> _writes = Future.value();
  bool _installed = false;
  bool _loaded = false;

  CrashLog({
    required this.storage,
    Breadcrumbs? breadcrumbs,
    Map<String, String> Function()? readVitals,
    DateTime Function()? now,
  }) : breadcrumbs = breadcrumbs ?? Breadcrumbs.instance,
       readVitals = readVitals ?? currentVitals,
       now = now ?? DateTime.now;

  List<CrashLogEntry> get entries => List.unmodifiable(_entries);

  /// Reads the existing log, turns a leftover session marker into a
  /// native-death entry, and opens a marker for this run.
  ///
  /// Safe to leave unawaited: anything [install] catches in the meantime is
  /// held in memory and merged in here, and nothing is written to the file
  /// until the history it would replace has been read back.
  Future<void> start({required String appVersion}) async {
    final stored = decodeCrashEntries(await storage.readLog());
    final leftover = unexpectedExitEntry(
      CrashSession.decode(await storage.readSession()),
      at: now(),
    );

    _entries = trimCrashEntries([...stored, ?leftover, ..._entries]);
    _loaded = true;

    final at = now();
    _session = CrashSession(
      startedAt: at,
      updatedAt: at,
      appVersion: appVersion,
      lifecycle: 'resumed',
      breadcrumbs: breadcrumbs.lines,
      vitals: _safeVitals(),
    );

    await _writeLog();
    await _writeSession();
    _startHeartbeat();
  }

  /// Hooks the three ways an error escapes in Flutter.
  ///
  /// Chains whatever was already installed rather than replacing it, so the
  /// console dump and the opt-in GitHub reporter keep working.
  void install() {
    if (_installed) return;
    _installed = true;

    final previousFlutter = FlutterError.onError;
    FlutterError.onError = (details) {
      record(
        details.exception,
        details.stack,
        source: CrashSource.flutter,
        context: details.context?.toString(),
      );
      previousFlutter?.call(details);
    };

    final previousPlatform = PlatformDispatcher.instance.onError;
    PlatformDispatcher.instance.onError = (error, stack) {
      record(error, stack, source: CrashSource.asyncError);
      previousPlatform?.call(error, stack);
      // Handled either way: an uncaught async error must not abort the isolate.
      return true;
    };

    try {
      Isolate.current.addErrorListener(
        RawReceivePort((dynamic pair) {
          if (pair is! List || pair.length < 2) return;
          record(
            pair.first ?? 'unknown isolate error',
            StackTrace.fromString('${pair.last}'),
            source: CrashSource.isolate,
          );
        }).sendPort,
      );
    } catch (e) {
      _log.info('Unable to listen for isolate errors: $e');
    }

    WidgetsBinding.instance.addObserver(this);
  }

  /// Appends [error], with the breadcrumb trail and memory numbers taken now.
  ///
  /// Synchronous and total: this runs from inside an error handler, so it may
  /// not throw and may not wait.
  void record(
    Object error,
    StackTrace? stack, {
    required CrashSource source,
    String? context,
  }) {
    try {
      final at = now();
      final entry = CrashLogEntry(
        firstSeen: at,
        lastSeen: at,
        source: source,
        error: error.toString(),
        stack: stack?.toString(),
        context: context,
        breadcrumbs: breadcrumbs.lines,
        vitals: _safeVitals(),
      );

      _entries = trimCrashEntries(appendCrashEntry(_entries, entry));
      _scheduleWrite();
    } catch (e) {
      _log.warning('Unable to record a crash: $e');
    }
  }

  /// Proof for the reader that the log works, written the same way a real
  /// failure is.
  Future<void> recordTestEntry() async {
    record(
      Exception('XTA crash log test entry'),
      StackTrace.current,
      source: CrashSource.test,
      context: 'written from Settings',
    );
    await flush();
  }

  Future<void> clear() async {
    _entries = const [];
    await _writeLog();
  }

  Future<void> flush() {
    _pendingWrite?.cancel();
    _pendingWrite = null;
    return _writeLog();
  }

  /// Marks this run as finished so the next launch does not report it as a
  /// crash.
  Future<void> markCleanShutdown() async {
    _heartbeat?.cancel();
    _heartbeat = null;
    _session = null;
    await flush();
    await storage.clearSession();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    breadcrumbs.drop(Crumb.lifecycle, state.name);

    if (state == AppLifecycleState.detached) {
      unawaited(markCleanShutdown());
      return;
    }

    _session = _session?.copyWith(
      updatedAt: now(),
      lifecycle: state.name,
      breadcrumbs: breadcrumbs.lines,
      vitals: _safeVitals(),
    );
    unawaited(_writeSession());

    if (state == AppLifecycleState.resumed) {
      _startHeartbeat();
    } else {
      _heartbeat?.cancel();
      _heartbeat = null;
    }
  }

  /// The single best warning an out-of-memory kill gives. Android delivers it
  /// seconds before it decides which process to take, so an entry that says
  /// "memory pressure at 412MB, then nothing" identifies a kill that would
  /// otherwise leave no trace at all.
  @override
  void didHaveMemoryPressure() {
    final vitals = _safeVitals();
    breadcrumbs.drop(
      Crumb.media,
      'system memory pressure '
      '(rss ${vitals['rss'] ?? '?'}, images ${vitals['imageCache'] ?? '?'})',
    );
    _session = _session?.copyWith(
      updatedAt: now(),
      breadcrumbs: breadcrumbs.lines,
      vitals: vitals,
    );
    unawaited(_writeSession());
  }

  void dispose() {
    _pendingWrite?.cancel();
    _heartbeat?.cancel();
    if (_installed) WidgetsBinding.instance.removeObserver(this);
  }

  void _startHeartbeat() {
    _heartbeat?.cancel();
    _heartbeat = Timer.periodic(sessionHeartbeat, (_) {
      _session = _session?.copyWith(
        updatedAt: now(),
        breadcrumbs: breadcrumbs.lines,
        vitals: _safeVitals(),
      );
      unawaited(_writeSession());
    });
  }

  void _scheduleWrite() {
    // Writing before [start] has read the file back would replace the whole
    // history with the one entry that just arrived.
    if (!_loaded || _pendingWrite != null) return;

    final since = now().difference(_lastWrite);
    if (since >= minWriteInterval) {
      unawaited(_writeLog());
      return;
    }

    _pendingWrite = Timer(minWriteInterval - since, () {
      _pendingWrite = null;
      unawaited(_writeLog());
    });
  }

  /// Serialised through [_writes] so two failures a millisecond apart cannot
  /// interleave and leave half a file behind.
  Future<void> _writeLog() {
    _lastWrite = now();
    final snapshot = encodeCrashEntries(_entries);
    return _writes = _writes.then((_) async {
      try {
        await storage.writeLog(snapshot);
      } catch (e) {
        _log.warning('Unable to write the crash log: $e');
      }
    });
  }

  Future<void> _writeSession() {
    final session = _session;
    if (session == null) return Future.value();
    final encoded = session.encode();
    return _writes = _writes.then((_) async {
      try {
        await storage.writeSession(encoded);
      } catch (e) {
        _log.info('Unable to write the session marker: $e');
      }
    });
  }

  Map<String, String> _safeVitals() {
    try {
      return readVitals();
    } catch (_) {
      return const {};
    }
  }
}

/// Memory and decoded-image numbers, which is what an out-of-memory kill is
/// made of. Every reading is optional: none of them is worth an exception
/// inside a crash handler.
Map<String, String> currentVitals() {
  final vitals = <String, String>{};

  try {
    vitals['rss'] = _megabytes(ProcessInfo.currentRss);
    vitals['rssPeak'] = _megabytes(ProcessInfo.maxRss);
  } catch (_) {
    // Not every platform reports it.
  }

  try {
    final cache = PaintingBinding.instance.imageCache;
    vitals['imageCache'] =
        '${_megabytes(cache.currentSizeBytes)}/'
        '${_megabytes(cache.maximumSizeBytes)}';
    vitals['images'] = '${cache.currentSize}+${cache.liveImageCount}live';
  } catch (_) {
    // The binding is not up yet.
  }

  return vitals;
}

String _megabytes(int bytes) =>
    '${(bytes / (1024 * 1024)).toStringAsFixed(1)}MB';

/// Where the log and the session marker live.
///
/// Split out so the whole service can be driven from a test without a device.
abstract interface class CrashLogStorage {
  Future<String> readLog();

  Future<void> writeLog(String contents);

  Future<String?> readSession();

  Future<void> writeSession(String contents);

  Future<void> clearSession();
}

/// Two plain files in the app's private support directory — not the cache
/// directory, which Android is free to empty exactly when a device is short of
/// space, which is when the log matters most.
class FileCrashLogStorage implements CrashLogStorage {
  static const logFileName = 'crash-log.json';
  static const sessionFileName = 'crash-session.json';

  Directory? _directory;

  Future<File> _file(String name) async {
    final directory = _directory ??= await getApplicationSupportDirectory();
    await directory.create(recursive: true);
    return File('${directory.path}/$name');
  }

  @override
  Future<String> readLog() async => await _read(logFileName) ?? '';

  @override
  Future<void> writeLog(String contents) => _write(logFileName, contents);

  @override
  Future<String?> readSession() => _read(sessionFileName);

  @override
  Future<void> writeSession(String contents) =>
      _write(sessionFileName, contents);

  @override
  Future<void> clearSession() async {
    try {
      final file = await _file(sessionFileName);
      if (await file.exists()) await file.delete();
    } catch (_) {
      // A marker that will not delete only costs one spurious entry.
    }
  }

  Future<String?> _read(String name) async {
    try {
      final file = await _file(name);
      return await file.exists() ? await file.readAsString() : null;
    } catch (_) {
      return null;
    }
  }

  /// Written to a sibling and renamed, so a process killed mid-write leaves the
  /// previous log intact instead of a truncated one.
  Future<void> _write(String name, String contents) async {
    final file = await _file(name);
    final temporary = File('${file.path}.tmp');
    await temporary.writeAsString(contents, flush: true);
    await temporary.rename(file.path);
  }
}
