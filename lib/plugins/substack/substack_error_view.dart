import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_transport.dart';
import 'package:xta/ui/errors.dart';

/// A failed Substack screen. A rate limit says so in the reader's language
/// rather than printing the exception; anything else is the usual error page.
class SubstackErrorView extends StatelessWidget {
  final Object? error;
  final VoidCallback? onRetry;

  const SubstackErrorView({super.key, required this.error, this.onRetry});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    if (error is SubstackRateLimitedException) {
      return EmojiErrorWidget(
        emoji: '⏳',
        message: l10n.plugin_substack_load_error,
        errorMessage: l10n.plugin_booru_error_rate_limited,
        onRetry: onRetry,
        showBackButton: false,
      );
    }
    return FullPageErrorWidget(
      error: error,
      stackTrace: null,
      prefix: l10n.plugin_substack_load_error,
      onRetry: onRetry,
    );
  }
}

/// The one-line status for a feed that loaded but could not finish.
String substackFailureLabel(L10n l10n, Object? error) =>
    error is SubstackRateLimitedException ? l10n.plugin_booru_error_rate_limited : l10n.plugin_substack_load_error;
