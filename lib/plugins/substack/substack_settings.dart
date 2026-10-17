import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';

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
        ],
      ),
    );
  }
}
