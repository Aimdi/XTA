import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/engagement_count.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/rolling_count.dart';
import 'package:xta/utils/formatting_locale.dart';

/// Names the post a conversation screen was opened on, so that one tile — and
/// only that one — is drawn the way X draws an opened post.
class FocalPostScope extends InheritedWidget {
  final String id;

  const FocalPostScope({super.key, required this.id, required super.child});

  /// The opened post's id, or null outside a conversation screen.
  static String? idOf(BuildContext context) => context.getInheritedWidgetOfExactType<FocalPostScope>()?.id;

  @override
  bool updateShouldNotify(FocalPostScope oldWidget) => id != oldWidget.id;
}

/// The copy an opened post was shown from while X's answer loaded. The post's
/// counts roll from this copy's numbers to X's, as in X's own app.
///
/// The conversation screen holds it for its first build only; later builds
/// carry no copy, so a post scrolled back into view does not roll again.
class CountRollOrigin extends InheritedWidget {
  final TweetWithCard? tweet;

  const CountRollOrigin({super.key, required this.tweet, required super.child});

  /// The copy of the post [id], or null when there is none.
  static TweetWithCard? of(BuildContext context, String? id) {
    final tweet = context.getInheritedWidgetOfExactType<CountRollOrigin>()?.tweet;
    return id != null && tweet?.idStr == id ? tweet : null;
  }

  @override
  bool updateShouldNotify(CountRollOrigin oldWidget) => false;
}

/// "16:12" and "9. Okt. 2026" for [createdAt] in [locale], honouring the
/// device's 24-hour setting.
(String, String) focalPostTimeAndDate(DateTime createdAt, String locale, {required bool use24Hour}) {
  final local = createdAt.toLocal();
  final time = use24Hour ? DateFormat.Hm(locale) : DateFormat.jm(locale);
  return (time.format(local), DateFormat.yMMMd(locale).format(local));
}

/// X's line under an opened post: `16:12 · 9. Okt. 2026 · 77K Views`, the
/// count in bold. Without a view count the line keeps just time and date.
///
/// When the count changes, or [fromViews] differs from it, the count rolls to
/// the new number; until then it is plain text in the line.
class TweetFocalMetaLine extends StatefulWidget {
  final DateTime? createdAt;
  final int? views;

  /// The views of the copy the post was opened from: the count rolls from it.
  final int? fromViews;

  const TweetFocalMetaLine({super.key, required this.createdAt, required this.views, this.fromViews});

  @override
  State<TweetFocalMetaLine> createState() => _TweetFocalMetaLineState();
}

class _TweetFocalMetaLineState extends State<TweetFocalMetaLine> {
  /// Set once the count has a number to roll from; the count is drawn by a
  /// [RollingCount] from then on.
  late int? _rollFrom = widget.fromViews;
  late bool _rolls = _differs(widget.fromViews, widget.views);

  @override
  void didUpdateWidget(TweetFocalMetaLine oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!_rolls && _differs(oldWidget.views, widget.views)) {
      _rolls = true;
      _rollFrom = oldWidget.views;
    }
  }

  /// Whether going from [from] to [to] views changes the shown count.
  static bool _differs(int? from, int? to) =>
      (from ?? 0) > 0 && (to ?? 0) > 0 && formatEngagementCount(from!) != formatEngagementCount(to!);

  @override
  Widget build(BuildContext context) {
    final style = tweetMetadataStyle(context).copyWith(fontSize: 15);
    final spans = <InlineSpan>[
      if (widget.createdAt != null) TextSpan(text: _timeAndDate(context)),
      if (widget.createdAt != null && _hasViews) const TextSpan(text: ' · '),
      if (_hasViews) ..._viewsSpans(context, style),
    ];
    if (spans.isEmpty) {
      return const SizedBox.shrink();
    }
    final line = Text.rich(TextSpan(style: style, children: spans));
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(
        kTweetHorizontalPadding,
        kTweetSpace3,
        kTweetHorizontalPadding,
        kTweetSpace1,
      ),
      // A rolling count sits in the line as a widget, which a screen reader
      // would read apart from the words around it.
      child: _rolls
          ? Semantics(
              label: _plainText(context),
              child: ExcludeSemantics(child: line),
            )
          : line,
    );
  }

  bool get _hasViews => (widget.views ?? 0) > 0;

  String _timeAndDate(BuildContext context) {
    final (time, date) = focalPostTimeAndDate(
      widget.createdAt!,
      currentFormattingLocale(),
      use24Hour: MediaQuery.alwaysUse24HourFormatOf(context),
    );
    return '$time · $date';
  }

  /// The line as plain words, for the screen reader while the count rolls.
  String _plainText(BuildContext context) => [
    if (widget.createdAt != null) _timeAndDate(context),
    if (_hasViews) _viewsLabel(context, formatEngagementCount(widget.views!)),
  ].join(' · ');

  String _viewsLabel(BuildContext context, String count) =>
      L10n.of(context).post_views_count(viewsPluralCount(widget.views!), count);

  /// The localized "77K Views", with the number picked out in bold wherever
  /// the language puts it.
  List<InlineSpan> _viewsSpans(BuildContext context, TextStyle style) {
    final count = formatEngagementCount(widget.views!);
    final label = _viewsLabel(context, count);
    final at = label.indexOf(count);
    if (at < 0) {
      return [TextSpan(text: label)];
    }
    final bold = style.copyWith(color: tweetPrimaryColor(context), fontWeight: FontWeight.w700);
    final from = _rollFrom;
    return [
      TextSpan(text: label.substring(0, at)),
      if (_rolls)
        WidgetSpan(
          alignment: PlaceholderAlignment.baseline,
          baseline: TextBaseline.alphabetic,
          child: RollingCount(
            count,
            value: widget.views,
            style: bold,
            from: from == null ? null : formatEngagementCount(from),
            fromValue: from,
          ),
        )
      else
        TextSpan(text: count, style: bold),
      TextSpan(text: label.substring(at + count.length)),
    ];
  }
}
