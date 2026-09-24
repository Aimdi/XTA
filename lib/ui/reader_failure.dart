import 'package:xta/utils/read_recovery.dart';
import 'package:xta/ui/rate_limit_retry.dart';
import 'package:flutter/material.dart';
import 'package:xta/ui/read_failure_kind.dart';
export 'package:xta/ui/read_failure_kind.dart';
import 'package:xta/client/login_webview.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/settings/diagnostics_screen.dart';

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
    final controls = Align(
      alignment: Alignment.topCenter,
      heightFactor: 1,
      child: Padding(
        padding: EdgeInsets.symmetric(horizontal: compact ? 8 : 16),
        child: Wrap(
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            _ReadRetry(error: error, onRetry: onRetry),
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
    if (!recoverAutomatically) return controls;
    return ReadRecovery(recoverableFailure: () => recoverableReadFailure(error), retry: onRetry, child: controls);
  }
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
  const _ReadRetry({required this.error, required this.onRetry});

  @override
  Widget build(BuildContext context) => readFailureKind(error) == ReadFailureKind.rateLimited
      ? RateLimitRetryButton(error: error, onRetry: onRetry)
      : TextButton.icon(onPressed: onRetry, icon: const Icon(Icons.refresh), label: Text(L10n.of(context).retry));
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
                      Navigator.push(context, MaterialPageRoute(builder: (_) => const TwitterLoginWebview())),
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
