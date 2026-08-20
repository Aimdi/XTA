import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/hackernews/hn_plugin.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';

class HnSettingsScreen extends StatelessWidget {
  const HnSettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      appBar: AppBar(title: Text(l10n.plugin_hn_title)),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
        children: [
          Icon(
            Icons.forum_outlined,
            size: 40,
            color: HackerNewsPlugin().brandColor,
          ),
          const SizedBox(height: 16),
          Text(
            l10n.plugin_hn_settings_blurb,
            style: Theme.of(context).textTheme.bodyLarge,
          ),
        ],
      ),
    );
  }
}
