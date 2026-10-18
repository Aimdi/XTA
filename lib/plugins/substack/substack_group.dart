import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/user.dart';

/// Follows [publication] if needed, then opens the group-membership sheet.
///
/// Same path Bluesky / Threads / Mastodon profiles use — Substack surfaces
/// used to only offer Follow, which left publications out of groups unless
/// you dug through the People list or the group edit sheet.
Future<void> addSubstackPublicationToGroup(
  BuildContext context,
  SubstackPublication publication,
) async {
  final pubs = context.read<SubstackPublicationsStore>();
  final subscriptions = context.read<SubscriptionsModel>();
  final groupsModel = context.read<GroupsModel>();

  if (pubs.followOf(publication) == null) {
    await pubs.add(publication);
    await subscriptions.reloadSubscriptions();
  }
  if (!context.mounted) return;

  final user = subscriptionOf(pubs.followOf(publication) ?? publication);
  final groups = await groupsModel.listGroupsForUser(user.id);
  if (!context.mounted) return;

  await pickUserGroups(
    context,
    user: user,
    followed: true,
    groupsForUser: groups,
  );
}
