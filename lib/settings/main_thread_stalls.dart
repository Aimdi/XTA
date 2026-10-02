import 'dart:async';
import 'dart:io';

import 'package:path_provider/path_provider.dart';

/// Where the native watchdog records a blocked Android main thread, relative to the app's files directory.
const mainThreadStallFile = 'diagnostics/main-thread-stalls.txt';

/// The most recent lines of the record, enough for one stack and its resume line.
const mainThreadStallLines = 60;

/// What the watchdog recorded about the Android main thread during this launch.
///
/// The directory lookup is itself a platform call, so it is bounded: the report must not hang behind the very thread
/// it is asking about.
Future<String> mainThreadStallSummary({
  Duration timeout = const Duration(seconds: 2),
  Future<Directory> Function()? filesDirectory,
}) async {
  if (filesDirectory == null && !Platform.isAndroid) return 'not watched on this platform';
  try {
    final directory = await (filesDirectory ?? getApplicationSupportDirectory)().timeout(timeout);
    final file = File('${directory.path}/$mainThreadStallFile');
    if (!await file.exists()) return 'no stall recorded';
    return describeMainThreadStalls(await file.readAsString());
  } on TimeoutException {
    return 'unknown (the platform did not answer within ${timeout.inSeconds}s)';
  } catch (error) {
    return 'unreadable (${error.runtimeType})';
  }
}

/// Keeps the tail of the record: the latest stall, its stack, and whether the thread came back.
String describeMainThreadStalls(String contents) {
  final lines = contents.split('\n').where((line) => line.trim().isNotEmpty).toList();
  if (lines.isEmpty) return 'no stall recorded';
  final kept = lines.length > mainThreadStallLines ? lines.sublist(lines.length - mainThreadStallLines) : lines;
  return '\n${kept.map((line) => '  $line').join('\n')}';
}
