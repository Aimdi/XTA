import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:timeago/timeago.dart' as timeago;
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/poll.dart';
import 'package:xta/tweet/tweet_chrome.dart';

/// Poll totals and shares are written in the reader's locale. Building a
/// pattern parses it, so one of each is kept per locale rather than one per
/// build of every poll.
final Map<String, NumberFormat> _decimalFormats = {};
final Map<String, NumberFormat> _percentFormats = {};

NumberFormat _decimalFormat(String locale) =>
    _decimalFormats.putIfAbsent(locale, () => NumberFormat.decimalPattern(locale));

/// "62.5%", but "100%" rather than "100.0%".
NumberFormat _percentFormat(String locale) =>
    _percentFormats.putIfAbsent(locale, () => NumberFormat.percentPattern(locale)..maximumFractionDigits = 1);

/// A poll's results, the way X draws them: one rounded bar per option filled
/// to its share, the option on the left and its share on the right, the
/// leader in bold on the accent colour, then the vote count and the time left.
///
/// No card around it: X sets a poll in the post like its text.
class TweetPollResults extends StatelessWidget {
  final TweetPoll poll;

  const TweetPollResults({super.key, required this.poll});

  @override
  Widget build(BuildContext context) {
    final locale = Intl.getCurrentLocale();
    final percent = _percentFormat(locale);

    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(kTweetHorizontalPadding, kTweetSpace2, kTweetHorizontalPadding, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final choice in poll.choices)
            PollOptionBar(
              label: choice.label,
              share: choice.share,
              percent: percent.format(choice.share),
              leading: poll.leads(choice),
            ),
          Padding(
            padding: const EdgeInsets.only(top: kTweetSpace1),
            child: Text(_summary(context, locale), style: tweetMetadataStyle(context)),
          ),
        ],
      ),
    );
  }

  /// "1,234 votes · Ends in 2 days".
  String _summary(BuildContext context, String locale) {
    final l10n = L10n.of(context);
    final endsAt = poll.endsAt;
    final relative = endsAt == null
        ? null
        : timeago.format(endsAt, allowFromNow: true, locale: Intl.shortLocale(locale));
    final closed = endsAt != null && endsAt.isBefore(DateTime.now());
    return [
      l10n.numberFormat_format_total_votes(poll.total, _decimalFormat(locale).format(poll.total)),
      if (relative != null)
        closed
            ? l10n.ended_timeago_format_endsAt_allowFromNow_true(relative)
            : l10n.ends_timeago_format_endsAt_allowFromNow_true(relative),
    ].join(' · ');
  }
}

/// One option of a poll's results.
class PollOptionBar extends StatelessWidget {
  static const double minHeight = 36;
  static const double radius = 6;

  final String label;

  /// The option's share of the vote, 0..1.
  final double share;

  /// [share], written for the reader.
  final String percent;

  final bool leading;

  const PollOptionBar({
    super.key,
    required this.label,
    required this.share,
    required this.percent,
    required this.leading,
  });

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    // The leader takes the accent; the others a neutral grey, as on X.
    final fill = leading ? scheme.primary.withValues(alpha: 0.35) : scheme.onSurface.withValues(alpha: 0.12);
    final style = tweetBodyStyle(context).copyWith(fontWeight: leading ? FontWeight.w700 : FontWeight.w400);

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      // Read as one: "Yes, 62.5%".
      child: MergeSemantics(
        child: Stack(
          children: [
            // Laid out as a fraction of the row rather than painted by a
            // progress indicator, so even a short fill keeps both ends round.
            Positioned.fill(
              child: FractionallySizedBox(
                alignment: AlignmentDirectional.centerStart,
                widthFactor: share.clamp(0.0, 1.0),
                child: DecoratedBox(
                  decoration: BoxDecoration(color: fill, borderRadius: BorderRadius.circular(radius)),
                ),
              ),
            ),
            ConstrainedBox(
              // Grows with the text instead of clipping it at large sizes.
              constraints: const BoxConstraints(minHeight: minHeight),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: kTweetSpace3, vertical: kTweetSpace1 + 2),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(label, maxLines: 2, overflow: TextOverflow.ellipsis, style: style),
                    ),
                    const SizedBox(width: kTweetSpace3),
                    Text(percent, style: style),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
