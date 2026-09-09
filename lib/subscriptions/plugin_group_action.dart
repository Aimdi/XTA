import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/group_membership_sheet.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/errors.dart';

/// The picker commits the local subscription only after the reader saves.
Future<void> editPluginAccountGroups(
  BuildContext context, {
  required Subscription subscription,
  required Future<void> Function() ensureFollowed,
}) async {
  final groups = context.read<GroupsModel>();
  final subscriptions = context.read<SubscriptionsModel>();
  try {
    await groups.reloadGroups(notifyReload: false);
    final selected = await groups.listGroupsForUser(subscription.id);
    if (!context.mounted) return;
    final chosen = await showGroupMembershipSheet(
      context, groups: groups.state, selected: selected,
    );
    if (chosen == null) return;
    if (chosen.isNotEmpty) {
      await ensureFollowed();
      await subscriptions.reloadSubscriptions();
      if (!subscriptions.state.any((item) => item.id == subscription.id)) {
        throw StateError('The local account could not be saved.');
      }
    }
    await groups.saveUserGroupMembership(subscription.id, chosen);
    await subscriptions.reloadSubscriptions();
  } catch (error, stack) {
    if (context.mounted) {
      await showDialog<void>(context: context, builder: (_) => AlertDialog(
        content: SingleChildScrollView(child: FullPageErrorWidget(
          error: error, stackTrace: stack,
          prefix: L10n.of(context).oops_something_went_wrong,
        )),
      ));
    }
  }
}
