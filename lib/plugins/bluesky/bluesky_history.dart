import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_thread_screen.dart';
import 'package:xta/reading/reading_history_entry.dart';

ReadingHistoryEntry blueskyHistoryEntry(BlueskyPost post) => ReadingHistoryEntry(
  source: pluginIdBluesky,
  kind: ReadingHistoryKind.post,
  nativeId: post.uri,
  url: post.url,
  author: post.authorName,
  text: post.text,
  extra: {'handle': post.handle, 'did': post.did, 'cid': post.cid},
);

ReadingHistoryEntry blueskyProfileHistoryEntry({required String actor, String? handle, String? name, String? bio}) =>
    ReadingHistoryEntry(
      source: pluginIdBluesky,
      kind: ReadingHistoryKind.profile,
      nativeId: actor,
      url: 'https://bsky.app/profile/${Uri.encodeComponent(handle ?? actor)}',
      author: name ?? handle ?? actor,
      text: bio ?? '',
      extra: {'handle': ?handle},
    );

/// Reopens the thread from what history kept; the thread screen fetches the rest by its URI.
Future<void> openBlueskyHistoryEntry(BuildContext context, ReadingHistoryEntry entry) => Navigator.push(
  context,
  MaterialPageRoute<void>(
    builder: (_) => entry.kind == ReadingHistoryKind.profile
        ? BlueskyProfileScreen(actor: entry.nativeId)
        : BlueskyThreadScreen(
            post: BlueskyPost(
              uri: entry.nativeId,
              cid: entry.extra['cid'] ?? '',
              handle: entry.extra['handle'] ?? '',
              did: entry.extra['did'] ?? '',
              authorName: entry.author,
              text: entry.text,
              url: entry.url ?? '',
            ),
          ),
  ),
);
