import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/stale_feed_preview.dart';

/// Banner shown above cached posts when a feed's first page failed.
///
/// It borrows the vocabulary of `ui/errors.dart` — the same emoji and the same
/// titles the full-page errors use — because the reader is being told the same
/// thing. What it adds is the part an error page cannot say: the posts below
/// are real, and this is when they were saved.
class StaleFeedBanner extends StatelessWidget {
  final StaleFeedReason reason;
  final DateTime? cachedAt;
  final VoidCallback? onRetry;
  final VoidCallback onDismiss;

  const StaleFeedBanner({
    super.key,
    required this.reason,
    required this.cachedAt,
    required this.onDismiss,
    this.onRetry,
  });

  static String emojiOf(StaleFeedReason reason) {
    switch (reason) {
      case StaleFeedReason.offline:
        return '🔌';
      case StaleFeedReason.timedOut:
        return '⏱️';
      case StaleFeedReason.rateLimited:
        return '⏳';
      case StaleFeedReason.noWorkingAccount:
        return '🤷';
      case StaleFeedReason.noAccount:
        return '🔑';
      case StaleFeedReason.endpointRefused:
        return '🚧';
      case StaleFeedReason.transactionUnavailable:
        return '🧾';
      case StaleFeedReason.unavailable:
        return '🚫';
      case StaleFeedReason.serviceUnavailable:
        return '🛠️';
      case StaleFeedReason.session:
        return '🔑';
      case StaleFeedReason.unknown:
        return '💥';
    }
  }

  static String titleOf(BuildContext context, StaleFeedReason reason) {
    switch (reason) {
      case StaleFeedReason.offline:
        return L10n.of(context).could_not_contact_twitter;
      case StaleFeedReason.timedOut:
        return L10n.of(context).timed_out;
      case StaleFeedReason.rateLimited:
        return L10n.of(context).rate_limited_title;
      case StaleFeedReason.noWorkingAccount:
        return L10n.of(context).no_working_account_title;
      case StaleFeedReason.noAccount:
        return L10n.of(context).no_account_available_title;
      case StaleFeedReason.endpointRefused:
        return L10n.of(context).endpoint_refused_title;
      case StaleFeedReason.transactionUnavailable:
        return L10n.of(context).reader_transaction_unavailable;
      case StaleFeedReason.unavailable:
        return L10n.of(context).reader_request_unavailable;
      case StaleFeedReason.serviceUnavailable:
        return L10n.of(context).reader_service_unavailable;
      case StaleFeedReason.session:
        return L10n.of(context).reader_sign_in_needed;
      case StaleFeedReason.unknown:
        return L10n.of(context).oops_something_went_wrong;
    }
  }

  static String? detailOf(BuildContext context, StaleFeedReason reason) {
    final l10n = L10n.of(context);
    return switch (reason) {
      StaleFeedReason.rateLimited => l10n.rate_limited_message,
      StaleFeedReason.noWorkingAccount => l10n.no_working_account_message,
      StaleFeedReason.noAccount => l10n.no_account_available_message,
      StaleFeedReason.endpointRefused => l10n.endpoint_refused_message,
      StaleFeedReason.transactionUnavailable =>
        l10n.reader_transaction_unavailable_hint,
      StaleFeedReason.unavailable => l10n.reader_request_unavailable_hint,
      StaleFeedReason.serviceUnavailable =>
        l10n.reader_service_unavailable_hint,
      StaleFeedReason.session => l10n.reader_sign_in_needed,
      StaleFeedReason.offline ||
      StaleFeedReason.timedOut ||
      StaleFeedReason.unknown => null,
    };
  }

  /// "from 14:05" for posts saved today, the full date for older ones — the
  /// distinction a reader needs to judge whether the feed is worth reading.
  static String ageLineOf(BuildContext context, DateTime? cachedAt) {
    if (cachedAt == null) {
      return L10n.of(context).feed_cached_posts_from_unknown_time;
    }
    final local = cachedAt.toLocal();
    final locale = Localizations.localeOf(context).toString();
    final today = sameCalendarDay(local, DateTime.now());
    final format = today ? DateFormat.Hm(locale) : DateFormat.yMMMd(locale).add_Hm();
    return L10n.of(context).feed_cached_posts_from(format.format(local));
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final retry = onRetry;
    final subtitle = theme.textTheme.bodySmall?.copyWith(color: theme.hintColor);

    return Material(
      color: theme.colorScheme.surfaceContainerHighest,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 4, 4),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.only(right: 12, top: 2),
              child: Text(emojiOf(reason), style: const TextStyle(fontSize: 22)),
            ),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    titleOf(context, reason),
                    style: theme.textTheme.titleSmall,
                  ),
                  if (detailOf(context, reason) case final detail?) ...[
                    const SizedBox(height: 2),
                    Text(detail, style: subtitle),
                  ],
                  const SizedBox(height: 2),
                  Text(ageLineOf(context, cachedAt), style: subtitle),
                  if (retry != null)
                    Align(
                      alignment: AlignmentDirectional.centerStart,
                      child: TextButton(onPressed: retry, child: Text(L10n.of(context).retry)),
                    ),
                ],
              ),
            ),
            IconButton(icon: const Icon(Icons.close), tooltip: L10n.of(context).close, onPressed: onDismiss),
          ],
        ),
      ),
    );
  }
}
