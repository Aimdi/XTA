import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
import 'package:xta/plugins/stocks/stocks_row_state.dart';
import 'package:xta/plugins/stocks/stocks_section_header.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';
import 'package:xta/ui/reorder_lift.dart';

/// The watchlist as quote rows under its title and count, ending in a button
/// to add a ticker. A long press lifts a row to drag it elsewhere; tapping
/// opens its page.
class StocksWatchlistList extends StatelessWidget {
  final List<String> symbols;
  final Map<String, TickerQuote> quotes;

  /// Symbols and token ids whose last lookup found nothing.
  final Set<String> failed;
  final Map<String, CryptoAsset> assets;
  final ValueChanged<String> onOpen;
  final ValueChanged<String>? onRetry;
  final VoidCallback? onAdd;

  /// A row dropped elsewhere: its index before it was lifted, and the index
  /// it ends up at.
  final void Function(int from, int to)? onReorder;
  final Future<void> Function() onRefresh;
  final ScrollController? controller;

  /// What to show under a symbol that has two readings, if it has.
  final Widget? Function(String symbol)? choicesFor;

  /// The reading the reader picked for a symbol that names both a coin and a
  /// listed security.
  final TickerInstrument Function(String symbol)? readingOf;

  const StocksWatchlistList({
    super.key,
    required this.symbols,
    required this.quotes,
    required this.onOpen,
    required this.onRefresh,
    this.failed = const {},
    this.assets = const {},
    this.onRetry,
    this.onAdd,
    this.onReorder,
    this.controller,
    this.choicesFor,
    this.readingOf,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ReorderableListView.builder(
        scrollController: controller,
        physics: const AlwaysScrollableScrollPhysics(),
        padding: pluginFeedPadding(context),
        buildDefaultDragHandles: false,
        proxyDecorator: xtaReorderLift,
        header: StocksSectionHeader(
          title: l10n.plugin_stocks_my_watchlist,
          status: l10n.plugin_stocks_ticker_count(symbols.length),
        ),
        footer: _footer(context, l10n),
        itemCount: symbols.length,
        onReorderItem: onReorder ?? (_, _) {},
        itemBuilder: (context, index) => ReorderableDelayedDragStartListener(
          key: ValueKey(symbols[index]),
          index: index,
          enabled: onReorder != null,
          child: Material(
            type: MaterialType.transparency,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [_row(context, l10n, symbols[index]), const Divider(height: 1)],
            ),
          ),
        ),
      ),
    );
  }

  Widget _row(BuildContext context, L10n l10n, String symbol) {
    final asset = assets[symbol];
    final quote = quotes[symbol];
    final reading = readingOf?.call(symbol) ?? TickerInstrument.auto;
    final name = asset == null ? stockRowName(symbol, quote, reading: reading) : null;
    final choices = choicesFor?.call(symbol);
    return StocksQuoteRow(
      title: asset?.bareLabel ?? name ?? spokenCashtag(symbol),
      network: asset?.chainName,
      subtitle: asset == null
          ? stockSubtitle(l10n, symbol, stockKindOf(symbol, quote, reading: reading), named: name != null)
          : asset.shortAddress,
      quote: quote,
      failed: failed.contains(symbol),
      onTap: () => onOpen(symbol),
      onRetry: onRetry == null ? null : () => onRetry!(symbol),
      choices: choices,
      warning: choices == null ? null : l10n.plugin_stocks_two_readings,
    );
  }

  Widget _footer(BuildContext context, L10n l10n) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(16, 20, 16, 0),
      child: Column(
        children: [
          if (onAdd != null)
            OutlinedButton.icon(
              onPressed: onAdd,
              style: OutlinedButton.styleFrom(
                minimumSize: const Size.fromHeight(48),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                side: BorderSide(color: theme.colorScheme.outlineVariant),
              ),
              icon: const Icon(Icons.add, size: 20),
              label: Text(l10n.plugin_stocks_add),
            ),
          if (onReorder != null && symbols.length > 1) ...[
            const SizedBox(height: 12),
            Text(
              l10n.plugin_stocks_reorder_hint,
              textAlign: TextAlign.center,
              style: theme.textTheme.bodySmall!.copyWith(fontSize: 13, color: theme.colorScheme.onSurfaceVariant),
            ),
          ],
        ],
      ),
    );
  }
}
