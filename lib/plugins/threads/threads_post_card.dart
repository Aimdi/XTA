import 'package:extended_image/extended_image.dart';
import 'package:xta/plugins/threads/threads_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/threads/threads_likes_store.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_profile_screen.dart';
import 'package:xta/plugins/threads/threads_rich_text.dart';
import 'package:xta/plugins/threads/threads_thread_screen.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/tweet/_like_button.dart';
import 'package:xta/tweet/tweet.dart' show tweetCardColor;
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/utils/urls.dart';

/// Avatar size matching X / Reddit / Mastodon cards.
const double kThreadsAvatarSize = 48;

/// Tallest a single image or link-preview banner is allowed relative to width.
const double kThreadsMediaMaxAspectRatio = 16 / 9;

final NumberFormat _threadsCountFormat = NumberFormat.compact(locale: 'en_US');

/// A Threads post as a timeline card.
///
/// Shaped like the posts around it. Meta feeds can carry likes, replies,
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

  const ThreadsPostCard({
    super.key,
    required this.post,
    this.showSourceBadge = true,
    this.openOnTap = true,
    this.onOpen,
    this.onAuthorTap,
    this.onOpenBrowser,
  });

  void _open(BuildContext context) {
    if (onOpen != null) {
      onOpen!();
      return;
    }
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => ThreadsThreadScreen(post: post)),
    );
  }

  void _openAuthor(BuildContext context) {
    if (onAuthorTap != null) {
      onAuthorTap!();
      return;
    }
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ThreadsProfileScreen(username: post.handle),
      ),
    );
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
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => ThreadsProfileScreen(username: handle)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        tweetFlatCard(
          color: tweetCardColor(context),
          child: InkWell(
            onTap: openOnTap ? () => _open(context) : null,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (post.isRepost) _repostLine(context),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      GestureDetector(
                        onTap: () => _openAuthor(context),
                        child: _avatar(context),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            GestureDetector(
                              onTap: () => _openAuthor(context),
                              behavior: HitTestBehavior.opaque,
                              child: _header(context),
                            ),
                            if (post.text.isNotEmpty) ...[
                              const SizedBox(height: 6),
                              ThreadsCaption(
                                text: post.text,
                                style: theme.textTheme.bodyLarge!.copyWith(
                                  height: 1.35,
                                ),
                              ),
                            ],
                            if (post.hasMedia) ...[
                              const SizedBox(height: 10),
                              _media(context),
                            ],
                            if (post.linkCard != null) ...[
                              const SizedBox(height: 10),
                              _ThreadsLinkPreview(card: post.linkCard!),
                            ],
                            _ThreadsEngagementRow(
                              post: post,
                              onOpen: () => _open(context),
                              onOpenBrowser: () => _openBrowser(context),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
        tweetHairlineDivider(context),
      ],
    );
  }

  Widget _repostLine(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;

    return Padding(
      padding: const EdgeInsets.only(left: 60, bottom: 6),
      child: GestureDetector(
        onTap: () => _openReposter(context),
        behavior: HitTestBehavior.opaque,
        child: Row(
          children: [
            Icon(Icons.repeat, size: 14, color: muted),
            const SizedBox(width: 6),
            Flexible(
              child: Text(
                L10n.of(
                  context,
                ).plugin_threads_reposted(post.reposterDisplayName),
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.labelMedium!.copyWith(color: muted),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _avatar(BuildContext context) {
    final theme = Theme.of(context);
    final avatar = post.avatarUrl;
    const size = kThreadsAvatarSize;

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
              cacheWidth: (size * MediaQuery.devicePixelRatioOf(context))
                  .ceil(),
            ),
    );
  }

  Widget _header(BuildContext context) {
    final theme = Theme.of(context);
    final date = post.publishedAt;
    final metaStyle = theme.textTheme.bodySmall!.copyWith(
      color: theme.colorScheme.onSurfaceVariant,
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Flexible(
              child: Text(
                post.authorName,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.titleSmall!.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
            if (post.isVerified) ...[
              const SizedBox(width: 4),
              Icon(Icons.verified, size: 16, color: theme.colorScheme.primary),
            ],
            if (date != null) ...[
              const SizedBox(width: 6),
              Text('· ${createCompactDate(date)}', style: metaStyle),
            ],
            if (showSourceBadge) ...[
              const SizedBox(width: 6),
              _badge(context, L10n.of(context).plugin_threads_title),
            ],
          ],
        ),
        Text(
          '@${post.handle}',
          overflow: TextOverflow.ellipsis,
          style: metaStyle,
        ),
      ],
    );
  }

  Widget _badge(BuildContext context, String label) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
      decoration: BoxDecoration(
        border: Border.all(color: theme.colorScheme.outline),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(label, style: theme.textTheme.labelSmall),
    );
  }

  void _openImages(BuildContext context, int index) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) =>
            _ThreadsImageViewer(images: post.images, initialIndex: index),
      ),
    );
  }

  Widget _media(BuildContext context) {
    final radius = tweetMediaRadiusOf(context);
    final width = MediaQuery.sizeOf(context).width;
    final scale = MediaQuery.devicePixelRatioOf(context);

    if (post.images.length == 1) {
      return GestureDetector(
        onTap: () => _openImages(context, 0),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(radius),
          child: AspectRatio(
            aspectRatio: kThreadsMediaMaxAspectRatio,
            child: ThreadsNetworkImage(
              post.images.first,
              fit: BoxFit.cover,
              cacheWidth: (width * scale).ceil(),
            ),
          ),
        ),
      );
    }

    return SizedBox(
      height: 240,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: post.images.length,
        separatorBuilder: (_, _) => const SizedBox(width: 6),
        itemBuilder: (context, index) => GestureDetector(
          onTap: () => _openImages(context, index),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(radius),
            child: ThreadsNetworkImage(
              post.images[index],
              width: 220,
              height: 240,
              fit: BoxFit.cover,
              cacheWidth: (220 * scale).ceil(),
            ),
          ),
        ),
      ),
    );
  }
}

/// Fullscreen pager for Threads images — pinch/drag via [ExtendedImageMode.gesture].
class _ThreadsImageViewer extends StatefulWidget {
  final List<String> images;
  final int initialIndex;

  const _ThreadsImageViewer({required this.images, required this.initialIndex});

  @override
  State<_ThreadsImageViewer> createState() => _ThreadsImageViewerState();
}

class _ThreadsImageViewerState extends State<_ThreadsImageViewer> {
  late final _controller = PageController(initialPage: widget.initialIndex);
  late int _index = widget.initialIndex;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
      ),
      body: Stack(
        alignment: Alignment.bottomCenter,
        children: [
          PageView.builder(
            controller: _controller,
            itemCount: widget.images.length,
            onPageChanged: (index) => setState(() => _index = index),
            itemBuilder: (context, index) => ThreadsNetworkImage(
              widget.images[index],
              fit: BoxFit.contain,
              mode: ExtendedImageMode.gesture,
              initGestureConfigHandler: (_) => GestureConfig(
                minScale: 1,
                maxScale: 4,
                animationMinScale: 0.8,
              ),
            ),
          ),
          if (widget.images.length > 1)
            Padding(
              padding: const EdgeInsets.only(bottom: 24),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  for (var i = 0; i < widget.images.length; i++)
                    Container(
                      width: 6,
                      height: 6,
                      margin: const EdgeInsets.symmetric(horizontal: 3),
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: i == _index ? Colors.white : Colors.white38,
                      ),
                    ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

/// Large article / link preview from Threads' `link_preview_attachment`.
class _ThreadsLinkPreview extends StatelessWidget {
  final ThreadsLinkCard card;

  const _ThreadsLinkPreview({required this.card});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final radius = tweetMediaRadiusOf(context);
    final host = card.providerName ?? Uri.tryParse(card.url)?.host ?? card.url;
    final width = MediaQuery.sizeOf(context).width;
    final scale = MediaQuery.devicePixelRatioOf(context);

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () => openUri(context, card.url),
        borderRadius: BorderRadius.circular(radius),
        child: Container(
          decoration: BoxDecoration(
            border: Border.all(color: theme.colorScheme.outlineVariant),
            borderRadius: BorderRadius.circular(radius),
          ),
          clipBehavior: Clip.antiAlias,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (card.hasImage)
                AspectRatio(
                  aspectRatio: kThreadsMediaMaxAspectRatio,
                  child: ThreadsNetworkImage(
                    card.imageUrl!,
                    fit: BoxFit.cover,
                    cacheWidth: (width * scale).ceil(),
                  ),
                ),
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      host,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.labelSmall!.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    if (card.title != null) ...[
                      const SizedBox(height: 4),
                      Text(
                        card.title!,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.titleSmall!.copyWith(
                          fontWeight: FontWeight.w700,
                          height: 1.25,
                        ),
                      ),
                    ],
                    if (card.description != null) ...[
                      const SizedBox(height: 4),
                      Text(
                        card.description!,
                        maxLines: 3,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodySmall!.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Replies / reposts from Meta when present; likes are local (never sent to Threads).
class _ThreadsEngagementRow extends StatelessWidget {
  final ThreadsPost post;
  final VoidCallback onOpen;
  final VoidCallback onOpenBrowser;

  const _ThreadsEngagementRow({
    required this.post,
    required this.onOpen,
    required this.onOpenBrowser,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final prefs = PrefService.of(context, listen: false);
    final hideCounts =
        prefs.get(optionZenMode) == true || prefs.get(optionCalmMode) == true;
    final likes = context.read<ThreadsLikesStore>();

    String metaLabel(int? count) {
      if (count == null || hideCounts) {
        return '';
      }
      return _threadsCountFormat.format(count);
    }

    return Padding(
      padding: const EdgeInsets.only(top: 2),
      child: Row(
        children: [
          TextButton.icon(
            style: footerButtonStyle,
            onPressed: onOpen,
            icon: Icon(Icons.mode_comment_outlined, size: 18, color: muted),
            label: Text(
              metaLabel(post.replyCount),
              style: theme.textTheme.bodySmall!.copyWith(color: muted),
            ),
          ),
          TextButton.icon(
            style: footerButtonStyle,
            onPressed: onOpen,
            icon: Icon(Icons.repeat, size: 18, color: muted),
            label: Text(
              metaLabel(post.repostCount),
              style: theme.textTheme.bodySmall!.copyWith(color: muted),
            ),
          ),
          ScopedBuilder<ThreadsLikesStore, List<ThreadsPost>>(
            store: likes,
            distinct: (_) => likes.isLiked(post.id),
            onState: (context, state) {
              final isLiked = likes.isLiked(post.id);
              final shown = post.likeCount == null
                  ? null
                  : post.likeCount! + (isLiked ? 1 : 0);
              final likeLabel = hideCounts || shown == null
                  ? ''
                  : _threadsCountFormat.format(shown);
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
