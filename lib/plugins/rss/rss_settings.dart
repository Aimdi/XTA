import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';

class RssSettingsScreen extends StatefulWidget {
  const RssSettingsScreen({super.key});

  @override
  State<RssSettingsScreen> createState() => _RssSettingsScreenState();
}

class _RssSettingsScreenState extends State<RssSettingsScreen> {
  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final prefs = PrefService.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_rss_title)),
      body: ListView(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Text(l10n.plugin_rss_settings_blurb),
          ),
          SwitchListTile(
            secondary: const Icon(Icons.dynamic_feed_outlined),
            title: Text(l10n.plugin_rss_in_home_feed),
            subtitle: Text(l10n.plugin_rss_in_home_feed_description),
            value: prefs.get<bool>(optionPluginRssInHomeFeed) == true,
            onChanged: (value) async {
              await PrefService.of(
                context,
                listen: false,
              ).set(optionPluginRssInHomeFeed, value);
              if (mounted) setState(() {});
            },
          ),
        ],
      ),
    );
  }
}
