import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_screen.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/plugins/plugin_backup.dart';
import 'package:xta/settings/backup_category.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_category.dart';
import 'package:xta/plugins/stocks/stocks_screen.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';

/// StockTwits-shaped watchlist: prices in a strip, cashtag posts in a feed.
/// Read-only — quotes are shown, nothing is traded.
class StocksPlugin extends XtaPlugin {
  StocksPlugin();

  @override
  String get id => pluginIdStocks;

  @override
  String get enabledPrefKey => optionPluginStocksEnabled;

  @override
  IconData get icon => Icons.candlestick_chart;

  @override
  PluginCategory get category => PluginCategory.markets;

  /// StockTwits green — the colour of a rising tape on that app.
  @override
  Color get brandColor => const Color(0xFF00C805);

  @override
  String? get homeTabPrefKey => optionPluginStocksShowTab;

  @override
  String title(BuildContext context) => L10n.of(context).plugin_stocks_title;

  @override
  String description(BuildContext context) => L10n.of(context).plugin_stocks_description;

  @override
  NavigationPage homePage(BuildContext context) {
    return NavigationPage(
      pluginIdStocks,
      (c) => L10n.of(c).plugin_stocks_title,
      const Icon(Icons.candlestick_chart_outlined),
      const Icon(Icons.candlestick_chart),
    );
  }

  @override
  Widget homeScreen({required ScrollController scrollController}) {
    return StocksScreen(scrollController: scrollController);
  }

  @override
  List<String> get tables => const [tableStockSubscription];

  @override
  List<PluginBackupSection> get backupSections => [
    PluginBackupSection(
      jsonKey: 'stockSubscriptions',
      table: tableStockSubscription,
      category: BackupCategory.stocks,
      fromMap: StockSubscription.fromMap,
    ),
  ];

  @override
  Future<void> forgetLoadedData(BuildContext context) async {
    await context.read<StocksWatchlistStore>().load();
  }
}
