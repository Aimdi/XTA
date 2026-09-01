import 'package:flutter_triple/flutter_triple.dart';

/// Groups temporarily read as one feed.
///
/// Not the same thing as nesting a group inside another: that is a decision
/// about how the groups are arranged, kept in the database, and it changes what
/// every screen says about them. This is a way of looking at several at once for
/// as long as you are looking — pick a few, read them together, drop them. It
/// lives in memory and is gone when the app is, which is what makes it cheap
/// enough to do on a whim.
///
/// Holds the *other* groups being read alongside whichever feed is open, so a
/// combination is always "this one, plus these".
class CombinedGroupsStore extends Store<Set<String>> {
  CombinedGroupsStore() : super(const {});

  bool contains(String id) => state.contains(id);

  void toggle(String id) {
    final next = Set<String>.from(state);
    if (!next.remove(id)) {
      next.add(id);
    }
    update(next);
  }

  void clear() {
    if (state.isNotEmpty) {
      update(const {});
    }
  }

  /// Drops [id] from the combination, for when it becomes the feed being shown
  /// — a group cannot be read alongside itself.
  void release(String id) {
    if (state.contains(id)) {
      update(Set<String>.from(state)..remove(id));
    }
  }
}
