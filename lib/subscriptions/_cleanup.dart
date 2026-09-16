import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/subscriptions/subscription_health.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile_model.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/cleanup_dialog.dart';

class BrokenSubscriptionsDialog extends StatelessWidget {
  const BrokenSubscriptionsDialog({super.key});

  Future<CleanupCheck> _check(SubscriptionsModel model, UserSubscription user) async {
    final health = await checkSubscriptionHealth<Profile>(
      byName: () => Twitter.getProfileByScreenName(user.screenName),
      byId: () => Twitter.getProfileById(user.id),
    );
    switch (health.kind) {
      case SubscriptionHealthKind.exists:
        return CleanupOk();
      case SubscriptionHealthKind.renamed:
        try {
          await model.repairSubscription(user, health.profile!.user);
          return CleanupRepaired('@${user.screenName} → @${health.profile!.user.screenName}');
        } catch (_) {
          return CleanupUnreachable();
        }
      case SubscriptionHealthKind.missing:
        return CleanupBroken(reason: L10n.current.user_not_found);
      case SubscriptionHealthKind.suspended:
        return CleanupBroken(reason: L10n.current.account_suspended);
      case SubscriptionHealthKind.rateLimited:
        return CleanupRateLimited();
      case SubscriptionHealthKind.unreachable:
        return CleanupUnreachable();
    }
  }

  @override
  Widget build(BuildContext context) {
    final model = context.read<SubscriptionsModel>();

    return CleanupScanDialog<UserSubscription>(
      title: L10n.of(context).find_broken_subscriptions,
      checkingLabel: L10n.of(context).checking_subscriptions,
      foundMessage: L10n.of(context).broken_subscriptions_found,
      noneFoundMessage: L10n.of(context).no_broken_subscriptions_found,
      unreachableMessage: L10n.of(context).some_subscriptions_could_not_be_checked,
      repairedMessage: L10n.of(context).renamed_subscriptions_updated,
      items: model.state.whereType<UserSubscription>().toList(),
      check: (user) => _check(model, user),
      itemLabel: (user) => '@${user.screenName}',
      onDelete: (broken) => model.removeSubscriptions(broken),
      onScanDone: (repairedCount) async {
        if (repairedCount > 0) {
          await model.reloadSubscriptions();
        }
      },
    );
  }
}
