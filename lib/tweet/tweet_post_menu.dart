import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/plugins/plugin_post_actions.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/tweet/quote_actions.dart';
import 'package:xta/tweet/tweet_action_style.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/tweet/tweet_open.dart';
import 'package:xta/user.dart';

/// The post's ⋯ menu, at the top-right of the header where X keeps it.
///
/// Its glyph is centred in a 48dp target so it lines up with the share glyph
/// at the end of the footer; translate, when shown, sits right before it.
class TweetPostMenuButton extends StatelessWidget {
  final TweetWithCard tweet;
  final String shareBaseUrl;

  const TweetPostMenuButton({super.key, required this.tweet, required this.shareBaseUrl});

  @override
  Widget build(BuildContext context) {
    final tweetId = openablePost(tweet)?.id;
    final url = shareableTweetUrl(tweet, shareBaseUrl);
    return tweetActionIconButton(
      context,
      icon: Icons.more_horiz,
      color: tweetFooterButtonsColorOf(context),
      style: tweetActionButtonStyle(tweetActionGlyphPadding(alignTop: true)),
      tooltip: L10n.of(context).more_info,
      onPressed: tweetId == null || url == null ? null : () => _showActions(context, tweetId, url),
    );
  }

  void _showActions(BuildContext context, String tweetId, String url) {
    showPluginPostActions(
      context,
      post: PluginPostArchive(id: tweetId, userId: tweet.user?.idStr ?? '', content: tweet.toJson()),
      url: url,
      onGroup: _canFileAuthor ? () => _fileAuthorInGroups(context) : null,
      onQuotes: () => openQuotesAndRetweets(context, tweetId: tweetId),
      onReposts: () => openQuotesAndRetweets(context, tweetId: tweetId, initialTab: 1),
    );
  }

  bool get _canFileAuthor => tweet.user?.idStr?.isNotEmpty == true && openableProfile(tweet.user) != null;

  Future<void> _fileAuthorInGroups(BuildContext context) async {
    final author = tweet.user!;
    final user = UserSubscription(
      id: author.idStr!,
      screenName: author.screenName!,
      name: author.name ?? author.screenName!,
      profileImageUrlHttps: author.profileImageUrlHttps,
      verified: author.verified ?? false,
      createdAt: author.createdAt ?? DateTime.now(),
      inFeed: true,
    );
    final groups = await context.read<GroupsModel>().listGroupsForUser(user.id);
    if (!context.mounted) return;
    await pickUserGroups(
      context,
      user: user,
      followed: context.read<SubscriptionsModel>().state.any((e) => e.id == user.id),
      groupsForUser: groups,
    );
  }
}
