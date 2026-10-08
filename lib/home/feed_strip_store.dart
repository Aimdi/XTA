import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/utils/pref_lists.dart';

/// Enabled plugins that can sit next to Following / For you.
List<String> enabledStripPluginIds(BasePrefService prefs) => [
  for (final plugin in builtInPlugins)
    if (plugin.supportsFeedStrip && plugin.isEnabled(prefs)) plugin.id,
];

/// Enabled plugins that hid their bottom-nav tab. Home used to show these on
/// its own; [legacyFeedStripIds] keeps them there for installs that had them.
List<String> hiddenTabFeedStripIds(BasePrefService prefs) => [
  for (final plugin in builtInPlugins)
    if (plugin.supportsFeedStrip &&
        plugin.isEnabled(prefs) &&
        !plugin.showsHomeTab(prefs))
      plugin.id,
];

/// The pins Home can show right now, each once and in pin order.
List<String> feedStripVisibleIds(BasePrefService prefs, List<String> pinned) =>
    [
      for (final id in _distinct(pinned))
        if (pluginById(id) case final plugin?
            when plugin.supportsFeedStrip && plugin.isEnabled(prefs))
          id,
    ];

/// Plugin ids the reader added next to Following / For you, in their order.
///
/// Unset means nothing was added, so a fresh install offers only Following and
/// X until the reader picks a timeline under “Add timeline”.
List<String> feedStripPluginIds(BasePrefService prefs) =>
    _distinct(stringListPref(prefs, optionHomeFeedStripPlugins) ?? const []);

/// What Home showed before timelines were hand-picked: the saved pins, or
/// every enabled network when none were saved, plus hidden-tab plugins.
List<String> legacyFeedStripIds(BasePrefService prefs) => _distinct([
  ...(stringListPref(prefs, optionHomeFeedStripPlugins) ??
      enabledStripPluginIds(prefs)),
  ...hiddenTabFeedStripIds(prefs),
]);

/// Runs once per install, before Home is built. A first launch starts with no
/// plugin timeline; an older install keeps exactly the timelines it showed.
Future<void> migrateFeedStripPins(
  BasePrefService prefs, {
  required bool firstLaunch,
}) async {
  if (prefs.get(optionHomeFeedStripHandPicked) == true) return;
  final pins = firstLaunch ? <String>[] : legacyFeedStripIds(prefs);
  final saved = stringListPref(prefs, optionHomeFeedStripPlugins);
  if (saved == null || !_same(saved, pins)) {
    await prefs.set(optionHomeFeedStripPlugins, pins);
  }
  await prefs.set(optionHomeFeedStripHandPicked, true);
}

/// Takes a plugin off Home, e.g. when it is uninstalled.
Future<void> forgetFeedStripPlugin(BasePrefService prefs, String pluginId) =>
    removeFromStringListPref(prefs, optionHomeFeedStripPlugins, pluginId);

/// Enabled plugins that can be added but are not on Home yet.
List<XtaPlugin> feedStripCandidates(
  BasePrefService prefs,
  List<String> pinned,
) {
  final shown = feedStripVisibleIds(prefs, pinned).toSet();
  return builtInPlugins
      .where(
        (p) =>
            p.supportsFeedStrip && p.isEnabled(prefs) && !shown.contains(p.id),
      )
      .toList(growable: false);
}

/// Adds [pluginId] to Home. Used when the reader hides its bottom-nav tab, so
/// the plugin moves to Home instead of dropping out of reach.
Future<void> pinPluginOnFeedStrip(
  BasePrefService prefs,
  String pluginId,
) async {
  final plugin = pluginById(pluginId);
  if (plugin == null || !plugin.supportsFeedStrip) return;

  final pinned = feedStripPluginIds(prefs);
  if (pinned.contains(pluginId)) return;
  await prefs.set(optionHomeFeedStripPlugins, [...pinned, pluginId]);
}

Future<void> unpinPluginFromFeedStrip(
  BasePrefService prefs,
  String pluginId,
) async {
  await forgetFeedStripPlugin(prefs, pluginId);
}

FeedStripStore? _maybeStrip(BuildContext context) {
  try {
    return context.read<FeedStripStore>();
  } on ProviderNotFoundException {
    return null;
  }
}

/// Store-aware pin so the home strip remounts in the same session.
Future<void> pinPluginOnFeedStripIn(
  BuildContext context,
  String pluginId,
) async {
  final strip = _maybeStrip(context);
  if (strip != null) {
    await strip.pin(pluginId);
    return;
  }
  await pinPluginOnFeedStrip(PrefService.of(context, listen: false), pluginId);
}

Future<void> unpinPluginFromFeedStripIn(
  BuildContext context,
  String pluginId,
) async {
  final strip = _maybeStrip(context);
  if (strip != null) {
    await strip.forget(pluginId);
    return;
  }
  await unpinPluginFromFeedStrip(
    PrefService.of(context, listen: false),
    pluginId,
  );
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

  Future<void> reorder(int oldIndex, int newIndex) async {
    final next = reorderFeedStripIds(state, oldIndex, newIndex);
    if (_same(next, state)) return;
    await setPlugins(next);
  }

  /// Persist the implied list the first time the reader edits the strip.
  Future<void> ensurePersisted() async {
    if (stringListPref(prefs, optionHomeFeedStripPlugins) != null) return;
    await prefs.set(optionHomeFeedStripPlugins, List<String>.from(state));
  }

  Future<void> pin(String pluginId) async {
    await ensurePersisted();
    await add(pluginId);
  }

  Future<void> forget(String pluginId) async {
    await forgetFeedStripPlugin(prefs, pluginId);
    if (!state.contains(pluginId)) return;
    update(state.where((id) => id != pluginId).toList());
  }
}

List<String> _distinct(Iterable<String> ids) => ids.toSet().toList();

bool _same(List<String> a, List<String> b) {
  if (a.length != b.length) return false;
  for (var i = 0; i < a.length; i++) {
    if (a[i] != b[i]) return false;
  }
  return true;
}

/// Moves one pin. [newIndex] is the slot after the item is taken out, matching
/// [ReorderableListView.onReorderItem].
List<String> reorderFeedStripIds(List<String> ids, int oldIndex, int newIndex) {
  if (oldIndex == newIndex || oldIndex < 0 || oldIndex >= ids.length) {
    return List<String>.from(ids);
  }
  final next = List<String>.from(ids);
  final id = next.removeAt(oldIndex);
  final to = newIndex < 0
      ? 0
      : (newIndex > next.length ? next.length : newIndex);
  next.insert(to, id);
  return next;
}
