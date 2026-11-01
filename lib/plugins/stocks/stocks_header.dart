import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/plugin_home_dock.dart';

/// The title row: the 22 sp title, or a 48 dp button, whichever is taller.
double stocksTitleRowHeight(BuildContext context) =>
    math.max(56, MediaQuery.textScalerOf(context).scale(22) * 1.3 + 16);

/// The row of text tabs.
double stocksTabRowHeight(BuildContext context) => math.max(48, MediaQuery.textScalerOf(context).scale(15) * 1.4 + 20);

/// Both rows and the line under them: what Home makes room for above Stocks.
double stocksHeaderHeight(BuildContext context) => stocksTitleRowHeight(context) + stocksTabRowHeight(context) + 1;

TextStyle _titleStyle(BuildContext context) =>
    Theme.of(context).textTheme.titleLarge!.copyWith(fontSize: 22, fontWeight: FontWeight.w700);

/// Stocks' header on its own route: **Stocks** with + and the settings, then
/// the four tabs as text.
class StocksHeader extends StatelessWidget {
  final String title;
  final List<PluginHomeTab> tabs;
  final List<Widget> actions;

  const StocksHeader({super.key, required this.title, required this.tabs, required this.actions});

  @override
  Widget build(BuildContext context) => SafeArea(
    bottom: false,
    child: Material(
      color: Theme.of(context).scaffoldBackgroundColor,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            height: stocksTitleRowHeight(context),
            child: IconButtonTheme(
              data: const IconButtonThemeData(style: pluginActionButtonStyle),
              child: Row(
                children: [
                  if (Navigator.canPop(context))
                    const SizedBox.square(dimension: 48, child: BackButton())
                  else
                    const SizedBox(width: 16),
                  Expanded(
                    child: Semantics(
                      header: true,
                      child: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis, style: _titleStyle(context)),
                    ),
                  ),
                  ...actions,
                  const SizedBox(width: 4),
                ],
              ),
            ),
          ),
          StocksTextTabs(tabs: tabs),
          const Divider(height: 1),
        ],
      ),
    ),
  );
}

/// Stocks' header in Home: the same two rows, the title opening the source
/// picker and the settings opening Home's sheet for Stocks.
class StocksHomeHeader extends StatelessWidget {
  final PluginHomeDockStore store;
  final VoidCallback? onPickSource;
  final Widget? services;
  final bool unread;

  const StocksHomeHeader({super.key, required this.store, this.onPickSource, this.services, this.unread = false});

  @override
  Widget build(BuildContext context) => ScopedBuilder<PluginHomeDockStore, Map<String, PluginDockEntry>>(
    store: store,
    onState: (context, _) {
      final l10n = L10n.of(context);
      final navigation = store.content(pluginIdStocks, 'navigation');
      final add = navigation?.actions.whereType<IconButton>().firstOrNull;
      return Material(
        key: const ValueKey('stocks-home-header'),
        color: Theme.of(context).scaffoldBackgroundColor,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            SizedBox(
              height: stocksTitleRowHeight(context),
              child: IconButtonTheme(
                data: const IconButtonThemeData(style: pluginActionButtonStyle),
                child: Row(
                  children: [
                    const SizedBox(width: 8),
                    Flexible(
                      child: _SourceTitle(title: l10n.plugin_stocks_title, unread: unread, onTap: onPickSource),
                    ),
                    const Spacer(),
                    ?add,
                    PluginDockOptionsButton(
                      store: store,
                      source: pluginIdStocks,
                      services: services,
                      includeActions: true,
                      includeSearch: true,
                      showSections: false,
                      tooltip: l10n.plugin_stocks_settings,
                    ),
                    const SizedBox(width: 4),
                  ],
                ),
              ),
            ),
            StocksTextTabs(tabs: navigation?.tabs ?? const []),
            const Divider(height: 1),
          ],
        ),
      );
    },
  );
}

/// `Stocks ▾`: the title that opens Home's sources, as the mark does for the
/// other plugins.
class _SourceTitle extends StatelessWidget {
  final String title;
  final bool unread;
  final VoidCallback? onTap;

  const _SourceTitle({required this.title, required this.unread, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final text = Badge(
      isLabelVisible: unread,
      smallSize: 7,
      child: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis, style: _titleStyle(context)),
    );
    if (onTap == null) return Padding(padding: const EdgeInsetsDirectional.only(start: 8), child: text);
    return Semantics(
      button: true,
      label: [title, if (unread) L10n.of(context).group_has_unread].join(', '),
      excludeSemantics: true,
      child: Tooltip(
        message: L10n.of(context).home_networks,
        child: InkWell(
          key: const ValueKey('plugin-source-picker-$pluginIdStocks'),
          borderRadius: BorderRadius.circular(8),
          onTap: onTap,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 48),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Flexible(child: text),
                  Icon(Icons.expand_more, size: 20, color: Theme.of(context).colorScheme.onSurfaceVariant),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// The four tabs as text of equal width, the selected one in the accent with
/// a line under it. When the labels do not fit, the labelled section picker.
class StocksTextTabs extends StatelessWidget {
  final List<PluginHomeTab> tabs;

  const StocksTextTabs({super.key, required this.tabs});

  @override
  Widget build(BuildContext context) => SizedBox(
    height: stocksTabRowHeight(context),
    child: LayoutBuilder(
      builder: (context, constraints) {
        if (tabs.isEmpty) return const SizedBox.shrink();
        if (!_fits(context, constraints.maxWidth)) {
          return Align(
            alignment: AlignmentDirectional.centerStart,
            child: PluginSectionPicker(tabs: tabs, verticalPadding: 4),
          );
        }
        final accent = stocksAccent(context);
        return Row(
          children: [
            for (final tab in tabs)
              Expanded(
                child: _TextTab(tab: tab, accent: accent),
              ),
          ],
        );
      },
    ),
  );

  bool _fits(BuildContext context, double width) {
    final room = width / tabs.length - 8;
    return tabs.every((tab) => stocksTextWidth(context, tab.label, _tabStyle(context)) <= room);
  }
}

TextStyle _tabStyle(BuildContext context) =>
    Theme.of(context).textTheme.titleSmall!.copyWith(fontSize: 15, fontWeight: FontWeight.w500);

class _TextTab extends StatelessWidget {
  final PluginHomeTab tab;
  final Color accent;

  const _TextTab({required this.tab, required this.accent});

  @override
  Widget build(BuildContext context) => Semantics(
    button: true,
    selected: tab.selected,
    label: tab.label,
    excludeSemantics: true,
    child: InkWell(
      onTap: tab.onTap,
      child: DecoratedBox(
        decoration: BoxDecoration(
          border: Border(bottom: BorderSide(width: 2, color: tab.selected ? accent : Colors.transparent)),
        ),
        child: Center(
          child: Text(
            tab.label,
            maxLines: 1,
            style: _tabStyle(
              context,
            ).copyWith(color: tab.selected ? accent : Theme.of(context).colorScheme.onSurfaceVariant),
          ),
        ),
      ),
    ),
  );
}
