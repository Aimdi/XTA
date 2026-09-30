import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_post_sheet.dart';
import 'package:xta/plugins/reddit/reddit_thread_screen.dart';
import 'package:xta/reading/reading_history_entry.dart';

ReadingHistoryEntry redditHistoryEntry(RedditPost post) => ReadingHistoryEntry(
  source: pluginIdReddit,
  kind: ReadingHistoryKind.post,
  nativeId: post.id,
  url: redditPostUrl(post),
  author: post.author ?? '',
  title: post.displayTitle,
  text: post.displaySelfText ?? '',
  extra: {'subreddit': post.subreddit, 'permalink': post.permalink},
);

Future<void> openRedditHistoryEntry(BuildContext context, ReadingHistoryEntry entry) => Navigator.push(
  context,
  MaterialPageRoute<void>(
    builder: (_) => RedditThreadScreen(
      post: RedditPost(
        id: entry.nativeId,
        title: entry.title,
        subreddit: entry.extra['subreddit'] ?? '',
        permalink: entry.extra['permalink'] ?? '',
        author: entry.author.isEmpty ? null : entry.author,
        isSelf: entry.text.isNotEmpty,
        selfText: entry.text.isEmpty ? null : entry.text,
      ),
    ),
  ),
);
