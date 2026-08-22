/// What the crash log holds, and the pure rules for keeping it small.
///
/// No Flutter and no file system, so the trimming, de-duplication and
/// formatting can all be exercised in a plain unit test.
library;

import 'dart:convert';

/// A rolling log is only useful if it cannot itself become the problem: 256 KB
/// is a few hundred stack traces, small enough to paste and to keep in memory.
const int kCrashLogMaxBytes = 256 * 1024;
const int kCrashLogMaxEntries = 40;

/// Where an entry came from. The value is written to disk, so it is stable.
enum CrashSource {
  /// `FlutterError.onError` — a widget, layout or paint failure.
  flutter('flutter'),

  /// An uncaught asynchronous error (`PlatformDispatcher.onError` or the
  /// guarded zone around `runApp`).
  asyncError('async'),

  /// An error thrown on a background isolate.
  isolate('isolate'),

  /// Not an error at all: the previous run never reported a clean shutdown, so
  /// the process was killed from the outside. Usually the Android low-memory
  /// killer, which leaves no Dart stack trace anywhere.
  nativeDeath('native-death'),

  /// Written by the reader from Settings, to prove the log works.
  test('test');

  final String stored;

  const CrashSource(this.stored);

  static CrashSource parse(String? value) => CrashSource.values.firstWhere(
    (source) => source.stored == value,
    orElse: () => CrashSource.asyncError,
  );
}

class CrashLogEntry {
  final DateTime firstSeen;
  final DateTime lastSeen;
  final CrashSource source;
  final String error;
  final String? stack;
  final String? context;

  /// Where the reader was, newest last.
  final List<String> breadcrumbs;

  /// Memory and image-cache numbers taken at the moment of the failure.
  final Map<String, String> vitals;

  final int count;

  const CrashLogEntry({
    required this.firstSeen,
    required this.lastSeen,
    required this.source,
    required this.error,
    this.stack,
    this.context,
    this.breadcrumbs = const [],
    this.vitals = const {},
    this.count = 1,
  });

  /// Two failures are "the same" when the type, message and top frames match.
  /// A rebuild loop throwing every frame must not fill the log with 40 copies
  /// of one bug and push the interesting history out.
  String get fingerprint {
    final frames = (stack ?? '')
        .split('\n')
        .map((line) => line.trim())
        .where((line) => line.isNotEmpty)
        .take(3)
        .join('|');
    return '${source.stored}|$error|$frames';
  }

  CrashLogEntry repeated(DateTime at) => CrashLogEntry(
    firstSeen: firstSeen,
    lastSeen: at,
    source: source,
    error: error,
    stack: stack,
    context: context,
    breadcrumbs: breadcrumbs,
    vitals: vitals,
    count: count + 1,
  );

  Map<String, dynamic> toJson() => {
    'firstSeen': firstSeen.toIso8601String(),
    'lastSeen': lastSeen.toIso8601String(),
    'source': source.stored,
    'error': error,
    if (stack != null) 'stack': stack,
    if (context != null) 'context': context,
    if (breadcrumbs.isNotEmpty) 'breadcrumbs': breadcrumbs,
    if (vitals.isNotEmpty) 'vitals': vitals,
    'count': count,
  };

  /// Every field is optional on the way in: this file survives app upgrades and
  /// is the last thing that should throw while a crash is being handled.
  static CrashLogEntry? fromJson(Object? raw) {
    if (raw is! Map) return null;
    final error = raw['error'];
    if (error is! String) return null;

    final firstSeen =
        DateTime.tryParse(raw['firstSeen'] as String? ?? '') ??
        DateTime.fromMillisecondsSinceEpoch(0);

    return CrashLogEntry(
      firstSeen: firstSeen,
      lastSeen:
          DateTime.tryParse(raw['lastSeen'] as String? ?? '') ?? firstSeen,
      source: CrashSource.parse(raw['source'] as String?),
      error: error,
      stack: raw['stack'] as String?,
      context: raw['context'] as String?,
      breadcrumbs: [
        for (final crumb in (raw['breadcrumbs'] as List?) ?? const [])
          if (crumb is String) crumb,
      ],
      vitals: {
        for (final entry in ((raw['vitals'] as Map?) ?? const {}).entries)
          '${entry.key}': '${entry.value}',
      },
      count: (raw['count'] as num?)?.toInt() ?? 1,
    );
  }

  /// Deliberately not localised. This is a technical dump pasted into a bug
  /// report; a stack trace wrapped in translated prose is harder to read for
  /// whoever has to fix it, and the surrounding screen is localised anyway.
  String format() {
    final lines = <String>[
      '--- ${source.stored} @ ${lastSeen.toIso8601String()}'
          '${count > 1 ? ' (x$count, first ${firstSeen.toIso8601String()})' : ''}',
      error,
    ];

    if (context != null && context!.isNotEmpty) {
      lines.add('context: $context');
    }
    if (vitals.isNotEmpty) {
      lines.add(
        'vitals: ${vitals.entries.map((e) => '${e.key}=${e.value}').join(' ')}',
      );
    }
    if (breadcrumbs.isNotEmpty) {
      lines
        ..add('trail:')
        ..addAll(breadcrumbs.map((crumb) => '  $crumb'));
    }
    if (stack != null && stack!.trim().isNotEmpty) {
      lines
        ..add('stack:')
        ..addAll(stack!.trimRight().split('\n').map((line) => '  $line'));
    }

    return lines.join('\n');
  }
}

/// Adds [entry] to [entries], collapsing it into the newest match instead of
/// appending a duplicate. Returns a new list; the input is not modified.
List<CrashLogEntry> appendCrashEntry(
  List<CrashLogEntry> entries,
  CrashLogEntry entry,
) {
  final index = entries.lastIndexWhere(
    (existing) => existing.fingerprint == entry.fingerprint,
  );
  if (index == -1) return [...entries, entry];

  final merged = entries[index].repeated(entry.lastSeen);
  return [...entries]
    ..removeAt(index)
    ..add(merged);
}

/// Drops the oldest entries until the log fits both caps.
///
/// A single stack trace can be enormous, so the byte cap is checked against the
/// encoded form rather than the entry count alone. One entry that is on its own
/// over the cap is still kept: a log with nothing in it is worse.
List<CrashLogEntry> trimCrashEntries(
  List<CrashLogEntry> entries, {
  int maxEntries = kCrashLogMaxEntries,
  int maxBytes = kCrashLogMaxBytes,
}) {
  var kept = entries.length > maxEntries
      ? entries.sublist(entries.length - maxEntries)
      : [...entries];

  while (kept.length > 1 && _encodedBytes(kept) > maxBytes) {
    kept = kept.sublist(1);
  }

  return kept;
}

int _encodedBytes(List<CrashLogEntry> entries) =>
    utf8.encode(encodeCrashEntries(entries)).length;

String encodeCrashEntries(List<CrashLogEntry> entries) =>
    jsonEncode([for (final entry in entries) entry.toJson()]);

/// Never throws: a corrupt or half-written file reads as an empty log rather
/// than taking the diagnostics screen down with it.
List<CrashLogEntry> decodeCrashEntries(String raw) {
  if (raw.trim().isEmpty) return const [];
  try {
    final decoded = jsonDecode(raw);
    if (decoded is! List) return const [];
    return [for (final item in decoded) ?CrashLogEntry.fromJson(item)];
  } catch (_) {
    return const [];
  }
}

/// The whole log as one pasteable block, newest last.
String formatCrashLog(List<CrashLogEntry> entries, {String? header}) {
  if (entries.isEmpty) return header ?? '';
  return [
    if (header != null && header.isNotEmpty) ...[header, ''],
    for (final entry in entries) ...[entry.format(), ''],
  ].join('\n').trimRight();
}
