import 'dart:convert';

import 'package:dart_twitter_api/twitter_api.dart';
import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';

import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/_media.dart';
import 'package:xta/tweet/_video.dart';
import 'package:xta/tweet/broadcasts.dart';
import 'package:xta/tweet/poll.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:intl/intl.dart';
import 'package:logging/logging.dart';
import 'package:pref/pref.dart';
import 'package:timeago/timeago.dart' as timeago;
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/utils/media_quality.dart';

/// Poll totals are grouped in the reader's locale. Building the pattern parses
/// it, so one is kept per locale rather than one per build of every poll.
final Map<String, NumberFormat> _decimalFormats = {};

NumberFormat _decimalFormat(String locale) =>
    _decimalFormats.putIfAbsent(locale, () => NumberFormat.decimalPattern(locale));

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
    _unifiedCard = _decodeUnifiedCard(widget.card);
  }

  @override
  void didUpdateWidget(TweetCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(widget.card, oldWidget.card)) {
      _unifiedCard = _decodeUnifiedCard(widget.card);
    }
  }

  static Map<String, dynamic>? _decodeUnifiedCard(Map<String, dynamic>? card) {
    final raw = card?['binding_values']?['unified_card']?['string_value'];
    if (raw is! String) {
      return null;
    }

    try {
      return jsonDecode(raw) as Map<String, dynamic>;
    } catch (e) {
      TweetCard.log.severe('Unable to decode the unified card');
      return null;
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

  Widget _createCard(String? url, Widget child, BuildContext context) {
    return _createBaseCard(
      child,
      onTap: url == null
          ? null
          : () async {
              await openLink(context, url);
            },
    );
  }

  Widget _createImage(String size, Map<String, dynamic>? image, BoxFit fit, {double? aspectRatio}) {
    if (image == null) {
      return Container();
    }

    Widget child;

    if (size == 'disabled') {
      child = Container();
    } else {
      child = LayoutBuilder(builder: (context, constraints) {
        final maxW = constraints.maxWidth;
        final cacheWidth = maxW.isFinite && maxW > 0
            ? (maxW * MediaQuery.devicePixelRatioOf(context)).ceil()
            : null;
        return ExtendedImage.network(
          image['url'],
          cache: true,
          fit: fit,
          cacheWidth: cacheWidth,
        );
      });
    }

    return AspectRatio(
      aspectRatio: aspectRatio ?? image['width'] / image['height'],
      child: child,
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

  /// One poll option: a rounded bar filled to its share, the option on the left
  /// and its percentage on the right.
  ///
  /// The old bar was a bare [LinearProgressIndicator] with the label painted
  /// over it — square, full-bleed and with the percentage crowding the option
  /// text it ran into.
  Widget _createVoteBar(BuildContext context, PollChoice choice, bool isLeading) {
    final theme = Theme.of(context);
    final tokens = XLookTokens.maybeOf(context);
    final track = tokens?.divider ?? theme.dividerColor;
    final fill = isLeading
        ? theme.colorScheme.primary.withValues(alpha: 0.45)
        : theme.colorScheme.primary.withValues(alpha: 0.18);
    final weight = isLeading ? FontWeight.w700 : FontWeight.w400;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: Stack(
          children: [
            Container(height: 34, color: track),
            // The fill is laid out as a fraction of the bar rather than painted
            // by a progress indicator, so it keeps the rounded ends.
            Positioned.fill(
              child: FractionallySizedBox(
                alignment: AlignmentDirectional.centerStart,
                widthFactor: choice.share.clamp(0.0, 1.0),
                child: Container(color: fill),
              ),
            ),
            SizedBox(
              height: 34,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(choice.label,
                          overflow: TextOverflow.ellipsis, style: TextStyle(fontWeight: weight)),
                    ),
                    const SizedBox(width: 8),
                    Text('${(choice.share * 100).round()}%', style: TextStyle(fontWeight: weight)),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  dynamic _createWebsiteCard(
      BuildContext context, Map<String, dynamic> unifiedCard, String uri, String imageSize, Widget media) {
    return _createCard(
        uri,
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            media,
            _createListTile(
              context,
              unifiedCard['component_objects']['details_1']['data']['title']['content'],
              unifiedCard['component_objects']['details_1']['data']['subtitle']['content'],
              null,
            ),
          ],
        ),
        context);
  }

  dynamic _createUnifiedCard(BuildContext context, String imageSize) {
    var unifiedCard = _unifiedCard;
    if (unifiedCard == null) {
      return Container();
    }


    switch (unifiedCard['type']) {
      case 'image_website':
        var media = unifiedCard['media_entities'][unifiedCard['component_objects']['media_1']['data']['id']];
        var uri = unifiedCard['destination_objects']['browser_1']['data']['url_data']['url'];

        var child = _createImage(
            imageSize,
            {
              'url': media['media_url_https'],
              'width': media['original_info']['width'],
              'height': media['original_info']['height'],
            },
            BoxFit.cover);
        return _createWebsiteCard(context, unifiedCard, uri, imageSize, child);
      case 'video_website':
        // https://twitter.com/yenisafak/status/1560244349451096064
        var media = unifiedCard['media_entities'][unifiedCard['component_objects']['media_1']['data']['id']];
        var uri = unifiedCard['destination_objects']['browser_with_docked_media_1']['data']['url_data']['url'];

        var child =
            TweetMedia(media: [Media.fromJson(media)], username: widget.tweet.user!.screenName!, sensitive: false);
        return _createWebsiteCard(context, unifiedCard, uri, imageSize, child);
      default:
        return Container();
    }
  }

  Widget _createVoteCard(BuildContext context, Map<String, dynamic> card, int numberOfChoices) {
    final poll = TweetPoll.fromCard(card, numberOfChoices);
    if (poll == null) {
      return Container();
    }

    final locale = Intl.getCurrentLocale();
    final numberFormat = _decimalFormat(locale);
    final endsAt = poll.endsAt;
    final closed = endsAt != null && endsAt.isBefore(DateTime.now());
    final relative = endsAt == null
        ? null
        : timeago.format(endsAt, allowFromNow: true, locale: Intl.shortLocale(locale));

    return TweetEmbedSurface(
      padding: const EdgeInsets.all(kTweetSpace3),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final choice in poll.choices) _createVoteBar(context, choice, choice.count == poll.leadingCount),
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: DefaultTextStyle.merge(
              style: Theme.of(context).textTheme.bodySmall!,
              child: Text([
                L10n.of(context).numberFormat_format_total_votes(poll.total, numberFormat.format(poll.total)),
                if (relative != null)
                  closed
                      ? L10n.of(context).ended_timeago_format_endsAt_allowFromNow_true(relative)
                      : L10n.of(context).ends_timeago_format_endsAt_allowFromNow_true(relative),
              ].join(' • ')),
            ),
          )
        ],
      ),
    );
  }

  String? _findCardUrl(Map<String, dynamic> card) {
    var link = card['url'];
    var urls = widget.tweet.entities?.urls ?? [];

    // Match up the card's URL with the link in the tweet entities, otherwise just use the card's URL
    var url = urls.firstWhere((element) => element.url == link, orElse: () => Url.fromJson({'expanded_url': link}));

    return url.expandedUrl;
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
        var image = card['binding_values']['thumbnail_image$imageKey']?['image_value'];

        return _createCard(
            _findCardUrl(card),
            Row(
              children: [
                Expanded(flex: 1, child: _createImage(imageSize, image, BoxFit.cover)),
                Expanded(
                    flex: 4,
                    child: _createListTile(
                        context,
                        card['binding_values']['title']['string_value'],
                        card['binding_values']?['description']?['string_value'],
                        card['binding_values']?['vanity_url']?['string_value']))
              ],
            ),
            context);
      case 'summary_large_image':
        var image = card['binding_values']['thumbnail_image$imageKey']?['image_value'];

        return _createCard(
            _findCardUrl(card),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _createImage(imageSize, image, BoxFit.cover),
                _createListTile(
                  context,
                  card['binding_values']['title']['string_value'],
                  card['binding_values']?['description']?['string_value'],
                  card['binding_values']?['vanity_url']?['string_value'],
                ),
              ],
            ),
            context);
      case 'player':
        var image = card['binding_values']['player_image$imageKey']?['image_value'];

        return _createCard(
            _findCardUrl(card),
            Row(
              children: [
                Expanded(flex: 1, child: _createImage(imageSize, image, BoxFit.cover, aspectRatio: 1)),
                Expanded(
                    flex: 4,
                    child: _createListTile(
                        context,
                        card['binding_values']['title']['string_value'],
                        card['binding_values']?['description']?['string_value'],
                        card['binding_values']?['vanity_url']?['string_value']))
              ],
            ),
            context);
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
        var url = card['binding_values']['website_url']['string_value'];
        var image = card['binding_values']['promo_image$imageKey']?['image_value'];
        var title = card['binding_values']['title']['string_value'];
        var vanityUrl = card['binding_values']['vanity_url']['string_value'];

        return _createCard(
            url,
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _createImage(imageSize, image, BoxFit.cover),
                _createListTile(context, title, null, vanityUrl),
              ],
            ),
            context);
      case 'unified_card':
        try {
          return _createUnifiedCard(context, imageSize);
        } catch (e) {
          TweetCard.log.severe('Unable to render the unified card');
          return Container();
        }
      case '745291183405076480:live_event':
        // https://twitter.com/Erdoanz11/status/1573765738032152577
        var url = card['binding_values']['card_url']['string_value'];
        var image = card['binding_values']['event_thumbnail$imageKey']?['image_value'];

        // TODO: This opens the URL externally. Create a screen for it in XTA
        return _createCard(
            url,
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _createImage(imageSize, image, BoxFit.cover),
                _createListTile(
                  context,
                  card['binding_values']['event_title']['string_value'],
                  card['binding_values']['event_subtitle']?['string_value'],
                  null,
                ),
              ],
            ),
            context);
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
