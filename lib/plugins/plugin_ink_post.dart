/// A Mastodon, Bluesky or Threads post in an X post's two columns, so the
/// three read alike in a mixed timeline and on their own screens
/// (docs/specs/feed-ink-networks.md).
library;

import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/tweet/tweet_action_style.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/tweet/tweet_ink_layout.dart';
import 'package:xta/ui/provenance_accent.dart';

class PluginInkPost extends StatelessWidget {
  /// The network: its badge on the avatar in a mixed timeline, and its name
  /// for a screen reader.
  final String pluginId;
  final bool showSourceBadge;

  /// Repost, boost and pinned lines above the post, as [TweetContextRow]s.
  final List<Widget> contextLines;

  /// The author's picture, [kTweetAvatarSize] across.
  final Widget avatar;
  final String authorName;
  final VoidCallback onOpenAuthor;

  /// A [TweetAuthorLine]. A tap on it opens the author, as the avatar does.
  final Widget authorLine;

  /// At the end of the author line, where an X post has ⋯.
  final List<Widget> trailing;

  /// Everything under the author line, each piece at the column's 16 dp sides.
  final List<Widget> body;

  /// The actions row. It runs from the column's start to the end edge, so its
  /// last glyph lines up with an X post's share glyph.
  final Widget actions;

  final Color? color;
  final VoidCallback? onTap;
  final VoidCallback onLongPress;

  /// Drawn over the card's colour and under the post, for a conversation.
  final Widget? rail;

  /// The hairline under the post. A conversation's connected posts go without,
  /// as an X thread's do, so the rail runs on unbroken.
  final bool divider;

  const PluginInkPost({
    super.key,
    required this.pluginId,
    required this.showSourceBadge,
    required this.avatar,
    required this.authorName,
    required this.onOpenAuthor,
    required this.authorLine,
    required this.body,
    required this.actions,
    required this.onLongPress,
    this.contextLines = const [],
    this.trailing = const [],
    this.color,
    this.onTap,
    this.rail,
    this.divider = true,
  });

  @override
  Widget build(BuildContext context) {
    final network = pluginById(pluginId)?.title(context);
    return RepaintBoundary(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          tweetFlatCard(
            color: color,
            child: InkWell(
              onTap: onTap,
              onLongPress: onLongPress,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  ...contextLines,
                  TweetInkLayout(
                    avatar: TweetAvatarTarget(
                      avatar: ProvenanceAvatarBadge(pluginId: pluginId, show: showSourceBadge, child: avatar),
                      size: kTweetAvatarSize,
                      label: [authorName, if (showSourceBadge && network != null) network].join(', '),
                      onTap: onOpenAuthor,
                    ),
                    authorLine: GestureDetector(
                      onTap: onOpenAuthor,
                      behavior: HitTestBehavior.opaque,
                      child: authorLine,
                    ),
                    trailing: trailing,
                    body: [
                      for (final piece in body)
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: kTweetHorizontalPadding),
                          child: piece,
                        ),
                      Padding(
                        padding: const EdgeInsetsDirectional.only(start: kTweetSpace2),
                        child: actions,
                      ),
                    ],
                    rail: rail,
                  ),
                ],
              ),
            ),
          ),
          if (divider) tweetHairlineDivider(context),
        ],
      ),
    );
  }
}

/// Follow, at the end of the author line where an X post has ⋯: the glyph on
/// the line, its 48 dp target reaching down over the text.
Widget pluginInkFollowButton(BuildContext context, {required String tooltip, required VoidCallback onPressed}) =>
    tweetActionIconButton(
      context,
      icon: Icons.person_add_alt_1_outlined,
      color: tweetFooterButtonsColorOf(context),
      style: tweetActionButtonStyle(tweetActionGlyphPadding(alignTop: true)),
      tooltip: tooltip,
      onPressed: onPressed,
    );
