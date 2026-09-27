import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:logging/logging.dart';
import 'package:xta/utils/diagnostic_privacy.dart';

void main() {
  test('release sink drops raw messages and exception objects, including message-only failures', () {
    final captured = <({String message, String name, int level, Object? error, StackTrace? stack})>[];
    void capture(String message, {required String name, required int level, Object? error, StackTrace? stackTrace}) {
      captured.add((message: message, name: name, level: level, error: error, stack: stackTrace));
    }

    for (final error in [const FormatException('private-body', 'private-source'), null]) {
      writeDiagnosticLog(
        LogRecord(
          Level.WARNING,
          'https://reader:private-password@example.org/private-path',
          'CacheHelper',
          error,
          StackTrace.fromString('#0 load (package:xta/utils/cache.dart:30:5)\nprivate-path'),
        ),
        releaseMode: true,
        writer: capture,
      );
    }
    expect(captured, hasLength(2));
    expect(captured.toString(), isNot(contains('private-')));
    expect(captured.first.name, 'CacheHelper');
    expect(captured.first.level, Level.WARNING.value);
    expect(captured.first.message, contains('FormatException'));
    expect(captured.first.error, isNull);
    expect(captured.first.stack.toString(), contains('package:xta/utils/cache.dart:30:5'));
  });

  test('debug sink preserves local debugging messages, exceptions and stacks', () {
    final expectedError = Exception('debug detail');
    final stack = StackTrace.current;
    var called = false;
    writeDiagnosticLog(
      LogRecord(Level.WARNING, 'debug message', 'Test', expectedError, stack),
      releaseMode: false,
      writer: (message, {required name, required level, error, stackTrace}) {
        called = true;
        expect(message, 'debug message');
        expect(error, same(expectedError));
        expect(stackTrace, same(stack));
      },
    );
    expect(called, isTrue);
  });

  test('stack filtering drops URLs, local paths and malformed frames instead of publishing them', () {
    final stack = diagnosticStack(
      StackTrace.fromString(
        '#0 load (package:xta/home/feed.dart:10:2)\n'
        '#1 read (file:///home/private-reader/file.dart:20:4)\n'
        '#2 read (https://private-host/feed?key=private-key:20:4)\n'
        '#3 read (package:xta/feed.dart?token=private-key:20:4)\n'
        'private-arbitrary-text\n'
        '<asynchronous suspension>\n'
        '#4 _Timer._runTimers (dart:isolate-patch/timer_impl.dart:430:19)',
      ),
    );
    expect(stack, contains('package:xta/home/feed.dart:10:2'));
    expect(stack, contains('dart:isolate-patch/timer_impl.dart:430:19'));
    expect(stack, isNot(contains('private-')));
    expect(diagnosticStack(null), '(no code frames available)');
    expect(diagnosticStack(StackTrace.fromString('private-unknown-format')), '(no code frames available)');
  });

  test('release Flutter errors use safe diagnostics while debug delegates to the previous handler', () {
    final details = FlutterErrorDetails(exception: Exception('private-widget-content'), stack: StackTrace.current);
    var previousCalls = 0;
    var loggedCalls = 0;
    void previous(FlutterErrorDetails value) {
      previousCalls++;
      expect(value, same(details));
    }

    void capture(String message, {required String name, required int level, Object? error, StackTrace? stackTrace}) {
      loggedCalls++;
      expect('$message $error $stackTrace', isNot(contains('private-')));
      expect(level, Level.SEVERE.value);
    }

    handleFlutterDiagnostic(details, releaseMode: true, previousHandler: previous, writer: capture);
    expect(previousCalls, 0);
    expect(loggedCalls, 1);
    handleFlutterDiagnostic(details, releaseMode: false, previousHandler: previous, writer: capture);
    expect(previousCalls, 1);
    expect(loggedCalls, 1);
  });
}
