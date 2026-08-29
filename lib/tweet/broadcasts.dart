import 'package:xta/client/client.dart';
import 'package:xta/utils/urls.dart';

/// Whether [tweet] is (or carries) an X broadcast / Spaces recording.
///
/// Marked by an `x.com/i/broadcasts/…` (or `/i/broadcast/…`, `pscp.tv/w/…`)
/// URL, the `*:broadcast` card, or the card's `broadcast_id` /
/// `broadcast_url` bindings. UserMedia often has the video without the card,
/// and a live one can have the card without media — either is enough.
bool tweetHasBroadcast(TweetWithCard tweet) {
  return broadcastIdOf(tweet) != null || isBroadcastCard(tweet.card);
}

/// The broadcast id this tweet points at, if it has one.
///
/// The link can sit on a URL entity, a media expanded/display URL, the card,
/// or the post body — UserMedia is inconsistent about which of those it keeps.
String? broadcastIdOf(TweetWithCard tweet) {
  for (final url in tweet.entities?.urls ?? const []) {
    final id = _idFrom(url.expandedUrl, url.displayUrl, url.url);
    if (id != null) {
      return id;
    }
  }
  for (final url in tweet.noteEntities?.urls ?? const []) {
    final id = _idFrom(url.expandedUrl, url.displayUrl, url.url);
    if (id != null) {
      return id;
    }
  }
  for (final media in tweet.extendedEntities?.media ?? const []) {
    final id = _idFrom(media.expandedUrl, media.displayUrl, media.url);
    if (id != null) {
      return id;
    }
  }
  for (final media in tweet.entities?.media ?? const []) {
    final id = _idFrom(media.expandedUrl, media.displayUrl, media.url);
    if (id != null) {
      return id;
    }
  }
  return broadcastIdFromCard(tweet.card) ??
      broadcastIdInText(tweet.fullText) ??
      broadcastIdInText(tweet.text) ??
      broadcastIdInText(tweet.noteText);
}

String? _idFrom(String? expanded, String? display, String? url) {
  return broadcastIdIn(expanded) ?? broadcastIdIn(display) ?? broadcastIdIn(url);
}

/// `https://x.com/i/broadcasts/{id}` for [tweet], or null.
String? broadcastUrlOf(TweetWithCard tweet) {
  final id = broadcastIdOf(tweet);
  return id == null ? null : broadcastUrlFor(id);
}

bool isBroadcastCard(Map<String, dynamic>? card) {
  final name = card?['name'] as String?;
  return name != null && name.endsWith(':broadcast');
}

String? broadcastIdFromCard(Map<String, dynamic>? card) {
  if (card == null) {
    return null;
  }
  final fromUrl = broadcastIdIn(_cardString(card, 'broadcast_url'));
  if (fromUrl != null) {
    return fromUrl;
  }
  final id = _cardString(card, 'broadcast_id');
  if (id != null && id.isNotEmpty) {
    return id;
  }
  return null;
}

/// Thumbnail the broadcast card carries, for a grid tile when there is no
/// native video.
String? broadcastThumbnailFromCard(Map<String, dynamic>? card) {
  final values = _cardValues(card);
  if (values == null) {
    return null;
  }
  for (final key in [
    'broadcast_thumbnail_large',
    'broadcast_thumbnail_x_large',
    'broadcast_thumbnail',
    'broadcast_thumbnail_small',
  ]) {
    final url = values[key]?['image_value']?['url'] as String?;
    if (url != null && url.isNotEmpty) {
      return url;
    }
  }
  return null;
}

String? _cardString(Map<String, dynamic> card, String key) {
  return _cardValues(card)?[key]?['string_value'] as String?;
}

Map<String, dynamic>? _cardValues(Map<String, dynamic>? card) {
  if (card == null) {
    return null;
  }
  final values = card['binding_values'];
  if (values is Map<String, dynamic>) {
    return values;
  }
  if (values is Map) {
    return Map<String, dynamic>.from(values);
  }
  if (values is List) {
    final out = <String, dynamic>{};
    for (final elm in values) {
      if (elm is Map && elm['key'] is String) {
        out[elm['key'] as String] = elm['value'];
      }
    }
    return out;
  }
  return null;
}

bool tweetHasVideoMedia(TweetWithCard tweet) {
  final media = tweet.extendedEntities?.media;
  if (media == null) {
    return false;
  }
  return media.any((item) => item.type == 'video');
}
