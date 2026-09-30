import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/rss/rss_opml_controls.dart';
import 'package:xta/reading/feed_appearance_controls.dart';
import 'package:xta/reading/feed_appearance_scope.dart';
import 'package:xta/reading/feed_appearance_store.dart';
import 'package:xta/reading/reader_translation_settings.dart';
import 'package:xta/reading/reading_history_screen.dart';
import 'package:xta/reading/shared_filter_settings.dart';
import 'package:xta/settings/settings_chrome.dart';

class ReaderToolEntry {
  final IconData icon;
  final String Function(BuildContext) title;
  final WidgetBuilder builder;
  const ReaderToolEntry({required this.icon, required this.title, required this.builder});
}

/// Future tools contribute real destinations through entries, with no placeholders.
class ReaderToolsSettings extends StatelessWidget {
  final List<ReaderToolEntry> entries;
  const ReaderToolsSettings({super.key, this.entries = const []});
  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final tools = [
      ReaderToolEntry(
        icon: Icons.palette_outlined,
        title: (context) => L10n.of(context).feed_appearance,
        builder: (_) => const FeedAppearanceSettings(),
      ),
      ReaderToolEntry(
        icon: Icons.translate,
        title: (context) => L10n.of(context).translation_title,
        builder: (_) => const ReaderTranslationSettings(),
      ),
      ReaderToolEntry(
        icon: Icons.history,
        title: (context) => L10n.of(context).history_title,
        builder: (_) => const ReadingHistoryScreen(),
      ),
      ReaderToolEntry(
        icon: Icons.filter_alt_outlined,
        title: (context) => L10n.of(context).filters,
        builder: (_) => const SharedFilterSettings(),
      ),
      ReaderToolEntry(
        icon: Icons.rss_feed,
        title: (context) => L10n.of(context).plugin_rss_opml_tool,
        builder: (_) => const RssOpmlScreen(),
      ),
      ...entries,
    ];
    return SettingsPageScaffold(
      title: l10n.reader_tools,
      body: SettingsList(
        children: [
          for (final tool in tools)
            ListTile(
              minTileHeight: 48,
              leading: Icon(tool.icon),
              title: Text(tool.title(context)),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => Navigator.push(context, MaterialPageRoute(builder: tool.builder)),
            ),
        ],
      ),
    );
  }
}

class FeedAppearanceSettings extends StatelessWidget {
  const FeedAppearanceSettings({super.key});
  @override
  Widget build(BuildContext context) {
    final store = FeedAppearanceStore.forPrefs(PrefService.of(context, listen: false));
    return SettingsPageScaffold(
      title: L10n.of(context).feed_appearance,
      body: ScopedBuilder<FeedAppearanceStore, Map<FeedIdentity, FeedAppearance>>(
        store: store,
        onState: (context, _) => SettingsList(
          children: [
            for (final feed in {
              const FeedIdentity('x', 'following'),
              const FeedIdentity('x', 'for-you'),
              ...store.knownFeeds,
            })
              ListTile(
                minTileHeight: 48,
                title: Text(feedAppearanceLabel(context, feed, store)),
                subtitle: Text(
                  store.appearance(feed).inherits
                      ? L10n.of(context).appearance_source_default
                      : L10n.of(context).feed_appearance,
                ),
                trailing: const Icon(Icons.chevron_right),
                onTap: () =>
                    showFeedAppearance(context, feed, store: store, label: feedAppearanceLabel(context, feed, store)),
              ),
          ],
        ),
      ),
    );
  }
}
