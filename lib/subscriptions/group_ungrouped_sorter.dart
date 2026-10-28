import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/database/entities.dart';

/// One choice in the sorter: an account and the groups it went into, none when
/// it was skipped.
class SorterChoice {
  final Subscription account;
  final List<String> groupIds;

  const SorterChoice(this.account, this.groupIds);

  bool get placed => groupIds.isNotEmpty;
}

/// Where the one-by-one sorter stands: the accounts to go through and one
/// choice for each account behind it.
class SorterState {
  final List<Subscription> accounts;
  final List<SorterChoice> choices;

  /// Whether a choice is being saved, when the sorter takes no other.
  final bool saving;

  /// Whether the last move took a choice back, for the side the card comes
  /// from.
  final bool backward;

  const SorterState(this.accounts, {this.choices = const [], this.saving = false, this.backward = false});

  int get position => choices.length;

  bool get finished => position >= accounts.length;

  Subscription? get current => finished ? null : accounts[position];

  int get placedCount => choices.where((choice) => choice.placed).length;

  List<Subscription> get skipped => [
    for (final choice in choices)
      if (!choice.placed) choice.account,
  ];

  /// [current] put in [groupIds], or skipped when there are none.
  SorterState decide(List<String> groupIds) =>
      SorterState(accounts, choices: [...choices, SorterChoice(current!, groupIds)]);

  /// The last choice taken back, its account current again.
  SorterState undo() => SorterState(accounts, choices: choices.sublist(0, choices.length - 1), backward: true);

  /// A new round of the accounts skipped so far.
  SorterState skippedRound() => SorterState(skipped);

  SorterState withSaving(bool saving) => SorterState(accounts, choices: choices, saving: saving, backward: backward);
}

typedef MembershipWrite = Future<void> Function(String accountId, List<String> groupIds);

/// Saves each of the sorter's choices as it is made, through [place] and,
/// for an undo, [unplace].
class UngroupedSorterStore extends Store<SorterState> {
  final MembershipWrite place;
  final MembershipWrite unplace;

  UngroupedSorterStore(List<Subscription> accounts, {required this.place, required this.unplace})
    : super(SorterState(accounts));

  /// Puts the current account in [groupIds] and moves on; false when there
  /// was nothing to do. A failed save throws and leaves the account current.
  Future<bool> put(List<String> groupIds) async {
    final account = state.current;
    if (account == null || state.saving || groupIds.isEmpty) return false;
    await _save(() => place(account.id, groupIds), () => state.decide(groupIds));
    return true;
  }

  void skip() {
    if (state.current == null || state.saving) return;
    update(state.decide(const []));
  }

  /// Takes the last choice back, out of the groups it put the account in;
  /// false when there was none.
  Future<bool> undo() async {
    if (state.choices.isEmpty || state.saving) return false;
    final last = state.choices.last;
    if (last.placed) {
      await _save(() => unplace(last.account.id, last.groupIds), () => state.undo());
    } else {
      update(state.undo());
    }
    return true;
  }

  void sortSkipped() {
    if (!state.finished || state.saving || state.skipped.isEmpty) return;
    update(state.skippedRound());
  }

  /// Runs [write] while saving, then moves to [next], read once it is done.
  Future<void> _save(Future<void> Function() write, SorterState Function() next) async {
    update(state.withSaving(true));
    try {
      await write();
      update(next());
    } finally {
      if (state.saving) update(state.withSaving(false));
    }
  }
}
