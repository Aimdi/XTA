import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
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

  if (!feeds.isFollowing(feed.id)) {
    await feeds.add(feed);
    await subscriptions.reloadSubscriptions();
  }
  if (!context.mounted) return;

  final user = subscriptionOf(feed);
  final groups = await groupsModel.listGroupsForUser(user.id);
  if (!context.mounted) return;

  await pickUserGroups(
    context,
    user: user,
    followed: true,
    groupsForUser: groups,
  );
}
