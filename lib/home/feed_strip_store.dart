import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_registry.dart';

/// Plugin ids currently pinned on the home feed strip (next to For you).
///
/// Null pref = never configured → keep the legacy Reddit auto-tab. Empty list =
/// the reader removed every plugin pin on purpose.
List<String> feedStripPluginIds(BasePrefService prefs) {
  final raw = prefs.getStringList(optionHomeFeedStripPlugins);
  if (raw != null) {
    return List<String>.from(raw);
  }
  if (prefs.get<bool>(optionPluginRedditEnabled) == true) {
    return const [pluginIdReddit];
  }
  return const [];
}

/// Enabled plugins that can be pinned but are not on the strip yet.
List<XtaPlugin> feedStripCandidates(
  BasePrefService prefs,
  List<String> pinned,
) {
  final pinnedSet = pinned.toSet();
  return builtInPlugins
      .where(
        (p) =>
            p.supportsFeedStrip &&
            p.isEnabled(prefs) &&
            !pinnedSet.contains(p.id),
      )
      .toList(growable: false);
}

/// Which plugin timelines sit next to Following / For you.
class FeedStripStore extends Store<List<String>> {
  final BasePrefService prefs;

  FeedStripStore(this.prefs) : super(feedStripPluginIds(prefs));

  Future<void> setPlugins(List<String> ids) async {
    final next = List<String>.from(ids);
    await prefs.set(optionHomeFeedStripPlugins, next);
    update(next);
  }

  Future<void> add(String pluginId) async {
    if (state.contains(pluginId)) return;
    await setPlugins([...state, pluginId]);
  }

  Future<void> remove(String pluginId) async {
    if (!state.contains(pluginId)) return;
    await setPlugins(state.where((e) => e != pluginId).toList());
  }

  /// Persist the legacy-implied list the first time the reader edits the strip.
  Future<void> ensurePersisted() async {
    if (prefs.getStringList(optionHomeFeedStripPlugins) != null) return;
    await prefs.set(optionHomeFeedStripPlugins, List<String>.from(state));
  }
}
