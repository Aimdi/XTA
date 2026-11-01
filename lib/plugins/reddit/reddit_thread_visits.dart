/// When the reader last opened each thread, so a comment written since can
/// say it is new. Kept on this device only, for the most recent threads.
library;

import 'dart:convert';

import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/reddit/reddit_comments.dart';

/// How many threads keep their last visit; older ones are forgotten first.
const kRedditThreadVisitsKept = 200;

/// Each thread's last visit, by permalink, in milliseconds since the epoch.
Map<String, int> redditThreadVisits(BasePrefService prefs) {
  try {
    final decoded = jsonDecode(prefs.get<String>(optionPluginRedditThreadVisits) ?? '{}');
    if (decoded is! Map) return const {};
    return {
      for (final MapEntry(:key, :value) in decoded.entries)
        if (key is String && value is int) key: value,
    };
  } catch (_) {
    return const {};
  }
}

/// When [thread] was last opened, or null when it never was.
DateTime? redditLastThreadVisit(BasePrefService prefs, String thread) {
  final last = redditThreadVisits(prefs)[redditThreadKey(thread)];
  return last == null ? null : DateTime.fromMillisecondsSinceEpoch(last);
}

/// Notes that [thread] was opened at [now], forgetting the oldest visits
/// beyond [kRedditThreadVisitsKept].
Future<void> redditRecordThreadVisit(BasePrefService prefs, String thread, DateTime now) async {
  final visits = {...redditThreadVisits(prefs), redditThreadKey(thread): now.millisecondsSinceEpoch};
  await prefs.set(optionPluginRedditThreadVisits, jsonEncode(redditKeepRecentVisits(visits)));
}

/// [visits] without the oldest, once there are more than [keep].
Map<String, int> redditKeepRecentVisits(Map<String, int> visits, {int keep = kRedditThreadVisitsKept}) {
  if (visits.length <= keep) return visits;
  final newest = visits.entries.toList()..sort((a, b) => b.value.compareTo(a.value));
  return Map.fromEntries(newest.take(keep));
}

/// One key per thread however its permalink was written.
String redditThreadKey(String permalink) =>
    permalink.endsWith('/') ? permalink.substring(0, permalink.length - 1) : permalink;

/// Whether [comment] was written after [lastVisit]. Nothing is new on a first
/// visit.
bool redditCommentIsNew(RedditComment comment, DateTime? lastVisit) {
  final created = comment.createdAt;
  return lastVisit != null && created != null && created.isAfter(lastVisit);
}
