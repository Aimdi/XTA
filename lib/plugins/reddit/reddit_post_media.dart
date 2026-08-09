import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/reddit/reddit_gallery_loader.dart';
import 'package:xta/plugins/reddit/reddit_gallery.dart';
import 'package:xta/plugins/reddit/reddit_media_urls.dart';
import 'package:xta/plugins/reddit/reddit_media_frame.dart';
import 'package:xta/plugins/reddit/reddit_sort_sheet.dart';
import 'package:xta/tweet/_video.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/capped_network_image.dart';
import 'package:xta/utils/urls.dart';

/// The picture, video or link that goes with a Reddit post, at the width a
/// tweet's media gets rather than as a 70px thumbnail beside the title.
///
/// Everything it shows goes into a [RedditMediaFrame]: bounded, tinted, and the
/// full width of the card, so no post can grow taller than a screen and nothing
/// leaves a hole while it loads. A post marked over-18 gets a
/// [RedditSensitiveGate] in front of the frame instead — the picture is not
/// fetched at all until it is asked for.
class RedditPostMedia extends StatelessWidget {
  final RedditPost post;

  /// Inset around the media. The feed card lays its own text out flush with the
  /// screen edge and needs the gutter here; a thread screen already has one.
  final EdgeInsets padding;

  const RedditPostMedia({
    super.key,
    required this.post,
    this.padding = const EdgeInsets.fromLTRB(16, 10, 16, 0),
  });

  @override
  Widget build(BuildContext context) {
    final media = _media(context);

    return media == null
        ? const SizedBox.shrink()
        : Padding(padding: padding, child: media);
  }

  Widget? _media(BuildContext context) {
    // v.redd.it plays in the same stack as every X video: same pool, same
    // creation gate, same controls. libmpv reads the DASH manifest whole —
    // video and its separate audio track together; the progressive fallback
    // is video-only and serves as the download target.
    final dash = post.videoDashUrl ?? post.videoFallbackUrl;
    if (dash != null) {
      final ratio = redditMediaAspectRatio(post.videoAspectRatio);

      return _gate(
        context,
        aspectRatio: ratio,
        child: _video(context, dash, ratio),
      );
    }

    final gallery = collapseRedditImageUrls(post.galleryImages);
    if (gallery.length > 1) {
      return _gate(
        context,
        aspectRatio: kRedditGalleryAspectRatio,
        child: RedditGallery(images: gallery),
      );
    }

    final image = post.imageUrl;
    if (image != null) {
      return _gate(context, child: _RedditImage(url: image));
    }

    final link = post.url;
    if (link != null && !post.isSelf) {
      final card = _RedditLinkCard(post: post, url: link);
      // A gallery scraped off old.reddit arrives as this link and nothing else:
      // its pictures are in the post's own JSON, not in that page. Fetch them
      // and show the album; keep the link until they arrive, or for good when
      // Reddit will not say.
      if (isRedditGalleryUrl(link)) {
        return RedditGalleryImages(
          loader: context.read<RedditGalleryLoader>(),
          permalink: post.permalink,
          placeholder: card,
          whenLoaded: (images) => _gate(
            context,
            aspectRatio: kRedditGalleryAspectRatio,
            child: RedditGallery(images: collapseRedditImageUrls(images)),
          ),
        );
      }
      return card;
    }

    return null;
  }

  Widget _gate(
    BuildContext context, {
    required Widget child,
    double aspectRatio = kRedditSensitiveAspectRatio,
  }) {
    return RedditSensitiveGate(
      sensitive: _shouldGate(context, post),
      revealKey: post.id,
      kind: _gateKind(post),
      aspectRatio: aspectRatio,
      child: child,
    );
  }

  Widget _video(BuildContext context, String dash, double ratio) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(tweetMediaRadiusOf(context)),
      child: TweetVideo(
        username: post.subreddit,
        loop: false,
        tweetId: 'reddit-${post.id}',
        metadata: TweetVideoMetadata(
          ratio,
          post.previewImage ?? post.thumbnailUrl,
          () async => TweetVideoUrls(dash, post.videoFallbackUrl),
        ),
      ),
    );
  }
}

/// The one picture of a picture post, at whatever height it turns out to be
/// within the frame's bounds.
class _RedditImage extends StatelessWidget {
  final String url;

  const _RedditImage({required this.url});

  @override
  Widget build(BuildContext context) {
    return RedditMediaFrame(
      child: Semantics(
        image: true,
        label: L10n.of(context).media,
        child: CappedNetworkImage(url: url),
      ),
    );
  }
}

/// How tall a picture inside a comment may get. Smaller than a post's, because
/// a reply is a reply — a full-height meme under one would bury the thread.
const double kRedditCommentMediaMaxHeight = 280;

/// Pictures and GIFs linked from a comment.
///
/// Deliberately Flutter's own [Image], not [ExtendedImage]: a GIF that does not
/// move is not a GIF, and animating multi-frame images is something the
/// framework's decoder does for free. Comment pictures are few and small, so
/// giving up the shared cache costs little.
class RedditCommentImages extends StatelessWidget {
  final List<String> urls;

  const RedditCommentImages({super.key, required this.urls});

  @override
  Widget build(BuildContext context) {
    if (urls.isEmpty) {
      return const SizedBox.shrink();
    }

    final label = L10n.of(context).media;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final url in urls)
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(10),
              child: ConstrainedBox(
                constraints: const BoxConstraints(
                  maxHeight: kRedditCommentMediaMaxHeight,
                ),
                child: Semantics(
                  image: true,
                  label: label,
                  child: LayoutBuilder(
                    builder: (context, constraints) => Image.network(
                      url,
                      fit: BoxFit.contain,
                      // Decoded at the width it is drawn at, which under an
                      // indented reply is a good deal less than the screen.
                      cacheWidth: constraints.hasBoundedWidth
                          ? (constraints.maxWidth *
                                    MediaQuery.devicePixelRatioOf(context))
                                .ceil()
                          : null,
                      alignment: Alignment.centerLeft,
                      errorBuilder: (context, error, stackTrace) =>
                          RedditBrokenImage(url: url),
                    ),
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }
}

/// A picture Reddit would not serve. The link survives, so it can still be
/// opened somewhere that will.
class RedditBrokenImage extends StatelessWidget {
  final String url;

  const RedditBrokenImage({super.key, required this.url});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Semantics(
      button: true,
      // Nothing here is a picture any more, so what it announces is what a tap
      // now does: take the reader to where the picture actually lives.
      tooltip: L10n.of(context).open_in_browser,
      child: InkWell(
        onTap: () => openUri(context, url),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.broken_image_outlined,
                size: 18,
                color: theme.colorScheme.onSurfaceVariant,
              ),
              const SizedBox(width: 6),
              Flexible(
                child: Text(
                  Uri.tryParse(url)?.host ?? url,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodySmall!.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Where a link, gallery or video post leads.
class _RedditLinkCard extends StatelessWidget {
  final RedditPost post;
  final String url;

  const _RedditLinkCard({required this.post, required this.url});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final radius = tweetMediaRadiusOf(context);
    final preview = post.previewImage;

    return Semantics(
      button: true,
      tooltip: L10n.of(context).open_in_browser,
      child: InkWell(
        onTap: () => openUri(context, url),
        borderRadius: BorderRadius.circular(radius),
        child: Container(
          decoration: BoxDecoration(
            border: Border.all(color: theme.colorScheme.outlineVariant),
            borderRadius: BorderRadius.circular(radius),
          ),
          clipBehavior: Clip.antiAlias,
          // Reddit's preview is the poster frame of a video or the lead image of
          // an article; showing it full width is what makes the card read as "the
          // post's file is here", with the row underneath saying where a tap goes.
          child: preview == null
              ? _domainRow(context)
              : Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [_banner(context, preview), _domainRow(context)],
                ),
        ),
      ),
    );
  }

  Widget _domainRow(BuildContext context) {
    final theme = Theme.of(context);

    return Row(
      children: [
        if (post.previewImage == null) _leading(context, post.thumbnailUrl),
        Expanded(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            child: Text(
              post.domain ?? Uri.tryParse(url)?.host ?? url,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodyMedium!.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.only(right: 12),
          child: Icon(
            Icons.open_in_new,
            size: 18,
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }

  Widget _banner(BuildContext context, String preview) {
    final banner = RedditMediaFrame(
      aspectRatio: kRedditMediaMaxAspectRatio,
      child: Stack(
        fit: StackFit.expand,
        children: [
          Semantics(
            image: true,
            label: L10n.of(context).media,
            child: CappedNetworkImage(url: preview),
          ),
          if (post.isVideo)
            const Center(
              child: CircleAvatar(
                radius: 24,
                backgroundColor: Colors.black54,
                child: Icon(Icons.play_arrow, color: Colors.white, size: 30),
              ),
            ),
        ],
      ),
    );

    // The poster frame of an over-18 post is the post: it is held back with the
    // same tile a picture would be.
    return RedditSensitiveGate(
      sensitive: _shouldGate(context, post),
      revealKey: post.id,
      kind: _gateKind(post),
      aspectRatio: kRedditMediaMaxAspectRatio,
      child: banner,
    );
  }

  /// The thumbnail when Reddit gave one, otherwise a square that says what kind
  /// of thing this is. A video gets a play badge over either.
  Widget _leading(BuildContext context, String? thumbnail) {
    final theme = Theme.of(context);

    return SizedBox(
      width: 88,
      height: 88,
      child: Stack(
        fit: StackFit.expand,
        children: [
          ColoredBox(color: theme.colorScheme.surfaceContainerHighest),
          if (thumbnail != null && !_shouldGate(context, post))
            CappedNetworkImage(url: thumbnail)
          else
            Icon(
              _shouldGate(context, post)
                  ? Icons.visibility_off_outlined
                  : Icons.link,
              color: theme.colorScheme.onSurfaceVariant,
            ),
          if (post.isVideo)
            const Center(
              child: CircleAvatar(
                radius: 16,
                backgroundColor: Colors.black54,
                child: Icon(Icons.play_arrow, color: Colors.white, size: 22),
              ),
            ),
        ],
      ),
    );
  }
}

bool _shouldGate(BuildContext context, RedditPost post) {
  if (post.spoiler) {
    return true;
  }
  final prefs = PrefService.of(context);
  return post.over18 && storedRedditNsfwMode(prefs) != RedditNsfwMode.show;
}

RedditSensitiveGateKind _gateKind(RedditPost post) => post.spoiler
    ? RedditSensitiveGateKind.spoiler
    : RedditSensitiveGateKind.nsfw;
