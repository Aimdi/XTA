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
  testWidgets('automatic recovery has a finite budget across rebuilds and failed attempts', (tester) async {
    final signals = StreamController<bool>.broadcast();
    final changes = ValueNotifier(0);
    addTearDown(signals.close);
    addTearDown(changes.dispose);
    var retries = 0;
    var loading = false;
    Object? failure = const SocketException('offline');
    Widget screen() => MaterialApp(
      home: Scaffold(
        body: ReadRecovery(
          networkEvents: signals.stream,
          changes: changes,
          isLoading: () => loading,
          recoverableFailure: () => failure,
          retry: () {
            retries++;
            loading = true;
            failure = null;
            changes.value++;
            scheduleMicrotask(() {
              failure = TimeoutException('still unavailable');
              loading = false;
              changes.value++;
            });
          },
          child: const SizedBox.expand(child: Text('Posts')),
        ),
      ),
    );
    await tester.pumpWidget(screen());
    await tester.pump();
    signals.add(true);
    await tester.pump();
    for (final delay in [2, 5, 15]) {
      await tester.pump(Duration(seconds: delay));
      await tester.pump();
    }
    expect(retries, 3);
    signals.add(true);
    await tester.pumpWidget(screen());
    await tester.pump(const Duration(minutes: 1));
    expect(retries, 3);
    signals.add(false);
    await tester.pump();
    signals.add(true);
    await tester.pump();
    await tester.pump(const Duration(seconds: 2));
    expect(retries, 4);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('background recovery waits for resume without resetting its budget', (tester) async {
    ReadRecovery.online = true;
    var retries = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ReadRecovery(
            recoverableFailure: () => const SocketException('offline'),
            retry: () => retries++,
            child: const SizedBox.expand(),
          ),
        ),
      ),
    );
    await tester.pump();
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.pump();
    await tester.pump(const Duration(seconds: 30));
    expect(retries, 0);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    await tester.pump(const Duration(seconds: 2));
    expect(retries, 1);
    await tester.pump(const Duration(minutes: 1));
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
        navigatorObservers: [readRouteObserver],
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
    navigator.currentState!.pop();
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 2));
    expect(retries, 1);
    await tester.pumpWidget(const SizedBox.shrink());
    signals.add(true);
    await tester.pump(const Duration(seconds: 1));
    expect(retries, 1);
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
