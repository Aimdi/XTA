import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/tiktok/tiktok_models.dart';
import 'package:xta/plugins/tiktok/tiktok_parse.dart';
import 'package:xta/plugins/tiktok/tiktok_player_screen.dart';
import 'package:xta/plugins/tiktok/tiktok_profile_screen.dart';
import 'package:xta/plugins/tiktok/tiktok_store.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/tweet/_like_button.dart';
import 'package:xta/tweet/tweet.dart' show tweetCardColor;
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/ui/dates.dart';

final NumberFormat _tiktokCountFormat = NumberFormat.compact(locale: 'en_US');

const double kTikTokAvatarSize = 48;

class TikTokPostCard extends StatelessWidget {
  final TikTokPost post;
  final bool openAuthor;
  final Future<void> Function()? onProfileClosed;

  const TikTokPostCard({
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
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 8, 0),
            child: Row(
              children: [
                GestureDetector(
                  onTap: openAuthor ? () => _openAuthor(context) : null,
                  child: TikTokAvatar(
                    url: post.author.avatarUrl,
                    seed: post.author.uniqueId,
                    name: post.author.displayName,
                    size: kTikTokAvatarSize,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Flexible(
                            child: Text(
                              post.author.displayName,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: theme.textTheme.titleSmall?.copyWith(
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                          if (post.author.verified) ...[
                            const SizedBox(width: 4),
                            Icon(
                              Icons.verified,
                              size: 16,
                              color: theme.colorScheme.primary,
                            ),
                          ],
                        ],
                      ),
                      Text(
                        '@${post.author.uniqueId} · ${createRelativeDate(post.createdAt)}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: muted,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          if (post.desc.trim().isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
              child: Text(post.desc),
            ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
            child: _Cover(post: post),
          ),
          if (post.playCount > 0)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
              child: Row(
                children: [
                  Icon(Icons.play_arrow, size: 16, color: muted),
                  const SizedBox(width: 2),
                  Text(
                    _tiktokCountFormat.format(post.playCount),
                    style: theme.textTheme.bodySmall?.copyWith(color: muted),
                  ),
                ],
              ),
            ),
          Padding(
            padding: const EdgeInsets.fromLTRB(8, 2, 4, 4),
            child: Row(
              children: [
                ScopedBuilder<TikTokLikesStore, Set<String>>(
                  store: context.read<TikTokLikesStore>(),
                  onState: (context, ids) => LikeButton(
                    isLiked: ids.contains(post.id),
                    label: _tiktokCountFormat.format(post.diggCount),
                    color: ids.contains(post.id)
                        ? theme.colorScheme.primary
                        : muted,
                    onPressed: () =>
                        context.read<TikTokLikesStore>().toggle(post.id),
                  ),
                ),
                const Spacer(),
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
        builder: (_) => TikTokProfileScreen(handle: post.author.uniqueId),
      ),
    );
    if (!context.mounted) return;
    await onProfileClosed?.call();
    if (!context.mounted) return;
  }
}

class _Cover extends StatelessWidget {
  final TikTokPost post;

  const _Cover({required this.post});

  @override
  Widget build(BuildContext context) {
    final ratio = post.aspectRatio.clamp(9 / 16, 16 / 9);
    final radius = tweetMediaRadiusOf(context);

    return ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: AspectRatio(
        aspectRatio: ratio,
        child: Material(
          color: Theme.of(context).colorScheme.surfaceContainerHighest,
          child: InkWell(
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => TikTokPlayerScreen(post: post)),
            ),
            child: Stack(
              fit: StackFit.expand,
              children: [
                if (post.coverUrl != null)
                  ExtendedImage.network(
                    post.coverUrl!,
                    fit: BoxFit.cover,
                    cache: true,
                  ),
                if (!post.isPhoto)
                  Center(
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: Colors.black45,
                        shape: BoxShape.circle,
                      ),
                      child: const Padding(
                        padding: EdgeInsets.all(12),
                        child: Icon(
                          Icons.play_arrow_rounded,
                          size: 56,
                          color: Colors.white,
                        ),
                      ),
                    ),
                  ),
                if (!post.isPhoto && post.durationSeconds > 0)
                  Positioned(
                    right: 10,
                    bottom: 10,
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: Colors.black54,
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 4,
                        ),
                        child: Text(
                          formatTikTokDuration(post.durationSeconds),
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class TikTokAvatar extends StatelessWidget {
  final String? url;
  final String seed;
  final String name;
  final double size;

  const TikTokAvatar({
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
        cacheWidth: (size * MediaQuery.devicePixelRatioOf(context)).ceil(),
      ),
    );
  }
}
