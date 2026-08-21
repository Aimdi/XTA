import 'package:xta/plugins/bluesky/bluesky_models.dart';

/// Stable identity of one row on the merged Bluesky home feed.
///
/// After [dedupeBlueskyPosts] this is the post URI. Kept as a helper so cards
/// and tests agree on what "the same row" means.
String blueskyFeedRowKey(BlueskyPost post) => post.uri;

/// One row per URI, first occurrence kept.
///
/// Merging author feeds (and a later gap-fill of the same first page) used to
/// paint the same post twice — Flutter then saw two [ValueKey]s with the same
/// URI and the cards swapped.
List<BlueskyPost> dedupeBlueskyPosts(Iterable<BlueskyPost> posts) {
  final seen = <String>{};
  return [
    for (final post in posts)
      if (post.uri.isNotEmpty && seen.add(post.uri)) post,
  ];
}

/// Newest first, URI as a tie-break so equal timestamps do not reshuffle.
int compareBlueskyFeed(BlueskyPost a, BlueskyPost b) {
  final byDate = (b.publishedAt ?? DateTime.fromMillisecondsSinceEpoch(0))
      .compareTo(a.publishedAt ?? DateTime.fromMillisecondsSinceEpoch(0));
  if (byDate != 0) {
    return byDate;
  }
  return a.uri.compareTo(b.uri);
}

List<BlueskyPost> stabilizeBlueskyFeed(Iterable<BlueskyPost> posts) {
  final next = dedupeBlueskyPosts(posts);
  next.sort(compareBlueskyFeed);
  return next;
}

/// Whether two painted pages are the same rows in the same order.
bool sameBlueskyFeedPage(List<BlueskyPost> a, List<BlueskyPost> b) {
  if (identical(a, b)) {
    return true;
  }
  if (a.length != b.length) {
    return false;
  }
  for (var i = 0; i < a.length; i++) {
    if (a[i].uri != b[i].uri) {
      return false;
    }
  }
  return true;
}

/// Whether [incoming] should replace a list already on screen.
///
/// A remount / TTL poll walks the per-account cache and first emits one
/// account's posts. Replacing the painted timeline with that prefix jumps
/// scroll to the top and rebuilds every card. New URIs (a real first page
/// change) still replace so they can appear.
bool blueskyFeedShouldReplace(
  List<BlueskyPost> current,
  List<BlueskyPost> incoming,
) {
  if (sameBlueskyFeedPage(current, incoming)) {
    return false;
  }
  if (current.isEmpty) {
    return incoming.isNotEmpty;
  }
  if (incoming.isEmpty) {
    return false;
  }
  if (incoming.length < current.length &&
      incoming.every((post) => current.any((row) => row.uri == post.uri))) {
    return false;
  }
  return true;
}

/// Fingerprint [ScopedBuilder] can compare so an unchanged page does not
/// rebuild the list.
String blueskyFeedDistinct(List<BlueskyPost> posts) =>
    posts.map((post) => post.uri).join('\n');
