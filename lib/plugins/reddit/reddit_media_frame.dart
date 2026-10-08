import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/tweet_chrome.dart';

/// How tall a picture is allowed to get before it is cropped.
///
/// A phone-height meme would otherwise push everything after it off the screen,
/// and a feed where one post fills the viewport is not a feed.
const double kRedditMediaMaxHeight = 420;

/// The shortest a media frame may be, so a wide banner is still something you
/// can aim at rather than a stripe.
const double kRedditMediaMinHeight = 120;

/// The tallest shape a framed piece of media may take: a full-height portrait.
const double kRedditMediaMinAspectRatio = 9 / 16;

/// And the widest, so a panorama is not drawn as a letterbox slit.
const double kRedditMediaMaxAspectRatio = 16 / 9;

/// The frame a gallery is paged inside. Square is the shape that wrongs a set
/// of mixed portrait and landscape pictures the least.
const double kRedditGalleryAspectRatio = 1;

/// The shape of the tile that stands in for content nobody has asked to see.
const double kRedditSensitiveAspectRatio = 4 / 3;

enum RedditSensitiveGateKind { nsfw, spoiler }

/// A shape that is safe to lay a card out around.
///
/// Reddit reports a size for its own videos and nothing else, so [reported] is
/// usually null. Whatever arrives is bounded at both ends: a 9:21 phone
/// recording is not allowed to take three screens, and a 32:9 panorama is not
/// allowed to become a hairline.
double redditMediaAspectRatio(
  double? reported, {
  double fallback = kRedditMediaMaxAspectRatio,
}) {
  if (reported == null || !reported.isFinite || reported <= 0) {
    return fallback;
  }

  return reported.clamp(kRedditMediaMinAspectRatio, kRedditMediaMaxAspectRatio);
}

/// The box every Reddit picture is painted into.
///
/// Rounded like a tweet's media, tinted underneath so a picture that is still
/// arriving — or one Reddit will not serve — is a frame with something in it
/// rather than a hole the card collapses around, and always the full width it
/// was offered, so an opened post and a card in the feed frame the same picture
/// the same way.
///
/// Give it an [aspectRatio] for a frame that is decided before the bytes land,
/// or leave it null to let the picture choose its own height within
/// [kRedditMediaMinHeight] and [maxHeight].
class RedditMediaFrame extends StatelessWidget {
  final double? aspectRatio;
  final double maxHeight;
  final Widget child;

  const RedditMediaFrame({
    super.key,
    this.aspectRatio,
    this.maxHeight = kRedditMediaMaxHeight,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    final painted = Stack(
      fit: StackFit.passthrough,
      children: [
        Positioned.fill(
          child: ColoredBox(
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
          ),
        ),
        child,
      ],
    );

    final ratio = aspectRatio;

    return ClipRRect(
      borderRadius: BorderRadius.circular(tweetMediaRadiusOf(context)),
      child: ratio != null
          ? AspectRatio(aspectRatio: ratio, child: painted)
          : ConstrainedBox(
              constraints: BoxConstraints(
                minHeight: kRedditMediaMinHeight,
                maxHeight: maxHeight,
              ),
              // The width is taken rather than asked for: an opened post lays
              // its column out left-aligned, and without this a portrait photo
              // would shrink to a narrow strip there while filling the card in
              // the feed.
              child: SizedBox(width: double.infinity, child: painted),
            ),
    );
  }
}

/// Holds sensitive media back until the reader asks for it.
///
/// Not a blur over the picture: the picture is not built at all, so nothing is
/// fetched, decoded or drawn until the tile is tapped. Whatever [child] costs,
/// a reader who did not ask does not pay it.
class RedditSensitiveGate extends StatefulWidget {
  /// Whether the post is marked over-18.
  final bool sensitive;

  final RedditSensitiveGateKind kind;

  /// The post this stands in front of. A feed recycles its cards, and the
  /// reveal must not survive being handed a different post.
  final String revealKey;

  /// The shape of the tile shown while the content is held back.
  final double aspectRatio;

  final Widget child;

  const RedditSensitiveGate({
    super.key,
    required this.sensitive,
    required this.revealKey,
    required this.child,
    this.kind = RedditSensitiveGateKind.nsfw,
    this.aspectRatio = kRedditSensitiveAspectRatio,
  });

  @override
  State<RedditSensitiveGate> createState() => _RedditSensitiveGateState();
}

class _RedditSensitiveGateState extends State<RedditSensitiveGate> {
  late bool _revealed = !widget.sensitive;

  @override
  void didUpdateWidget(RedditSensitiveGate old) {
    super.didUpdateWidget(old);
    // Same element, different post: carrying "shown" across that took the
    // cover off a picture nobody had asked to see.
    if (old.revealKey != widget.revealKey ||
        old.sensitive != widget.sensitive) {
      _revealed = !widget.sensitive;
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_revealed) {
      return widget.child;
    }

    final l10n = L10n.of(context);
    final theme = Theme.of(context);

    return ClipRRect(
      borderRadius: BorderRadius.circular(tweetMediaRadiusOf(context)),
      child: LayoutBuilder(
        builder: (context, constraints) => Semantics(
          button: true,
          child: Container(
            // A minimum rather than a fixed height: at a large text size the
            // wording is what decides how tall the tile is, and a fixed frame
            // would clip it.
            constraints: BoxConstraints(
              minHeight: constraints.hasBoundedWidth
                  ? constraints.maxWidth / widget.aspectRatio
                  : kRedditMediaMinHeight,
            ),
            color: theme.colorScheme.surfaceContainerHighest,
            child: Material(
              type: MaterialType.transparency,
              child: InkWell(
                onTap: () => setState(() => _revealed = true),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(_icon, color: theme.colorScheme.onSurfaceVariant),
                        const SizedBox(height: 8),
                        Text(
                          _label(l10n),
                          textAlign: TextAlign.center,
                          style: theme.textTheme.titleSmall,
                        ),
                        Text(
                          _description(l10n),
                          textAlign: TextAlign.center,
                          style: theme.textTheme.bodySmall!.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  IconData get _icon => switch (widget.kind) {
    RedditSensitiveGateKind.nsfw => Icons.visibility_off_outlined,
    RedditSensitiveGateKind.spoiler => Icons.warning_amber_outlined,
  };

  String _label(L10n l10n) => switch (widget.kind) {
    RedditSensitiveGateKind.nsfw => l10n.possibly_sensitive,
    RedditSensitiveGateKind.spoiler => l10n.plugin_reddit_spoiler,
  };

  String _description(L10n l10n) => switch (widget.kind) {
    RedditSensitiveGateKind.nsfw => l10n.tap_to_show_getMediaType_item_type(
      l10n.media,
    ),
    RedditSensitiveGateKind.spoiler => l10n.plugin_reddit_tap_to_show_spoiler,
  };
}
