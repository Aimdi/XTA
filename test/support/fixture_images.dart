import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

/// The image disk cache asks a platform channel for its folder; refusing at once keeps
/// every fetch inside the test clock (the test HTTP client then answers 400).
void failImageDiskCache() {
  const pathProvider = MethodChannel('plugins.flutter.io/path_provider');
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(pathProvider, (_) async => throw PlatformException(code: 'unavailable'));
  addTearDown(() => messenger.setMockMethodCallHandler(pathProvider, null));
}

FlutterExceptionHandler? _testErrorHandler;

/// Every fixture image fails (the test HTTP client answers 400); a page scrolled away
/// before its failure arrives would otherwise be reported as a test error.
void ignoreFixtureImageFailures() {
  final handler = _testErrorHandler = FlutterError.onError;
  FlutterError.onError = (details) {
    if (details.library != 'image resource service') handler?.call(details);
  };
}

/// Lets failed image fetches and their single retry finish, then the UI settle.
Future<void> settleFixtureImages(WidgetTester tester) async {
  for (var i = 0; i < 4; i++) {
    await tester.pump(const Duration(milliseconds: 150));
  }
  await tester.pumpAndSettle();
}

/// Ends a test: unmounts, drains timers and gives the test its error handler back.
Future<void> disposeFixtureScreen(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox());
  await tester.pump(const Duration(seconds: 1));
  FlutterError.onError = _testErrorHandler;
}
