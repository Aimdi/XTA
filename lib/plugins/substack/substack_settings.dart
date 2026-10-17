import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/substack/substack_reader_style.dart';

class SubstackSettingsScreen extends StatelessWidget {
  const SubstackSettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      appBar: AppBar(title: Text(l10n.plugin_substack_title)),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          PrefSwitch(
            title: Text(l10n.plugin_substack_native_reader),
            subtitle: Text(l10n.plugin_substack_native_reader_description),
            pref: optionPluginSubstackNativeReader,
          ),
          PrefSwitch(
            title: Text(l10n.plugin_substack_mark_read_on_scroll),
            subtitle: Text(l10n.plugin_substack_mark_read_on_scroll_description),
            pref: optionPluginSubstackMarkReadOnScroll,
          ),
          PrefDropdown<String>(
            fullWidth: false,
            title: Text(l10n.plugin_substack_reader_font),
            pref: optionPluginSubstackReaderFont,
            items: [
              DropdownMenuItem(value: SubstackReaderFont.serif.name, child: Text(l10n.plugin_substack_font_serif)),
              DropdownMenuItem(value: SubstackReaderFont.sans.name, child: Text(l10n.plugin_substack_font_sans)),
            ],
          ),
          PrefDropdown<String>(
            fullWidth: false,
            title: Text(l10n.plugin_substack_column_width),
            pref: optionPluginSubstackColumnWidth,
            items: [
              DropdownMenuItem(value: SubstackColumnWidth.narrow.name, child: Text(l10n.plugin_substack_column_narrow)),
              DropdownMenuItem(value: SubstackColumnWidth.medium.name, child: Text(l10n.plugin_substack_column_medium)),
              DropdownMenuItem(value: SubstackColumnWidth.wide.name, child: Text(l10n.plugin_substack_column_wide)),
            ],
          ),
        ],
      ),
    );
  }
}
