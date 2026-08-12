import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_thread_screen.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/utils/urls.dart';

/// Avatar size matching X / Reddit cards so Fediverse posts don't look smaller.
const double kMastodonAvatarSize = 48;

/// Tallest a single attached image is allowed to paint relative to its width.
const double kMastodonMediaMaxAspectRatio = 16 / 9;

final NumberFormat _mastodonCountFormat = NumberFormat.compact(locale: 'en_US');

/// A Mastodon status as a timeline card — tweet-sized layout, counts, link preview.
class MastodonPostCard extends StatelessWidget {
  final MastodonPost post;
  final bool showSourceBadge;

  /// When false, the card body does not navigate (used for the root of a thread).
  final bool openOnTap;

  /// Override for opening the post in-app. Defaults to [MastodonThreadScreen].
  final VoidCallback? onOpen;

  /// Author avatar / name — defaults to [MastodonProfileScreen].
  final VoidCallback? onAuthorTap;

  /// External browser affordance in the engagement row.
  final VoidCallback? onOpenBrowser;

  const MastodonPostCard({
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
      MaterialPageRoute(builder: (_) => MastodonThreadScreen(post: post)),
    );
  }

  void _openAuthor(BuildContext context) {
    if (onAuthorTap != null) {
      onAuthorTap!();
      return;
    }
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => MastodonProfileScreen(acct: post.acct)),
    );
  }

  void _openBrowser(BuildContext context) {
    if (onOpenBrowser != null) {
      onOpenBrowser!();
      return;
    }
    openUri(context, post.url);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return RepaintBoundary(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          tweetFlatCard(
            color: theme.cardColor,
            child: InkWell(
              onTap: openOnTap ? () => _open(context) : null,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                child: Row(
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
                            Text(
                              post.text,
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
                            _MastodonLinkPreview(card: post.linkCard!),
                          ],
                          _MastodonEngagementRow(
                            post: post,
                            onOpen: () => _open(context),
                            onOpenBrowser: () => _openBrowser(context),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          tweetHairlineDivider(context),
        ],
      ),
    );
  }

  Widget _avatar(BuildContext context) {
    final theme = Theme.of(context);
    final avatar = post.avatarUrl;
    const size = kMastodonAvatarSize;

    return ClipOval(
      child: avatar == null
          ? FallbackAvatar(
              seed: post.acct,
              displayName: post.authorName,
              size: size,
              accent: theme.colorScheme.primary,
            )
          : ExtendedImage.network(
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
    final l10n = L10n.of(context);
    final date = post.publishedAt;
    final muted = theme.colorScheme.onSurfaceVariant;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            if (post.boosted) ...[
              Icon(Icons.repeat, size: 14, color: muted),
              const SizedBox(width: 4),
            ],
            Flexible(
              child: Text(
                post.authorName,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.titleSmall!.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
            if (date != null) ...[
              const SizedBox(width: 6),
              Text(
                '· ${createRelativeDate(date)}',
                style: theme.textTheme.bodySmall,
              ),
            ],
          ],
        ),
        Row(
          children: [
            Flexible(
              child: Text(
                '@${post.acct}',
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodySmall!.copyWith(color: muted),
              ),
            ),
            if (showSourceBadge) ...[
              const SizedBox(width: 6),
              _badge(context, l10n.plugin_mastodon_title),
            ],
          ],
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

  Widget _media(BuildContext context) {
    final radius = tweetMediaRadiusOf(context);
    final width = MediaQuery.sizeOf(context).width;
    final scale = MediaQuery.devicePixelRatioOf(context);

    if (post.images.length == 1) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(radius),
        child: AspectRatio(
          aspectRatio: kMastodonMediaMaxAspectRatio,
          child: ExtendedImage.network(
            post.images.first,
            fit: BoxFit.cover,
            cacheWidth: (width * scale).ceil(),
          ),
        ),
      );
    }

    return SizedBox(
      height: 220,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: post.images.length,
        separatorBuilder: (_, _) => const SizedBox(width: 6),
        itemBuilder: (context, index) => ClipRRect(
          borderRadius: BorderRadius.circular(radius),
          child: ExtendedImage.network(
            post.images[index],
            width: 200,
            height: 220,
            fit: BoxFit.cover,
            cacheWidth: (200 * scale).ceil(),
          ),
        ),
      ),
    );
  }
}

/// Large article / link preview from Mastodon's PreviewCard.
class _MastodonLinkPreview extends StatelessWidget {
  final MastodonLinkCard card;

  const _MastodonLinkPreview({required this.card});

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
                  aspectRatio: kMastodonMediaMaxAspectRatio,
                  child: ExtendedImage.network(
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

/// Read-only replies / boosts / favourites — body opens in-app; the icon leaves.
class _MastodonEngagementRow extends StatelessWidget {
  final MastodonPost post;
  final VoidCallback onOpen;
  final VoidCallback onOpenBrowser;

  const _MastodonEngagementRow({
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

    String label(int count) =>
        hideCounts ? '' : _mastodonCountFormat.format(count);

    return Padding(
      padding: const EdgeInsets.only(top: 2),
      child: Row(
        children: [
          TextButton.icon(
            style: footerButtonStyle,
            onPressed: onOpen,
            icon: Icon(Icons.mode_comment_outlined, size: 18, color: muted),
            label: Text(
              label(post.repliesCount),
              style: theme.textTheme.bodySmall!.copyWith(color: muted),
            ),
          ),
          TextButton.icon(
            style: footerButtonStyle,
            onPressed: onOpen,
            icon: Icon(Icons.repeat, size: 18, color: muted),
            label: Text(
              label(post.reblogsCount),
              style: theme.textTheme.bodySmall!.copyWith(color: muted),
            ),
          ),
          TextButton.icon(
            style: footerButtonStyle,
            onPressed: onOpen,
            icon: Icon(Icons.favorite_border, size: 18, color: muted),
            label: Text(
              label(post.favouritesCount),
              style: theme.textTheme.bodySmall!.copyWith(color: muted),
            ),
          ),
          const Spacer(),
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
