import 'package:xta/plugins/social_account_groups.dart';
import 'package:xta/plugins/threads/threads_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_post_actions.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_ink_post.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/plugins/plugin_profile_tabs.dart';
import 'package:xta/plugins/threads/threads_archive.dart';
import 'package:xta/plugins/threads/threads_likes_store.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_profile_screen.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/plugins/threads/threads_rich_text.dart';
import 'package:xta/plugins/threads/threads_media.dart';
import 'package:xta/plugins/threads/threads_quote.dart';
import 'package:xta/plugins/threads/threads_thread_screen.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/tweet/_like_button.dart';
import 'package:xta/tweet/thread_rail.dart';
import 'package:xta/tweet/tweet.dart' show tweetCardColor;
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/tweet/tweet_header.dart';
import 'package:xta/tweet/tweet_ink_layout.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/utils/urls.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/links/link_opening.dart';
import 'package:xta/links/link_post_context.dart';
import 'package:xta/links/link_preview_card.dart';

/// A Threads post as a timeline card.
///
/// Laid out as an X post is (docs/specs/feed-ink-networks.md). Meta feeds can carry likes, replies,
/// reposts and a link preview; RSSHub cannot — counts stay blank rather than
/// inventing zeroes, but the engagement icons stay so the card still feels
/// like the Threads app.
class ThreadsPostCard extends StatelessWidget {
  final ThreadsPost post;

  /// Set in a mixed timeline so the card says where it came from.
  final bool showSourceBadge;

  /// When false, the card body does not navigate (used for the root of a thread).
  final bool openOnTap;

  /// Override for opening the post in-app. Defaults to [ThreadsThreadScreen].
  final VoidCallback? onOpen;

  /// Author avatar / name — defaults to [ThreadsProfileScreen].
  final VoidCallback? onAuthorTap;

  /// External browser affordance in the engagement row.
  final VoidCallback? onOpenBrowser;

  /// In a conversation: the rail to the post above and to the one below,
  /// through the avatar, as an X thread draws it.
  final bool threadConnectTop;
  final bool threadConnectBottom;

  const ThreadsPostCard({
    super.key,
    required this.post,
    this.showSourceBadge = true,
    this.openOnTap = true,
    this.onOpen,
    this.onAuthorTap,
    this.onOpenBrowser,
    this.threadConnectTop = false,
    this.threadConnectBottom = false,
  });

  void _open(BuildContext context) {
    if (onOpen != null) {
      onOpen!();
      return;
    }
    Navigator.push(context, MaterialPageRoute(builder: (_) => ThreadsThreadScreen(post: post)));
  }

  void _openAuthor(BuildContext context) {
    if (onAuthorTap != null) {
      onAuthorTap!();
      return;
    }
    Navigator.push(context, MaterialPageRoute(builder: (_) => ThreadsProfileScreen(username: post.handle)));
  }

  void _openBrowser(BuildContext context) {
    if (onOpenBrowser != null) {
      onOpenBrowser!();
      return;
    }
    final url = post.url;
    if (url != null) {
      openUri(context, url);
    }
  }

  void _openReposter(BuildContext context) {
    final handle = post.repostedByHandle;
    if (handle == null || handle.isEmpty) {
      return;
    }
    Navigator.push(context, MaterialPageRoute(builder: (_) => ThreadsProfileScreen(username: handle)));
  }

  @override
  Widget build(BuildContext context) {
    final accounts = context.read<ThreadsAccountsStore>();
    return ScopedBuilder<ThreadsAccountsStore, List<ThreadsAccount>>(
      store: accounts,
      distinct: (_) => accounts.follows(post.handle),
      onState: (context, _) => _post(context, following: accounts.follows(post.handle)),
    );
  }

  Widget _post(BuildContext context, {required bool following}) {
    final date = post.publishedAt;
    final replyTo = post.replyToHandle;
    return PluginInkPost(
      pluginId: pluginIdThreads,
      showSourceBadge: showSourceBadge,
      color: tweetCardColor(context),
      onTap: openOnTap ? () => _open(context) : null,
      onLongPress: () => showPluginPostActions(
        context,
        post: threadsArchive(post),
        url: post.url ?? post.id,
        onGroup: () => addThreadsAccountToGroup(context, _account),
      ),
      contextLines: [if (post.isRepost) _repostLine(context)],
      avatar: _avatar(context),
      authorName: post.authorName,
      onOpenAuthor: () => _openAuthor(context),
      authorLine: TweetAuthorLine(
        displayName: post.authorName,
        handle: post.handle,
        verified: post.isVerified,
        timestamp: date == null ? null : Timestamp(timestamp: date, compact: true),
      ),
      trailing: [
        if (!following)
          pluginInkFollowButton(
            context,
            tooltip: L10n.of(context).plugin_threads_follow,
            onPressed: () => context.read<ThreadsAccountsStore>().add(_account),
          ),
      ],
      body: [
        if (post.topicTag case final tag?)
          Align(
            alignment: AlignmentDirectional.centerStart,
            child: ThreadsTopicTag(tag: tag),
          ),
        if (replyTo != null && replyTo.isNotEmpty) PluginReplyingTo(name: replyTo),
        _content(context),
      ],
      actions: _ThreadsEngagementRow(
        post: post,
        onOpen: () => _open(context),
        onOpenBrowser: () => _openBrowser(context),
      ),
      // Inside the card, so the card's colour does not cover it.
      rail: threadConnectTop || threadConnectBottom
          ? ThreadRailLines(connectTop: threadConnectTop, connectBottom: threadConnectBottom)
          : null,
      divider: !threadConnectBottom,
    );
  }

  ThreadsAccount get _account => ThreadsAccount(handle: post.handle, name: post.authorName, avatarUrl: post.avatarUrl);

  /// The text, media, link card, quote and "Show thread", in that order.
  Widget _content(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      if (post.text.isNotEmpty) ...[
        const SizedBox(height: kInkTextGap),
        ThreadsCaption(text: post.text, fragments: post.fragments, style: tweetBodyStyle(context)),
      ],
      if (post.hasMedia) ...[
        const SizedBox(height: 10),
        ThreadsPostMedia(post: post, onOpenPost: openOnTap ? () => _open(context) : null),
      ],
      if (post.linkCard != null) ...[const SizedBox(height: 10), _linkPreview(context, post.linkCard!)],
      if (post.quoted != null) ...[const SizedBox(height: 10), ThreadsQuotedPost(quote: post.quoted!)],
      if (post.selfThreadCount > 0 && openOnTap)
        _ShowThreadButton(count: post.selfThreadCount, onPressed: () => _open(context)),
    ],
  );

  Widget _linkPreview(BuildContext context, ThreadsLinkCard card) => LinkPreviewCard(
    url: card.url,
    title: card.title,
    description: card.description,
    imageUrl: card.imageUrl,
    layout: linkPreviewLayoutFor(card.url, hasImage: card.hasImage),
    imageBuilder: (_, url, cacheWidth) => ThreadsNetworkImage(url, fit: BoxFit.cover, cacheWidth: cacheWidth),
    onTap: () => openPostLink(context, card.url, title: card.title, post: _linkContext(context)),
  );

  LinkPostContext _linkContext(BuildContext context) => LinkPostContext(
    sourceId: pluginIdThreads,
    author: post.authorName,
    avatarUrl: post.avatarUrl,
    replies: post.replyCount,
    reposts: post.repostCount,
    likes: post.likeCount,
    postUrl: post.url,
    openPost: openOnTap
        ? () {
            if (context.mounted) _open(context);
          }
        : null,
  );

  Widget _repostLine(BuildContext context) {
    final reposter = post.repostedByHandle;
    return TweetContextRow(
      icon: Icons.repeat,
      label: Text(L10n.of(context).plugin_threads_reposted(post.reposterDisplayName), overflow: TextOverflow.ellipsis),
      onTap: reposter == null || reposter.isEmpty ? null : () => _openReposter(context),
    );
  }

  Widget _avatar(BuildContext context) {
    final theme = Theme.of(context);
    final avatar = post.avatarUrl;
    const size = kTweetAvatarSize;

    return ClipOval(
      child: avatar == null
          ? FallbackAvatar(
              seed: post.handle,
              displayName: post.authorName,
              size: size,
              accent: theme.colorScheme.primary,
            )
          : ThreadsNetworkImage(
              avatar,
              width: size,
              height: size,
              fit: BoxFit.cover,
              cacheWidth: (size * MediaQuery.devicePixelRatioOf(context)).ceil(),
            ),
    );
  }
}

/// Replies / reposts from Meta when present; likes are local (never sent to Threads).
class _ThreadsEngagementRow extends StatelessWidget {
  final ThreadsPost post;
  final VoidCallback onOpen;
  final VoidCallback onOpenBrowser;

  const _ThreadsEngagementRow({required this.post, required this.onOpen, required this.onOpenBrowser});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final prefs = PrefService.of(context, listen: false);
    final hideCounts = prefs.get(optionZenMode) == true || prefs.get(optionCalmMode) == true;
    final likes = context.read<ThreadsLikesStore>();

    String metaLabel(int? count) {
      if (count == null || hideCounts) {
        return '';
      }
      return compactCount(count);
    }

    return Padding(
      padding: const EdgeInsets.only(top: 2),
      child: Row(
        children: [
          TextButton.icon(
            style: footerButtonStyle,
            onPressed: onOpen,
            icon: Icon(Icons.mode_comment_outlined, size: 18, color: muted),
            label: Text(metaLabel(post.replyCount), style: theme.textTheme.bodySmall!.copyWith(color: muted)),
          ),
          TextButton.icon(
            style: footerButtonStyle,
            onPressed: onOpen,
            icon: Icon(Icons.repeat, size: 18, color: muted),
            label: Text(metaLabel(post.repostCount), style: theme.textTheme.bodySmall!.copyWith(color: muted)),
          ),
          ScopedBuilder<ThreadsLikesStore, List<ThreadsPost>>(
            store: likes,
            distinct: (_) => likes.isLiked(post.id),
            onState: (context, state) {
              final isLiked = likes.isLiked(post.id);
              final shown = post.likeCount == null ? null : post.likeCount! + (isLiked ? 1 : 0);
              final likeLabel = hideCounts || shown == null ? '' : compactCount(shown);
              return LikeButton(
                isLiked: isLiked,
                label: likeLabel,
                color: isLiked ? theme.colorScheme.primary : muted,
                onPressed: () async {
                  final wasLiked = isLiked;
                  await likes.toggle(post);
                  if (!wasLiked && context.mounted) {
                    maybeShowLikeToast(context);
                  }
                },
              );
            },
          ),
          PluginPostBookmark(post: threadsArchive(post)),
          const Spacer(),
          if (post.url != null)
            tweetFooterIconButton(
              context,
              Icons.open_in_new,
              muted,
              null,
              onOpenBrowser,
              L10n.of(context).open_in_browser,
            ),
        ],
      ),
    );
  }
}

/// The topic a post was filed under — Threads' "› Books" — opening its feed
/// on Threads, where tag search lives.
class ThreadsTopicTag extends StatelessWidget {
  final String tag;

  const ThreadsTopicTag({super.key, required this.tag});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = theme.colorScheme.onSurfaceVariant;
    return InkWell(
      onTap: () => openLink(context, threadsTagSearchUrl(tag)),
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 2),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.chevron_right, size: 14, color: color),
            Flexible(
              child: Text(
                tag,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.labelMedium!.copyWith(color: color, fontWeight: FontWeight.w600),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// "Show N more posts in this thread" — the author chained more under this
/// one, which the feed shows only the first of.
class _ShowThreadButton extends StatelessWidget {
  final int count;
  final VoidCallback onPressed;

  const _ShowThreadButton({required this.count, required this.onPressed});

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: AlignmentDirectional.centerStart,
      child: TextButton.icon(
        style: TextButton.styleFrom(minimumSize: const Size(48, 40), padding: const EdgeInsets.symmetric(horizontal: 4)),
        onPressed: onPressed,
        icon: const Icon(Icons.forum_outlined, size: 18),
        label: Text(L10n.of(context).plugin_threads_show_thread(count)),
      ),
    );
  }
}
