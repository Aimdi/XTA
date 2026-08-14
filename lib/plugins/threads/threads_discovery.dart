import 'package:xta/plugins/plugin_feed_people.dart';
import 'package:xta/plugins/threads/threads_models.dart';

/// A public handle the reader can try when they do not know anyone yet.
const kThreadsStarterHandles = ['zuck', 'mosseri', 'meta'];

/// Original authors (and For You authors) the reader has not followed yet.
///
/// Reposts come first: the followed account boosted someone new.
List<PluginFeedPerson> peopleToFollowFromThreads({
  required List<ThreadsPost> posts,
  required bool Function(String handle) alreadyFollows,
  int cap = 12,
}) {
  final seen = <String>{};
  final fromReposts = <PluginFeedPerson>[];
  final others = <PluginFeedPerson>[];

  void consider(ThreadsPost post, {required bool fromRepost}) {
    final handle = post.handle.trim().toLowerCase();
    if (handle.isEmpty || alreadyFollows(handle) || !seen.add(handle)) {
      return;
    }
    final person = PluginFeedPerson(
      handle: handle,
      name: post.authorName.trim().isEmpty ? handle : post.authorName,
      avatarUrl: post.avatarUrl,
      fromRepost: fromRepost,
    );
    (fromRepost ? fromReposts : others).add(person);
  }

  for (final post in posts) {
    consider(post, fromRepost: post.isRepost);
  }
  return [...fromReposts, ...others].take(cap).toList(growable: false);
}
