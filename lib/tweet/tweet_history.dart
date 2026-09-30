import 'package:flutter/material.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/x/x_plugin.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/reading/reading_history_entry.dart';
import 'package:xta/status.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/utils/rich_text.dart';

/// The post a tile shows: a repost is recorded as the post it carries.
ReadingHistoryEntry tweetHistoryEntry(TweetWithCard tweet) {
  final shown = tweet.retweetedStatusWithCard ?? tweet;
  final user = shown.user;
  final text = unescapeHtml(shown.noteText ?? shown.fullText ?? shown.text ?? '');
  return ReadingHistoryEntry(
    source: pluginIdX,
    kind: ReadingHistoryKind.post,
    nativeId: shown.idStr ?? '',
    url: shareableTweetUrl(shown, 'https://x.com'),
    author: user?.name ?? user?.screenName ?? '',
    text: shareableTweetText(shown, text),
    extra: {'handle': ?user?.screenName},
  );
}

ReadingHistoryEntry xProfileHistoryEntry({required String screenName, String? id, String? name, String? bio}) =>
    ReadingHistoryEntry(
      source: pluginIdX,
      kind: ReadingHistoryKind.profile,
      nativeId: screenName,
      url: 'https://x.com/${Uri.encodeComponent(screenName)}',
      author: name ?? screenName,
      text: bio ?? '',
      extra: {'handle': screenName, 'id': ?id},
    );

Future<void> openTweetHistoryEntry(BuildContext context, ReadingHistoryEntry entry) async {
  final handle = entry.extra['handle'];
  if (entry.kind == ReadingHistoryKind.profile) {
    await Navigator.pushNamed(
      context,
      routeProfile,
      arguments: ProfileScreenArguments(entry.extra['id'], handle, null),
    );
    return;
  }
  await Navigator.pushNamed(
    context,
    routeStatus,
    arguments: StatusScreenArguments(id: entry.nativeId, username: handle),
  );
}
