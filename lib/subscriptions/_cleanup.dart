import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile_model.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/cleanup_dialog.dart';

// Error codes X uses for accounts that definitively no longer exist:
// 34/50 = not found, 63 = suspended, -1 = unavailable for another reason.
const _goneCodes = {34, 50, 63, -1};

enum _LookupResult { exists, gone, suspended, unreachable, rateLimited }

class BrokenSubscriptionsDialog extends StatelessWidget {
  const BrokenSubscriptionsDialog({super.key});

  Future<_LookupResult> _lookup(Future<Profile> Function() fetch) async {
    try {
      await fetch();
      return _LookupResult.exists;
    } on TwitterError catch (e) {
      if (e.code == 63) {
        return _LookupResult.suspended;
      }
      return _goneCodes.contains(e.code) ? _LookupResult.gone : _LookupResult.unreachable;
    } on RateLimitedException {
      return _LookupResult.rateLimited;
    } catch (_) {
      return _LookupResult.unreachable;
    }
  }

  Future<CleanupCheck> _check(SubscriptionsModel model, UserSubscription user) async {
    // The app opens profiles by screen name, so that lookup is the reference
    // for whether a subscription still works.
    final byName = await _lookup(() => Twitter.getProfileByScreenName(user.screenName));
    switch (byName) {
      case _LookupResult.exists:
        return CleanupOk();
      case _LookupResult.suspended:
        return CleanupBroken(reason: L10n.current.account_suspended);
      case _LookupResult.unreachable:
        return CleanupUnreachable();
      case _LookupResult.rateLimited:
        return CleanupRateLimited();
      case _LookupResult.gone:
        break;
    }

    // The screen name is gone; the id tells a rename (repairable) apart from a
    // genuinely deleted account.
    try {
      final profile = await Twitter.getProfileById(user.id);
      await model.repairSubscription(user, profile.user);
      return CleanupRepaired('@${user.screenName} → @${profile.user.screenName}');
    } on TwitterError catch (e) {
      if (e.code == 63) {
        return CleanupBroken(reason: L10n.current.account_suspended);
      }
      if (_goneCodes.contains(e.code)) {
        return CleanupBroken(reason: L10n.current.user_not_found);
      }
      return CleanupUnreachable();
    } on RateLimitedException {
      return CleanupRateLimited();
    } catch (_) {
      return CleanupBroken(reason: L10n.current.user_not_found);
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
