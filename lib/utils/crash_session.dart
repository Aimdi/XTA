/// The marker that turns a silent native kill into a log line.
///
/// Dart never sees an Android low-memory kill: the process is gone, no handler
/// runs, and the reader only knows "it closed itself". So each run leaves a
/// small marker file behind and removes it on a clean shutdown. A marker still
/// present on the next launch means the last run did not get to finish, and the
/// state it was in when it stopped being updated says roughly why.
library;

import 'dart:convert';

import 'package:xta/utils/crash_log_entry.dart';

class CrashSession {
  final DateTime startedAt;
  final DateTime updatedAt;
  final String appVersion;

  /// The last `AppLifecycleState` name seen. `resumed` means the app was on
  /// screen when it died, which is the case worth reporting; Android reclaims
  /// backgrounded processes as a matter of routine.
  final String lifecycle;

  final List<String> breadcrumbs;
  final Map<String, String> vitals;

  const CrashSession({
    required this.startedAt,
    required this.updatedAt,
    required this.appVersion,
    required this.lifecycle,
    this.breadcrumbs = const [],
    this.vitals = const {},
  });

  bool get wasInForeground => lifecycle == 'resumed';

  CrashSession copyWith({
    DateTime? updatedAt,
    String? lifecycle,
    List<String>? breadcrumbs,
    Map<String, String>? vitals,
  }) => CrashSession(
    startedAt: startedAt,
    updatedAt: updatedAt ?? this.updatedAt,
    appVersion: appVersion,
    lifecycle: lifecycle ?? this.lifecycle,
    breadcrumbs: breadcrumbs ?? this.breadcrumbs,
    vitals: vitals ?? this.vitals,
  );

  Map<String, dynamic> toJson() => {
    'startedAt': startedAt.toIso8601String(),
    'updatedAt': updatedAt.toIso8601String(),
    'appVersion': appVersion,
    'lifecycle': lifecycle,
    'breadcrumbs': breadcrumbs,
    'vitals': vitals,
  };

  String encode() => jsonEncode(toJson());

  /// Never throws: a marker half-written by a process that was killed mid-write
  /// is exactly the case this file exists for.
  static CrashSession? decode(String? raw) {
    if (raw == null || raw.trim().isEmpty) return null;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map) return null;
      final startedAt = DateTime.tryParse(
        decoded['startedAt'] as String? ?? '',
      );
      if (startedAt == null) return null;

      return CrashSession(
        startedAt: startedAt,
        updatedAt:
            DateTime.tryParse(decoded['updatedAt'] as String? ?? '') ??
            startedAt,
        appVersion: decoded['appVersion'] as String? ?? 'unknown',
        lifecycle: decoded['lifecycle'] as String? ?? 'unknown',
        breadcrumbs: [
          for (final crumb in (decoded['breadcrumbs'] as List?) ?? const [])
            if (crumb is String) crumb,
        ],
        vitals: {
          for (final entry in ((decoded['vitals'] as Map?) ?? const {}).entries)
            '${entry.key}': '${entry.value}',
        },
      );
    } catch (_) {
      return null;
    }
  }
}

/// The log entry a leftover [session] deserves, or null when there is nothing
/// worth telling the reader about.
///
/// A run that was already in the background when it stopped being updated is
/// not reported: Android kills backgrounded apps constantly and a log full of
/// those would bury the one entry that matters.
CrashLogEntry? unexpectedExitEntry(CrashSession? session, {DateTime? at}) {
  if (session == null) return null;
  if (!session.wasInForeground) return null;

  final ended = session.updatedAt;
  return CrashLogEntry(
    firstSeen: ended,
    lastSeen: at ?? ended,
    source: CrashSource.nativeDeath,
    error:
        'Previous session ended unexpectedly while on screen. '
        'No Dart error was recorded, so the process was killed from outside '
        '(most often the Android low-memory killer, or a native crash).',
    context:
        'app ${session.appVersion}, '
        'ran ${ended.difference(session.startedAt).inSeconds}s',
    breadcrumbs: session.breadcrumbs,
    vitals: session.vitals,
  );
}
