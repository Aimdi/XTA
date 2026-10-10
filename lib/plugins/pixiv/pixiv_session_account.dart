import 'package:flutter/foundation.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';

/// What a reader session's lists were loaded for: the account, and the Show
/// R-18 and Hide AI choices, which drop works as each page is parsed.
typedef PixivSessionKey = ({int userId, bool showR18, bool hideAi});

PixivSessionKey pixivSessionKeyOf(BasePrefService prefs) => (
  userId: prefs.get<int>(optionPluginPixivUserId) ?? 0,
  showR18: prefs.get<bool>(optionPluginPixivShowR18) == true,
  hideAi: prefs.get<bool>(optionPluginPixivHideAi) == true,
);

/// Signing out, another account signing in, or Show R-18 / Hide AI changing
/// calls [onChanged], so one account's private lists (bookmarks, the
/// watchlist) never show under another and works loaded under the old
/// choices do not stay on screen.
///
/// The screen calls [check] when it is opened, built or told of a sign-in,
/// rather than on every preference write: a switch writes several keys, and
/// lists reloaded halfway through it would ask with the last account's token.
class PixivSessionAccountStore extends Store<PixivSessionKey> {
  final BasePrefService prefs;
  final VoidCallback onChanged;

  PixivSessionAccountStore(this.prefs, {required this.onChanged}) : super(pixivSessionKeyOf(prefs));

  /// Whether the lists were loaded for another account or other content choices.
  bool get behind => pixivSessionKeyOf(prefs) != state;

  /// Catches up with the account and choices in use, emptying the lists if they changed.
  void check() {
    final now = pixivSessionKeyOf(prefs);
    if (now == state) return;
    // From no id to one is the signed-in account learning its id, not a switch.
    final switched = state.userId != 0 && now.userId != state.userId;
    final refiltered = now.showR18 != state.showR18 || now.hideAi != state.hideAi;
    update(now);
    if (switched || refiltered) onChanged();
  }
}
