import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_home_dock.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/websources/web_source_add_screen.dart';
import 'package:xta/plugins/websources/web_source_dialogs.dart';
import 'package:xta/plugins/websources/web_source_mark.dart';
import 'package:xta/plugins/websources/web_source_screen.dart';
import 'package:xta/plugins/websources/web_sources_plugin.dart';
import 'package:xta/plugins/websources/web_sources_settings.dart';
import 'package:xta/plugins/websources/web_sources_store.dart';
import 'package:xta/ui/empty_pane.dart';

/// The Web sources tab: the sources installed, and Add source.
class WebSourcesScreen extends StatelessWidget {
  final ScrollController scrollController;

  const WebSourcesScreen({super.key, required this.scrollController});

  static void _add(BuildContext context) =>
      Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const WebSourceAddScreen()));

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final sources = context.read<WebSourcesStore>();
    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: Column(
        children: [
          PluginHomeChrome(
            title: l10n.plugin_websources_title,
            mark: pluginMark(WebSourcesPlugin(), size: 24),
            accent: webSourcesBrand,
            actions: [
              IconButton(
                tooltip: l10n.plugin_websources_add,
                icon: const Icon(Icons.add),
                onPressed: () => _add(context),
              ),
              PluginHomeSecondaryAction(
                label: l10n.settings,
                icon: const Icon(Icons.settings_outlined),
                onPressed: () =>
                    Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const WebSourcesSettingsScreen())),
              ),
            ],
          ),
          const Divider(height: 1),
          Expanded(
            child: ScopedBuilder<WebSourcesStore, List<InstalledWebSource>>(
              store: sources,
              onState: (context, installed) => installed.isEmpty
                  ? EmptyPane(
                      icon: Icons.travel_explore,
                      message: l10n.plugin_websources_empty,
                      scrollController: scrollController,
                      action: FilledButton(onPressed: () => _add(context), child: Text(l10n.plugin_websources_add)),
                    )
                  : ListView(
                      controller: scrollController,
                      children: [for (final entry in installed) _SourceTile(entry: entry)],
                    ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SourceTile extends StatelessWidget {
  final InstalledWebSource entry;

  const _SourceTile({required this.entry});

  Future<void> _review(BuildContext context) async {
    final sources = context.read<WebSourcesStore>();
    final waiting = entry.waiting;
    if (waiting == null) return;
    if (await confirmWebSource(context, waiting, origin: entry.origin, update: true)) {
      await sources.agreeToUpdate(entry.source.id);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final source = entry.source;
    return ListTile(
      leading: WebSourceMark(source: source, size: 40),
      title: Text(source.name),
      subtitle: Text(
        source.description.isEmpty ? l10n.plugin_websources_lists(source.feeds.length) : source.description,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: entry.waiting == null
          ? null
          : FilledButton.tonal(onPressed: () => _review(context), child: Text(l10n.plugin_websources_review_update)),
      onTap: () => Navigator.push(context, MaterialPageRoute<void>(builder: (_) => WebSourceScreen(source: source))),
    );
  }
}
