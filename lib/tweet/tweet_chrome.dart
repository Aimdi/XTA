import 'package:flutter/material.dart';
import 'package:xta/ui/contrast.dart';
import 'package:xta/ui/x_look_theme.dart';

/// Shared timeline chrome for tweet tiles.
///
/// These values are the tweet module's implementation of the Quiet Signal
/// design tokens. Feature widgets should consume these names instead of
/// introducing another literal for the same role.
const double kTweetSpace1 = 4;
const double kTweetSpace2 = 8;
const double kTweetSpace3 = 12;
const double kTweetSpace4 = 16;
const double kTweetSpace6 = 24;

const double kTweetHorizontalPadding = kTweetSpace4;
const double kTweetVerticalPadding = kTweetSpace3;
const double kTweetAvatarSize = 40;
const double kTweetQuotedAvatarSize = 32;
const double kTweetTouchTarget = 48;
const double kTweetActionIconSize = 20;
const double kTweetThreadRailWidth = 2;
const double kTweetMediaGap = 2;
const double kTweetMediaRadius = 16;
const double kTweetDividerThickness = 0.5;

ShapeBorder get kTweetCardShape =>
    const RoundedRectangleBorder(borderRadius: BorderRadius.zero);

Color tweetDividerColor(BuildContext context) {
  final tokens = XLookTokens.maybeOf(context);
  if (tokens != null) return tokens.divider;
  return Theme.of(context).colorScheme.surfaceBright.withAlpha(150);
}

double tweetMediaRadiusOf(BuildContext context) =>
    XLookTokens.maybeOf(context)?.mediaRadius ?? kTweetMediaRadius;

Color tweetPrimaryColor(BuildContext context) =>
    XLookTokens.maybeOf(context)?.onBackground ??
    Theme.of(context).colorScheme.onSurface;

Color tweetSecondaryColor(BuildContext context) =>
    XLookTokens.maybeOf(context)?.secondary ??
    Theme.of(context).colorScheme.onSurfaceVariant;

Color tweetAccentColor(BuildContext context) =>
    XLookTokens.maybeOf(context)?.accent ??
    Theme.of(context).colorScheme.primary;

/// Accent corrected for small text against the surface it is painted on.
///
/// The selectable Aimdi palette includes yellow, orange and green. Those are
/// excellent identifying colours but fail text contrast on a light surface if
/// they are used unchanged.
Color tweetReadableAccentColor(BuildContext context, {Color? background}) {
  if (background == null) {
    return Theme.of(context).colorScheme.primary;
  }
  return ensureContrast(tweetAccentColor(context), background);
}

/// Foreground for a solid accent control. A fixed white glyph disappears on
/// most of the selectable accents, especially yellow and orange.
Color tweetOnAccentColor(BuildContext context) {
  return contrastingForeground(tweetAccentColor(context));
}

Color tweetSurfaceColor(BuildContext context) =>
    XLookTokens.maybeOf(context)?.card ?? Theme.of(context).colorScheme.surface;

TextStyle tweetBodyStyle(BuildContext context) =>
    Theme.of(context).textTheme.bodyMedium!.copyWith(
      color: tweetPrimaryColor(context),
      fontSize: 15,
      height: 1.35,
      fontWeight: FontWeight.w400,
    );

TextStyle tweetDisplayNameStyle(BuildContext context) =>
    Theme.of(context).textTheme.titleMedium!.copyWith(
      color: tweetPrimaryColor(context),
      fontSize: 15,
      height: 1.25,
      fontWeight: FontWeight.w700,
    );

TextStyle tweetMetadataStyle(BuildContext context) =>
    Theme.of(context).textTheme.bodySmall!.copyWith(
      color: tweetSecondaryColor(context),
      fontSize: 13,
      height: 1.35,
      fontWeight: FontWeight.w400,
    );

TextStyle tweetLabelStyle(BuildContext context) =>
    Theme.of(context).textTheme.labelLarge!.copyWith(
      color: tweetPrimaryColor(context),
      fontSize: 14,
      height: 1.3,
      fontWeight: FontWeight.w700,
    );

/// Edge-to-edge, elevation-free surface used by standalone tiles and thread wrappers.
Widget tweetFlatCard({
  required Color? color,
  required Widget child,
  // The shape is a zero-radius rectangle, so an antialiased clip per tile was
  // a clip-path layer spent clipping nothing.
  Clip clipBehavior = Clip.none,
}) {
  return Card(
    elevation: 0,
    margin: EdgeInsets.zero,
    shape: kTweetCardShape,
    clipBehavior: clipBehavior,
    color: color,
    child: child,
  );
}

/// Nudges [surface] a few percent towards [toward] — the flat-design
/// equivalent of elevation, so a nested card separates without a shadow.
Color _lift(Color surface, Color toward) =>
    Color.alphaBlend(toward.withValues(alpha: 0.06), surface);

/// Chrome for a quoted tweet nested inside another tweet.
///
/// The nested card has to read as nested on every theme, including the pure
/// black ones where a translucent surface tint disappears entirely, so the
/// border comes from an outline token and the fill is lifted off the parent
/// card rather than matching it.
BoxDecoration quoteCardDecoration(BuildContext context) {
  final tokens = XLookTokens.maybeOf(context);
  final scheme = Theme.of(context).colorScheme;
  final onSurface = tokens?.onBackground ?? scheme.onSurface;
  final fill = _lift(tokens?.card ?? scheme.surface, onSurface);

  // X's own hairline (#EFF3F4 light, #38444D dim) barely registers against the
  // card it outlines, which is what made quotes indistinguishable from separate
  // posts. Correct it against the fill it is drawn on instead of picking a
  // colour per theme.
  final border = ensureContrast(
    tokens?.border ?? scheme.outline,
    fill,
    minRatio: 1.5,
  );

  return BoxDecoration(
    color: fill,
    border: Border.all(color: border),
    borderRadius: BorderRadius.circular(tweetMediaRadiusOf(context)),
  );
}

/// The shared bounded surface for content embedded inside a post.
class TweetEmbedSurface extends StatelessWidget {
  final Widget child;
  final VoidCallback? onTap;
  final EdgeInsetsGeometry margin;
  final EdgeInsetsGeometry? padding;
  final String? semanticLabel;

  const TweetEmbedSurface({
    super.key,
    required this.child,
    this.onTap,
    this.margin = const EdgeInsetsDirectional.fromSTEB(
      kTweetHorizontalPadding,
      kTweetSpace2,
      kTweetHorizontalPadding,
      0,
    ),
    this.padding,
    this.semanticLabel,
  });

  @override
  Widget build(BuildContext context) {
    final content = padding == null
        ? child
        : Padding(padding: padding!, child: child);
    final decorated = Container(
      decoration: quoteCardDecoration(context),
      clipBehavior: Clip.antiAlias,
      child: onTap == null ? content : InkWell(onTap: onTap, child: content),
    );

    return Semantics(
      button: onTap != null,
      label: semanticLabel,
      child: Padding(padding: margin, child: decorated),
    );
  }
}

/// Shared frame for timeline media. It reserves one visual boundary for all
/// photos, GIFs and video instead of letting each medium invent its own card.
class TweetMediaFrame extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry margin;

  const TweetMediaFrame({
    super.key,
    required this.child,
    this.margin = const EdgeInsetsDirectional.fromSTEB(
      kTweetHorizontalPadding,
      kTweetSpace2,
      kTweetHorizontalPadding,
      0,
    ),
  });

  @override
  Widget build(BuildContext context) {
    final tokens = XLookTokens.maybeOf(context);
    final surface =
        tokens?.card ?? Theme.of(context).colorScheme.surface;
    final border = ensureContrast(
      tokens?.border ?? Theme.of(context).colorScheme.outlineVariant,
      surface,
      minRatio: 1.3,
    );
    return RepaintBoundary(
      child: Container(
        margin: margin,
        clipBehavior: Clip.antiAlias,
        decoration: BoxDecoration(
          border: Border.all(color: border, width: kTweetDividerThickness),
          borderRadius: BorderRadius.circular(tweetMediaRadiusOf(context)),
        ),
        child: child,
      ),
    );
  }
}

/// A small overlay label used for media position and type badges.
class TweetMediaBadge extends StatelessWidget {
  final String? label;
  final IconData? icon;
  final String? semanticLabel;

  const TweetMediaBadge({
    super.key,
    this.label,
    this.icon,
    this.semanticLabel,
  }) : assert(label != null || icon != null);

  @override
  Widget build(BuildContext context) {
    final badge = DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.68),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: Colors.white24, width: 0.5),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: kTweetSpace2,
          vertical: kTweetSpace1,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (icon != null)
              Icon(icon, color: Colors.white, size: 16),
            if (icon != null && label != null)
              const SizedBox(width: kTweetSpace1),
            if (label != null)
              Text(
                label!,
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  color: Colors.white,
                  fontSize: 12,
                  height: 1.2,
                  fontWeight: FontWeight.w700,
                ),
              ),
          ],
        ),
      ),
    );

    if (semanticLabel == null) {
      return badge;
    }
    return Semantics(
      label: semanticLabel,
      child: ExcludeSemantics(child: badge),
    );
  }
}

/// Community Notes are supporting context rather than another post or a
/// generic Material card. This keeps the note visually attached to its host
/// while giving the heading and body a predictable reading order.
class TweetCommunityNote extends StatelessWidget {
  final String title;
  final Widget child;

  const TweetCommunityNote({
    super.key,
    required this.title,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return TweetEmbedSurface(
      margin: const EdgeInsets.all(kTweetSpace2),
      padding: const EdgeInsets.all(kTweetSpace3),
      semanticLabel: title,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Icon(
                Icons.group_outlined,
                size: 16,
                color: tweetSecondaryColor(context),
              ),
              const SizedBox(width: kTweetSpace2),
              Expanded(
                child: Text(
                  title,
                  style: tweetLabelStyle(context),
                ),
              ),
            ],
          ),
          const SizedBox(height: kTweetSpace2),
          DefaultTextStyle.merge(
            style: tweetBodyStyle(context),
            child: child,
          ),
        ],
      ),
    );
  }
}

/// Quiet context above or within a post: repost, pinned, thread and similar
/// labels all share the same alignment, icon scale and text treatment.
class TweetContextRow extends StatelessWidget {
  final IconData icon;
  final Widget label;
  final VoidCallback? onTap;
  final double contentStart;

  const TweetContextRow({
    super.key,
    required this.icon,
    required this.label,
    this.onTap,
    this.contentStart =
        kTweetHorizontalPadding + kTweetAvatarSize + kTweetSpace3,
  });

  @override
  Widget build(BuildContext context) {
    final content = Padding(
      padding: EdgeInsetsDirectional.fromSTEB(
        contentStart,
        kTweetSpace2,
        kTweetHorizontalPadding,
        0,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Icon(icon, size: 16, color: tweetSecondaryColor(context)),
          const SizedBox(width: kTweetSpace2),
          Flexible(
            child: DefaultTextStyle.merge(
              style: tweetMetadataStyle(
                context,
              ).copyWith(fontWeight: FontWeight.w600),
              child: label,
            ),
          ),
        ],
      ),
    );

    if (onTap == null) return content;
    return InkWell(
      onTap: onTap,
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
        child: content,
      ),
    );
  }
}

/// A subdued in-feed state for tombstones and recoverable embedded failures.
class TweetStateTile extends StatelessWidget {
  final IconData icon;
  final String message;
  final VoidCallback? onTap;

  const TweetStateTile({
    super.key,
    required this.icon,
    required this.message,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return TweetEmbedSurface(
      onTap: onTap,
      padding: const EdgeInsets.all(kTweetSpace3),
      child: ConstrainedBox(
        constraints: const BoxConstraints(
          minHeight: kTweetTouchTarget - kTweetSpace6,
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Icon(
              icon,
              size: kTweetActionIconSize,
              color: tweetSecondaryColor(context),
            ),
            const SizedBox(width: kTweetSpace2),
            Expanded(child: Text(message, style: tweetMetadataStyle(context))),
          ],
        ),
      ),
    );
  }
}

/// Calm full-feed empty state. Feature-specific copy is supplied by callers.
class TweetEmptyState extends StatelessWidget {
  final String message;

  const TweetEmptyState({super.key, required this.message});

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.topCenter,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 560),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(
            kTweetSpace6,
            48,
            kTweetSpace6,
            kTweetSpace6,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.forum_outlined,
                size: 48,
                color: tweetSecondaryColor(context),
              ),
              const SizedBox(height: kTweetSpace4),
              Text(
                message,
                textAlign: TextAlign.center,
                style: tweetBodyStyle(
                  context,
                ).copyWith(color: tweetSecondaryColor(context)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

Widget tweetHairlineDivider(BuildContext context) {
  return Divider(
    height: 0,
    thickness: kTweetDividerThickness,
    color: tweetDividerColor(context),
  );
}
