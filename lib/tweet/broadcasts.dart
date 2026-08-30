import 'package:xta/client/client.dart';
import 'package:xta/utils/json.dart';
import 'package:xta/utils/urls.dart';

/// Whether [tweet] is a live video, Space, or recording of either.
bool tweetIsLive(TweetWithCard tweet) =>
    tweetHasBroadcast(tweet) || tweetHasSpace(tweet);


/// Whether [tweet] is (or carries) an X broadcast / live-video recording.
///
/// Marked by an `x.com/i/broadcasts/…` (or `/i/broadcast/…`, `pscp.tv/w/…`)
/// URL, the `*:broadcast` card, or the card's `broadcast_id` /
/// `broadcast_url` bindings. UserMedia often has the video without the card,
/// and a live one can have the card without media — either is enough.
bool tweetHasBroadcast(TweetWithCard tweet) {
  return broadcastIdOf(tweet) != null || isBroadcastCard(tweet.card);
}

/// Whether [tweet] is (or carries) an X Space.
///
/// Marked by an `x.com/i/spaces/…` URL, the `*:audiospace` card, or the
/// card's `id` / `vanity_url` bindings. Spaces are usually card-only audio.
bool tweetHasSpace(TweetWithCard tweet) {
  return spaceIdOf(tweet) != null || isAudioSpaceCard(tweet.card);
}

/// The broadcast id this tweet points at, if it has one.
String? broadcastIdOf(TweetWithCard tweet) {
  for (final candidate in _urlCandidates(tweet)) {
    final id = broadcastIdIn(candidate);
    if (id != null) {
      return id;
    }
  }
  return broadcastIdFromCard(tweet.card) ??
      broadcastIdInText(tweet.fullText) ??
      broadcastIdInText(tweet.text) ??
      broadcastIdInText(tweet.noteText);
}

/// The Space id this tweet points at, if it has one.
String? spaceIdOf(TweetWithCard tweet) {
  for (final candidate in _urlCandidates(tweet)) {
    final id = spaceIdIn(candidate);
    if (id != null) {
      return id;
    }
  }
  return spaceIdFromCard(tweet.card) ??
      spaceIdInText(tweet.fullText) ??
      spaceIdInText(tweet.text) ??
      spaceIdInText(tweet.noteText);
}

Iterable<String?> _urlCandidates(TweetWithCard tweet) sync* {
  for (final url in tweet.entities?.urls ?? const []) {
    yield url.expandedUrl;
    yield url.displayUrl;
    yield url.url;
  }
  for (final url in tweet.noteEntities?.urls ?? const []) {
    yield url.expandedUrl;
    yield url.displayUrl;
    yield url.url;
  }
  for (final media in tweet.extendedEntities?.media ?? const []) {
    yield media.expandedUrl;
    yield media.displayUrl;
    yield media.url;
  }
  for (final media in tweet.entities?.media ?? const []) {
    yield media.expandedUrl;
    yield media.displayUrl;
    yield media.url;
  }
}

/// `https://x.com/i/broadcasts/{id}` for [tweet], or null.
String? broadcastUrlOf(TweetWithCard tweet) {
  final id = broadcastIdOf(tweet);
  return id == null ? null : broadcastUrlFor(id);
}

/// `https://x.com/i/spaces/{id}` for [tweet], or null.
String? spaceUrlOf(TweetWithCard tweet) {
  final id = spaceIdOf(tweet);
  return id == null ? null : spaceUrlFor(id);
}

/// Watch URL: Space if present, otherwise the broadcast.
String? liveUrlOf(TweetWithCard tweet) =>
    spaceUrlOf(tweet) ?? broadcastUrlOf(tweet);

bool isBroadcastCard(Map<String, dynamic>? card) {
  final name = card?['name'] as String?;
  return name != null && name.endsWith(':broadcast');
}

bool isAudioSpaceCard(Map<String, dynamic>? card) {
  final name = card?['name'] as String?;
  return name != null && name.endsWith(':audiospace');
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

String? spaceIdFromCard(Map<String, dynamic>? card) {
  if (card == null) {
    return null;
  }
  final fromVanity = spaceIdIn(_cardString(card, 'vanity_url'));
  if (fromVanity != null) {
    return fromVanity;
  }
  final id = _cardString(card, 'id');
  if (id != null && id.isNotEmpty) {
    return id;
  }
  return spaceIdIn(_cardString(card, 'card_url'));
}

/// Thumbnail the live card carries, for a grid tile when there is no native video.
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
    'thumbnail_image_large',
    'thumbnail_image_x_large',
    'thumbnail_image',
    'thumbnail_image_small',
    'thumbnail',
  ]) {
    final imageUrl = values[key]?['image_value']?['url'] as String?;
    if (imageUrl != null && imageUrl.isNotEmpty) {
      return imageUrl;
    }
    final stringUrl = values[key]?['string_value'] as String?;
    if (stringUrl != null && stringUrl.isNotEmpty && stringUrl.startsWith('http')) {
      return stringUrl;
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

/// Card `broadcast_media_key` — what `/live_video_stream/status` actually wants.
String? broadcastMediaKeyOf(TweetWithCard tweet) {
  final card = tweet.card;
  if (card == null) {
    return null;
  }
  final key = _cardString(card, 'broadcast_media_key');
  if (key == null || key.isEmpty) {
    return null;
  }
  return key;
}

/// Some Space cards already carry a `media_key` binding.
String? spaceMediaKeyOf(TweetWithCard tweet) {
  final card = tweet.card;
  if (card == null) {
    return null;
  }
  final key = _cardString(card, 'media_key');
  if (key == null || key.isEmpty) {
    return null;
  }
  return key;
}

/// HLS URL in `/live_video_stream/status/{mediaKey}`.
String? playbackUrlFromBroadcastStatus(Object? json) {
  return Json(json)['source']['noRedirectPlaybackUrl'].string;
}

/// `media_key` in GraphQL AudioSpaceById.
String? mediaKeyFromAudioSpace(Object? json) {
  return Json(json)['data']['audioSpace']['metadata']['media_key'].string;
}

/// `media_key` in `/1.1/broadcasts/show.json`.
String? mediaKeyFromBroadcastsShow(Object? json, String id) {
  return Json(json)['broadcasts'][id]['media_key'].string;
}

/// Ids and keys needed to start a live/VOD stream in the in-app player.
class LivePlayRequest {
  final String? mediaKey;
  final String? broadcastId;
  final String? spaceId;
  final String? imageUrl;
  final double aspectRatio;

  const LivePlayRequest({
    this.mediaKey,
    this.broadcastId,
    this.spaceId,
    this.imageUrl,
    this.aspectRatio = 16 / 9,
  });

  bool get isSpace => spaceId != null && spaceId!.isNotEmpty;

  bool get canResolve {
    bool present(String? value) => value != null && value.isNotEmpty;
    return present(mediaKey) || present(broadcastId) || present(spaceId);
  }

  String? get watchUrl {
    if (isSpace) {
      return spaceUrlFor(spaceId!);
    }
    if (broadcastId != null && broadcastId!.isNotEmpty) {
      return broadcastUrlFor(broadcastId!);
    }
    return null;
  }

  factory LivePlayRequest.fromUrl(String url) {
    final spaceId = spaceIdIn(url);
    if (spaceId != null) {
      return LivePlayRequest(spaceId: spaceId);
    }
    return LivePlayRequest(broadcastId: broadcastIdIn(url));
  }

  factory LivePlayRequest.fromTweet(TweetWithCard tweet) {
    return LivePlayRequest(
      mediaKey: broadcastMediaKeyOf(tweet) ?? spaceMediaKeyOf(tweet),
      broadcastId: broadcastIdOf(tweet),
      spaceId: spaceIdOf(tweet),
      imageUrl: broadcastThumbnailFromCard(tweet.card),
    );
  }

  LivePlayRequest copyWith({
    String? mediaKey,
    String? broadcastId,
    String? spaceId,
    String? imageUrl,
    double? aspectRatio,
  }) {
    return LivePlayRequest(
      mediaKey: mediaKey ?? this.mediaKey,
      broadcastId: broadcastId ?? this.broadcastId,
      spaceId: spaceId ?? this.spaceId,
      imageUrl: imageUrl ?? this.imageUrl,
      aspectRatio: aspectRatio ?? this.aspectRatio,
    );
  }
}

/// Resolves an HLS playlist for a live room, Space, or recorded broadcast.
///
/// Same `/live_video_stream/status` path the tweet card already uses. A card
/// `media_key` is enough; otherwise the broadcast id or Space id is turned
/// into one first.
Future<String?> livePlaybackUrl(LivePlayRequest request) async {
  try {
    var key = request.mediaKey;
    if (!_present(key) && _present(request.broadcastId)) {
      key = mediaKeyFromBroadcastsShow(
        await Twitter.fetchBroadcastsShow(request.broadcastId!),
        request.broadcastId!,
      );
    }
    if (!_present(key) && _present(request.spaceId)) {
      key = mediaKeyFromAudioSpace(await Twitter.fetchAudioSpace(request.spaceId!));
    }
    key ??= _present(request.broadcastId) ? '28_${request.broadcastId}' : request.broadcastId;
    if (!_present(key)) {
      return null;
    }
    return playbackUrlFromBroadcastStatus(await Twitter.getBroadcastDetails(key!));
  } catch (_) {
    return null;
  }
}

bool _present(String? value) => value != null && value.isNotEmpty;

