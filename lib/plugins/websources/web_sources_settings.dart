import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/ui/labeled_pref.dart';

class WebSourcesSettingsScreen extends StatelessWidget {
  const WebSourcesSettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_websources_title)),
      body: ListView(
        children: [
          LabeledPref(
            child: PrefSwitch(
              title: Text(l10n.plugin_websources_in_home_feed),
              subtitle: Text(l10n.plugin_websources_in_home_feed_description),
              pref: optionPluginWebSourcesInHomeFeed,
            ),
          ),
        ],
      ),
    );
  }
}
