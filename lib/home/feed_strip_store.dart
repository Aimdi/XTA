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
/// Null pref = never configured → every enabled network. Empty list = the
/// reader removed every plugin pin on purpose — except plugins that hid
/// their bottom-nav tab, which stay here so they remain reachable.
List<String> feedStripPluginIds(BasePrefService prefs) {
  final raw = stringListPref(prefs, optionHomeFeedStripPlugins);
  final pinned = raw != null
      ? List<String>.from(raw)
      : enabledStripPluginIds(prefs);
  return feedStripVisibleIds(prefs, pinned);
}

/// Pins newly enabled networks once, the same way a first install used to
/// drop Reddit next to For you. A pin the reader then removes stays gone.
Future<List<String>> seedFeedStripPlugins(BasePrefService prefs) async {
  final current = feedStripPluginIds(prefs);
  final seeded =
      stringListPref(prefs, optionSeededStripPlugins) ?? const <String>[];
  final next = List<String>.from(current);
  final newly = <String>[];

  for (final id in enabledStripPluginIds(prefs)) {
    if (seeded.contains(id) || next.contains(id)) continue;
    next.add(id);
    newly.add(id);
  }

  if (newly.isEmpty &&
      stringListPref(prefs, optionHomeFeedStripPlugins) != null) {
    return current;
  }

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
  final pinned = stringListPref(prefs, optionHomeFeedStripPlugins);
  if (pinned != null && pinned.contains(pluginId)) {
    await prefs.set(
      optionHomeFeedStripPlugins,
      pinned.where((id) => id != pluginId).toList(),
    );
  }
  final seeded = stringListPref(prefs, optionSeededStripPlugins);
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

  final raw = stringListPref(prefs, optionHomeFeedStripPlugins);
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

  /// Persist the implied list the first time the reader edits the strip.
  Future<void> ensurePersisted() async {
    if (stringListPref(prefs, optionHomeFeedStripPlugins) != null) return;
    await prefs.set(optionHomeFeedStripPlugins, List<String>.from(state));
  }

  Future<void> pin(String pluginId) async {
    await ensurePersisted();
    await add(pluginId);
  }

  /// Offer a pin to every enabled network that has never been offered one.
  Future<void> seedEnabled() async {
    final next = await seedFeedStripPlugins(prefs);
    if (!_same(next, state)) update(next);
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
