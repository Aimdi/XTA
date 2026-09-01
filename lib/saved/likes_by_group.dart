/// Breaking a list of liked posts down by the groups their authors belong to.
///
/// Likes are kept on the device and have only ever been one flat list. Sorting
/// them by the group the author is in is the same question the timeline
/// already answers — "which of my feeds is this from" — asked of things you
/// kept rather than things you were shown.
library;

import 'package:xta/database/entities.dart';

/// One heading in the broken-down likes list.
class LikesSection<T> {
  /// The group's id, or null for the section holding likes whose author is in
  /// no group at all.
  final String? groupId;
  final List<T> items;

  const LikesSection({required this.groupId, required this.items});

  bool get isUngrouped => groupId == null;
}

/// Splits [items] into one section per group, newest sections first by the
/// order of [groupIds], with anything ungrouped last.
///
/// A post whose author is in several groups appears under each of them: the
/// alternative is picking one arbitrarily, which hides it from the others.
/// A post with no author — a like kept from before the author was recorded —
/// counts as ungrouped rather than being dropped.
List<LikesSection<T>> likesByGroup<T>(
  List<T> items, {
  required String? Function(T item) authorOf,
  required List<SubscriptionGroupMember> members,
  required List<String> groupIds,
}) {
  final groupsOfAuthor = <String, Set<String>>{};
  for (final member in members) {
    groupsOfAuthor.putIfAbsent(member.profile, () => <String>{}).add(member.group);
  }

  final sections = <LikesSection<T>>[];

  for (final groupId in groupIds) {
    final inGroup =
        items.where((item) => groupsOfAuthor[authorOf(item)]?.contains(groupId) ?? false).toList(growable: false);
    if (inGroup.isNotEmpty) {
      sections.add(LikesSection(groupId: groupId, items: inGroup));
    }
  }

  final ungrouped = items.where((item) {
    final groups = groupsOfAuthor[authorOf(item)];
    return groups == null || groups.isEmpty || !groups.any(groupIds.contains);
  }).toList(growable: false);

  if (ungrouped.isNotEmpty) {
    sections.add(LikesSection(groupId: null, items: ungrouped));
  }

  return sections;
}
