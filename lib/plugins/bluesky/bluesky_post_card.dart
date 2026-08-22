import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_butterfly_icon.dart';
import 'package:xta/plugins/bluesky/bluesky_facets.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_search_sheet.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/bluesky/bluesky_thread_screen.dart';
import 'package:xta/plugins/plugin_card_row.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/plugins/plugin_profile_tabs.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/tweet/_like_button.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/utils/urls.dart';
import 'package:xta/plugins/plugin_counts.dart';

const double kBlueskyAvatarSize = 48;

/// A Bluesky post as a timeline card — avatar column, counts, quote, link card.
class BlueskyPostCard extends StatelessWidget {
  final BlueskyPost post;
  final bool showSourceBadge;
  final bool openOnTap;
  final VoidCallback? onOpen;
  final VoidCallback? onAuthorTap;
  final VoidCallback? onOpenBrowser;

  const BlueskyPostCard({
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
      MaterialPageRoute(builder: (_) => BlueskyThreadScreen(post: post)),
    );
  }

  void _openAuthor(BuildContext context) {
    if (onAuthorTap != null) {
      onAuthorTap!();
      return;
    }
    final actor = post.did.isNotEmpty ? post.did : post.handle;
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => BlueskyProfileScreen(actor: actor)),
    );
  }

  void _openReposter(BuildContext context) {
    final handle = post.repostedByHandle;
    if (handle == null || handle.isEmpty) {
      return;
    }
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => BlueskyProfileScreen(actor: handle)),
    );
  }

  void _openBrowser(BuildContext context) {
    if (onOpenBrowser != null) {
      onOpenBrowser!();
      return;
    }
    openUri(context, post.url);
  }

  void _onFacet(BuildContext context, BlueskyFacet facet) {
    switch (facet.kind) {
      case BlueskyFacetKind.link:
        openLink(context, facet.value);
      case BlueskyFacetKind.mention:
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => BlueskyProfileScreen(actor: facet.value),
          ),
        );
      case BlueskyFacetKind.tag:
        showBlueskySearchSheet(
          context,
          initialQuery: '#${facet.value}',
          initialTab: BlueskySearchTab.posts,
        );
    }
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
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (post.isRepost) _repostBanner(context),
                    if (post.replyToHandle != null &&
                        post.replyToHandle!.isNotEmpty)
                      PluginReplyingTo(name: post.replyToHandle!),
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
                                BlueskyRichText(
                                  text: post.text,
                                  facets: post.facets,
                                  style: theme.textTheme.bodyLarge!.copyWith(
                                    height: 1.35,
                                  ),
                                  onFacetTap: (facet) =>
                                      _onFacet(context, facet),
                                ),
                              ],
                              if (post.hasMedia) ...[
                                const SizedBox(height: 10),
                                PluginPostMedia(items: post.mediaItems),
                              ],
                              if (post.quotedPost != null) ...[
                                const SizedBox(height: 10),
                                _QuotedPost(quote: post.quotedPost!),
                              ],
                              if (post.linkCard != null) ...[
                                const SizedBox(height: 10),
                                _BlueskyLinkPreview(card: post.linkCard!),
                              ],
                              _BlueskyEngagementRow(
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
      ),
    );
  }

  Widget _repostBanner(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final name = post.repostedByName ?? post.repostedByHandle ?? '';

    return Padding(
      padding: const EdgeInsets.only(bottom: 8, left: 60),
      child: GestureDetector(
        onTap: () => _openReposter(context),
        behavior: HitTestBehavior.opaque,
        child: Row(
          children: [
            Icon(Icons.repeat, size: 14, color: muted),
            const SizedBox(width: 6),
            Flexible(
              child: Text(
                L10n.of(context).plugin_bluesky_reposted(name),
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodySmall!.copyWith(color: muted),
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
    const size = kBlueskyAvatarSize;

    return ClipOval(
      child: avatar == null
          ? FallbackAvatar(
              seed: post.handle,
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
        PluginNameMetaRow(
          name: Text(
            post.authorName,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.titleSmall!.copyWith(
              fontWeight: FontWeight.w800,
            ),
          ),
          meta: [if (date != null) createRelativeDate(date)],
        ),
        Row(
          children: [
            Flexible(
              child: Text(
                '@${post.handle}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodySmall!.copyWith(color: muted),
              ),
            ),
            if (showSourceBadge) ...[
              const SizedBox(width: kPluginMetaGap),
              Tooltip(
                message: l10n.plugin_bluesky_title,
                child: const BlueskyButterflyIcon(size: 14),
              ),
            ],
            _followButton(context),
          ],
        ),
      ],
    );
  }

  Widget _followButton(BuildContext context) {
    final accounts = context.read<BlueskyAccountsStore>();
    return ScopedBuilder<BlueskyAccountsStore, List<BlueskyAccount>>(
      store: accounts,
      distinct: (_) => accounts.follows(post.handle),
      onState: (context, _) {
        if (accounts.follows(post.handle)) {
          return const SizedBox.shrink();
        }
        return TextButton(
          onPressed: () => accounts.add(
            BlueskyAccount(
              handle: post.handle,
              name: post.authorName,
              avatarUrl: post.avatarUrl,
              did: post.did.isEmpty ? null : post.did,
            ),
          ),
          child: Text(L10n.of(context).plugin_bluesky_follow),
        );
      },
    );
  }
}

class _QuotedPost extends StatelessWidget {
  final BlueskyPost quote;

  const _QuotedPost({required this.quote});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final radius = tweetMediaRadiusOf(context);

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () => Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => BlueskyThreadScreen(post: quote)),
        ),
        borderRadius: BorderRadius.circular(radius),
        child: Container(
          decoration: quoteCardDecoration(context),
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                quote.authorName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.titleSmall!.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
              Text(
                '@${quote.handle}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodySmall!.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              if (quote.hasMedia) ...[
                const SizedBox(height: 8),
                PluginPostMedia(items: quote.mediaItems),
              ],
              if (quote.text.isNotEmpty) ...[
                const SizedBox(height: 6),
                BlueskyRichText(
                  text: quote.text,
                  facets: quote.facets,
                  maxLines: 6,
                  overflow: TextOverflow.ellipsis,
                  onFacetTap: (facet) {
                    switch (facet.kind) {
                      case BlueskyFacetKind.link:
                        openLink(context, facet.value);
                      case BlueskyFacetKind.mention:
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) =>
                                BlueskyProfileScreen(actor: facet.value),
                          ),
                        );
                      case BlueskyFacetKind.tag:
                        showBlueskySearchSheet(
                          context,
                          initialQuery: '#${facet.value}',
                          initialTab: BlueskySearchTab.posts,
                        );
                    }
                  },
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _BlueskyLinkPreview extends StatelessWidget {
  final BlueskyLinkCard card;

  const _BlueskyLinkPreview({required this.card});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final radius = tweetMediaRadiusOf(context);
    final host = Uri.tryParse(card.url)?.host ?? card.url;
    final width = MediaQuery.sizeOf(context).width;
    final scale = MediaQuery.devicePixelRatioOf(context);

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () => openLink(context, card.url),
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
                  aspectRatio: clampPluginMediaAspect(null),
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

/// Replies / reposts from the AppView; likes are local (never sent to Bluesky).
class _BlueskyEngagementRow extends StatelessWidget {
  final BlueskyPost post;
  final VoidCallback onOpen;
  final VoidCallback onOpenBrowser;

  const _BlueskyEngagementRow({
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
    final likes = context.read<BlueskyLikesStore>();

    String label(int count) => hideCounts ? '' : compactCount(count);

    return Padding(
      padding: const EdgeInsets.only(top: 2),
      child: Row(
        children: [
          TextButton.icon(
            style: footerButtonStyle,
            onPressed: onOpen,
            icon: Icon(Icons.mode_comment_outlined, size: 18, color: muted),
            label: Text(
              label(post.replyCount),
              style: theme.textTheme.bodySmall!.copyWith(color: muted),
            ),
          ),
          TextButton.icon(
            style: footerButtonStyle,
            onPressed: onOpen,
            icon: Icon(Icons.repeat, size: 18, color: muted),
            label: Text(
              label(post.repostCount),
              style: theme.textTheme.bodySmall!.copyWith(color: muted),
            ),
          ),
          ScopedBuilder<BlueskyLikesStore, List<BlueskyPost>>(
            store: likes,
            distinct: (_) => likes.isLiked(post.uri),
            onState: (context, state) {
              final isLiked = likes.isLiked(post.uri);
              final shown = post.likeCount + (isLiked ? 1 : 0);
              return LikeButton(
                isLiked: isLiked,
                label: hideCounts ? '' : compactCount(shown),
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
