/// What a Reddit screen shows when it is not showing posts.
///
/// Every screen in the plugin has the same four moments — loading, empty,
/// failed, finished — and each one had grown its own answer: a bare spinner
/// here, "No results" for a genuinely empty subreddit there, and a failure that
/// printed `null` where a stack trace would have gone. They are one set of
/// widgets now, so a fix lands everywhere at once.
library;

import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/ui/errors.dart';

/// The sentence to lead with: what happened, in terms the reader can act on.
String redditErrorHeadline(L10n l10n, Object error) {
  if (error is! RedditException) {
    return l10n.oops_something_went_wrong;
  }

  return switch (error.kind) {
    RedditErrorKind.notConfigured => l10n.plugin_reddit_not_configured,
    RedditErrorKind.unauthorized => l10n.plugin_reddit_error_client_id,
    RedditErrorKind.blocked => l10n.plugin_reddit_error_blocked,
    RedditErrorKind.notFound => l10n.plugin_reddit_error_not_found,
    RedditErrorKind.rateLimited => l10n.plugin_reddit_error_rate_limited,
    RedditErrorKind.badResponse => l10n.plugin_reddit_error_response,
    RedditErrorKind.network => l10n.plugin_reddit_error_network,
  };
}

/// What actually happened underneath. Without it a refusal, a timeout and a
/// reshaped response all read the same, and "it doesn't work" is all anyone can
/// report back.
String redditErrorDetail(Object error) => error is RedditException ? error.detail : '$error';

/// Headline and detail together, for the places that take a single string.
String redditErrorMessage(L10n l10n, Object error) {
  if (error is! RedditException) {
    return '$error';
  }

  final headline = redditErrorHeadline(l10n, error);

  return error.detail.isEmpty ? headline : '$headline\n\n${error.detail}';
}

/// A glyph per failure, so the shape of the problem is readable before the
/// words are.
String redditErrorEmoji(Object error) {
  if (error is! RedditException) {
    return '💥';
  }

  return switch (error.kind) {
    RedditErrorKind.notConfigured || RedditErrorKind.unauthorized => '🔑',
    RedditErrorKind.blocked => '🚧',
    RedditErrorKind.notFound => '🤷',
    RedditErrorKind.rateLimited => '⏳',
    RedditErrorKind.badResponse => '💥',
    RedditErrorKind.network => '🔌',
  };
}

/// A failure with a way out of it.
class RedditErrorState extends StatelessWidget {
  final Object error;
  final VoidCallback onRetry;

  /// Offered alongside Retry — signing in, choosing another source — for the
  /// failures where retrying the same request cannot help.
  final List<Widget> actions;

  const RedditErrorState({super.key, required this.error, required this.onRetry, this.actions = const []});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    // Scrolling when the text is large is the shared layout's job now, so this
    // must not add a scroll view of its own around it.
    return ActionableErrorWidget(
      emoji: redditErrorEmoji(error),
      title: redditErrorHeadline(l10n, error),
      details: redditErrorDetail(error),
      actions: [
        FilledButton.icon(icon: const Icon(Icons.refresh), label: Text(l10n.retry), onPressed: onRetry),
        ...actions,
      ],
    );
  }
}

/// Nothing to read, which is not the same as something having gone wrong.
class RedditEmptyState extends StatelessWidget {
  final IconData icon;
  final String message;
  final Widget? action;

  const RedditEmptyState({super.key, required this.icon, required this.message, this.action});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(24, 48, 24, 24),
      child: Column(
        children: [
          Icon(icon, size: 48, color: theme.colorScheme.outline),
          const SizedBox(height: 16),
          Text(message, textAlign: TextAlign.center, style: theme.textTheme.bodyLarge),
          if (action != null) ...[const SizedBox(height: 16), action!],
        ],
      ),
    );
  }
}

/// The end of a list, said out loud. A feed that simply stopped scrolling left
/// the reader waiting for a page that was never coming.
class RedditEndOfList extends StatelessWidget {
  const RedditEndOfList({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 28),
      child: Text(
        L10n.of(context).zen_mode_feed_end,
        textAlign: TextAlign.center,
        style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
      ),
    );
  }
}

/// A page still loading. Centred, and given room of its own so it does not sit
/// squashed against whatever is above it.
class RedditLoadingState extends StatelessWidget {
  const RedditLoadingState({super.key});

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.symmetric(vertical: 48),
      child: Center(child: CircularProgressIndicator()),
    );
  }
}
