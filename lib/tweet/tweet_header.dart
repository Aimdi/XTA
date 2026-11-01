import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_ink_layout.dart';
import 'package:xta/ui/verified_badges.dart';
import 'package:xta/user_verification.dart';

/// Author hierarchy shared by ordinary, quoted and threaded posts.
class TweetAuthorBlock extends StatelessWidget {
  final String? displayName;
  final String? handle;
  final bool verified;

  /// The parsed badges; when absent, [verified] alone draws the blue check.
  final UserVerification? verification;
  final Widget? timestamp;
  final Widget? trailing;

  /// What a handle is written after: "@" on X, "u/" on Reddit.
  final String handlePrefix;

  /// See [TweetAuthorLine.handleSpan].
  final TweetHandleSpan? handleSpan;

  const TweetAuthorBlock({
    super.key,
    required this.displayName,
    required this.handle,
    required this.verified,
    this.verification,
    this.timestamp,
    this.trailing,
    this.handlePrefix = '@',
    this.handleSpan,
  });

  @override
  Widget build(BuildContext context) {
    if (displayName == null && handle == null && timestamp == null) {
      return Align(alignment: AlignmentDirectional.centerEnd, child: trailing);
    }

    final largeText = tweetAuthorNeedsTwoLines(context);

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  Flexible(
                    child: Text(
                      displayName ?? '',
                      overflow: TextOverflow.ellipsis,
                      maxLines: largeText ? 2 : 1,
                      style: tweetDisplayNameStyle(context),
                    ),
                  ),
                  VerifiedBadges(
                    verification:
                        verification ?? UserVerification.fromFlag(verified),
                    size: 16,
                    gap: kTweetSpace1,
                  ),
                ],
              ),
              if (handle != null || timestamp != null) ...[
                const SizedBox(height: kTweetSpace1),
                Wrap(
                  spacing: kTweetSpace1,
                  runSpacing: kTweetSpace1,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    if (handle != null)
                      _handleText(
                        '$handlePrefix$handle',
                        tweetMetadataStyle(context),
                        handleSpan,
                      ),
                    if (handle != null && timestamp != null)
                      Text('·', style: tweetMetadataStyle(context)),
                    if (timestamp != null)
                      DefaultTextStyle.merge(
                        style: tweetMetadataStyle(context),
                        child: timestamp!,
                      ),
                  ],
                ),
              ],
            ],
          ),
        ),
        // The actions size their own 48dp targets and place their glyphs on
        // the name's line, as X does.
        if (trailing != null) ...[
          const SizedBox(width: kTweetSpace1),
          trailing!,
        ],
      ],
    );
  }
}

class TweetHeader extends StatelessWidget {
  final Widget avatar;
  final VoidCallback onOpenProfile;
  final String? displayName;
  final String? handle;
  final bool verified;
  final UserVerification? verification;
  final Widget? timestamp;
  final Widget? trailing;
  final bool compact;

  const TweetHeader({
    super.key,
    required this.avatar,
    required this.onOpenProfile,
    required this.displayName,
    required this.handle,
    required this.verified,
    this.verification,
    this.timestamp,
    this.trailing,
    this.compact = false,
  });

  @override
  Widget build(BuildContext context) {
    final avatarSize = compact ? kTweetQuotedAvatarSize : kTweetAvatarSize;
    final top = compact ? kTweetSpace2 : kTweetVerticalPadding;
    return Padding(
      // Header actions run to the edge: the ⋯ glyph, centred in its target,
      // then lines up with the share glyph closing the footer.
      padding: EdgeInsetsDirectional.fromSTEB(
        kTweetHorizontalPadding,
        top,
        trailing == null ? kTweetSpace2 : 0,
        kTweetSpace1,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TweetAvatarTarget(
            avatar: avatar,
            size: avatarSize,
            label: displayName,
            onTap: onOpenProfile,
          ),
          const SizedBox(width: kTweetSpace3),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(top: kTweetSpace1),
              child: TweetAuthorBlock(
                displayName: displayName,
                handle: handle,
                verified: verified,
                verification: verification,
                timestamp: timestamp,
                trailing: trailing,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Whether text is scaled up far enough that an author line needs two lines.
bool tweetAuthorNeedsTwoLines(BuildContext context) =>
    MediaQuery.textScalerOf(context).scale(1) >= 1.3;

/// The handle and time on an author line: the name's size, in the secondary
/// colour, so the two share one line box.
TextStyle tweetAuthorMetaStyle(BuildContext context) => tweetDisplayNameStyle(
  context,
).copyWith(color: tweetSecondaryColor(context), fontWeight: FontWeight.w400);

/// A handle drawn as more than its text, in the [style] of the line it is on.
typedef TweetHandleSpan = InlineSpan Function(TextStyle style);

/// A handle on one line, shortened with an ellipsis: as wide as what is drawn,
/// so a shortened handle leaves no gap before the time.
Widget _handleText(String text, TextStyle style, TweetHandleSpan? span) =>
    Text.rich(
      span?.call(style) ?? TextSpan(text: text),
      maxLines: 1,
      softWrap: false,
      overflow: TextOverflow.ellipsis,
      textWidthBasis: TextWidthBasis.longestLine,
      style: style,
    );

/// The share of an author line its time always gets, however long the name.
const double kTweetAuthorTimeShare = 0.45;

/// Below this a handle is left out rather than cut to an ellipsis alone.
const double kTweetAuthorHandleMinWidth = 40;

/// Concept A's author line: the name, its badges, then "@handle · time", on
/// one line. The handle gives way first, then the name; the badges and the
/// time keep their size. With large text the handle and time take a second
/// line, as [TweetAuthorBlock] lays them out.
class TweetAuthorLine extends StatelessWidget {
  final String? displayName;
  final String? handle;
  final bool verified;
  final UserVerification? verification;
  final Widget? timestamp;

  /// What a handle is written after: "@" on X, "u/" on Reddit.
  final String handlePrefix;

  /// Draws the handle where it is more than "[handlePrefix][handle]":
  /// Mastodon dims the server.
  final TweetHandleSpan? handleSpan;

  /// Set where the name or the handle opens something of its own.
  final VoidCallback? onTapName;
  final VoidCallback? onTapHandle;

  const TweetAuthorLine({
    super.key,
    required this.displayName,
    required this.handle,
    required this.verified,
    this.verification,
    this.timestamp,
    this.handlePrefix = '@',
    this.handleSpan,
    this.onTapName,
    this.onTapHandle,
  });

  @override
  Widget build(BuildContext context) {
    if (displayName == null && handle == null && timestamp == null) {
      return const SizedBox.shrink();
    }
    if (tweetAuthorNeedsTwoLines(context)) {
      return TweetAuthorBlock(
        displayName: displayName,
        handle: handle,
        verified: verified,
        verification: verification,
        timestamp: timestamp,
        handlePrefix: handlePrefix,
        handleSpan: handleSpan,
      );
    }
    final meta = tweetAuthorMetaStyle(context);
    final name = _nameWidth(context);
    return LayoutBuilder(
      builder: (context, constraints) {
        final line = constraints.maxWidth;
        return Row(
          children: [
            Flexible(child: _nameAndHandle(context, meta)),
            if (timestamp != null)
              _time(meta, math.max(line * kTweetAuthorTimeShare, line - name)),
          ],
        );
      },
    );
  }

  /// The name and its badges at their full width.
  double _nameWidth(BuildContext context) {
    final painter = TextPainter(
      text: TextSpan(
        text: displayName ?? '',
        style: tweetDisplayNameStyle(context),
      ),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      maxLines: 1,
    )..layout();
    final width = painter.width;
    painter.dispose();
    return width +
        verifiedBadgesWidth(
          context,
          verification ?? UserVerification.fromFlag(verified),
          size: 16,
          gap: kTweetSpace1,
        );
  }

  /// "· time", up to [maxWidth]: what the name leaves, and never less than
  /// [kTweetAuthorTimeShare] of the line, where an absolute date shortens.
  Widget _time(TextStyle meta, double maxWidth) {
    return ConstrainedBox(
      constraints: BoxConstraints(maxWidth: maxWidth),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(width: kTweetSpace1),
          Text('·', style: meta),
          const SizedBox(width: kTweetSpace1),
          Flexible(
            child: DefaultTextStyle.merge(
              style: meta,
              maxLines: 1,
              softWrap: false,
              overflow: TextOverflow.ellipsis,
              child: timestamp!,
            ),
          ),
        ],
      ),
    );
  }

  /// The name and its badges take what they need, up to the whole line; the
  /// handle gets what is left.
  Widget _nameAndHandle(BuildContext context, TextStyle meta) {
    final handle = this.handle;
    return LayoutBuilder(
      builder: (context, constraints) => Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          ConstrainedBox(
            constraints: BoxConstraints(
              maxWidth: math.max(0, constraints.maxWidth),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Flexible(
                  child: _tappable(
                    onTapName,
                    Text(
                      displayName ?? '',
                      maxLines: 1,
                      softWrap: false,
                      overflow: TextOverflow.ellipsis,
                      textWidthBasis: TextWidthBasis.longestLine,
                      style: tweetDisplayNameStyle(context),
                    ),
                  ),
                ),
                VerifiedBadges(
                  verification:
                      verification ?? UserVerification.fromFlag(verified),
                  size: 16,
                  gap: kTweetSpace1,
                ),
              ],
            ),
          ),
          if (handle != null)
            Flexible(
              child: LayoutBuilder(
                builder: (context, room) =>
                    room.maxWidth < kTweetAuthorHandleMinWidth
                    ? const SizedBox.shrink()
                    : Padding(
                        padding: const EdgeInsetsDirectional.only(
                          start: kTweetSpace1,
                        ),
                        child: _tappable(
                          onTapHandle,
                          _handleText('$handlePrefix$handle', meta, handleSpan),
                        ),
                      ),
              ),
            ),
        ],
      ),
    );
  }

  static Widget _tappable(VoidCallback? onTap, Widget child) => onTap == null
      ? child
      : GestureDetector(
          onTap: onTap,
          behavior: HitTestBehavior.opaque,
          child: child,
        );
}
