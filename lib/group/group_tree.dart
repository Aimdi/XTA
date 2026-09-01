/// Nesting for subscription groups.
///
/// A group may sit inside another, the way a browser nests tabs in a tab group.
/// The parent shows its children instead of standing beside them on the board,
/// and its feed is the union of its own members and everything nested in it.
///
/// These are pure functions over a `child id -> parent id` map so the rules —
/// especially the ones that stop a group being nested inside itself — can be
/// tested without a database.
library;

/// Every id whose members belong to [id]'s feed: itself and everything nested
/// inside it, however deep.
///
/// A cycle in the stored data cannot hang this: an id already visited is not
/// followed a second time.
Set<String> groupAndDescendants(String id, Map<String, String?> parentOf) {
  final found = <String>{id};
  final pending = <String>[id];

  while (pending.isNotEmpty) {
    final current = pending.removeLast();
    for (final child in childrenOf(current, parentOf)) {
      if (found.add(child)) {
        pending.add(child);
      }
    }
  }

  return found;
}

/// The groups nested directly inside [id].
List<String> childrenOf(String id, Map<String, String?> parentOf) => parentOf
    .entries
    .where((e) => e.value == id)
    .map((e) => e.key)
    .toList(growable: false);

/// The groups that stand on their own, which is what a board shows.
///
/// A group whose parent has been deleted counts as top level rather than
/// disappearing — otherwise it would still exist but be unreachable.
List<String> topLevelGroups(
  Iterable<String> ids,
  Map<String, String?> parentOf,
) {
  final present = ids.toSet();

  return ids
      .where((id) {
        final parent = parentOf[id];
        return parent == null || parent == id || !present.contains(parent);
      })
      .toList(growable: false);
}

/// [ids] with every group placed directly under its parent, parents first.
///
/// Nesting used to hide a child from the board entirely, which made "put inside
/// group" look like it had deleted the group: it vanished and there was nothing
/// to open. A child belongs *under* its parent, not instead of it.
///
/// Order within a level is the order given, so whatever sort or manual
/// arrangement the board is using still holds.
List<String> groupsInTreeOrder(
  Iterable<String> ids,
  Map<String, String?> parentOf,
) {
  final present = ids.toList(growable: false);
  final ordered = <String>[];
  final placed = <String>{};

  void place(String id) {
    if (!placed.add(id)) {
      return;
    }
    ordered.add(id);
    for (final child in present.where((c) => parentOf[c] == id && c != id)) {
      place(child);
    }
  }

  for (final id in topLevelGroups(present, parentOf)) {
    place(id);
  }
  // A group inside a cycle belongs to no top level and would otherwise be lost.
  for (final id in present) {
    place(id);
  }

  return ordered;
}

/// Whether nesting [child] inside [parent] would close a loop.
///
/// True when they are the same group, or when [parent] is already somewhere
/// inside [child] — either would make a group contain itself, and a feed that
/// never finished resolving.
bool wouldNestInsideItself(
  String child,
  String parent,
  Map<String, String?> parentOf,
) {
  if (child == parent) {
    return true;
  }

  final seen = <String>{};
  String? current = parent;
  while (current != null && seen.add(current)) {
    if (current == child) {
      return true;
    }
    current = parentOf[current];
  }

  return false;
}

/// How deep [id] sits, counting from zero at the top.
///
/// Used to indent a nested group, and bounded by the same visited-set guard so
/// broken data cannot loop.
int depthOf(String id, Map<String, String?> parentOf) {
  var depth = 0;
  final seen = <String>{id};
  var current = parentOf[id];

  while (current != null && seen.add(current)) {
    depth++;
    current = parentOf[current];
  }

  return depth;
}

/// [groups] with NSFW-marked ones moved after the rest, keeping relative order.
({List<T> safe, List<T> nsfw}) partitionNsfwGroups<T>(
  Iterable<T> groups,
  bool Function(T) isNsfw,
) {
  final safe = <T>[];
  final nsfw = <T>[];
  for (final group in groups) {
    (isNsfw(group) ? nsfw : safe).add(group);
  }
  return (safe: safe, nsfw: nsfw);
}
