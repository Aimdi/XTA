import 'package:flutter/foundation.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';

/// The account a reader session's lists were loaded for. Signing out, or
/// another account signing in, calls [onSwitched] so one account's private
/// lists (bookmarks, the watchlist) never show under another.
///
/// The screen calls [check] when it is opened, built or told of a sign-in,
/// rather than on every preference write: a switch writes several keys, and
/// lists reloaded halfway through it would ask with the last account's token.
class PixivSessionAccountStore extends Store<int> {
  final BasePrefService prefs;
  final VoidCallback onSwitched;

  PixivSessionAccountStore(this.prefs, {required this.onSwitched}) : super(_userId(prefs));

  static int _userId(BasePrefService prefs) => prefs.get<int>(optionPluginPixivUserId) ?? 0;

  /// Whether the account in use is not the one the lists were loaded for.
  bool get behind => _userId(prefs) != state;

  /// Catches up with the account in use, emptying the lists if it is another.
  void check() {
    final id = _userId(prefs);
    if (id == state) return;
    // From no id to one is the signed-in account learning its id, not a switch.
    final switched = state != 0;
    update(id);
    if (switched) onSwitched();
  }
}
