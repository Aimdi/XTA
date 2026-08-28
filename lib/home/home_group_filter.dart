import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/group_tree.dart';
import 'package:xta/home/home_account_filter.dart';

/// Profile ids that belong to a disabled group, including nested children of
/// those groups so turning off a parent hides the whole branch.
Set<String> profileIdsExcludedByGroups({
  required Iterable<SubscriptionGroupMember> members,
  required Set<String> disabledGroupIds,
  Map<String, String?> parentOf = const {},
}) {
  if (disabledGroupIds.isEmpty) {
    return const {};
  }
  final expanded = {
    for (final id in disabledGroupIds) ...groupAndDescendants(id, parentOf),
  };
  return {
    for (final member in members)
      if (expanded.contains(member.group)) member.profile,
  };
}

bool subscriptionAllowedInFollowing(
  Subscription subscription,
  Set<String> excludedProfileIds,
) {
  return subscription.inFeed && !excludedProfileIds.contains(subscription.id);
}

class HomeGroupFilterStore extends Store<Set<String>> {
  final BasePrefService prefs;

  HomeGroupFilterStore(this.prefs)
    : super(
        homeFeedDisabledIdsFromPrefs(
          prefs.get(optionHomeFeedDisabledGroupIds),
        ).toSet(),
      );

  Future<void> reload() async {
    await execute(() async {
      return homeFeedDisabledIdsFromPrefs(
        prefs.get(optionHomeFeedDisabledGroupIds),
      ).toSet();
    });
  }

  Future<void> setEnabled(String groupId, bool enabled) async {
    await execute(() async {
      final next = Set<String>.from(state);
      if (enabled) {
        next.remove(groupId);
      } else {
        next.add(groupId);
      }
      await prefs.set(
        optionHomeFeedDisabledGroupIds,
        homeFeedDisabledIdsToPrefs(next),
      );
      return next;
    });
  }
}
