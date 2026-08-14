import 'package:xta/plugins/instagram/instagram_models.dart';
import 'package:xta/plugins/instagram/instagram_parse.dart';

/// Guests cannot people-search; a typed @handle should open the profile.
bool instagramSearchOpensHandle({
  required bool hasSession,
  required String query,
}) => !hasSession && normaliseInstagramHandle(query) != null;

/// Public handles a guest For You can read without knowing anyone yet.
const kInstagramDiscoverHandles = ['instagram', 'natgeo', 'nasa'];

/// Round-robin posts from several public profiles into one discovery feed.
List<InstagramPost> interleaveInstagramDiscover(
  Iterable<List<InstagramPost>> pages,
) {
  final lists = [for (final page in pages) page];
  final seen = <String>{};
  final out = <InstagramPost>[];
  var index = 0;
  var added = true;
  while (added) {
    added = false;
    for (final page in lists) {
      if (index >= page.length) continue;
      final post = page[index];
      if (!seen.add(post.id)) continue;
      out.add(post);
      added = true;
    }
    index++;
  }
  return out;
}

/// Authors in a For You page the reader does not already follow.
List<InstagramAuthor> peopleToFollowFromInstagram({
  required List<InstagramPost> posts,
  required bool Function(String handle) alreadyFollows,
  int cap = 12,
}) {
  final seen = <String>{};
  final people = <InstagramAuthor>[];
  for (final post in posts) {
    final handle = post.author.username.trim().toLowerCase();
    if (handle.isEmpty || alreadyFollows(handle) || !seen.add(handle)) {
      continue;
    }
    people.add(post.author);
    if (people.length >= cap) break;
  }
  return people;
}
