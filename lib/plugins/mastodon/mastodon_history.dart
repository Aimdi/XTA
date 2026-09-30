import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_thread_screen.dart';
import 'package:xta/reading/reading_history_entry.dart';

ReadingHistoryEntry mastodonHistoryEntry(MastodonPost post) => ReadingHistoryEntry(
  source: pluginIdMastodon,
  kind: ReadingHistoryKind.post,
  nativeId: post.url,
  url: post.url,
  author: post.authorName,
  text: post.text,
  extra: {'handle': post.acct, 'id': post.id},
);

ReadingHistoryEntry mastodonProfileHistoryEntry({required String acct, String? name, String? bio, String? url}) =>
    ReadingHistoryEntry(
      source: pluginIdMastodon,
      kind: ReadingHistoryKind.profile,
      nativeId: acct,
      url: url,
      author: name ?? acct,
      text: bio ?? '',
      extra: {'handle': acct},
    );

/// Reopens the thread from what history kept; the thread screen fetches the rest.
Future<void> openMastodonHistoryEntry(BuildContext context, ReadingHistoryEntry entry) => Navigator.push(
  context,
  MaterialPageRoute<void>(
    builder: (_) => entry.kind == ReadingHistoryKind.profile
        ? MastodonProfileScreen(acct: entry.nativeId)
        : MastodonThreadScreen(
            post: MastodonPost(
              id: entry.extra['id'] ?? '',
              acct: entry.extra['handle'] ?? '',
              authorName: entry.author,
              text: entry.text,
              url: entry.url ?? entry.nativeId,
            ),
          ),
  ),
);
