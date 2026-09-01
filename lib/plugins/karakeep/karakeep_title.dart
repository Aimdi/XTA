import 'package:xta/client/client.dart';

/// Title Karakeep should show for a saved post: the author, then as much of the
/// post as fits on one line, so a saved bookmark is recognisable in the list
/// even before Karakeep's crawler fills in its own metadata.
String karakeepTitleFor(TweetWithCard tweet, String tweetText) {
  final author = tweet.user?.name?.trim();
  final handle = tweet.user?.screenName?.trim();
  final byline = author != null && author.isNotEmpty
      ? (handle != null && handle.isNotEmpty ? '$author (@$handle)' : author)
      : (handle != null && handle.isNotEmpty ? '@$handle' : null);

  final text = tweetText.replaceAll(RegExp(r'\s+'), ' ').trim();
  if (byline == null) {
    return text;
  }
  return text.isEmpty ? byline : '$byline: $text';
}
