import 'package:xta/client/client.dart';
import 'package:xta/utils/urls.dart';

/// Whether [tweet] is (or carries) an X broadcast / Spaces recording.
///
/// Those posts are marked by an `x.com/i/broadcasts/…` URL, and sometimes by
/// the `*:broadcast` card. Either is enough — UserMedia often has the video
/// without the card, and a live one can have the card without media.
bool tweetHasBroadcast(TweetWithCard tweet) {
  for (final url in tweet.entities?.urls ?? const []) {
    if (broadcastIdIn(url.expandedUrl) != null) {
      return true;
    }
  }
  return isBroadcastCard(tweet.card);
}

bool isBroadcastCard(Map<String, dynamic>? card) {
  final name = card?['name'] as String?;
  return name != null && name.endsWith(':broadcast');
}

bool tweetHasVideoMedia(TweetWithCard tweet) {
  final media = tweet.extendedEntities?.media;
  if (media == null) {
    return false;
  }
  return media.any((item) => item.type == 'video');
}
