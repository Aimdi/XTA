import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/user.dart';

/// Follows [feed] if needed, then opens the group-membership sheet.
Future<void> addRssFeedToGroup(BuildContext context, RssFeed feed) async {
  final feeds = context.read<RssFeedsStore>();
  final subscriptions = context.read<SubscriptionsModel>();
  final groupsModel = context.read<GroupsModel>();
  final messenger = ScaffoldMessenger.maybeOf(context);
  final failed = L10n.of(context).plugin_rss_save_failed;

  var followed = feeds.followedFeed(feed.feedUrl);
  if (followed == null) {
    followed = await feeds.add(feed);
    if (followed == null) {
      messenger?.showSnackBar(SnackBar(content: Text(failed)));
      return;
    }
    await subscriptions.reloadSubscriptions();
  }
  if (!context.mounted) return;

  final user = subscriptionOf(followed);
  final groups = await groupsModel.listGroupsForUser(user.id);
  if (!context.mounted) return;

  await pickUserGroups(
    context,
    user: user,
    followed: true,
    groupsForUser: groups,
  );
}
