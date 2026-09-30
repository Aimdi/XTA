import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_profile_screen.dart';
import 'package:xta/plugins/threads/threads_thread_screen.dart';
import 'package:xta/reading/reading_history_entry.dart';

ReadingHistoryEntry threadsHistoryEntry(ThreadsPost post) => ReadingHistoryEntry(
  source: pluginIdThreads,
  kind: ReadingHistoryKind.post,
  nativeId: post.id,
  url: post.url,
  author: post.authorName,
  text: post.text,
  extra: {'handle': post.handle},
);

ReadingHistoryEntry threadsProfileHistoryEntry({required String username, String? name, String? bio}) =>
    ReadingHistoryEntry(
      source: pluginIdThreads,
      kind: ReadingHistoryKind.profile,
      nativeId: username,
      url: 'https://www.threads.com/@${Uri.encodeComponent(username)}',
      author: name ?? username,
      text: bio ?? '',
      extra: {'handle': username},
    );

Future<void> openThreadsHistoryEntry(BuildContext context, ReadingHistoryEntry entry) => Navigator.push(
  context,
  MaterialPageRoute<void>(
    builder: (_) => entry.kind == ReadingHistoryKind.profile
        ? ThreadsProfileScreen(username: entry.nativeId)
        : ThreadsThreadScreen(
            post: ThreadsPost(
              id: entry.nativeId,
              handle: entry.extra['handle'] ?? '',
              authorName: entry.author,
              text: entry.text,
              url: entry.url,
            ),
          ),
  ),
);
