import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/links/link_post_context.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/tweet/tweet_chrome.dart';

const double _avatarSize = 32;

/// The post an article came from, floating over the article: its author, its
/// counts and its network. Tapping it goes back to the post.
///
/// Counts are display only, as on the cards: nothing here posts, likes or
/// reposts anywhere.
class LinkPostContextBar extends StatelessWidget {
  final LinkPostContext post;
  final VoidCallback onBackToPost;
  final VoidCallback? onSharePost;

  const LinkPostContextBar({super.key, required this.post, required this.onBackToPost, this.onSharePost});

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(28);
    final surface = tweetSurfaceColor(context);
    return Material(
      color: Color.alphaBlend(tweetPrimaryColor(context).withValues(alpha: 0.06), surface),
      elevation: 3,
      shadowColor: Colors.black54,
      shape: RoundedRectangleBorder(
        borderRadius: radius,
        side: BorderSide(color: tweetDividerColor(context)),
      ),
      clipBehavior: Clip.antiAlias,
      child: Row(
        children: [
          Expanded(child: _backArea(context)),
          if (onSharePost != null)
            IconButton(
              tooltip: L10n.of(context).share_tweet_link,
              onPressed: onSharePost,
              icon: Icon(Icons.send_outlined, size: kTweetActionIconSize, color: tweetSecondaryColor(context)),
            ),
          _mark(),
        ],
      ),
    );
  }

  Widget _backArea(BuildContext context) {
    final counts = _Counts.of(context, post);
    return Semantics(
      button: true,
      label: [
        L10n.of(context).link_browser_back_to_post,
        if (post.author.isNotEmpty) post.author,
        ...counts.spoken,
      ].join(', '),
      excludeSemantics: true,
      onTap: onBackToPost,
      child: InkWell(
        onTap: onBackToPost,
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: kTweetTouchTarget + 8),
          child: Padding(
            padding: const EdgeInsetsDirectional.fromSTEB(kTweetSpace2 + 2, kTweetSpace2, kTweetSpace1, kTweetSpace2),
            child: Row(
              children: [
                _avatar(context),
                const SizedBox(width: kTweetSpace3),
                Expanded(child: counts.build(context)),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _avatar(BuildContext context) {
    final fallback = FallbackAvatar(
      seed: post.author.isEmpty ? post.sourceId : post.author,
      displayName: post.author,
      size: _avatarSize,
      accent: tweetAccentColor(context),
    );
    final url = post.avatarUrl?.trim() ?? '';
    if (url.isEmpty) return fallback;
    return ClipOval(
      child: ExtendedImage.network(
        url,
        width: _avatarSize,
        height: _avatarSize,
        fit: BoxFit.cover,
        cacheWidth: (_avatarSize * MediaQuery.devicePixelRatioOf(context)).ceil(),
        loadStateChanged: (state) => state.extendedImageLoadState == LoadState.failed ? fallback : null,
      ),
    );
  }

  Widget _mark() {
    final plugin = pluginById(post.sourceId);
    if (plugin == null) return const SizedBox(width: kTweetSpace3);
    return Padding(
      padding: const EdgeInsetsDirectional.only(end: kTweetSpace3 + 2),
      child: ExcludeSemantics(child: pluginMark(plugin, size: 22)),
    );
  }
}

/// The three counts, written once for the eye and once for a screen reader.
class _Counts {
  final List<(IconData, String)> shown;
  final List<String> spoken;

  const _Counts(this.shown, this.spoken);

  factory _Counts.of(BuildContext context, LinkPostContext post) {
    final l10n = L10n.of(context);
    final hidden = _countsHidden(context);
    String label(int? count) => hidden || count == null ? '' : compactCount(count);
    final entries = [
      (Icons.favorite_border, post.likes, l10n.link_post_likes_count),
      (Icons.mode_comment_outlined, post.replies, l10n.link_post_replies_count),
      (Icons.repeat, post.reposts, l10n.link_post_reposts_count),
    ];
    return _Counts(
      [for (final (icon, count, _) in entries) (icon, label(count))],
      [
        for (final (_, count, speak) in entries)
          if (label(count).isNotEmpty) speak(label(count)),
      ],
    );
  }

  Widget build(BuildContext context) {
    final color = tweetSecondaryColor(context);
    final style = tweetMetadataStyle(context).copyWith(color: tweetPrimaryColor(context));
    return Wrap(
      spacing: kTweetSpace4,
      runSpacing: kTweetSpace1,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        for (final (icon, label) in shown)
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 18, color: color),
              if (label.isNotEmpty) ...[const SizedBox(width: kTweetSpace1), Text(label, style: style)],
            ],
          ),
      ],
    );
  }
}

/// Zen and calm modes hide counts on every card; the bar follows them.
bool _countsHidden(BuildContext context) {
  try {
    final prefs = PrefService.of(context, listen: false);
    return prefs.get(optionZenMode) == true || prefs.get(optionCalmMode) == true;
  } catch (_) {
    return false;
  }
}
