import 'package:xta/database/entities.dart';

/// The X search that reads one chunk of a group's members: their posts, or a saved search's words, joined by OR
/// under X's ~512 character query limit, then the group's replies and reposts settings.
String groupSearchQuery(List<Subscription> members, {required bool includeReplies, required bool includeRetweets}) {
  var query = '';
  for (final member in members) {
    final part = switch (member) {
      UserSubscription(:final screenName) => 'from:$screenName',
      SearchSubscription(:final id) => '"$id"',
      _ => '',
    };
    if (part.isEmpty) continue;
    if (query.length + part.length < 512) {
      query = query.isEmpty ? part : '$query OR $part';
    } else {
      assert(false, 'a chunk of $members does not fit one query');
      query = part;
    }
  }
  if (!includeReplies) query += ' -filter:replies ';
  return query + (includeRetweets ? ' include:nativeretweets ' : ' -filter:retweets ');
}
