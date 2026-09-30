import 'package:xta/reading/mixed_feed_definition.dart';

/// One post a source of a mix returned. [identity] names the post on its network, so the same post reached through
/// two sources is shown once; [date] is its publication date when the source gives one.
class MixedEntry {
  final String identity;
  final DateTime? date;
  final Object item;
  const MixedEntry({required this.identity, required this.item, this.date});
}

/// What a mix has read from one source so far, in the source's own order.
class MixedSlot {
  final List<MixedEntry> entries;

  /// Where each entry sorts among the sources: its date, or for an undated entry the date of the entry before it
  /// (or, for the first, when its page arrived). Never shown as a date.
  final List<DateTime> sortDates;
  final bool exhausted;
  final bool failed;

  const MixedSlot({this.entries = const [], this.sortDates = const [], this.exhausted = false, this.failed = false});

  /// Still able to give more entries later, so merging must wait for it.
  bool get active => !exhausted && !failed;

  MixedSlot appended(List<MixedEntry> page, {required bool exhausted, required DateTime fetchedAt}) {
    var previous = sortDates.lastOrNull ?? fetchedAt;
    final dates = [for (final entry in page) previous = entry.date ?? previous];
    return MixedSlot(
      entries: List.unmodifiable([...entries, ...page]),
      sortDates: List.unmodifiable([...sortDates, ...dates]),
      exhausted: exhausted,
    );
  }

  MixedSlot withFailure(bool failed) =>
      MixedSlot(entries: entries, sortDates: sortDates, exhausted: exhausted, failed: failed);
}

/// An entry placed in the mix, with the index of the source it came from.
class MixedPick {
  final int slot;
  final MixedEntry entry;
  const MixedPick(this.slot, this.entry);
}

/// How far merging has got: what is shown, how many entries of each source are used, and whose turn is next.
class MixedMergeState {
  final List<MixedPick> shown;
  final List<int> consumed;
  final int turn;
  final Set<String> seen;
  const MixedMergeState._(this.shown, this.consumed, this.turn, this.seen);

  MixedMergeState.start(int slots) : this._(const [], List.filled(slots, 0), 0, const {});
}

class _Merge {
  final List<MixedSlot> slots;
  final List<MixedPick> shown;
  final List<int> consumed;
  final Set<String> seen;
  int turn;

  _Merge(MixedMergeState state, this.slots)
    : shown = [...state.shown],
      consumed = [...state.consumed],
      seen = {...state.seen},
      turn = state.turn;

  bool hasHead(int slot) => consumed[slot] < slots[slot].entries.length;

  void take(int slot) {
    final entry = slots[slot].entries[consumed[slot]++];
    if (seen.add(entry.identity)) shown.add(MixedPick(slot, entry));
  }

  MixedMergeState get state => MixedMergeState._(List.unmodifiable(shown), List.unmodifiable(consumed), turn, seen);

  /// The newest next entry among the sources, until a source that may have more has nothing fetched.
  void chronological() {
    while (true) {
      if (Iterable.generate(slots.length).any((slot) => slots[slot].active && !hasHead(slot))) return;
      int? newest;
      for (var slot = 0; slot < slots.length; slot++) {
        if (hasHead(slot) && (newest == null || _newer(slot, newest))) newest = slot;
      }
      if (newest == null) return;
      take(newest);
    }
  }

  bool _newer(int slot, int than) =>
      slots[slot].sortDates[consumed[slot]].isAfter(slots[than].sortDates[consumed[than]]);

  /// One entry from each source in turn; sources that ran out are skipped, one that may have more is waited for.
  void alternating() {
    while (true) {
      final next = _nextTurn();
      if (next == null) return;
      while (hasHead(next) && seen.contains(slots[next].entries[consumed[next]].identity)) {
        consumed[next]++;
      }
      if (!hasHead(next)) {
        turn = next;
        continue;
      }
      take(next);
      turn = (next + 1) % slots.length;
    }
  }

  /// The source whose entry comes next, or null when merging has to wait or everything is used.
  int? _nextTurn() {
    for (var step = 0; step < slots.length; step++) {
      final slot = (turn + step) % slots.length;
      if (hasHead(slot)) return slot;
      if (slots[slot].active) {
        turn = slot;
        return null;
      }
    }
    return null;
  }
}

/// Continues [state] with every entry of [slots] that can be placed now; what is already shown never moves.
MixedMergeState mergeMixedSlots(MixedMergeState state, List<MixedSlot> slots, MixedFeedOrder order) {
  if (slots.isEmpty) return state;
  final merge = _Merge(state, slots);
  switch (order) {
    case MixedFeedOrder.chronological:
      merge.chronological();
    case MixedFeedOrder.alternating:
      merge.alternating();
  }
  return merge.state;
}

/// The sources that must be read further before more entries can be placed.
Set<int> mixedSlotsToRead(MixedMergeState state, List<MixedSlot> slots) => {
  for (var slot = 0; slot < slots.length; slot++)
    if (slots[slot].active && state.consumed[slot] >= slots[slot].entries.length) slot,
};
