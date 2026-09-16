import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:xta/main.dart' as app;
import 'package:xta/constants.dart';
import 'package:xta/profile/profile.dart';

/// Run on a configured test install. No follows/groups are changed by this test.
void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets('repeated profile visits and back during loading keep navigation responsive', (tester) async {
    app.main();
    // Bounded pumps: an intentionally stalled spinner must not block the test.
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 500));
    }
    final navigator = tester.state<NavigatorState>(find.byType(Navigator).first);
    expect(navigator.canPop(), isFalse, reason: 'Finish setup and dismiss account/update dialogs before running.');
    final durations = <int>[];
    await binding.traceAction(() async {
      for (var i = 0; i < 30; i++) {
        final watch = Stopwatch()..start();
        final closed = navigator.pushNamed(
          routeProfile,
          arguments: ProfileScreenArguments(
            null,
            const String.fromEnvironment('XTA_TEST_PROFILE', defaultValue: 'FlutterDev'),
            null,
          ),
        );
        await tester.pump(const Duration(milliseconds: 150));
        await tester.pump(const Duration(milliseconds: 350));
        expect(navigator.canPop(), isTrue);
        navigator.pop();
        await tester.pump(const Duration(milliseconds: 500));
        await closed.timeout(const Duration(seconds: 3));
        durations.add(watch.elapsedMilliseconds);
        expect(tester.takeException(), isNull, reason: 'profile visit $i');
        expect(navigator.canPop(), isFalse);
      }
    }, reportKey: 'reader_session_timeline');
    binding.reportData ??= {};
    binding.reportData!['profile_return_ms'] = durations;
  });
}
