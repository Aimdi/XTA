import 'dart:developer' as developer;

import 'package:flutter/foundation.dart';
import 'package:logging/logging.dart';

// Keep source locations, not arbitrary lines, local paths or function text.
final _codeFrame = RegExp(
  r'^\s*(#\d+)\s+[^\r\n]+\(((?:package:[a-zA-Z0-9_]+/[a-zA-Z0-9_./-]+\.dart|dart:[a-zA-Z0-9_./-]+)(?::\d+){1,2})\)\s*$',
);

String diagnosticStack(StackTrace? stack) {
  final frames = (stack?.toString() ?? '')
      .split('\n')
      .map(_codeFrame.firstMatch)
      .whereType<RegExpMatch>()
      .take(80)
      .map((match) => '${match.group(1)} ${match.group(2)}')
      .toList();
  return frames.isEmpty ? '(no code frames available)' : frames.join('\n');
}

typedef DiagnosticLogWriter =
    void Function(String message, {required String name, required int level, Object? error, StackTrace? stackTrace});

void writeDiagnosticLog(LogRecord record, {bool releaseMode = kReleaseMode, DiagnosticLogWriter? writer}) {
  final write = writer ?? developer.log;
  write(
    releaseMode
        ? '${record.level.name} diagnostic${record.error == null ? '' : ' (${record.error.runtimeType})'}'
        : record.message,
    name: record.loggerName,
    level: record.level.value,
    error: releaseMode ? null : record.error,
    stackTrace: releaseMode
        ? (record.stackTrace == null ? null : StackTrace.fromString(diagnosticStack(record.stackTrace)))
        : record.stackTrace,
  );
}

void handleFlutterDiagnostic(
  FlutterErrorDetails details, {
  bool releaseMode = kReleaseMode,
  FlutterExceptionHandler? previousHandler,
  DiagnosticLogWriter? writer,
}) {
  if (!releaseMode) {
    previousHandler?.call(details);
    return;
  }
  writeDiagnosticLog(
    LogRecord(Level.SEVERE, 'Flutter error', 'Flutter', details.exception, details.stack),
    releaseMode: true,
    writer: writer,
  );
}
