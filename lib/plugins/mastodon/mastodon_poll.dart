import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:timeago/timeago.dart' as timeago;
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';

/// Multiple-choice results count voters, because one person can choose several
/// options. Older servers may omit that denominator; keep the count in that case.
double? mastodonPollFraction(MastodonPoll poll, MastodonPollOption option) {
  if (!poll.resultsAvailable) return null;
  final total = poll.multiple ? poll.votersCount : poll.votesCount;
  if (total == null) return null;
  if (total <= 0) return 0;
  return (option.votes / total).clamp(0.0, 1.0);
}

class MastodonPollResults extends StatelessWidget {
  final MastodonPoll poll;
  const MastodonPollResults({super.key, required this.poll});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final locale = Localizations.localeOf(context).toString();
    final closed = poll.expired || (poll.expiresAt?.isBefore(DateTime.now()) ?? false);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final option in poll.options) _option(context, option, locale),
        const SizedBox(height: 4),
        Text(
          [
            if (poll.resultsAvailable)
              l10n.numberFormat_format_total_votes(
                poll.votesCount.clamp(0, 1 << 53),
                NumberFormat.decimalPattern(locale).format(poll.votesCount.clamp(0, 1 << 53)),
              )
            else
              l10n.plugin_mastodon_poll_unavailable,
            closed ? l10n.plugin_mastodon_poll_closed : l10n.plugin_mastodon_poll_open,
            if (poll.expiresAt != null) _endTime(l10n, locale, closed),
          ].join(' · '),
          style: Theme.of(context).textTheme.bodySmall,
        ),
      ],
    );
  }

  Widget _option(BuildContext context, MastodonPollOption option, String locale) {
    final fraction = mastodonPollFraction(poll, option);
    final count = NumberFormat.decimalPattern(locale).format(option.votes.clamp(0, 1 << 53));
    final result = fraction == null ? count : NumberFormat.percentPattern(locale).format(fraction);
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: MergeSemantics(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(child: Text(option.title)),
                if (poll.resultsAvailable) ...[const SizedBox(width: 8), Text(result)],
              ],
            ),
            if (fraction != null) ...[
              const SizedBox(height: 4),
              ExcludeSemantics(
                child: LinearProgressIndicator(value: fraction, minHeight: 6, borderRadius: BorderRadius.circular(4)),
              ),
            ],
          ],
        ),
      ),
    );
  }

  String _endTime(L10n l10n, String locale, bool closed) {
    final relative = timeago.format(poll.expiresAt!, allowFromNow: true, locale: Intl.shortLocale(locale));
    return closed
        ? l10n.ended_timeago_format_endsAt_allowFromNow_true(relative)
        : l10n.ends_timeago_format_endsAt_allowFromNow_true(relative);
  }
}
