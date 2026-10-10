import 'package:flutter/widgets.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_split.dart';
import 'package:xta/plugins/pixiv/pixiv_grid_columns.dart';
import 'package:xta/plugins/pixiv/pixiv_image_source.dart';
import 'package:xta/plugins/pixiv/pixiv_quality.dart';
import 'package:xta/settings/settings_view_store.dart';

/// The app's preferences when there are any above [context]. Widgets built without them
/// (tests, previews) use each setting's default.
BasePrefService? pixivPrefsOf(BuildContext context) => context.getInheritedWidgetOfExactType<PrefService>()?.service;

/// Every viewing preference at its default, for a first launch and for a plugin reset.
final pixivViewingDefaults = <String, Object>{
  optionPluginPixivImageHost: pixivImageHost,
  for (final slot in PixivQualitySlot.values) slot.pref: slot.fallback.name,
  optionPluginPixivGridColumnsPortrait: pixivGridColumnsAuto,
  optionPluginPixivGridColumnsLandscape: pixivGridColumnsAuto,
  optionPluginPixivSwipeBetweenWorks: false,
  optionPluginPixivDetailLayout: PixivDetailLayout.auto.name,
  optionPluginPixivDetailSplit: pixivSplitDefaultFraction,
  optionPluginPixivAiBadge: true,
};

/// What a works grid and its tiles read: columns, the tile image and its server, the AI badge.
const pixivGridPrefKeys = [
  optionPluginPixivGridColumnsPortrait,
  optionPluginPixivGridColumnsLandscape,
  optionPluginPixivQualityFeed,
  optionPluginPixivImageHost,
  optionPluginPixivAiBadge,
];

/// Whether a work opened from a list sits in a pager of its neighbours; off until switched on.
bool pixivSwipesBetweenWorks(BasePrefService? prefs) => prefs?.get<bool>(optionPluginPixivSwipeBetweenWorks) == true;

/// Whether grid tiles label AI-generated works; on until switched off.
bool pixivShowsAiBadge(BasePrefService? prefs) => prefs?.get<bool>(optionPluginPixivAiBadge) ?? true;

/// Builds again when one of [keys] changes and for nothing else the app stores, so a grid
/// follows its settings without repainting each time a token or a read position is saved.
class PixivPrefsBuilder extends StatefulWidget {
  final List<String> keys;
  final WidgetBuilder builder;

  const PixivPrefsBuilder({super.key, required this.keys, required this.builder});

  @override
  State<PixivPrefsBuilder> createState() => _PixivPrefsBuilderState();
}

class _PixivPrefsBuilderState extends State<PixivPrefsBuilder> {
  final _revision = SettingsRevisionStore();
  BasePrefService? _prefs;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final prefs = pixivPrefsOf(context);
    if (identical(prefs, _prefs)) return;
    _unlisten();
    _prefs = prefs;
    for (final key in widget.keys) {
      prefs?.addKeyListener(key, _revision.refresh);
    }
  }

  void _unlisten() {
    for (final key in widget.keys) {
      _prefs?.removeKeyListener(key, _revision.refresh);
    }
  }

  @override
  void dispose() {
    _unlisten();
    _revision.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) =>
      ScopedBuilder<SettingsRevisionStore, int>(store: _revision, onState: (context, _) => widget.builder(context));
}
