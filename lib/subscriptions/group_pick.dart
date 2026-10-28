import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';

/// How many groups Recently used remembers.
const _recentPickLimit = 12;

/// The groups in the order the picker opens with: those the account is in
/// first, because confirming one is the commonest reason to open it, then the
/// groups picked last, then the rest, each in [groups]' order, once, and only
/// while it exists.
List<String> groupPickOrder(
  List<SubscriptionGroup> groups, {
  required Iterable<String> selected,
  required List<String> recent,
}) {
  final chosen = selected.toSet();
  final ids = [for (final group in groups) group.id];
  final known = ids.toSet();
  return <String>{...ids.where(chosen.contains), ...recent.where(known.contains), ...ids}.toList();
}

/// The groups last added in the picker, newest first.
List<String> recentGroupPicks(BasePrefService prefs) => [
  for (final id in (prefs.get<String>(optionSubscriptionGroupsRecentPicks) ?? '').split(','))
    if (id.isNotEmpty) id,
];

/// Puts [added] at the front of Recently used, [_recentPickLimit] at most.
Future<void> rememberGroupPicks(BasePrefService prefs, Iterable<String> added) async {
  if (added.isEmpty) return;
  final recent = <String>{...added, ...recentGroupPicks(prefs)}.take(_recentPickLimit);
  await prefs.set(optionSubscriptionGroupsRecentPicks, recent.join(','));
}

/// The groups ticked in the picker, never the list it was opened with.
class GroupMembershipStore extends Store<Set<String>> {
  GroupMembershipStore(Iterable<String> selected) : super(Set.unmodifiable(selected));

  void toggle(String id) {
    final next = state.toSet();
    if (!next.remove(id)) next.add(id);
    update(Set.unmodifiable(next));
  }

  void add(String id) => update(Set.unmodifiable({...state, id}));
}
