import 'package:flutter/foundation.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';

/// The account a reader session's lists were loaded for. Signing out, or
/// another account signing in, calls [onSwitched] so one account's private
/// lists (bookmarks, the watchlist) never show under another.
class PixivSessionAccountStore extends Store<int> {
  final BasePrefService prefs;
  final VoidCallback onSwitched;

  PixivSessionAccountStore(this.prefs, {required this.onSwitched}) : super(_userId(prefs)) {
    prefs.addKeyListener(optionPluginPixivUserId, _check);
  }

  static int _userId(BasePrefService prefs) => prefs.get<int>(optionPluginPixivUserId) ?? 0;

  void _check() {
    final id = _userId(prefs);
    if (id == state) return;
    // From no id to one is the signed-in account learning its id, not a switch.
    final switched = state != 0;
    update(id);
    if (switched) onSwitched();
  }

  @override
  Future<void> destroy() {
    prefs.removeKeyListener(optionPluginPixivUserId, _check);
    return super.destroy();
  }
}
