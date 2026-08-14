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
  final Future<void> Function()? onProfileClosed;

  const InstagramPostCard({
    super.key,
    required this.post,
    this.openAuthor = true,
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
            title: Text(
              post.author.displayName,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontWeight: FontWeight.w800),
            ),
            subtitle: Text(
              '@${post.author.username} · ${createRelativeDate(post.createdAt)}',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          if (post.caption.trim().isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
              child: Text(post.caption),
            ),
          if (post.coverUrl != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(
                  tweetMediaRadiusOf(context),
                ),
                child: AspectRatio(
                  aspectRatio: 1,
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      ExtendedImage.network(
                        post.coverUrl!,
                        fit: BoxFit.cover,
                        cache: true,
                      ),
                      if (post.isVideo)
                        const Center(
                          child: Icon(
                            Icons.play_circle_fill,
                            size: 56,
                            color: Colors.white70,
                          ),
                        ),
                    ],
                  ),
                ),
              ),
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
    return ClipOval(
      child: ExtendedImage.network(
        url!,
        width: size,
        height: size,
        fit: BoxFit.cover,
        cache: true,
      ),
    );
  }
}
