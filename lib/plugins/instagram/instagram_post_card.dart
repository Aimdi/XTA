import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/instagram/instagram_models.dart';
import 'package:xta/plugins/instagram/instagram_profile_screen.dart';
import 'package:xta/plugins/instagram/instagram_store.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/tweet/_like_button.dart';
import 'package:xta/tweet/tweet.dart' show tweetCardColor;
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/utils/urls.dart';

final NumberFormat _igCount = NumberFormat.compact(locale: 'en_US');

class InstagramPostCard extends StatelessWidget {
  final InstagramPost post;
  final bool openAuthor;
  final bool showFollow;
  final Future<void> Function()? onFollowed;
  final Future<void> Function()? onProfileClosed;

  const InstagramPostCard({
    super.key,
    required this.post,
    this.openAuthor = true,
    this.showFollow = false,
    this.onFollowed,
    this.onProfileClosed,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;

    return tweetFlatCard(
      color: tweetCardColor(context),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ListTile(
            contentPadding: const EdgeInsets.fromLTRB(16, 8, 8, 0),
            leading: GestureDetector(
              onTap: openAuthor ? () => _openAuthor(context) : null,
              child: InstagramAvatar(
                url: post.author.avatarUrl,
                seed: post.author.username,
                name: post.author.displayName,
              ),
            ),
            title: Row(
              children: [
                Flexible(
                  child: Text(
                    post.author.displayName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                ),
                if (post.author.isVerified) ...[
                  const SizedBox(width: 4),
                  Icon(
                    Icons.verified,
                    size: 16,
                    color: theme.colorScheme.primary,
                  ),
                ],
              ],
            ),
            subtitle: Text(
              '@${post.author.username} · ${createRelativeDate(post.createdAt)}',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            trailing: showFollow
                ? _FollowButton(author: post.author, onFollowed: onFollowed)
                : null,
          ),
          if (post.caption.trim().isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
              child: Text(post.caption),
            ),
          if (post.displayUrls.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
              child: _PostMedia(post: post),
            ),
          Padding(
            padding: const EdgeInsets.fromLTRB(8, 2, 4, 4),
            child: Row(
              children: [
                ScopedBuilder<InstagramLikesStore, Set<String>>(
                  store: context.read<InstagramLikesStore>(),
                  onState: (context, ids) => LikeButton(
                    isLiked: ids.contains(post.id),
                    label: _igCount.format(post.likeCount),
                    color: ids.contains(post.id)
                        ? theme.colorScheme.primary
                        : muted,
                    onPressed: () =>
                        context.read<InstagramLikesStore>().toggle(post.id),
                  ),
                ),
                const Spacer(),
                tweetFooterIconButton(
                  context,
                  Icons.open_in_new,
                  muted,
                  null,
                  () => openUri(context, post.webUri().toString()),
                  l10n.plugin_instagram_open_on_site,
                ),
                tweetFooterIconButton(
                  context,
                  Icons.share_outlined,
                  muted,
                  null,
                  () => SharePlus.instance.share(
                    ShareParams(text: post.webUri().toString()),
                  ),
                  l10n.share_tweet_link,
                ),
              ],
            ),
          ),
          tweetHairlineDivider(context),
        ],
      ),
    );
  }

  Future<void> _openAuthor(BuildContext context) async {
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => InstagramProfileScreen(handle: post.author.username),
      ),
    );
    if (!context.mounted) return;
    await onProfileClosed?.call();
  }
}

class _FollowButton extends StatelessWidget {
  final InstagramAuthor author;
  final Future<void> Function()? onFollowed;

  const _FollowButton({required this.author, this.onFollowed});

  @override
  Widget build(BuildContext context) {
    final follows = context.read<InstagramFollowsStore>();
    return ScopedBuilder<InstagramFollowsStore, List<InstagramFollow>>(
      store: follows,
      distinct: (_) => follows.containsHandle(author.username),
      onState: (context, _) {
        if (follows.containsHandle(author.username)) {
          return const SizedBox.shrink();
        }
        return TextButton(
          onPressed: () async {
            await follows.followAuthor(author);
            await onFollowed?.call();
          },
          child: Text(L10n.of(context).plugin_instagram_follow),
        );
      },
    );
  }
}

class _PostMedia extends StatefulWidget {
  final InstagramPost post;

  const _PostMedia({required this.post});

  @override
  State<_PostMedia> createState() => _PostMediaState();
}

class _PostMediaState extends State<_PostMedia> {
  late final PageController _pages;
  var _index = 0;

  @override
  void initState() {
    super.initState();
    _pages = PageController();
  }

  @override
  void dispose() {
    _pages.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final urls = widget.post.displayUrls;
    return ClipRRect(
      borderRadius: BorderRadius.circular(tweetMediaRadiusOf(context)),
      child: AspectRatio(
        aspectRatio: 1,
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (urls.length == 1)
              _NetworkImage(url: urls.first)
            else
              PageView.builder(
                controller: _pages,
                itemCount: urls.length,
                onPageChanged: (index) => setState(() => _index = index),
                itemBuilder: (_, index) => _NetworkImage(url: urls[index]),
              ),
            if (widget.post.isVideo)
              const Center(
                child: Icon(
                  Icons.play_circle_fill,
                  size: 56,
                  color: Colors.white70,
                ),
              ),
            if (urls.length > 1)
              Positioned(
                right: 8,
                top: 8,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: Colors.black54,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 4,
                    ),
                    child: Text(
                      '${_index + 1} / ${urls.length}',
                      style: const TextStyle(color: Colors.white, fontSize: 12),
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _NetworkImage extends StatelessWidget {
  final String url;

  const _NetworkImage({required this.url});

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final maxW = constraints.maxWidth;
        final cacheWidth = maxW.isFinite && maxW > 0
            ? (maxW * MediaQuery.devicePixelRatioOf(context)).ceil()
            : null;
        return ExtendedImage.network(
          url,
          fit: BoxFit.cover,
          cache: true,
          cacheWidth: cacheWidth,
          loadStateChanged: (state) {
            if (state.extendedImageLoadState != LoadState.failed) return null;
            return ColoredBox(
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
              child: Icon(
                Icons.broken_image_outlined,
                color: Theme.of(context).colorScheme.outline,
              ),
            );
          },
        );
      },
    );
  }
}

class InstagramAvatar extends StatelessWidget {
  final String? url;
  final String seed;
  final String name;
  final double size;

  const InstagramAvatar({
    super.key,
    required this.url,
    required this.seed,
    required this.name,
    this.size = 40,
  });

  @override
  Widget build(BuildContext context) {
    if (url == null || url!.isEmpty) {
      return FallbackAvatar(
        seed: seed,
        displayName: name,
        size: size,
        accent: Theme.of(context).colorScheme.primary,
      );
    }
    final cache = (size * MediaQuery.devicePixelRatioOf(context)).ceil();
    return ClipOval(
      child: ExtendedImage.network(
        url!,
        width: size,
        height: size,
        fit: BoxFit.cover,
        cache: true,
        cacheWidth: cache,
        cacheHeight: cache,
        loadStateChanged: (state) {
          if (state.extendedImageLoadState != LoadState.failed) return null;
          return FallbackAvatar(
            seed: seed,
            displayName: name,
            size: size,
            accent: Theme.of(context).colorScheme.primary,
          );
        },
      ),
    );
  }
}
