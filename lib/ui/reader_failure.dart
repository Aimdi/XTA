import 'package:xta/utils/read_recovery.dart';
import 'package:xta/ui/rate_limit_retry.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/ui/read_failure_kind.dart';
export 'package:xta/ui/read_failure_kind.dart';
import 'package:xta/client/x_cookie_login.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/settings/diagnostics_screen.dart';
import 'package:xta/utils/reader_value_store.dart';

String readFailureMessage(L10n l10n, Object? error) => switch (readFailureKind(error)) {
  ReadFailureKind.connection => l10n.reader_connection_failed,
  ReadFailureKind.timedOut => l10n.timed_out,
  ReadFailureKind.session => l10n.reader_sign_in_needed,
  ReadFailureKind.rateLimited => l10n.rate_limited_title,
  ReadFailureKind.endpointRefused => l10n.endpoint_refused_title,
  ReadFailureKind.transactionUnavailable => l10n.reader_transaction_unavailable,
  ReadFailureKind.unavailable => l10n.reader_request_unavailable,
  ReadFailureKind.serviceUnavailable => l10n.reader_service_unavailable,
  ReadFailureKind.unknown => l10n.oops_something_went_wrong,
};

class ReaderFailureNotice extends StatelessWidget {
  final String source;
  final Object? error;
  final VoidCallback onRetry;
  final bool compact;
  final bool recoverAutomatically;
  final String? contextMessage;
  final VoidCallback? onDismiss;
  const ReaderFailureNotice({
    super.key,
    this.source = 'x',
    required this.error,
    required this.onRetry,
    this.compact = false,
    // Automatic recovery belongs to the stable screen, not its transient error row.
    this.recoverAutomatically = false,
    this.contextMessage,
    this.onDismiss,
  });
  @override
  Widget build(BuildContext context) {
    // Built below the optional recovery so the controls see its countdown.
    final controls = Builder(builder: _controls);
    if (!recoverAutomatically) return controls;
    return ReadRecovery(recoverableFailure: () => recoverableReadFailure(error), retry: onRetry, child: controls);
  }

  Widget _controls(BuildContext context) => Align(
    alignment: Alignment.topCenter,
    heightFactor: 1,
    child: Padding(
      padding: EdgeInsets.symmetric(horizontal: compact ? 8 : 16),
      child: Wrap(
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          _ReadRetry(error: error, onRetry: onRetry, automatic: true),
          IconButton(
            tooltip: L10n.of(context).more_info,
            icon: const Icon(Icons.info_outline, size: 18),
            onPressed: () => showReaderFailureDetails(
              context,
              source: source,
              error: error,
              onRetry: onRetry,
              contextMessage: contextMessage,
            ),
          ),
          if (onDismiss != null)
            IconButton(
              tooltip: L10n.of(context).close,
              icon: const Icon(Icons.close, size: 18),
              onPressed: onDismiss,
            ),
        ],
      ),
    ),
  );
}

Future<void> showReaderFailureDetails(
  BuildContext context, {
  String source = 'x',
  required Object? error,
  required VoidCallback onRetry,
  String? contextMessage,
}) => showModalBottomSheet<void>(
  context: context,
  showDragHandle: true,
  builder: (sheetContext) => SafeArea(
    child: SingleChildScrollView(
      child: _ReaderFailureDetails(
        source: source,
        error: error,
        contextMessage: contextMessage,
        onRetry: () {
          Navigator.pop(sheetContext);
          onRetry();
        },
      ),
    ),
  ),
);

class _ReadRetry extends StatelessWidget {
  final Object? error;
  final VoidCallback onRetry;
  final bool automatic;
  const _ReadRetry({required this.error, required this.onRetry, this.automatic = false});

  bool get _rateLimited => readFailureKind(error) == ReadFailureKind.rateLimited;

  @override
  Widget build(BuildContext context) => automatic && recoverableReadFailure(error) != null
      ? ScheduledReadRetry(idle: _manual(context), builder: _automatic)
      : _manual(context);

  Widget _manual(BuildContext context) => _rateLimited
      ? RateLimitRetryButton(error: error, onRetry: onRetry)
      : TextButton.icon(onPressed: onRetry, icon: const Icon(Icons.refresh), label: Text(L10n.of(context).retry));

  // Before a rate limit resets, a manual retry is disabled anyway.
  Widget _automatic(BuildContext context, int seconds) => _rateLimited
      ? ReadRetryCountdown(seconds: seconds)
      : Wrap(
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [ReadRetryCountdown(seconds: seconds), _manual(context)],
        );
}

/// The enclosing [ReadRecovery]'s countdown while it has one, else [idle].
class ScheduledReadRetry extends StatelessWidget {
  final Widget idle;
  final Widget Function(BuildContext context, int seconds) builder;
  const ScheduledReadRetry({super.key, this.idle = const SizedBox.shrink(), this.builder = _countdown});

  static Widget _countdown(BuildContext context, int seconds) => ReadRetryCountdown(seconds: seconds);

  @override
  Widget build(BuildContext context) {
    final countdown = ReadRecovery.countdownOf(context);
    if (countdown == null) return idle;
    return ScopedBuilder<ReaderValueStore<int>, int>(
      store: countdown,
      onState: (context, seconds) => seconds > 0 ? builder(context, seconds) : idle,
    );
  }
}

/// The calm "trying again in 5 s …" line shown while a surface retries on its own.
class ReadRetryCountdown extends StatelessWidget {
  final int seconds;
  const ReadRetryCountdown({super.key, required this.seconds});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final color = theme.hintColor;
    // A long rate-limit wait or reduced motion gets a still icon, not a spinner.
    final still = seconds > 60 || MediaQuery.disableAnimationsOf(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox.square(
            dimension: 14,
            child: still
                ? Icon(Icons.schedule, size: 14, color: color)
                : CircularProgressIndicator(strokeWidth: 2, color: color),
          ),
          const SizedBox(width: 8),
          Flexible(
            child: Text(
              seconds < 60
                  ? l10n.reader_auto_retry_seconds(seconds)
                  : l10n.reader_auto_retry_in(formatRetryCountdown(seconds)),
              style: theme.textTheme.bodySmall?.copyWith(color: color),
            ),
          ),
        ],
      ),
    );
  }
}

class _ReaderFailureDetails extends StatelessWidget {
  final String source;
  final Object? error;
  final VoidCallback onRetry;
  final String? contextMessage;
  const _ReaderFailureDetails({required this.source, required this.error, required this.onRetry, this.contextMessage});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final plugin = pluginById(source);
    final name = plugin?.title(context) ?? 'X';
    final kind = readFailureKind(error);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  '$name · ${readFailureMessage(l10n, error)}',
                  style: Theme.of(context).textTheme.titleSmall,
                ),
              ),
              IconButton(tooltip: l10n.close, icon: const Icon(Icons.close), onPressed: () => Navigator.pop(context)),
            ],
          ),
          if (contextMessage != null) Text(contextMessage!),
          if (kind == ReadFailureKind.rateLimited)
            Text(l10n.reader_rate_limit_hint),
          if (kind == ReadFailureKind.endpointRefused)
            Text(l10n.endpoint_refused_message),
          if (kind == ReadFailureKind.transactionUnavailable)
            Text(l10n.reader_transaction_unavailable_hint),
          if (kind == ReadFailureKind.unavailable)
            Text(l10n.reader_request_unavailable_hint),
          if (kind == ReadFailureKind.serviceUnavailable)
            Text(l10n.reader_service_unavailable_hint),
          Wrap(
            spacing: 8,
            children: [
              _ReadRetry(error: error, onRetry: onRetry),
              if (kind == ReadFailureKind.session && source == 'x')
                TextButton.icon(
                  onPressed: () =>
                      Navigator.push(context, MaterialPageRoute(builder: (_) => xLoginScreen())),
                  icon: const Icon(Icons.login),
                  label: Text(l10n.add_account),
                ),
              if (source == 'x' || plugin?.settingsScreen(context) != null)
                TextButton.icon(
                  onPressed: () {
                    final screen = plugin?.settingsScreen(context) ?? const DiagnosticsScreen();
                    Navigator.push(context, MaterialPageRoute(builder: (_) => screen));
                  },
                  icon: const Icon(Icons.info_outline),
                  label: Text(source == 'x' ? l10n.diagnostics : l10n.settings),
                ),
            ],
          ),
        ],
      ),
    );
  }
}
