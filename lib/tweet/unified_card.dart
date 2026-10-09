import 'dart:convert';

import 'package:dart_twitter_api/twitter_api.dart';
import 'package:logging/logging.dart';
import 'package:xta/utils/json.dart';

final _log = Logger('UnifiedCard');

/// The unified card inside [card], decoded from the JSON string X wraps it in.
///
/// The binding values are keyed once the client has read the tweet, but a
/// card kept as X sent it still has them as a list of `{key, value}` pairs.
Map<String, dynamic>? unifiedCardOf(Map<String, dynamic>? card) {
  final values = Json(card)['binding_values'];
  final keyed = values['unified_card'];
  final entry = keyed.exists
      ? keyed
      : values.list
            .where((e) => e['key'].string == 'unified_card')
            .map((e) => e['value'])
            .firstOrNull;
  final raw = entry?['string_value'].string;
  if (raw == null) return null;
  try {
    final decoded = jsonDecode(raw);
    return decoded is Map<String, dynamic> ? decoded : null;
  } on FormatException {
    _log.severe('Unable to decode the unified card');
    return null;
  }
}

/// An `image_carousel_website` card: several pictures that all lead to one
/// page, e.g. https://x.com/UEFA/status/2082854732020760880.
class CarouselCardData {
  final String url;
  final String title;
  final String? subtitle;
  final List<Media> media;

  const CarouselCardData({
    required this.url,
    required this.title,
    required this.subtitle,
    required this.media,
  });

  /// Null when the card has no page to open, no title or no picture.
  static CarouselCardData? fromUnified(Map<String, dynamic> unified) {
    final json = Json(unified);
    final details = json['component_objects']['details_1']['data'];
    final destination = details['destination'].string ?? 'browser_1';
    final url =
        json['destination_objects'][destination]['data']['url_data']['url']
            .string;
    final title = details['title']['content'].string;
    final media =
        json['component_objects']['swipeable_media_1']['data']['media_list']
            .list
            .map((item) => item['id'].string)
            .nonNulls
            .map((id) => json['media_entities'][id].raw)
            .whereType<Map<String, dynamic>>()
            .map(Media.fromJson)
            .toList(growable: false);
    if (url == null || title == null || media.isEmpty) return null;
    return CarouselCardData(
      url: url,
      title: title,
      subtitle: details['subtitle']['content'].string,
      media: media,
    );
  }
}

final _grokRenderTags = RegExp(
  r'<grok:render[^>]*>.*?</grok:render>',
  dotAll: true,
);

/// A shared Grok conversation, e.g. https://x.com/elonmusk/status/2098507671083036843.
///
/// The card has no `type`; its `details_1` component says `grok_share`.
class GrokShareCardData {
  final String url;
  final String question;
  final String answer;
  final String grokScreenName;
  final String? grokImageUrl;

  const GrokShareCardData({
    required this.url,
    required this.question,
    required this.answer,
    required this.grokScreenName,
    required this.grokImageUrl,
  });

  static bool isGrokShare(Map<String, dynamic>? unified) =>
      Json(unified)['component_objects']['details_1']['type'].string ==
      'grok_share';

  /// Null when the card has no conversation to show or nowhere to lead.
  static GrokShareCardData? fromUnified(Map<String, dynamic> unified) {
    if (!isGrokShare(unified)) return null;
    final json = Json(unified);
    final data = json['component_objects']['details_1']['data'];
    final destination = data['destination'].string ?? 'destination_1';
    final url =
        json['destination_objects'][destination]['data']['url_data']['url']
            .string;
    final preview = data['conversation_preview'].list;
    final question = _firstMessage(preview, 'USER');
    if (url == null || question == null) return null;
    final grok = data['grok_user'];
    return GrokShareCardData(
      url: url,
      question: question,
      answer: (_firstMessage(preview, 'AGENT') ?? '')
          .replaceAll(_grokRenderTags, '')
          .trim(),
      grokScreenName: grok['screen_name'].string ?? 'grok',
      grokImageUrl: grok['profile_image_url_https'].string,
    );
  }

  static String? _firstMessage(List<Json> preview, String sender) => preview
      .where((m) => m['sender'].string == sender)
      .map((m) => m['message'].string?.trim())
      .where((m) => m != null && m.isNotEmpty)
      .firstOrNull;
}

/// Whether [card] shows a shared Grok conversation, whose link the post's text
/// then need not repeat.
bool isGrokShareCard(Map<String, dynamic>? card) =>
    Json(card)['name'].string == 'unified_card' &&
    GrokShareCardData.isGrokShare(unifiedCardOf(card));
