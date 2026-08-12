import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/tiktok/tiktok_client.dart';
import 'package:xta/plugins/tiktok/tiktok_models.dart';
import 'package:xta/plugins/tiktok/tiktok_player_screen.dart';
import 'package:xta/plugins/tiktok/tiktok_profile_screen.dart';
import 'package:xta/plugins/tiktok/tiktok_store.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/tweet/_like_button.dart';
import 'package:xta/tweet/_video.dart';
import 'package:xta/tweet/tweet.dart' show tweetCardColor;
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/tweet/video_quality.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/utils/urls.dart';

final NumberFormat _tiktokCountFormat = NumberFormat.compact(locale: 'en_US');

class TikTokPostCard extends StatelessWidget {
  final TikTokPost post;
  final bool openAuthor;

  const TikTokPostCard({super.key, required this.post, this.openAuthor = true});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final scheme = Theme.of(context).colorScheme;

    return tweetFlatCard(
      color: tweetCardColor(context),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ListTile(
            contentPadding: const EdgeInsets.fromLTRB(12, 8, 8, 0),
            leading: GestureDetector(
              onTap: openAuthor ? () => _openAuthor(context) : null,
              child: TikTokAvatar(
                url: post.author.avatarUrl,
                seed: post.author.uniqueId,
                name: post.author.displayName,
                size: 44,
              ),
            ),
            title: Text(
              post.author.displayName,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            subtitle: Text(
              '@${post.author.uniqueId} · ${createRelativeDate(post.createdAt)}',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          if (post.desc.trim().isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: Text(post.desc),
            ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 0),
            child: _Media(post: post),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(4, 0, 8, 4),
            child: Row(
              children: [
                ScopedBuilder<TikTokLikesStore, Set<String>>(
                  store: context.read<TikTokLikesStore>(),
                  onState: (context, ids) => LikeButton(
                    isLiked: ids.contains(post.id),
                    label: _tiktokCountFormat.format(post.diggCount),
                    color: ids.contains(post.id) ? scheme.primary : null,
                    onPressed: () =>
                        context.read<TikTokLikesStore>().toggle(post.id),
                  ),
                ),
                TextButton.icon(
                  onPressed: () => openUri(context, post.webUri().toString()),
                  style: footerButtonStyle,
                  icon: const Icon(Icons.mode_comment_outlined, size: 20),
                  label: Text(_tiktokCountFormat.format(post.commentCount)),
                ),
                const Spacer(),
                IconButton(
                  tooltip: l10n.share_tweet_content,
                  icon: const Icon(Icons.share_outlined),
                  onPressed: () => SharePlus.instance.share(
                    ShareParams(text: post.webUri().toString()),
                  ),
                ),
                IconButton(
                  tooltip: l10n.plugin_tiktok_open_on_site,
                  icon: const Icon(Icons.open_in_new),
                  onPressed: () => openUri(context, post.webUri().toString()),
                ),
              ],
            ),
          ),
          tweetHairlineDivider(context),
        ],
      ),
    );
  }

  void _openAuthor(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => TikTokProfileScreen(handle: post.author.uniqueId),
      ),
    );
  }
}

class _Media extends StatelessWidget {
  final TikTokPost post;

  const _Media({required this.post});

  @override
  Widget build(BuildContext context) {
    final preferEmbed =
        PrefService.of(context).get<bool>(optionPluginTiktokPreferEmbed) ==
        true;
    final ratio = post.aspectRatio.clamp(9 / 16, 16 / 9);
    final radius = tweetMediaRadiusOf(context);

    return ClipRRect(
      borderRadius: BorderRadius.circular(radius),
      child: AspectRatio(
        aspectRatio: ratio,
        child: preferEmbed || post.playUrl == null
            ? _Cover(post: post)
            : TweetVideo(
                username: post.author.uniqueId,
                loop: true,
                tweetId: 'tiktok-${post.id}',
                metadata: TweetVideoMetadata(
                  ratio,
                  post.coverUrl,
                  () async => _urls(context, post),
                ),
              ),
      ),
    );
  }
}

TweetVideoUrls _urls(BuildContext context, TikTokPost post) {
  final headers = context.read<TikTokClient>().playbackHeaders;
  final qualities = [
    for (final source in post.sources)
      if (source.label != 'download')
        TweetVideoQuality(source.url, source.label ?? '—'),
  ];
  return TweetVideoUrls(
    post.playUrl!,
    post.sources
        .where((s) => s.label == 'download')
        .map((s) => s.url)
        .firstOrNull,
    qualities: qualities,
    httpHeaders: headers,
  );
}

class _Cover extends StatelessWidget {
  final TikTokPost post;

  const _Cover({required this.post});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
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
            )
          else
            ColoredBox(
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
            ),
          const Center(
            child: Icon(Icons.play_circle_fill, size: 64, color: Colors.white),
          ),
        ],
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
