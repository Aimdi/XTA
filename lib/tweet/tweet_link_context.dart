import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/links/link_post_context.dart';
import 'package:xta/plugins/x/x_plugin.dart' show pluginIdX;
import 'package:xta/status.dart';
import 'package:xta/tweet/tweet_footer.dart' show shareableTweetUrl;
import 'package:xta/tweet/tweet_open.dart';

/// The X post a card's link was opened from, for the in-app browser's bar.
///
/// On the post's own screen the post is what is underneath the browser, so
/// going back to it is just closing the browser; anywhere else it opens the
/// post the same way tapping the tile does.
LinkPostContext tweetLinkContext(BuildContext context, TweetWithCard tweet) {
  final target = openablePost(tweet);
  final onStatus = ModalRoute.of(context)?.settings.name == routeStatus;
  final reposts = tweet.retweetCount == null && tweet.quoteCount == null
      ? null
      : (tweet.retweetCount ?? 0) + (tweet.quoteCount ?? 0);
  return LinkPostContext(
    sourceId: pluginIdX,
    author: tweet.user?.name ?? tweet.user?.screenName ?? '',
    avatarUrl: tweet.user?.profileImageUrlHttps,
    replies: tweet.replyCount,
    reposts: reposts,
    likes: tweet.favoriteCount,
    postUrl: shareableTweetUrl(tweet, _shareBaseUrl(context)),
    openPost: target == null || onStatus
        ? null
        : () {
            if (!context.mounted) return;
            Navigator.pushNamed(
              context,
              routeStatus,
              arguments: StatusScreenArguments(
                id: target.id,
                username: target.username,
                tweetOpened: true,
                initialTweet: tweet,
              ),
            );
          },
  );
}

String _shareBaseUrl(BuildContext context) {
  final custom = PrefService.of(
    context,
    listen: false,
  ).get<String>(optionShareBaseUrl);
  return custom != null && custom.isNotEmpty ? custom : 'https://x.com';
}
