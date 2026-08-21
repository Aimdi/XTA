import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/utils/pref_lists.dart';

/// How many recent ids we keep. The strip only shows a few; the rest stay
/// behind the networks switcher in this order.
const kNetworkRecentsLimit = 12;

List<String> homeRecentNetworkIds(BasePrefService prefs) => List<String>.from(
  stringListPref(prefs, optionHomeRecentNetworks) ?? const [],
);

/// Plugin ids opened on the home strip, newest first.
class NetworkRecentsStore extends Store<List<String>> {
  final BasePrefService prefs;

  NetworkRecentsStore(this.prefs) : super(homeRecentNetworkIds(prefs));

  Future<void> touch(String pluginId) async {
    if (pluginId.isEmpty) return;
    final next = [pluginId, ...state.where((id) => id != pluginId)];
    if (next.length > kNetworkRecentsLimit) {
      next.removeRange(kNetworkRecentsLimit, next.length);
    }
    if (_same(next, state)) return;
    await prefs.set(optionHomeRecentNetworks, next);
    update(next);
  }

  Future<void> forget(String pluginId) async {
    if (!state.contains(pluginId)) return;
    final next = state.where((id) => id != pluginId).toList();
    await prefs.set(optionHomeRecentNetworks, next);
    update(next);
  }
}

bool _same(List<String> a, List<String> b) {
  if (a.length != b.length) return false;
  for (var i = 0; i < a.length; i++) {
    if (a[i] != b[i]) return false;
  }
  return true;
}
