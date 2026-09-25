import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/x/x_reader_routes.dart';

/// Reader tools shared by X's home source and its standalone client.
class XReaderChrome extends StatelessWidget {
  final VoidCallback onSearch;
  final ValueChanged<XReaderDestination> onOpenDestination;

  const XReaderChrome({super.key, required this.onSearch, required this.onOpenDestination});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Material(
      color: Theme.of(context).scaffoldBackgroundColor,
      child: Padding(
        padding: const EdgeInsetsDirectional.only(start: 16, end: 4),
        child: Row(
          children: [
            Expanded(
              child: Semantics(header: true, child: Text(l10n.foryou, style: Theme.of(context).textTheme.titleSmall)),
            ),
            IconButton(
              key: const ValueKey('x-reader-search'),
              tooltip: l10n.search_in_plugin(l10n.source_x),
              style: pluginActionButtonStyle,
              icon: const Icon(Icons.search),
              onPressed: onSearch,
            ),
            PopupMenuButton<XReaderDestination>(
              key: const ValueKey('x-reader-library-menu'),
              tooltip: '${l10n.source_x}: ${l10n.subscriptions}, ${l10n.saved}, ${l10n.account}',
              style: pluginActionButtonStyle,
              icon: const Icon(Icons.more_horiz),
              onSelected: onOpenDestination,
              itemBuilder: (_) => [
                _shortcut(XReaderDestination.subscriptions, Icons.people_outline, l10n.subscriptions),
                _shortcut(XReaderDestination.saved, Icons.bookmarks_outlined, l10n.saved),
                _shortcut(XReaderDestination.accounts, Icons.manage_accounts_outlined, l10n.account),
              ],
            ),
          ],
        ),
      ),
    );
  }

  PopupMenuItem<XReaderDestination> _shortcut(XReaderDestination destination, IconData icon, String label) =>
      PopupMenuItem(
        key: ValueKey('x-reader-${destination.name}'),
        value: destination,
        child: Row(
          children: [
            Icon(icon),
            const SizedBox(width: 12),
            Flexible(child: Text(label)),
          ],
        ),
      );
}
