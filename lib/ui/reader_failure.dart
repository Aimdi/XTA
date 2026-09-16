import 'package:xta/utils/read_recovery.dart';
import 'package:xta/ui/rate_limit_retry.dart';
import 'dart:async';
import 'dart:io' show SocketException;
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/errors.dart';
import 'package:xta/client/login_webview.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/settings/diagnostics_screen.dart';

enum ReadFailureKind { connection, timedOut, session, rateLimited, unavailable, unknown }

ReadFailureKind readFailureKind(Object? error) {
  if (error is TimeoutException) return ReadFailureKind.timedOut;
  if (error is SocketException || error is http.ClientException) return ReadFailureKind.connection;
  if (error is RateLimitedException || (error is HttpException && error.statusCode == 429))
    return ReadFailureKind.rateLimited;
  if (error is NoAccountAvailableException ||
      error is NoWorkingAccountException ||
      (error is HttpException && error.statusCode == 401) ||
      (error is TwitterError && const [32, 89, 215].contains(error.code))) {
    return ReadFailureKind.session;
  }
  if (error is EndpointRefusedException || (error is HttpException && const [403, 404].contains(error.statusCode)))
    return ReadFailureKind.unavailable;
  return ReadFailureKind.unknown;
}

Object? recoverableReadFailure(Object? error) => switch (readFailureKind(error)) {
  ReadFailureKind.connection || ReadFailureKind.timedOut => error,
  _ => null,
};

String readFailureMessage(L10n l10n, Object? error) => switch (readFailureKind(error)) {
  ReadFailureKind.connection => l10n.reader_connection_failed,
  ReadFailureKind.timedOut => l10n.timed_out,
  ReadFailureKind.session => l10n.reader_sign_in_needed,
  ReadFailureKind.rateLimited => l10n.rate_limited_title,
  ReadFailureKind.unavailable => l10n.endpoint_refused_title,
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
    this.recoverAutomatically = true,
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
          if (kind == ReadFailureKind.rateLimited) Text(l10n.reader_rate_limit_hint),
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
