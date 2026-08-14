import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/plugin_feed_people.dart';

/// Original authors of posts, reposts, and quotes the reader has not followed.
///
/// Reposts come first: someone you already follow boosted a new account.
List<PluginFeedPerson> peopleToFollowFromBluesky({
  required List<BlueskyPost> posts,
  required bool Function(String handle) alreadyFollows,
  int cap = 12,
}) {
  final seen = <String>{};
  final fromReposts = <PluginFeedPerson>[];
  final others = <PluginFeedPerson>[];

  void consider({
    required String handle,
    required String name,
    String? avatarUrl,
    required bool fromRepost,
  }) {
    final key = handle.trim().toLowerCase();
    if (key.isEmpty || alreadyFollows(key) || !seen.add(key)) {
      return;
    }
    final person = PluginFeedPerson(
      handle: key,
      name: name.trim().isEmpty ? key : name,
      avatarUrl: avatarUrl,
      fromRepost: fromRepost,
    );
    (fromRepost ? fromReposts : others).add(person);
  }

  for (final post in posts) {
    consider(
      handle: post.handle,
      name: post.authorName,
      avatarUrl: post.avatarUrl,
      fromRepost: post.isRepost,
    );
    final quote = post.quotedPost;
    if (quote != null) {
      consider(
        handle: quote.handle,
        name: quote.authorName,
        avatarUrl: quote.avatarUrl,
        fromRepost: false,
      );
    }
  }
  return [...fromReposts, ...others].take(cap).toList(growable: false);
}
