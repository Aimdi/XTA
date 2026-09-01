import 'dart:ui';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/plugin_card_row.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_search_sheet.dart';
import 'package:xta/plugins/mastodon/mastodon_text.dart';
import 'package:xta/plugins/mastodon/mastodon_thread_screen.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/utils/urls.dart';
import 'package:xta/plugins/plugin_counts.dart';

/// Avatar size matching X / Reddit cards so Fediverse posts don't look smaller.
const double kMastodonAvatarSize = 48;

/// Tallest a single attached image is allowed to paint relative to its width.
const double kMastodonMediaMaxAspectRatio = 16 / 9;

/// A Mastodon status as a timeline card — tweet-sized layout, counts, link preview.
class MastodonPostCard extends StatelessWidget {
  final MastodonPost post;
  final bool showSourceBadge;
  final bool pinned;

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
    this.pinned = false,
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
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (post.boosted) _boostBanner(context),
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
                              if (post.replyToAcct != null) _replyLine(context),
                              _SpoilerBody(post: post, media: _media(context)),
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

  Widget _boostBanner(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final name = post.boostedBy ?? post.authorName;
    return Padding(
      padding: const EdgeInsets.only(bottom: 8, left: 4),
      child: Row(
        children: [
          Icon(Icons.repeat, size: 14, color: muted),
          const SizedBox(width: 6),
          Flexible(
            child: Text(
              L10n.of(context).plugin_mastodon_boosted(name),
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodySmall!.copyWith(color: muted),
            ),
          ),
        ],
      ),
    );
  }

  Widget _replyLine(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 2),
      child: Text(
        '${L10n.of(context).replying_to} @${post.replyToAcct}',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: theme.textTheme.bodySmall!.copyWith(
          color: theme.colorScheme.onSurfaceVariant,
        ),
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
          meta: [
            if (date != null) createRelativeDate(date),
            if (post.edited) l10n.plugin_mastodon_edited,
          ],
        ),
        PluginHandleBadgeRow(
          handle: _MastodonHandle(acct: post.acct, muted: muted),
          badges: [
            if (pinned) PluginCardBadge(label: l10n.plugin_mastodon_pinned),
            if (showSourceBadge)
              PluginCardBadge(label: l10n.plugin_mastodon_title),
          ],
        ),
      ],
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

/// `@user@instance`, with the instance dimmed.
///
/// Which server someone posts from is half of who they are on the Fediverse,
/// so the card never drops it — but it is the part that gets shortened when
/// the handle will not fit, because the name is what a reader recognises.
class _MastodonHandle extends StatelessWidget {
  final String acct;
  final Color muted;

  const _MastodonHandle({required this.acct, required this.muted});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final style = theme.textTheme.bodySmall!.copyWith(color: muted);
    final at = acct.indexOf('@');

    if (at <= 0) {
      return Text(
        '@$acct',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: style,
      );
    }

    return Text.rich(
      TextSpan(
        children: [
          TextSpan(text: '@${acct.substring(0, at)}'),
          TextSpan(
            text: acct.substring(at),
            style: style.copyWith(color: muted.withValues(alpha: 0.7)),
          ),
        ],
      ),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: style,
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

class _SpoilerBody extends StatefulWidget {
  final MastodonPost post;
  final Widget media;

  const _SpoilerBody({required this.post, required this.media});

  @override
  State<_SpoilerBody> createState() => _SpoilerBodyState();
}

class _SpoilerBodyState extends State<_SpoilerBody> {
  var _open = false;

  @override
  Widget build(BuildContext context) {
    final post = widget.post;
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    if (post.hasSpoiler && !_open) {
      return _MastodonContentWarning(
        text: post.spoilerText,
        onShow: () => setState(() => _open = true),
      );
    }
    return _visible(theme, l10n, blur: post.sensitive && !_open);
  }

  Widget _visible(ThemeData theme, L10n l10n, {required bool blur}) {
    final post = widget.post;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // The warning stays on screen once opened. Mastodon readers use it to
        // decide whether to keep reading, and a post whose warning vanished on
        // the first tap gave them nothing to close it again by.
        if (post.hasSpoiler)
          _MastodonContentWarning(
            text: post.spoilerText,
            open: true,
            onHide: () => setState(() => _open = false),
          ),
        if (post.text.isNotEmpty) ...[
          const SizedBox(height: 6),
          MastodonRichText(
            text: post.text,
            mentionAccts: post.mentionAccts,
            style: theme.textTheme.bodyLarge!.copyWith(height: 1.35),
            onMentionTap: (acct) => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => MastodonProfileScreen(acct: acct),
              ),
            ),
            onTagTap: (tag) => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => MastodonTagScreen(tag: tag)),
            ),
          ),
        ],
        if (post.quote != null) ...[
          const SizedBox(height: 10),
          _QuoteEmbed(quote: post.quote!),
        ],
        if (post.hasMedia) ...[
          const SizedBox(height: 10),
          GestureDetector(
            onTap: blur ? () => setState(() => _open = true) : null,
            child: blur
                ? ImageFiltered(
                    imageFilter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
                    child: widget.media,
                  )
                : widget.media,
          ),
        ],
        if (post.poll != null) ...[
          const SizedBox(height: 10),
          _PollBars(poll: post.poll!),
        ],
        if (post.linkCard != null) ...[
          const SizedBox(height: 10),
          _MastodonLinkPreview(card: post.linkCard!),
        ],
      ],
    );
  }
}

/// A Mastodon content warning, which is a label and a decision, not a headline.
///
/// Mastodon shows the author's warning text under a "Content warning" heading
/// so a reader can tell the warning apart from the post it is warning about.
/// The card used to print the spoiler text in bold body type, which read as if
/// the post itself simply started that way.
class _MastodonContentWarning extends StatelessWidget {
  final String text;
  final bool open;
  final VoidCallback? onShow;
  final VoidCallback? onHide;

  const _MastodonContentWarning({
    required this.text,
    this.open = false,
    this.onShow,
    this.onHide,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final accent = theme.colorScheme.tertiary;
    return Container(
      margin: const EdgeInsets.only(top: 6),
      padding: const EdgeInsets.fromLTRB(10, 8, 6, 4),
      decoration: BoxDecoration(
        color: accent.withValues(alpha: 0.07),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: accent.withValues(alpha: 0.35)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.only(top: 1),
                child: Icon(
                  Icons.warning_amber_rounded,
                  size: 16,
                  color: accent,
                ),
              ),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  l10n.content_warning,
                  style: theme.textTheme.labelMedium?.copyWith(
                    color: accent,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          if (text.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 2, right: 4),
              child: Text(text, style: theme.textTheme.bodyMedium),
            ),
          Align(
            alignment: AlignmentDirectional.centerEnd,
            child: TextButton(
              onPressed: open ? onHide : onShow,
              child: Text(open ? l10n.hide : l10n.show),
            ),
          ),
        ],
      ),
    );
  }
}

class _PollBars extends StatelessWidget {
  final MastodonPoll poll;

  const _PollBars({required this.poll});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final total = poll.votesCount <= 0
        ? poll.options.fold<int>(0, (sum, o) => sum + o.votes)
        : poll.votesCount;
    return Column(
      children: [
        for (final option in poll.options)
          Padding(
            padding: const EdgeInsets.only(bottom: 6),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(child: Text(option.title)),
                    Text(
                      total == 0
                          ? '0%'
                          : '${((option.votes / total) * 100).round()}%',
                      style: theme.textTheme.labelSmall,
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                ClipRRect(
                  borderRadius: BorderRadius.circular(4),
                  child: LinearProgressIndicator(
                    value: total == 0 ? 0 : option.votes / total,
                    minHeight: 6,
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

class _QuoteEmbed extends StatelessWidget {
  final MastodonQuotedPost quote;

  const _QuoteEmbed({required this.quote});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final radius = tweetMediaRadiusOf(context);
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () => Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => MastodonThreadScreen(post: quote.asPost),
          ),
        ),
        borderRadius: BorderRadius.circular(radius),
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
          decoration: BoxDecoration(
            border: Border.all(color: theme.colorScheme.outlineVariant),
            borderRadius: BorderRadius.circular(radius),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                quote.authorName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.labelLarge?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
              Text(
                '@${quote.acct}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              if (quote.text.isNotEmpty) ...[
                const SizedBox(height: 6),
                Text(quote.text, maxLines: 6, overflow: TextOverflow.ellipsis),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
