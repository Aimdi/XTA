import 'dart:convert';

import 'package:dart_twitter_api/twitter_api.dart';
import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';

import 'package:quax/client/client.dart';
import 'package:quax/constants.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/tweet/_media.dart';
import 'package:quax/tweet/_video.dart';
import 'package:quax/tweet/poll.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/ui/x_look_theme.dart';
import 'package:quax/utils/urls.dart';
import 'package:intl/intl.dart';
import 'package:logging/logging.dart';
import 'package:pref/pref.dart';
import 'package:timeago/timeago.dart' as timeago;
import 'package:quax/plugins/plugin_links.dart';

class TweetCard extends StatelessWidget {
  static final log = Logger('TweetCard');

  final TweetWithCard tweet;
  final Map<String, dynamic>? card;

  const TweetCard({super.key, required this.tweet, required this.card});

  Container _createBaseCard(Widget child, BuildContext context) {
    return Container(
        margin: const EdgeInsets.symmetric(horizontal: 12),
        width: double.infinity,
        child: Card(
          clipBehavior: Clip.antiAlias,
          color: Theme.of(context).colorScheme.inversePrimary,
          elevation: 0,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(kTweetMediaRadius)),
          child: child,
        ));
  }

  GestureDetector _createCard(String? url, Widget child, BuildContext context) {
    return GestureDetector(
      child: _createBaseCard(child, context),
      onTap: () async {
        if (url == null) {
          return;
        }
        // A Substack card opens in the in-app reader when the plugin is on.
        if (await openWithPlugins(context, url)) {
          return;
        }
        await openUri(context, url);
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

  Container _createListTile(BuildContext context, String title, String? description, String? uri) {
    return Container(
      padding: const EdgeInsets.only(left: 12, right: 12, bottom: 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            margin: const EdgeInsets.only(top: 4),
            child: Text(
              title,
              overflow: TextOverflow.ellipsis,
              maxLines: 1,
              style: Theme.of(context)
                  .textTheme
                  .titleMedium!
                  .copyWith(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w500),
            ),
          ),
          if (description != null)
            Container(
              margin: const EdgeInsets.only(top: 4),
              child: Text(
                description,
                overflow: TextOverflow.ellipsis,
                maxLines: 2,
                style: Theme.of(context).textTheme.bodyMedium!.copyWith(color: Colors.white, fontSize: 12),
              ),
            ),
          if (uri != null)
            Container(
              margin: EdgeInsets.only(top: description == null ? 4 : 8),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  const Icon(Icons.link, size: 12, color: Colors.white),
                  const SizedBox(width: 4),
                  Text(uri,
                      style: Theme.of(context).textTheme.bodySmall!.copyWith(
                            color: Colors.white,
                          )),
                ],
              ),
            )
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
                alignment: Alignment.centerLeft,
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
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 0, vertical: 10),
              child: _createListTile(context, unifiedCard['component_objects']['details_1']['data']['title']['content'],
                  unifiedCard['component_objects']['details_1']['data']['subtitle']['content'], null),
            ),
          ],
        ),
        context);
  }

  dynamic _createUnifiedCard(BuildContext context, Map<String, dynamic> card, String imageKey, String imageSize) {
    var unifiedCard = jsonDecode(card['binding_values']['unified_card']['string_value']) as Map<String, dynamic>;

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

        var child = TweetMedia(media: [Media.fromJson(media)], username: tweet.user!.screenName!, sensitive: false);
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

    final numberFormat = NumberFormat.decimalPattern();
    final endsAt = poll.endsAt;
    final closed = endsAt != null && endsAt.isBefore(DateTime.now());
    final relative =
        endsAt == null ? null : timeago.format(endsAt, allowFromNow: true, locale: Intl.shortLocale(Intl.getCurrentLocale()));

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16),
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
    var urls = tweet.entities?.urls ?? [];

    // Match up the card's URL with the link in the tweet entities, otherwise just use the card's URL
    var url = urls.firstWhere((element) => element.url == link, orElse: () => Url.fromJson({'expanded_url': link}));

    return url.expandedUrl;
  }

  @override
  Widget build(BuildContext context) {
    var card = this.card;
    if (card == null) {
      return Container();
    }

    var imageKey = '';
    var imageSize = PrefService.of(context, listen: false).get(optionImageQuality);
    if (imageSize == 'thumb') {
      imageKey = '_small';
    } else if (imageSize == 'medium') {
      imageKey = '_large';
    } else if (imageSize == 'large') {
      imageKey = '_x_large';
    }

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
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 0, vertical: 10),
                  child: _createListTile(
                      context,
                      card['binding_values']['title']['string_value'],
                      card['binding_values']?['description']?['string_value'],
                      card['binding_values']?['vanity_url']?['string_value']),
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
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 0, vertical: 10),
                  child: _createListTile(context, title, null, vanityUrl),
                ),
              ],
            ),
            context);
      case 'unified_card':
        try {
          return _createUnifiedCard(context, card, imageKey, imageSize);
        } catch (e) {
          log.severe('Unable to render the unified card');
          return Container();
        }
      case '745291183405076480:live_event':
        // https://twitter.com/Erdoanz11/status/1573765738032152577
        var url = card['binding_values']['card_url']['string_value'];
        var image = card['binding_values']['event_thumbnail$imageKey']?['image_value'];

        // Open URL in in-app browser for better user experience and security
        return _createCard(
            url,
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _createImage(imageSize, image, BoxFit.cover),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 0, vertical: 10),
                  child: _createListTile(context, card['binding_values']['event_title']['string_value'],
                      card['binding_values']['event_subtitle']?['string_value'], null),
                ),
              ],
            ),
            context);
      case '745291183405076480:broadcast':
        // https://twitter.com/KwasiKwarteng/status/1573229010779516929
        var uri = card['binding_values']['card_url']['string_value'];
        var image = card['binding_values']['broadcast_thumbnail$imageKey']?['image_value']['url'];
        var key = card['binding_values']['broadcast_media_key']['string_value'];

        var width = double.parse(card['binding_values']['broadcast_width']['string_value']);
        var height = double.parse(card['binding_values']['broadcast_height']['string_value']);

        var aspectRatio = width / height;

        var child = TweetVideo(
            username: 'username',
            loop: false,
            metadata: TweetVideoMetadata(aspectRatio, image, () async {
              var broadcast = await Twitter.getBroadcastDetails(key);

              return TweetVideoUrls(broadcast['source']['noRedirectPlaybackUrl'], null);
            }));

        var username = card['binding_values']['broadcaster_username']['string_value'];
        var title = card['binding_values']['broadcast_title']['string_value'];

        // Documented card states: live, ended, upcoming, replay
        // For now, we handle all states with the same UI

        // Open URL in in-app browser for better user experience and security
        return _createCard(
            uri,
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                child,
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 0, vertical: 10),
                  child: _createListTile(context, title, '@$username', null),
                ),
              ],
            ),
            context);
      default:
        return Container();
    }
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
