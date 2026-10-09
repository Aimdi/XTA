import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/engagement_count.dart';
import 'package:xta/tweet/tweet_chrome.dart';

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

/// "16:12" and "9. Okt. 2026" for [createdAt] in [locale], honouring the
/// device's 24-hour setting.
(String, String) focalPostTimeAndDate(DateTime createdAt, String locale, {required bool use24Hour}) {
  final local = createdAt.toLocal();
  final time = use24Hour ? DateFormat.Hm(locale) : DateFormat.jm(locale);
  return (time.format(local), DateFormat.yMMMd(locale).format(local));
}

/// X's line under an opened post: `16:12 · 9. Okt. 2026 · 77K Views`, the
/// count in bold. Without a view count the line keeps just time and date.
class TweetFocalMetaLine extends StatelessWidget {
  final DateTime? createdAt;
  final int? views;

  const TweetFocalMetaLine({super.key, required this.createdAt, required this.views});

  @override
  Widget build(BuildContext context) {
    final style = tweetMetadataStyle(context).copyWith(fontSize: 15);
    final spans = <InlineSpan>[
      if (createdAt != null) TextSpan(text: _timeAndDate(context)),
      if (createdAt != null && _hasViews) const TextSpan(text: ' · '),
      if (_hasViews) ..._viewsSpans(context, style),
    ];
    if (spans.isEmpty) {
      return const SizedBox.shrink();
    }
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(
        kTweetHorizontalPadding,
        kTweetSpace3,
        kTweetHorizontalPadding,
        kTweetSpace1,
      ),
      child: Text.rich(TextSpan(style: style, children: spans)),
    );
  }

  bool get _hasViews => (views ?? 0) > 0;

  String _timeAndDate(BuildContext context) {
    final (time, date) = focalPostTimeAndDate(
      createdAt!,
      Intl.getCurrentLocale(),
      use24Hour: MediaQuery.alwaysUse24HourFormatOf(context),
    );
    return '$time · $date';
  }

  /// The localized "77K Views", with the number picked out in bold wherever
  /// the language puts it.
  List<InlineSpan> _viewsSpans(BuildContext context, TextStyle style) {
    final count = formatEngagementCount(views!);
    final label = L10n.of(context).post_views_count(viewsPluralCount(views!), count);
    final at = label.indexOf(count);
    if (at < 0) {
      return [TextSpan(text: label)];
    }
    return [
      TextSpan(text: label.substring(0, at)),
      TextSpan(
        text: count,
        style: style.copyWith(color: tweetPrimaryColor(context), fontWeight: FontWeight.w700),
      ),
      TextSpan(text: label.substring(at + count.length)),
    ];
  }
}
