import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_registry.dart';

/// Enabled plugins that hid their bottom-nav tab. They used to fall back to
/// Groups chips; the home strip is where you switch sites now.
List<String> hiddenTabFeedStripIds(BasePrefService prefs) => [
  for (final plugin in builtInPlugins)
    if (plugin.supportsFeedStrip &&
        plugin.isEnabled(prefs) &&
        !plugin.showsHomeTab(prefs))
      plugin.id,
];

/// Saved pins plus hidden-tab plugins, so turning a tab off cannot strand it.
List<String> feedStripVisibleIds(BasePrefService prefs, List<String> pinned) {
  final seen = <String>{};
  return [
    for (final id in pinned)
      if (seen.add(id)) id,
    for (final id in hiddenTabFeedStripIds(prefs))
      if (seen.add(id)) id,
  ];
}

/// Plugin ids currently pinned on the home feed strip (next to For you).
///
/// Null pref = never configured → keep the legacy Reddit auto-tab. Empty list =
/// the reader removed every plugin pin on purpose — except plugins that hid
/// their bottom-nav tab, which stay here so they remain reachable.
List<String> feedStripPluginIds(BasePrefService prefs) {
  final raw = prefs.getStringList(optionHomeFeedStripPlugins);
  final pinned = raw != null
      ? List<String>.from(raw)
      : (prefs.get<bool>(optionPluginRedditEnabled) == true
            ? const [pluginIdReddit]
            : const <String>[]);
  return feedStripVisibleIds(prefs, pinned);
}

/// Enabled plugins that can be pinned but are not on the strip yet.
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

/// Pins [pluginId] on the home strip. Used when a plugin is installed or its
/// bottom-nav tab is turned off — Groups is not a site switcher.
Future<void> pinPluginOnFeedStrip(
  BasePrefService prefs,
  String pluginId,
) async {
  final plugin = pluginById(pluginId);
  if (plugin == null || !plugin.supportsFeedStrip) return;

  final raw = prefs.getStringList(optionHomeFeedStripPlugins);
  final pinned = raw ?? feedStripPluginIds(prefs);
  if (pinned.contains(pluginId)) {
    if (raw == null) {
      await prefs.set(optionHomeFeedStripPlugins, List<String>.from(pinned));
    }
    return;
  }
  await prefs.set(optionHomeFeedStripPlugins, [...pinned, pluginId]);
}

Future<void> unpinPluginFromFeedStrip(
  BasePrefService prefs,
  String pluginId,
) async {
  final pinned = prefs.getStringList(optionHomeFeedStripPlugins);
  if (pinned == null || !pinned.contains(pluginId)) return;
  await prefs.set(
    optionHomeFeedStripPlugins,
    pinned.where((id) => id != pluginId).toList(),
  );
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

  /// Persist the legacy-implied list the first time the reader edits the strip.
  Future<void> ensurePersisted() async {
    if (prefs.getStringList(optionHomeFeedStripPlugins) != null) return;
    await prefs.set(optionHomeFeedStripPlugins, List<String>.from(state));
  }

  Future<void> pin(String pluginId) async {
    await ensurePersisted();
    await add(pluginId);
  }

  /// Persist hidden-tab plugins so they survive as home destinations.
  Future<void> pinHiddenTabs() async {
    final extra = [
      for (final id in hiddenTabFeedStripIds(prefs))
        if (!state.contains(id)) id,
    ];
    if (extra.isEmpty) return;
    await ensurePersisted();
    await setPlugins([...state, ...extra]);
  }

  Future<void> forget(String pluginId) async {
    await unpinPluginFromFeedStrip(prefs, pluginId);
    if (!state.contains(pluginId)) return;
    update(state.where((id) => id != pluginId).toList());
  }
}
