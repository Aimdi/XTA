import 'dart:async';
import 'dart:io' show HttpDate;
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/accounts.dart';
import 'package:xta/client/account_fetch_gate.dart';
import 'package:xta/client/rate_limit_tracker.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/utils/reader_value_store.dart';

DateTime? knownRateLimitReset(Object? error, List<Map<String, DateTime>> accounts, DateTime now) {
  if (error is HttpException && error.statusCode == 429) {
    final headers = error.response.headers;
    final epoch = int.tryParse(headers['x-rate-limit-reset'] ?? '');
    if (epoch != null) return DateTime.fromMillisecondsSinceEpoch(epoch * 1000);
    final retry = headers['retry-after'];
    final seconds = int.tryParse(retry ?? '');
    if (seconds != null) return now.add(Duration(seconds: seconds));
    try {
      return retry == null ? null : HttpDate.parse(retry);
    } catch (_) {
      return null;
    }
  }
  if (error is! RateLimitedException || error.operations.isEmpty || accounts.isEmpty) return null;
  final resets = <DateTime>[];
  for (final operation in error.operations) {
    for (final account in accounts) {
      final matches = account.entries.where((e) => e.key.split('/').last == operation && e.value.isAfter(now));
      if (matches.isEmpty) return null; // Another usable account/operation can be tried.
      resets.add(matches.map((e) => e.value).reduce((a, b) => a.isAfter(b) ? a : b));
    }
  }
  return resets.reduce((a, b) => a.isBefore(b) ? a : b);
}

Future<DateTime?> readRateLimitReset(Object? error) async {
  final now = DateTime.now();
  if (error is HttpException) return knownRateLimitReset(error, const [], now);
  var accounts = await getAccounts();
  final preferred = accounts.where((a) => !AccountFetchGate.disabledIds.contains(a.id)).toList();
  if (preferred.isNotEmpty) accounts = preferred;
  return knownRateLimitReset(error, accounts.map((a) => RateLimitTracker.activeFor(a.id, now)).toList(), now);
}

class RateLimitRetryButton extends StatefulWidget {
  final Object? error;
  final DateTime Function()? clock;
  final VoidCallback onRetry;
  final Future<DateTime?> Function(Object?)? lookup;
  const RateLimitRetryButton({super.key, required this.error, required this.onRetry, this.lookup, this.clock});
  @override
  State<RateLimitRetryButton> createState() => _RateLimitRetryButtonState();
}

class _RateLimitRetryButtonState extends State<RateLimitRetryButton> with WidgetsBindingObserver {
  final _remaining = ReaderValueStore<int>(0);
  DateTime? _reset;
  Timer? _timer;
  int _generation = 0;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  Future<void> _load() async {
    final generation = ++_generation;
    try {
      final reset = await (widget.lookup ?? readRateLimitReset)(widget.error);
      if (!mounted || generation != _generation) return;
      _reset = reset;
      _start();
    } catch (_) {
      /* Unknown deadlines leave manual retry available. */
    }
  }

  void _tick() {
    final milliseconds = _reset?.difference((widget.clock ?? DateTime.now)()).inMilliseconds ?? 0;
    _remaining.update(milliseconds <= 0 ? 0 : (milliseconds / 1000).ceil());
    if (_remaining.state == 0) _timer?.cancel();
  }

  void _start() {
    _timer?.cancel();
    _tick();
    if (_remaining.state > 0) _timer = Timer.periodic(const Duration(seconds: 1), (_) => _tick());
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _start();
    } else {
      _timer?.cancel();
    }
  }

  @override
  void didUpdateWidget(RateLimitRetryButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.error, widget.error)) {
      _reset = null;
      _start();
      _load();
    }
  }

  @override
  void dispose() {
    _generation++;
    _timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    _remaining.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<ReaderValueStore<int>, int>(
    store: _remaining,
    onState: (context, seconds) => TextButton.icon(
      icon: const Icon(Icons.refresh),
      onPressed: seconds > 0 ? null : widget.onRetry,
      label: Text(
        seconds > 0
            ? L10n.of(context).reader_retry_in('${seconds ~/ 60}:${(seconds % 60).toString().padLeft(2, '0')}')
            : L10n.of(context).retry,
      ),
    ),
  );
}
