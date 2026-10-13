/// What the reader has seen of Following: each site's newest post id there.
/// Ids rise with every upload on all four engines, so a post with a higher
/// id than any seen on the last visit came in since.
library;

import 'dart:convert';

import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_models.dart';

/// Each site's newest post id seen on Following, by host.
Map<String, int> booruSeenIds(BasePrefService prefs) {
  try {
    final decoded = jsonDecode(prefs.get<String>(optionPluginBooruFollowingSeen) ?? '{}');
    if (decoded is! Map) return const {};
    return {
      for (final MapEntry(:key, :value) in decoded.entries)
        if (key is String && value is int) key: value,
    };
  } catch (_) {
    return const {};
  }
}

/// Notes [posts] as seen: each site keeps the newest id seen on it.
Future<void> booruMarkSeen(BasePrefService prefs, Iterable<BooruPost> posts) async {
  final seen = booruSeenIds(prefs);
  final next = posts.fold(seen, _withNewest);
  if (!identical(next, seen)) await prefs.set(optionPluginBooruFollowingSeen, jsonEncode(next));
}

/// Whether [post] came in after the newest post [since] holds for its site.
/// Nothing is new on a site not visited before.
bool booruPostIsNew(Map<String, int> since, BooruPost post) => switch ((since[post.host], int.tryParse(post.id))) {
  (final seen?, final id?) => id > seen,
  _ => false,
};

Map<String, int> _withNewest(Map<String, int> seen, BooruPost post) => switch (int.tryParse(post.id)) {
  final id? when id > (seen[post.host] ?? -1) => {...seen, post.host: id},
  _ => seen,
};
