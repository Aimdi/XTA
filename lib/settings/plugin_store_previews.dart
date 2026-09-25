import 'package:flutter/material.dart';
import 'package:flutter/widget_previews.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/plugin_store_tile.dart';

@Preview(name: 'Plugin store tile - light', group: 'Settings', size: Size(720, 160), brightness: Brightness.light)
@Preview(name: 'Plugin store tile - dark', group: 'Settings', size: Size(720, 160), brightness: Brightness.dark)
Widget pluginStoreTilePreview() => const _PluginStorePreview();

@Preview(name: 'Plugin store tile - 200% text', group: 'Settings', size: Size(360, 240))
Widget pluginStoreTileLargeTextPreview() => const _PluginStorePreview(width: 320, textScale: 2);

class _PluginStorePreview extends StatelessWidget {
  final double width;
  final double textScale;

  const _PluginStorePreview({this.width = 680, this.textScale = 1});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      theme: ThemeData(colorSchemeSeed: Colors.blue),
      darkTheme: ThemeData(brightness: Brightness.dark, colorSchemeSeed: Colors.blue),
      themeMode: ThemeMode.system,
      home: Builder(
        builder: (context) {
          final media = MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale));
          return Scaffold(
            body: Center(
              child: MediaQuery(
                data: media,
                child: SizedBox(width: width, child: _previewTile(context)),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _previewTile(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return PluginStoreTile(
      leading: Container(
        width: 28,
        height: 28,
        decoration: BoxDecoration(color: theme.colorScheme.primaryContainer, borderRadius: BorderRadius.circular(8)),
        child: Icon(Icons.rss_feed, size: 18, color: theme.colorScheme.onPrimaryContainer),
      ),
      title: Text(
        l10n.plugin_rss_title,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
      ),
      subtitle: Text(
        l10n.plugin_rss_description,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
      ),
      actions: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextButton(onPressed: () {}, child: Text(l10n.plugin_open)),
          const SizedBox(width: 8),
          IconButton(tooltip: l10n.settings, onPressed: () {}, icon: const Icon(Icons.tune)),
        ],
      ),
    );
  }
}
