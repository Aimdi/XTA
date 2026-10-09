import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';

/// The launch default for the seen flag: a fresh install sees the cards, an
/// install that already has prefs never does.
Map<String, Object> introLaunchDefaults({required bool firstLaunch}) => {optionIntroSeen: !firstLaunch};

/// Whether the first-launch cards have been seen. The pref is the truth; the
/// store lets the root swap between the intro and Home without a restart.
class IntroStore extends Store<bool> {
  final BasePrefService prefs;

  IntroStore(this.prefs) : super(prefs.get<bool>(optionIntroSeen) == true);

  Future<void> markSeen() => _write(true);

  Future<void> showAgain() => _write(false);

  Future<void> _write(bool seen) async {
    await prefs.set(optionIntroSeen, seen);
    update(seen);
  }
}

/// Which card is on screen, so the indicator and the buttons follow the pager.
class IntroPageStore extends Store<int> {
  IntroPageStore([super.initialState = 0]);

  void show(int page) => update(page);
}

/// The accounts on this device, re-read after the login webview pops: it
/// returns nothing, so the table is polled through [loader].
class IntroAccountsStore extends Store<List<Account>> {
  final Future<List<Account>> Function() loader;

  IntroAccountsStore(this.loader) : super(const []);

  Future<void> refresh() => execute(loader);
}
