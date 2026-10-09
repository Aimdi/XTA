import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_display.dart';

/// Grid and viewer choices in Booru settings.
class BooruDisplaySettings extends StatelessWidget {
  const BooruDisplaySettings({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final prefs = PrefService.of(context);
    final columns = prefs.get<int>(optionPluginBooruGridColumns) ?? 0;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        PrefTitle(title: Text(l10n.plugin_booru_section_display)),
        ListTile(
          title: Text(l10n.plugin_booru_grid_columns),
          subtitle: Text(_columnsLabel(l10n, columns)),
          onTap: () => _pickColumns(context, prefs, columns),
        ),
        PrefSwitch(
          title: Text(l10n.plugin_booru_small_thumbnails),
          subtitle: Text(l10n.plugin_booru_small_thumbnails_description),
          pref: optionPluginBooruSmallThumbnails,
        ),
        PrefSwitch(
          title: Text(l10n.plugin_booru_tile_details),
          subtitle: Text(l10n.plugin_booru_tile_details_description),
          pref: optionPluginBooruTileDetails,
        ),
        PrefSwitch(
          title: Text(l10n.plugin_booru_blur_explicit),
          subtitle: Text(l10n.plugin_booru_blur_explicit_description),
          pref: optionPluginBooruBlurExplicit,
        ),
        PrefSwitch(
          title: Text(l10n.plugin_booru_original_in_viewer),
          subtitle: Text(l10n.plugin_booru_original_in_viewer_description),
          pref: optionPluginBooruOriginalInViewer,
        ),
      ],
    );
  }

  String _columnsLabel(L10n l10n, int columns) => columns > 0 ? '$columns' : l10n.plugin_booru_grid_columns_auto;

  Future<void> _pickColumns(BuildContext context, BasePrefService prefs, int current) async {
    final l10n = L10n.of(context);
    final next = await showModalBottomSheet<int>(
      context: context,
      builder: (context) => SafeArea(
        child: RadioGroup<int>(
          groupValue: current,
          onChanged: (value) => Navigator.pop(context, value),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (final value in booruGridColumnChoices)
                RadioListTile<int>(value: value, title: Text(_columnsLabel(l10n, value))),
            ],
          ),
        ),
      ),
    );
    if (next != null) await prefs.set(optionPluginBooruGridColumns, next);
  }
}
