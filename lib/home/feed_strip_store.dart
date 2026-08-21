import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_registry.dart';

/// Enabled plugins that can sit next to Following / For you.
List<String> enabledStripPluginIds(BasePrefService prefs) => [
  for (final plugin in builtInPlugins)
    if (plugin.supportsFeedStrip && plugin.isEnabled(prefs)) plugin.id,
];

/// Plugin ids currently pinned on the home feed strip (next to For you).
///
/// Null pref = never configured → every enabled network. Empty list = the
/// reader removed every plugin pin on purpose.
List<String> feedStripPluginIds(BasePrefService prefs) {
  final raw = prefs.getStringList(optionHomeFeedStripPlugins);
  if (raw != null) {
    return List<String>.from(raw);
  }
  return enabledStripPluginIds(prefs);
}

/// Pins newly enabled networks once, the same way a first install used to
/// drop Reddit next to For you. A pin the reader then removes stays gone.
Future<List<String>> seedFeedStripPlugins(BasePrefService prefs) async {
  final current = feedStripPluginIds(prefs);
  final seeded =
      prefs.getStringList(optionSeededStripPlugins) ?? const <String>[];
  final next = List<String>.from(current);
  final newly = <String>[];

  for (final id in enabledStripPluginIds(prefs)) {
    if (seeded.contains(id) || next.contains(id)) continue;
    next.add(id);
    newly.add(id);
  }

  if (newly.isEmpty &&
      prefs.getStringList(optionHomeFeedStripPlugins) != null) {
    return current;
  }

  // Persist the implied list on first seed so later edits have a real pin list.
  final seededNext = {...seeded, ...next}.toList();
  await prefs.set(optionSeededStripPlugins, seededNext);
  await prefs.set(optionHomeFeedStripPlugins, next);
  return next;
}

/// Drops a plugin from the strip and lets a later install offer it again.
Future<void> forgetFeedStripPlugin(
  BasePrefService prefs,
  String pluginId,
) async {
  final pinned = prefs.getStringList(optionHomeFeedStripPlugins);
  if (pinned != null && pinned.contains(pluginId)) {
    await prefs.set(
      optionHomeFeedStripPlugins,
      pinned.where((id) => id != pluginId).toList(),
    );
  }
  final seeded = prefs.getStringList(optionSeededStripPlugins);
  if (seeded != null && seeded.contains(pluginId)) {
    await prefs.set(
      optionSeededStripPlugins,
      seeded.where((id) => id != pluginId).toList(),
    );
  }
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

  /// Persist the implied list the first time the reader edits the strip.
  Future<void> ensurePersisted() async {
    if (prefs.getStringList(optionHomeFeedStripPlugins) != null) return;
    await prefs.set(optionHomeFeedStripPlugins, List<String>.from(state));
  }

  /// Offer a pin to every enabled network that has never been offered one.
  Future<void> seedEnabled() async {
    final next = await seedFeedStripPlugins(prefs);
    if (!_same(next, state)) update(next);
  }

  Future<void> forget(String pluginId) async {
    await forgetFeedStripPlugin(prefs, pluginId);
    if (!state.contains(pluginId)) return;
    update(state.where((id) => id != pluginId).toList());
  }
}

bool _same(List<String> a, List<String> b) {
  if (a.length != b.length) return false;
  for (var i = 0; i < a.length; i++) {
    if (a[i] != b[i]) return false;
  }
  return true;
}
