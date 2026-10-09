import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/tweet/tweet_chrome.dart';

/// How a link preview inside a post is drawn.
enum LinkPreviewLayout {
  /// Threads' article row: a small tile, the domain and the title.
  compact,

  /// The picture across the card, for links where the picture is the point.
  large,
}

const _pictureKinds = {'video', 'photo', 'player'};

const _videoHosts = {'youtube.com', 'youtu.be', 'vimeo.com', 'twitch.tv', 'dailymotion.com'};

/// Compact for an article, large only when the image is what the link is.
///
/// Threads draws a plain article link as one short row whatever picture the
/// site offers; the share image of a news story is decoration. A video, a
/// photo page or a player is different — the picture is the content — so
/// those keep the full-width image.
LinkPreviewLayout linkPreviewLayoutFor(String url, {required bool hasImage, String? kind}) {
  if (!hasImage) return LinkPreviewLayout.compact;
  if (_pictureKinds.contains(kind?.toLowerCase())) {
    return LinkPreviewLayout.large;
  }
  final host = linkDomain(url);
  final isVideoHost = _videoHosts.any((domain) => host == domain || host.endsWith('.$domain'));
  return isVideoHost ? LinkPreviewLayout.large : LinkPreviewLayout.compact;
}

/// The host a reader recognises: lower case, without `www.`.
String linkDomain(String url) {
  final host = Uri.tryParse(url.trim())?.host.toLowerCase() ?? '';
  if (host.isEmpty) return url.trim();
  return host.startsWith('www.') ? host.substring(4) : host;
}

/// Draws a preview image; networks with their own image loader pass one.
typedef LinkPreviewImageBuilder = Widget Function(BuildContext context, String url, int? cacheWidth);

Widget _networkImage(BuildContext context, String url, int? cacheWidth) => ExtendedImage.network(
  url,
  fit: BoxFit.cover,
  cache: true,
  cacheWidth: cacheWidth,
  loadStateChanged: (state) => state.extendedImageLoadState == LoadState.failed ? const _LinkGlyph() : null,
);

const double kLinkPreviewTileSize = 56;
const double _cardRadius = 16;
const double _tileRadius = 8;

/// The one link preview every network's post uses.
class LinkPreviewCard extends StatelessWidget {
  final String url;
  final String? title;

  /// Shown under the title in the large layout only.
  final String? description;
  final String? imageUrl;
  final LinkPreviewLayout layout;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final LinkPreviewImageBuilder imageBuilder;

  const LinkPreviewCard({
    super.key,
    required this.url,
    required this.onTap,
    this.title,
    this.description,
    this.imageUrl,
    this.layout = LinkPreviewLayout.compact,
    this.onLongPress,
    this.imageBuilder = _networkImage,
  });

  bool get _hasImage => imageUrl?.trim().isNotEmpty == true;

  String get _title {
    final value = title?.trim() ?? '';
    return value.isNotEmpty ? value : url.replaceFirst(RegExp(r'^https?://'), '');
  }

  @override
  Widget build(BuildContext context) {
    final domain = linkDomain(url);
    final radius = BorderRadius.circular(_cardRadius);
    return MergeSemantics(
      child: Material(
        type: MaterialType.transparency,
        child: InkWell(
          onTap: onTap,
          onLongPress: onLongPress,
          borderRadius: radius,
          child: Semantics(
            link: true,
            label: '$domain, $_title',
            child: ExcludeSemantics(
              child: Container(
                decoration: BoxDecoration(
                  border: Border.all(color: tweetDividerColor(context)),
                  borderRadius: radius,
                ),
                clipBehavior: Clip.antiAlias,
                child: layout == LinkPreviewLayout.large && _hasImage
                    ? _large(context, domain)
                    : _compact(context, domain),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _compact(BuildContext context, String domain) => Padding(
    padding: const EdgeInsets.all(kTweetSpace3),
    child: Row(
      children: [
        _tile(context),
        const SizedBox(width: kTweetSpace3),
        Expanded(child: _texts(context, domain, titleLines: 3)),
      ],
    ),
  );

  Widget _tile(BuildContext context) {
    final fill = Color.alphaBlend(tweetPrimaryColor(context).withValues(alpha: 0.08), tweetSurfaceColor(context));
    final scale = MediaQuery.devicePixelRatioOf(context);
    return ClipRRect(
      borderRadius: BorderRadius.circular(_tileRadius),
      child: SizedBox.square(
        dimension: kLinkPreviewTileSize,
        child: ColoredBox(
          color: fill,
          child: _hasImage
              ? imageBuilder(context, imageUrl!.trim(), (kLinkPreviewTileSize * scale).ceil())
              : const _LinkGlyph(),
        ),
      ),
    );
  }

  Widget _large(BuildContext context, String domain) {
    final width = MediaQuery.sizeOf(context).width;
    final scale = MediaQuery.devicePixelRatioOf(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        AspectRatio(aspectRatio: 16 / 9, child: imageBuilder(context, imageUrl!.trim(), (width * scale).ceil())),
        Padding(
          padding: const EdgeInsets.fromLTRB(kTweetSpace3, kTweetSpace2 + 2, kTweetSpace3, kTweetSpace2 + 2),
          child: _texts(context, domain, titleLines: 2, withDescription: true),
        ),
      ],
    );
  }

  Widget _texts(BuildContext context, String domain, {required int titleLines, bool withDescription = false}) {
    final secondary = tweetMetadataStyle(context);
    final summary = description?.trim() ?? '';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(domain, maxLines: 1, overflow: TextOverflow.ellipsis, style: secondary),
        const SizedBox(height: 2),
        Text(
          _title,
          maxLines: titleLines,
          overflow: TextOverflow.ellipsis,
          style: tweetBodyStyle(context).copyWith(fontWeight: FontWeight.w600, height: 1.3),
        ),
        if (withDescription && summary.isNotEmpty) ...[
          const SizedBox(height: kTweetSpace1),
          Text(summary, maxLines: 3, overflow: TextOverflow.ellipsis, style: secondary),
        ],
      ],
    );
  }
}

class _LinkGlyph extends StatelessWidget {
  const _LinkGlyph();

  @override
  Widget build(BuildContext context) => Center(child: Icon(Icons.link, size: 26, color: tweetSecondaryColor(context)));
}
