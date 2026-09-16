import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/utils/read_recovery.dart';
import 'package:xta/utils/read_visibility.dart';

void main() {
  setUp(() {
    VisibilityDetectorController.instance.updateInterval = Duration.zero;
    ReadRecovery.online = false;
  });
  tearDown(() {
    VisibilityDetectorController.instance.updateInterval = const Duration(milliseconds: 500);
    ReadRecovery.online = false;
  });
  testWidgets('visible failure retries once per connection event, not once per rebuild', (tester) async {
    final signals = StreamController<bool>.broadcast();
    addTearDown(signals.close);
    var retries = 0;
    Object failure = const SocketException('offline');
    Widget screen() => MaterialApp(
      home: Scaffold(
        body: ReadRecovery(
          networkEvents: signals.stream,
          recoverableFailure: () => failure,
          retry: () {
            retries++;
          },
          child: const SizedBox.expand(child: Text('Posts')),
        ),
      ),
    );
    await tester.pumpWidget(screen());
    await tester.pump();
    signals.add(false);
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    expect(retries, 0);
    signals.add(true);
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    expect(retries, 1);
    failure = TimeoutException('retry also failed');
    await tester.pumpWidget(screen());
    await tester.pump(const Duration(seconds: 20));
    expect(retries, 1);
    await tester.pumpWidget(const SizedBox.shrink());
  });
  testWidgets('hidden routes and disposed views never retry', (tester) async {
    final signals = StreamController<bool>.broadcast();
    addTearDown(signals.close);
    final navigator = GlobalKey<NavigatorState>();
    var retries = 0;
    await tester.pumpWidget(
      MaterialApp(
        navigatorKey: navigator,
        home: Scaffold(
          body: ReadRecovery(
            networkEvents: signals.stream,
            recoverableFailure: () => const SocketException('offline'),
            retry: () {
              retries++;
            },
            child: const SizedBox.expand(child: Text('Posts')),
          ),
        ),
      ),
    );
    await tester.pump();
    navigator.currentState!.push(MaterialPageRoute<void>(builder: (_) => const Scaffold(body: Text('Profile'))));
    await tester.pumpAndSettle();
    signals.add(true);
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    expect(retries, 0);
    await tester.pumpWidget(const SizedBox.shrink());
    signals.add(true);
    await tester.pump(const Duration(seconds: 1));
    expect(retries, 0);
    expect(tester.takeException(), isNull);
  });
  testWidgets('kept-alive group visibility pauses covered routes and resumes after back', (tester) async {
    final navigator = GlobalKey<NavigatorState>();
    final events = <String>[];
    await tester.pumpWidget(
      MaterialApp(
        navigatorKey: navigator,
        navigatorObservers: [readRouteObserver],
        home: Scaffold(
          body: ReadVisibility(
            onHidden: () => events.add('hidden'),
            onVisible: () => events.add('visible'),
            child: const SizedBox.expand(child: Text('Group')),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(events.last, 'visible');
    navigator.currentState!.push(MaterialPageRoute<void>(builder: (_) => const Scaffold(body: Text('Profile'))));
    await tester.pumpAndSettle();
    expect(events.last, 'hidden');
    navigator.currentState!.pop();
    await tester.pumpAndSettle();
    expect(events.last, 'visible');
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
