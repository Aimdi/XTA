import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_home_dock.dart';
import 'package:xta/plugins/x/x_reader_routes.dart';

/// Reader tools shared by X's home source and its standalone client.
///
/// Inside Home they join the compact source header and its options sheet;
/// elsewhere they stay a row above the timeline.
class XReaderChrome extends StatelessWidget {
  final VoidCallback onSearch;
  final ValueChanged<XReaderDestination> onOpenDestination;

  const XReaderChrome({super.key, required this.onSearch, required this.onOpenDestination});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final search = IconButton(
      key: const ValueKey('x-reader-search'),
      tooltip: l10n.search_in_plugin(l10n.source_x),
      style: pluginActionButtonStyle,
      icon: const Icon(Icons.search),
      onPressed: onSearch,
    );
    return PluginDockContribution(
      slot: 'navigation',
      content: PluginDockContent(
        actions: [
          search,
          for (final destination in XReaderDestination.values)
            IconButton(
              tooltip: _label(l10n, destination),
              icon: Icon(_icon(destination)),
              onPressed: () => onOpenDestination(destination),
            ),
        ],
      ),
      fallback: Material(
        color: Theme.of(context).scaffoldBackgroundColor,
        child: Padding(
          padding: const EdgeInsetsDirectional.only(start: 16, end: 4),
          child: Row(
            children: [
              Expanded(
                child: Semantics(header: true, child: Text(l10n.foryou, style: Theme.of(context).textTheme.titleSmall)),
              ),
              search,
              PopupMenuButton<XReaderDestination>(
                key: const ValueKey('x-reader-library-menu'),
                tooltip: '${l10n.source_x}: ${l10n.subscriptions}, ${l10n.saved}, ${l10n.account}',
                style: pluginActionButtonStyle,
                icon: const Icon(Icons.more_horiz),
                onSelected: onOpenDestination,
                itemBuilder: (_) => [for (final destination in XReaderDestination.values) _shortcut(l10n, destination)],
              ),
            ],
          ),
        ),
      ),
    );
  }

  static IconData _icon(XReaderDestination destination) => switch (destination) {
    XReaderDestination.subscriptions => Icons.people_outline,
    XReaderDestination.saved => Icons.bookmarks_outlined,
    XReaderDestination.accounts => Icons.manage_accounts_outlined,
  };

  static String _label(L10n l10n, XReaderDestination destination) => switch (destination) {
    XReaderDestination.subscriptions => l10n.subscriptions,
    XReaderDestination.saved => l10n.saved,
    XReaderDestination.accounts => l10n.account,
  };

  PopupMenuItem<XReaderDestination> _shortcut(L10n l10n, XReaderDestination destination) => PopupMenuItem(
    key: ValueKey('x-reader-${destination.name}'),
    value: destination,
    child: Row(
      children: [
        Icon(_icon(destination)),
        const SizedBox(width: 12),
        Flexible(child: Text(_label(l10n, destination))),
      ],
    ),
  );
}
