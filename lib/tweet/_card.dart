import 'package:dart_twitter_api/twitter_api.dart';
import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';

import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/tweet/_media.dart';
import 'package:xta/tweet/_video.dart';
import 'package:xta/tweet/broadcasts.dart';
import 'package:xta/tweet/grok_share_card.dart';
import 'package:xta/tweet/poll.dart';
import 'package:xta/tweet/poll_results.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/unified_card.dart';
import 'package:logging/logging.dart';
import 'package:pref/pref.dart';
import 'package:xta/links/link_opening.dart';
import 'package:xta/links/link_preview_card.dart';
import 'package:xta/tweet/tweet_link_context.dart';
import 'package:xta/utils/media_quality.dart';
import 'package:xta/utils/json.dart';

class TweetCard extends StatefulWidget {
  static final log = Logger('TweetCard');

  final TweetWithCard tweet;
  final Map<String, dynamic>? card;

  const TweetCard({super.key, required this.tweet, required this.card});

  @override
  State<TweetCard> createState() => _TweetCardState();
}

class _TweetCardState extends State<TweetCard> {
  /// A unified card arrives as a JSON string several kilobytes long, so it is
  /// decoded when the card is handed over rather than on every build.
  Map<String, dynamic>? _unifiedCard;

  @override
  void initState() {
    super.initState();
    _unifiedCard = unifiedCardOf(widget.card);
  }

  @override
  void didUpdateWidget(TweetCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(widget.card, oldWidget.card)) {
      _unifiedCard = unifiedCardOf(widget.card);
    }
  }

  Widget _createBaseCard(
    Widget child, {
    VoidCallback? onTap,
  }) {
    return TweetEmbedSurface(
      onTap: onTap,
      child: SizedBox(width: double.infinity, child: child),
    );
  }

  Widget _createCard(
    String? url,
    Widget child,
    BuildContext context, {
    String? title,
  }) {
    return _createBaseCard(
      child,
      onTap: url == null
          ? null
          : () => _openLink(context, url, title),
    );
  }

  Future<void> _openLink(BuildContext context, String url, String? title) =>
      openPostLink(
        context,
        url,
        title: title,
        post: tweetLinkContext(context, widget.tweet),
      );

  /// An article link as Threads shows one: a tile, the domain, the title.
  Widget _createCompactCard(
    BuildContext context,
    Json values,
    String? url,
    String imageSize,
  ) {
    final title = values['title']['string_value'].string;
    final image = imageSize == 'disabled'
        ? null
        : values['thumbnail_image']['image_value']['url'].string;
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(
        kTweetHorizontalPadding,
        kTweetSpace2,
        kTweetHorizontalPadding,
        0,
      ),
      child: LinkPreviewCard(
        url: url ?? values['vanity_url']['string_value'].string ?? '',
        title: title,
        imageUrl: image,
        onTap: url == null ? null : () => _openLink(context, url, title),
      ),
    );
  }

  Widget _createImage(
    String size,
    Map<String, dynamic>? image,
    BoxFit fit, {
    double? aspectRatio,
  }) {
    if (image == null) return Container();

    final data = Json(image);
    final width = data['width'].number;
    final height = data['height'].number;
    final ratio = aspectRatio ??
        (width != null && height != null && width > 0 && height > 0
            ? width / height
            : 16 / 9);

    if (size == 'disabled') {
      return AspectRatio(aspectRatio: ratio, child: Container());
    }

    final url = data['url'].string;
    if (url == null || url.isEmpty) return Container();

    return AspectRatio(
      aspectRatio: ratio,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final maxW = constraints.maxWidth;
          final cacheWidth = maxW.isFinite && maxW > 0
              ? (maxW * MediaQuery.devicePixelRatioOf(context)).ceil()
              : null;
          return ExtendedImage.network(
            url,
            cache: true,
            fit: fit,
            cacheWidth: cacheWidth,
          );
        },
      ),
    );
  }

  Widget _createListTile(
    BuildContext context,
    String title,
    String? description,
    String? uri,
  ) {
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(
        kTweetSpace3,
        kTweetSpace2,
        kTweetSpace3,
        kTweetSpace3,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            overflow: TextOverflow.ellipsis,
            maxLines: 2,
            style: tweetLabelStyle(context),
          ),
          if (description != null) ...[
            const SizedBox(height: kTweetSpace1),
            Text(
              description,
              overflow: TextOverflow.ellipsis,
              maxLines: 3,
              style: tweetMetadataStyle(
                context,
              ).copyWith(color: tweetPrimaryColor(context)),
            ),
          ],
          if (uri != null) ...[
            SizedBox(
              height: description == null ? kTweetSpace1 : kTweetSpace2,
            ),
            Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Icon(
                  Icons.link,
                  size: 14,
                  color: tweetReadableAccentColor(context),
                ),
                const SizedBox(width: kTweetSpace1),
                Expanded(
                  child: Text(
                    uri,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: tweetMetadataStyle(context).copyWith(
                      color: tweetReadableAccentColor(context),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  dynamic _createWebsiteCard(
      BuildContext context,
      Map<String, dynamic> unifiedCard,
      String? uri,
      String imageSize,
      Widget media,
  ) {
    final title = Json(unifiedCard)['component_objects']['details_1']['data']['title']['content'].string;
    return _createCard(
        uri,
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            media,
            _createListTile(
              context,
              title ?? '',
              Json(unifiedCard)['component_objects']['details_1']['data']['subtitle']['content'].string,
              null,
            ),
          ],
        ),
        context,
        title: title);
  }

  dynamic _createUnifiedCard(BuildContext context, String imageSize) {
    var unifiedCard = _unifiedCard;
    if (unifiedCard == null) {
      return Container();
    }


    switch (unifiedCard['type']) {
      case 'image_website':
        final json = Json(unifiedCard);
        final mediaId = json['component_objects']['media_1']['data']['id'].string;
        final media = mediaId == null ? const Json(null) : json['media_entities'][mediaId];
        final uri = json['destination_objects']['browser_1']['data']['url_data']['url'].string;

        var child = _createImage(
            imageSize,
            {
              'url': media['media_url_https'].string,
              'width': media['original_info']['width'].number ?? 1,
              'height': media['original_info']['height'].number ?? 1,
            },
            BoxFit.cover);
        return _createWebsiteCard(context, unifiedCard, uri, imageSize, child);
      case 'video_website':
        // https://twitter.com/yenisafak/status/1560244349451096064
        final json = Json(unifiedCard);
        final mediaId = json['component_objects']['media_1']['data']['id'].string;
        final rawMedia = mediaId == null ? null : json['media_entities'][mediaId].raw;
        final uri = json['destination_objects']['browser_with_docked_media_1']['data']['url_data']['url'].string;
        if (rawMedia is! Map<String, dynamic>) {
          return Container();
        }
        var child = TweetMedia(
          media: [Media.fromJson(rawMedia)],
          username: widget.tweet.user?.screenName ?? '',
          sensitive: false,
        );
        return _createWebsiteCard(context, unifiedCard, uri, imageSize, child);
      case 'image_carousel_website':
        return _createCarouselCard(context, unifiedCard, imageSize);
      case null when GrokShareCardData.isGrokShare(unifiedCard):
        return _createGrokShareCard(context, unifiedCard);
      default:
        return Container();
    }
  }

  /// Every picture of the carousel in the media row, above the page they lead to.
  Widget _createCarouselCard(
    BuildContext context,
    Map<String, dynamic> unifiedCard,
    String imageSize,
  ) {
    final carousel = CarouselCardData.fromUnified(unifiedCard);
    if (carousel == null) return Container();
    final media = TweetMedia(
      media: carousel.media,
      username: widget.tweet.user?.screenName ?? '',
      sensitive: false,
    );
    return _createWebsiteCard(
      context,
      unifiedCard,
      carousel.url,
      imageSize,
      media,
    );
  }

  Widget _createGrokShareCard(
    BuildContext context,
    Map<String, dynamic> unifiedCard,
  ) {
    final share = GrokShareCardData.fromUnified(unifiedCard);
    if (share == null) return Container();
    return GrokShareCard(
      share: share,
      onTap: () => _openLink(context, share.url, null),
    );
  }

  Widget _createVoteCard(BuildContext context, Map<String, dynamic> card, int numberOfChoices) {
    final poll = TweetPoll.fromCard(card, numberOfChoices);
    return poll == null ? Container() : TweetPollResults(poll: poll);
  }

  /// A large-image card stays large only when its picture is the content —
  /// a video page, say; a news story's share image goes in the tile.
  bool _prefersCompact(
    Map<String, dynamic> card,
    String? url,
    String imageSize,
  ) {
    final image = Json(card)['binding_values']['thumbnail_image']['image_value']
        ['url'].string;
    return linkPreviewLayoutFor(
          url ?? '',
          hasImage: image != null && imageSize != 'disabled',
        ) ==
        LinkPreviewLayout.compact;
  }

  String? _findCardUrl(Map<String, dynamic> card) {
    final link = Json(card)['url'].string;
    if (link == null || link.isEmpty) return null;
    var urls = widget.tweet.entities?.urls ?? [];

    // Match up the card's URL with the link in the tweet entities, otherwise just use the card's URL.
    var url = urls.firstWhere(
      (element) => element.url == link,
      orElse: () => Url.fromJson({'expanded_url': link}),
    );

    return url.expandedUrl ?? link;
  }

  @override
  Widget build(BuildContext context) {
    var card = widget.card;
    if (card == null) {
      return Container();
    }

    var imageSize = PrefService.of(context, listen: false).get<String>(optionImageQuality) ?? '';
    // `small` and anything unknown keep the card's unsuffixed default key.
    var imageKey = switch (MediaQuality.fromStored(imageSize, fallback: MediaQuality.small)) {
      MediaQuality.thumb => '_small',
      MediaQuality.small => '',
      MediaQuality.medium => '_large',
      MediaQuality.large => '_x_large',
    };

    switch (card['name']) {
      case 'summary':
      case 'summary_large_image'
          when _prefersCompact(card, _findCardUrl(card), imageSize):
        return _createCompactCard(
          context,
          Json(card)['binding_values'],
          _findCardUrl(card),
          imageSize,
        );
      case 'summary_large_image':
        final values = Json(card)['binding_values'];
        final image = values['thumbnail_image$imageKey']['image_value'].raw;
        final title = values['title']['string_value'].string;
        final description = values['description']['string_value'].string;
        final vanityUrl = values['vanity_url']['string_value'].string;
        return _createCard(
          _findCardUrl(card),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              _createImage(
                imageSize,
                image is Map<String, dynamic> ? image : null,
                BoxFit.cover,
              ),
              _createListTile(
                context,
                title ?? '',
                description,
                vanityUrl,
              ),
            ],
          ),
          context,
          title: title,
        );
      case 'player':
        final values = Json(card)['binding_values'];
        final image = values['player_image$imageKey']['image_value'].raw;
        final title = values['title']['string_value'].string;
        final description = values['description']['string_value'].string;
        final vanityUrl = values['vanity_url']['string_value'].string;

        return _createCard(
          _findCardUrl(card),
          Row(
            children: [
              Expanded(
                flex: 1,
                child: _createImage(
                  imageSize,
                  image is Map<String, dynamic> ? image : null,
                  BoxFit.cover,
                  aspectRatio: 1,
                ),
              ),
              Expanded(
                flex: 4,
                child: _createListTile(
                  context,
                  title ?? '',
                  description,
                  vanityUrl,
                ),
              ),
            ],
          ),
          context,
          title: title,
        );
      // The image variants carry the same choice bindings; only the artwork
      // differs, and it was never shown. They used to fall through to the
      // default and render nothing at all.
      case 'poll2choice_text_only':
      case 'poll2choice_image':
        return _createVoteCard(context, card, 2);
      case 'poll3choice_text_only':
      case 'poll3choice_image':
        return _createVoteCard(context, card, 3);
      case 'poll4choice_text_only':
      case 'poll4choice_image':
        return _createVoteCard(context, card, 4);
      case 'promo_website':
        // https://twitter.com/CMEGroup/status/1573288572647612416
        final values = Json(card)['binding_values'];
        final url = values['website_url']['string_value'].string;
        final image = values['promo_image$imageKey']['image_value'].raw;
        final title = values['title']['string_value'].string;
        final vanityUrl = values['vanity_url']['string_value'].string;

        return _createCard(
            url,
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _createImage(
                  imageSize,
                  image is Map<String, dynamic> ? image : null,
                  BoxFit.cover,
                ),
                _createListTile(context, title ?? '', null, vanityUrl),
              ],
            ),
            context,
            title: title);
      case 'unified_card':
        try {
          return _createUnifiedCard(context, imageSize);
        } catch (e) {
          TweetCard.log.severe('Unable to render the unified card');
          return Container();
        }
      case '745291183405076480:live_event':
        // https://twitter.com/Erdoanz11/status/1573765738032152577
        final values = Json(card)['binding_values'];
        final url = values['card_url']['string_value'].string;
        final image = values['event_thumbnail$imageKey']['image_value'].raw;
        final title = values['event_title']['string_value'].string;
        return _createCard(
            url,
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _createImage(
                  imageSize,
                  image is Map<String, dynamic> ? image : null,
                  BoxFit.cover,
                ),
                _createListTile(
                  context,
                  title ?? '',
                  values['event_subtitle']['string_value'].string,
                  null,
                ),
              ],
            ),
            context,
            title: title);
      case '745291183405076480:broadcast':
        // https://twitter.com/KwasiKwarteng/status/1573229010779516929
        final values = card['binding_values'] as Map<String, dynamic>?;
        var image = values?['broadcast_thumbnail$imageKey']?['image_value']?['url'] as String?;
        var key = values?['broadcast_media_key']?['string_value'] as String?;
        final broadcastId = broadcastIdFromCard(card);

        final width = double.tryParse('${values?['broadcast_width']?['string_value'] ?? ''}') ?? 16;
        final height = double.tryParse('${values?['broadcast_height']?['string_value'] ?? ''}') ?? 9;
        var aspectRatio = height == 0 ? 16 / 9 : width / height;
        // Square thumbnails around a landscape stream used to leave a fat
        // white bar under the player.
        if (!aspectRatio.isFinite || aspectRatio <= 0 || aspectRatio < 1.2) {
          aspectRatio = 16 / 9;
        }

        if (key == null && broadcastId == null) {
          return Container();
        }

        var child = TweetVideo(
            username: 'username',
            loop: false,
            metadata: TweetVideoMetadata.live(
              aspectRatio: aspectRatio,
              imageUrl: image,
              playbackUrl: () => livePlaybackUrl(
                LivePlayRequest(
                  mediaKey: key,
                  broadcastId: broadcastId,
                ),
              ),
            ));

        // Just the player. Title/@username sat in a pale card under the video
        // and read as a blank white bar; the tweet already has the text.
        return TweetMediaFrame(
          child: ColoredBox(color: Colors.black, child: child),
        );
      default:
        if (isAudioSpaceCard(card)) {
          return _createAudioSpacePlayer(card);
        }
        return Container();
    }
  }

  Widget _createAudioSpacePlayer(Map<String, dynamic> card) {
    final spaceId = spaceIdFromCard(card);
    if (spaceId == null) {
      return Container();
    }
    return TweetMediaFrame(
      child: ColoredBox(
        color: Colors.black,
        child: TweetVideo(
          username: widget.tweet.user?.screenName ?? 'space',
          loop: false,
          metadata: TweetVideoMetadata.live(
            imageUrl: broadcastThumbnailFromCard(card),
            playbackUrl: () => livePlaybackUrl(
              LivePlayRequest.fromTweet(widget.tweet),
            ),
          ),
        ),
      ),
    );
  }
}

class UnknownCardType implements Exception {
  final String? tweet;
  final String type;

  UnknownCardType(this.tweet, this.type);

  @override
  String toString() {
    return 'UnknownCardType{tweet: $tweet, type: $type}';
  }
}

class UnknownUnifiedCardType implements Exception {
  final String? tweet;
  final String type;

  UnknownUnifiedCardType(this.tweet, this.type);

  @override
  String toString() {
    return 'UnknownUnifiedCardType{tweet: $tweet, type: $type}';
  }
}
