import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_archive.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/ui/cleanup_dialog.dart';

class BrokenBookmarksDialog extends StatelessWidget {
  const BrokenBookmarksDialog({super.key});

  // A post still exists when its detail response contains the post itself;
  // deleted posts come back empty or as a tombstone entry.
  Future<CleanupCheck> _check(SavedTweet saved) async {
    if (isRedditArchiveBlob(saved.content) || isRedditArchiveId(saved.id)) {
      return CleanupOk();
    }
    try {
      final status = await Twitter.getTweet(saved.id);
      final exists = status.chains
          .any((chain) => chain.tweets.any((tweet) => tweet.idStr == saved.id && tweet.isTombstone != true));
      return exists ? CleanupOk() : CleanupBroken();
    } on RateLimitedException {
      return CleanupRateLimited();
    } catch (_) {
      return CleanupUnreachable();
    }
  }

  String _label(SavedTweet saved) {
    try {
      final json = jsonDecode(saved.content ?? '{}');
      final reddit = redditPostFromArchive(json);
      if (reddit != null) {
        return 'r/${reddit.subreddit}: ${reddit.title}';
      }
      final screenName = json['user']?['screen_name'] as String?;
      final text = (json['full_text'] ?? json['text'] ?? '') as String;
      final snippet = text.length > 50 ? '${text.substring(0, 50)}…' : text;
      if (screenName != null) {
        return snippet.isEmpty ? '@$screenName' : '@$screenName: $snippet';
      }
      return snippet.isEmpty ? saved.id : snippet;
    } catch (_) {
      return saved.id;
    }
  }

  @override
  Widget build(BuildContext context) {
    final model = context.read<SavedTweetModel>();

    return CleanupScanDialog<SavedTweet>(
      title: L10n.of(context).find_broken_bookmarks,
      checkingLabel: L10n.of(context).checking_bookmarks,
      foundMessage: L10n.of(context).broken_bookmarks_found,
      noneFoundMessage: L10n.of(context).no_broken_bookmarks_found,
      unreachableMessage: L10n.of(context).some_bookmarks_could_not_be_checked,
      items: model.state.toList(),
      check: _check,
      itemLabel: _label,
      onDelete: (broken) => model.removeSavedTweets(broken.map((e) => e.id).toList()),
    );
  }
}
