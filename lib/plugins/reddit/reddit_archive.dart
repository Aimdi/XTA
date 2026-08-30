import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/saved/folder_picker.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/tweet/tweet_footer.dart';

/// Marker stored inside Archiv blobs so a Reddit post is not parsed as a tweet.
const redditArchiveKind = 'reddit';

const redditArchiveIdPrefix = 'reddit:';

/// Id used in `saved_tweet` / `liked_tweet` so a Reddit post cannot collide
/// with an X snowflake.
String redditArchiveId(String postId) => '$redditArchiveIdPrefix$postId';

bool isRedditArchiveId(String id) => id.startsWith(redditArchiveIdPrefix);

/// Snapshot filed in Archiv. `SavedContentIndex` recognises [redditArchiveKind].
Map<String, dynamic> redditArchiveBlob(RedditPost post) => {
      'xtaPlugin': redditArchiveKind,
      'post': post.toJson(),
    };

RedditPost? redditPostFromArchive(Object? decoded) {
  if (decoded is! Map) {
    return null;
  }
  final map = Map<String, dynamic>.from(decoded);
  if (map['xtaPlugin'] != redditArchiveKind) {
    return null;
  }
  return RedditPost.fromSnapshot(map['post']);
}

bool isRedditArchiveBlob(String? content) {
  if (content == null || content.isEmpty) {
    return false;
  }
  try {
    return redditPostFromArchive(jsonDecode(content)) != null;
  } catch (_) {
    return false;
  }
}

String redditArchiveHaystack(RedditPost post) => [
      post.title,
      post.selfText,
      post.subreddit,
      post.author,
    ].whereType<String>().join('\n').toLowerCase();

Future<void> fileRedditPost(BuildContext context, RedditPost post) async {
  await fileSavedTweet(
    context,
    tweetId: redditArchiveId(post.id),
    userId: post.subreddit,
    content: redditArchiveBlob(post),
    folderId: rememberedSaveFolder(PrefService.of(context, listen: false)),
  );
  if (context.mounted) {
    await _mirrorRedditSaved(context, post, filed: true);
  }
}

Future<void> unfileRedditPost(BuildContext context, RedditPost post) async {
  await context.read<SavedTweetModel>().deleteSavedTweet(redditArchiveId(post.id));
  if (context.mounted) {
    await _mirrorRedditSaved(context, post, filed: false);
  }
}

Future<void> pickRedditPostFolder(BuildContext context, RedditPost post) async {
  await showSaveToFolderSheet(
    context,
    tweetId: redditArchiveId(post.id),
    userId: post.subreddit,
    content: redditArchiveBlob(post),
  );
  if (!context.mounted) {
    return;
  }
  final filed = context.read<SavedTweetModel>().isSaved(redditArchiveId(post.id));
  await _mirrorRedditSaved(context, post, filed: filed);
}

/// Files a local Reddit upvote into Archiv → Gefällt mir, and removes it
/// when the arrow is undone. Reddit is never told.
Future<void> syncRedditLikeToArchive(
  BuildContext context,
  RedditPost post, {
  required bool upvoted,
}) async {
  LikedTweetModel liked;
  try {
    liked = context.read<LikedTweetModel>();
  } on ProviderNotFoundException {
    return;
  }
  if (upvoted) {
    await liked.likeTweet(
      redditArchiveId(post.id),
      post.subreddit,
      redditArchiveBlob(post),
    );
    if (context.mounted) {
      maybeShowLikeToast(context);
    }
  } else {
    await liked.unlikeTweet(redditArchiveId(post.id));
  }
}

Future<void> _mirrorRedditSaved(
  BuildContext context,
  RedditPost post, {
  required bool filed,
}) async {
  RedditSavedStore store;
  try {
    store = context.read<RedditSavedStore>();
  } on ProviderNotFoundException {
    return;
  }
  if (filed == store.isSaved(post)) {
    return;
  }
  await store.toggle(post);
}
