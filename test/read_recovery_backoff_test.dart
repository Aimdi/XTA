import 'dart:async';
import 'dart:io' show SocketException;

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/errors.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/ui/rate_limit_retry.dart';
import 'package:xta/ui/reader_failure.dart';
import 'package:xta/utils/read_recovery.dart';
import 'package:xta/utils/read_visibility.dart';

HttpException _http(int status, [Map<String, String> headers = const {}]) =>
    HttpException(http.Response('', status, headers: headers));

/// A reading surface whose every retry loads and fails again with [outcome].
class _Surface {
  final changes = ValueNotifier(0);
  final network = StreamController<ReadNetworkState>.broadcast();
  bool loading = false;
  Object? failure;
  int retries = 0;
  Object? Function() outcome;
  _Surface(this.failure, {Object? Function()? outcome}) : outcome = outcome ?? (() => TimeoutException('again'));

  void retry() {
    retries++;
    loading = true;
    failure = null;
    changes.value++;
    scheduleMicrotask(() {
      failure = outcome();
      loading = false;
      changes.value++;
    });
  }

  void fail(Object? next) {
    failure = next;
    changes.value++;
  }

  Widget recovery({
    FutureOr<void> Function()? retry,
    Future<DateTime?> Function(Object?)? reset,
    bool networkStream = false,
    Widget child = const SizedBox.expand(),
  }) => ReadRecovery(
    changes: changes,
    networkStates: networkStream ? network.stream : null,
    isLoading: () => loading,
    recoverableFailure: () => failure,
    retry: retry ?? this.retry,
    rateLimitReset: reset ?? readRateLimitReset,
    child: child,
  );

  Future<void> dispose() async {
    await network.close();
    changes.dispose();
  }
}

Widget _app(Widget body, {GlobalKey<NavigatorState>? navigator, bool observeRoutes = true}) => MaterialApp(
  navigatorKey: navigator,
  navigatorObservers: [if (observeRoutes) readRouteObserver],
  locale: const Locale('de'),
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  home: Scaffold(body: body),
);

/// Two frames: the visibility report, then the post-frame callback delivering it.
Future<void> _show(WidgetTester tester, Widget app) async {
  await tester.pumpWidget(app);
  await tester.pump();
  await tester.pump();
}

/// Each backoff step fires exactly at its delay and not a second earlier.
Future<void> _expectBackoff(WidgetTester tester, _Surface surface, {int from = 0}) async {
  for (final (step, delay) in readRetryDelays.indexed) {
    await tester.pump(delay - const Duration(seconds: 1));
    expect(surface.retries, from + step, reason: 'before step ${step + 1}');
    await tester.pump(const Duration(seconds: 1));
    expect(surface.retries, from + step + 1, reason: 'at step ${step + 1}');
  }
}

void main() {
  setUp(() {
    VisibilityDetectorController.instance.updateInterval = Duration.zero;
    ReadRecovery.online = null;
  });
  tearDown(() {
    VisibilityDetectorController.instance.updateInterval = const Duration(milliseconds: 500);
    ReadRecovery.online = null;
  });

  final transient = <String, Object Function()>{
    'connection': () => const SocketException('down'),
    'timedOut': () => TimeoutException('slow'),
    'serviceUnavailable': () => _http(503),
    'unavailable': () => _http(403),
    'transactionUnavailable': () => TransactionIdUnavailableException(Exception('shape')),
    'unknown': () => const FormatException('html instead of json'),
  };
  for (final MapEntry(key: kind, value: error) in transient.entries) {
    testWidgets('$kind failures retry on a growing backoff and stop at the budget', (tester) async {
      final surface = _Surface(error(), outcome: error);
      addTearDown(surface.dispose);
      await _show(tester, _app(surface.recovery()));
      await _expectBackoff(tester, surface);
      await tester.pump(const Duration(minutes: 10));
      expect(surface.retries, readRetryDelays.length);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }

  testWidgets('a success renews the budget for the next failure', (tester) async {
    var failuresLeft = 2;
    final surface = _Surface(const SocketException('down'));
    surface.outcome = () => failuresLeft-- > 0 ? TimeoutException('again') : null;
    addTearDown(surface.dispose);
    await _show(tester, _app(surface.recovery()));
    await tester.pump(const Duration(seconds: 2 + 5 + 15));
    expect(surface.retries, 3);
    expect(surface.failure, isNull);

    surface.outcome = () => TimeoutException('later');
    surface.fail(const SocketException('later'));
    await _expectBackoff(tester, surface, from: 3);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('sign-in, endpoint and definitive account failures stay manual', (tester) async {
    for (final failure in <Object>[
      NoAccountAvailableException(),
      NoWorkingAccountException(),
      _http(401),
      EndpointRefusedException('UserTweets'),
      TwitterError(uri: 'x', code: 63, message: 'Suspended'),
    ]) {
      final surface = _Surface(failure);
      await _show(tester, _app(surface.recovery()));
      await tester.pump(const Duration(minutes: 10));
      expect(surface.retries, 0, reason: '$failure');
      await tester.pumpWidget(const SizedBox.shrink());
      await surface.dispose();
    }
  });

  testWidgets('a rate limit retries once, after its known reset', (tester) async {
    DateTime reset() => DateTime.now().add(const Duration(seconds: 90));
    final surface = _Surface(RateLimitedException(), outcome: RateLimitedException.new);
    addTearDown(surface.dispose);
    var lookups = 0;
    await _show(
      tester,
      _app(
        surface.recovery(
          reset: (_) async {
            lookups++;
            return reset();
          },
        ),
      ),
    );
    await tester.pump(const Duration(seconds: 88));
    expect(surface.retries, 0);
    await tester.pump(const Duration(seconds: 4));
    expect(surface.retries, 1);
    await tester.pump(const Duration(minutes: 30));
    expect(surface.retries, 1);
    expect(lookups, 2);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('a rate limit without a known reset stays manual', (tester) async {
    final surface = _Surface(RateLimitedException());
    addTearDown(surface.dispose);
    await _show(tester, _app(surface.recovery(reset: (_) async => null)));
    await tester.pump(const Duration(minutes: 30));
    expect(surface.retries, 0);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('nothing retries while loading', (tester) async {
    final surface = _Surface(const SocketException('down'))..loading = true;
    addTearDown(surface.dispose);
    await _show(tester, _app(surface.recovery()));
    await tester.pump(const Duration(minutes: 5));
    expect(surface.retries, 0);
    surface.loading = false;
    surface.changes.value++;
    await tester.pump(const Duration(seconds: 2));
    expect(surface.retries, 1);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('nothing retries while the surface is out of view', (tester) async {
    final surface = _Surface(const SocketException('down'));
    final offstage = ValueNotifier(true);
    addTearDown(surface.dispose);
    addTearDown(offstage.dispose);
    await _show(
      tester,
      _app(
        ValueListenableBuilder<bool>(
          valueListenable: offstage,
          builder: (_, hidden, child) => Offstage(offstage: hidden, child: child),
          child: surface.recovery(),
        ),
      ),
    );
    await tester.pump(const Duration(minutes: 5));
    expect(surface.retries, 0);
    offstage.value = false;
    await tester.pump();
    await tester.pump();
    await tester.pump(const Duration(seconds: 2));
    expect(surface.retries, 1);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('a reported offline network pauses retries until it reconnects', (tester) async {
    final surface = _Surface(const SocketException('down'));
    addTearDown(surface.dispose);
    await _show(tester, _app(surface.recovery(networkStream: true)));
    surface.network.add(const ReadNetworkState(online: false));
    await tester.pump();
    await tester.pump(const Duration(minutes: 5));
    expect(surface.retries, 0);
    surface.network.add(const ReadNetworkState(online: true, networkId: 'wifi'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 2));
    expect(surface.retries, 1);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('an unknown network does not block a cold-start retry', (tester) async {
    expect(ReadRecovery.online, isNull);
    final surface = _Surface(const SocketException('down'));
    addTearDown(surface.dispose);
    await _show(tester, _app(surface.recovery()));
    await tester.pump(const Duration(seconds: 2));
    expect(surface.retries, 1);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('nothing retries while backgrounded; resume retries again', (tester) async {
    final surface = _Surface(const SocketException('down'));
    addTearDown(surface.dispose);
    await _show(tester, _app(surface.recovery()));
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.pump(const Duration(minutes: 5));
    expect(surface.retries, 0);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    await tester.pump();
    await tester.pump(const Duration(seconds: 2));
    expect(surface.retries, 1);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  for (final observeRoutes in [true, false]) {
    testWidgets('nothing retries under a sheet (route observer: $observeRoutes)', (tester) async {
      final navigator = GlobalKey<NavigatorState>();
      final surface = _Surface(const SocketException('down'));
      addTearDown(surface.dispose);
      await _show(tester, _app(surface.recovery(), navigator: navigator, observeRoutes: observeRoutes));
      unawaited(showModalBottomSheet<void>(context: navigator.currentContext!, builder: (_) => const Text('Details')));
      await tester.pump();
      await tester.pump(const Duration(minutes: 5));
      expect(surface.retries, 0);
      navigator.currentState!.pop();
      await tester.pump();
      await tester.pump();
      await tester.pump(const Duration(seconds: 2));
      expect(surface.retries, 1);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }

  testWidgets('a retry that throws, synchronously or later, neither crashes nor loops', (tester) async {
    for (final retry in <FutureOr<void> Function(_Surface)>[
      (surface) {
        surface.retries++;
        throw StateError('cannot start');
      },
      (surface) async {
        surface.retries++;
        throw StateError('failed later');
      },
    ]) {
      final surface = _Surface(const SocketException('down'));
      await _show(tester, _app(surface.recovery(retry: () => retry(surface))));
      await tester.pump(const Duration(minutes: 10));
      expect(surface.retries, 1);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
      await surface.dispose();
    }
  });

  testWidgets('new error objects for the same failing request never renew the budget', (tester) async {
    final surface = _Surface(const SocketException('down'));
    addTearDown(surface.dispose);
    // Refresh-then-fetch: the error clears without loading before the next attempt fails.
    void flickeringRetry() {
      surface.retries++;
      surface.fail(null);
      scheduleMicrotask(() {
        surface.loading = true;
        surface.changes.value++;
        scheduleMicrotask(() {
          surface.loading = false;
          surface.fail(TimeoutException('again'));
        });
      });
    }

    await _show(tester, _app(surface.recovery(retry: flickeringRetry)));
    final reemit = Timer.periodic(const Duration(seconds: 1), (_) => surface.fail(TimeoutException('same request')));
    addTearDown(reemit.cancel);
    await tester.pump(const Duration(minutes: 10));
    reemit.cancel();
    expect(surface.retries, readRetryDelays.length);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('the notice shows the countdown, keeps manual retry, then falls back to it', (tester) async {
    final surface = _Surface(const SocketException('down'));
    addTearDown(surface.dispose);
    final notice = ReaderFailureNotice(error: surface.failure, onRetry: surface.retry);
    await _show(tester, _app(surface.recovery(child: notice), observeRoutes: false));
    expect(find.text('Versuche es in 2 s erneut …'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(find.text('Erneut versuchen'), findsOneWidget);
    expect(find.byTooltip(L10n.current.more_info), findsOneWidget);
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('Versuche es in 1 s erneut …'), findsOneWidget);
    await tester.pump(const Duration(seconds: 1 + 5 + 15 + 30 + 60));
    await tester.pump();
    expect(surface.retries, readRetryDelays.length);
    expect(find.byType(ReadRetryCountdown), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.text('Erneut versuchen'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('a rate-limited notice counts down to the reset instead of a disabled button', (tester) async {
    final error = RateLimitedException();
    final surface = _Surface(error);
    addTearDown(surface.dispose);
    final notice = ReaderFailureNotice(error: error, onRetry: surface.retry);
    final reset = DateTime.now().add(const Duration(minutes: 3));
    await _show(tester, _app(surface.recovery(child: notice, reset: (_) async => reset)));
    await tester.pump();
    expect(find.byType(ReadRetryCountdown), findsOneWidget);
    expect(find.textContaining(RegExp(r'Versuche es in [23]:\d\d erneut')), findsOneWidget);
    expect(find.byType(RateLimitRetryButton), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('countdown, its details sheet and large text fit a narrow screen', (tester) async {
    tester.view.physicalSize = const Size(320, 480);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final surface = _Surface(const SocketException('down'));
    addTearDown(surface.dispose);
    final notice = ReaderFailureNotice(error: surface.failure, onRetry: surface.retry, onDismiss: () {});
    await _show(
      tester,
      MediaQuery(
        data: const MediaQueryData(size: Size(320, 480), textScaler: TextScaler.linear(2)),
        child: _app(surface.recovery(child: notice)),
      ),
    );
    expect(find.byType(ReadRetryCountdown), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('visibility reported between frames schedules the frame that delivers it', (tester) async {
    VisibilityDetectorController.instance.updateInterval = const Duration(milliseconds: 500);
    final events = <String>[];
    await tester.pumpWidget(
      MaterialApp(
        home: ReadVisibility(
          onHidden: () => events.add('hidden'),
          onVisible: () => events.add('visible'),
          child: const SizedBox.expand(),
        ),
      ),
    );
    await tester.pump();
    expect(tester.binding.hasScheduledFrame, isFalse);
    // The report arrives in a scheduler task, after the screen went still.
    await tester.binding.delayed(const Duration(seconds: 1));
    expect(tester.binding.hasScheduledFrame, isTrue);
    await tester.pump();
    expect(events, ['hidden', 'visible']);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  test('the budget spends each backoff step once and the reset retry once', () {
    final now = DateTime(2026);
    final budget = ReadRetryBudget(readRetryDelays);
    for (final delay in readRetryDelays) {
      expect(budget.waitFor(ReadRetry.backoff, now: now), delay);
      budget.spend(ReadRetry.backoff);
    }
    expect(budget.waitFor(ReadRetry.backoff, now: now), isNull);
    expect(budget.waitFor(ReadRetry.atReset, now: now), isNull);
    final reset = now.add(const Duration(seconds: 30));
    expect(budget.waitFor(ReadRetry.atReset, reset: reset, now: now), const Duration(seconds: 31));
    expect(
      budget.waitFor(ReadRetry.atReset, reset: now.subtract(const Duration(minutes: 1)), now: now),
      const Duration(seconds: 1),
    );
    budget.spend(ReadRetry.atReset);
    expect(budget.waitFor(ReadRetry.atReset, reset: reset, now: now), isNull);
    expect(budget.waitFor(ReadRetry.manual, now: now), isNull);
    budget.renew();
    expect(budget.waitFor(ReadRetry.backoff, now: now), readRetryDelays.first);
    expect(budget.waitFor(ReadRetry.atReset, reset: reset, now: now), isNotNull);
  });
}
