/// Who a group's members are, for the two ways a group feed is fetched.
///
/// X accounts go into a search query. Everyone else is fetched by the plugin
/// that owns them and slotted into the same timeline as dated cards. Mixing the
/// two in one query is what dropped Reddit and Substack posts — or worse,
/// searched `from:flutter` on X and rendered an empty tweet.
library;

import 'package:xta/database/entities.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/subscription_source.dart';

typedef GroupMemberSplit = ({
  List<Subscription> xMembers,
  Map<SubscriptionSource, List<Subscription>> pluginMembers,
});

/// Splits [members] into who X can search for and who each plugin fetches.
///
/// Named rather than subtracted: what X can search for is a closed set, so the
/// next plugin whose members join a group cannot silently end up in a search
/// query by not being on a list of exclusions.
GroupMemberSplit splitGroupMembers(Iterable<Subscription> members) {
  final pluginMembers = {
    for (final source in subscriptionSources)
      if (members.where(source.owns).toList(growable: false) case final owned
          when owned.isNotEmpty)
        source: owned,
  };
  final xMembers = [
    for (final member in members)
      if (member is UserSubscription || member is SearchSubscription) member,
  ];
  return (xMembers: xMembers, pluginMembers: pluginMembers);
}

/// The ids one plugin source is asked for from a split.
List<String> pluginMemberIds(
  Map<SubscriptionSource, List<Subscription>> pluginMembers,
  SubscriptionSource source,
) =>
    pluginMembers[source]?.map((e) => e.id).toList(growable: false) ?? const [];
