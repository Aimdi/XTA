import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_view_store.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';
import 'package:xta/plugins/stocks/stocks_add_sheet.dart';
import 'package:xta/plugins/stocks/stocks_plugin.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_markets.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/crypto_quote_store.dart';
import 'package:xta/plugins/stocks/crypto_asset_screen.dart';
import 'package:xta/plugins/stocks/stocks_posts_feed.dart';
import 'package:xta/plugins/stocks/stocks_watchlist_query.dart';
import 'package:xta/plugins/stocks/stocks_watchlist_reel.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_quote_cache.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/x_controls.dart';

/// Markets + watchlist + trending cashtag feed.
///
/// getquin / Yahoo Finance put a tape of indices and "what's moving" above
/// the social stream; StockTwits puts the watchlist there. This screen is
/// all three, as tabs, still read-only.
class StocksScreen extends StatefulWidget {
  final ScrollController scrollController;

  const StocksScreen({super.key, required this.scrollController});

  @override
  State<StocksScreen> createState() => _StocksScreenState();
}

class _StocksScreenState extends State<StocksScreen> {
  final TickerClient _client = TickerClient();
  final _cryptoQuotes = CryptoQuoteStore();

  /// 0 watchlist, 1 trending, 2 markets.
  final _view = PluginViewStore<_StocksViewState>(
    const _StocksViewState(),
    snapshot: (state) => state.copyWith(loading: false),
  );
  int get _tab => _view.state.tab;

  /// Null = whole watchlist feed; otherwise posts for that one cashtag.
  String? get _filterSymbol => _view.state.filters[_tab];

  List<String> get _trending => _view.state.trending;

  TickerQuoteCache get _cache => context.read<TickerQuoteCache>();

  StocksWatchlistStore get _watchlist => context.read<StocksWatchlistStore>();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      await _watchlist.load();
      if (!mounted) return;
      await _selectTab(_tab);
    });
  }

  @override
  void dispose() {
    _view.destroy();
    _cryptoQuotes.destroy();
    _client.httpClient.close();
    super.dispose();
  }

  Future<void> _selectTab(int tab) async {
    _view.select(_view.state.copyWith(tab: tab));
    if (tab == 1 && _trending.isEmpty) await _loadTrending();
    await _refreshQuotes();
  }

  Future<void> _refreshQuotes() async {
    if (!mounted) return;
    final symbols = switch (_tab) {
      1 => _trending,
      2 => kMarketIndexSymbols,
      _ => _watchlist.state,
    };
    await Future.wait([
      _cache.ensure(symbols.where((symbol) => CryptoAsset.contractForId(symbol) == null)),
      if (_tab == 0) _cryptoQuotes.ensure(_watchlist.cryptoAssets.values),
    ]);
  }

  Future<void> _loadTrending() async {
    if (_view.state.loading) return;
    _view.select(_view.state.copyWith(loading: true, failed: false));
    try {
      final raw = await _client.fetchTrending();
      if (!mounted) return;
      _view.select(
        _view.state.copyWith(trending: [for (final symbol in raw.take(16)) spokenCashtag(symbol)], loading: false),
      );
      await _cache.ensure(_trending);
    } on TickerException {
      if (mounted) _view.select(_view.state.copyWith(loading: false, failed: true));
    }
  }

  Future<void> _addSymbol() async {
    final entered = await showStocksAddSheet(context, client: _client);
    if (entered == null || entered.isEmpty || !mounted) return;

    await _watchlist.add(entered);
    if (mounted) await _refreshQuotes();
  }

  void _openAsset(String symbol) {
    final asset = _watchlist.assetFor(symbol);
    if (asset == null) {
      openTicker(context, symbol);
    } else {
      Navigator.push(context, MaterialPageRoute<void>(
        builder: (_) => CryptoAssetScreen(asset: asset),
      ));
    }
  }

  Future<void> _remove(String symbol) async {
    await _watchlist.remove(symbol);
    if (mounted && _filterSymbol == symbol) {
      _view.select(
        _view.state.copyWith(
          filters: {
            for (final entry in _view.state.filters.entries) entry.key: entry.value == symbol ? null : entry.value,
          },
        ),
      );
    }
  }

  Future<void> _manageWatchlist() async {
    await showModalBottomSheet(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) {
        final store = sheetContext.read<StocksWatchlistStore>();
        return SafeArea(
          child: ScopedBuilder<StocksWatchlistStore, List<String>>(
            store: store,
            onState: (_, symbols) => ListView(
              shrinkWrap: true,
              children: [
                ListTile(
                  title: Text(L10n.of(sheetContext).plugin_stocks_watchlist),
                  subtitle: Text(L10n.of(sheetContext).plugin_stocks_feed_hint),
                ),
                for (final symbol in symbols)
                  ListTile(
                    title: Text(store.labelFor(symbol)),
                    subtitle: store.assetFor(symbol) == null ? null : Text(store.assetFor(symbol)!.subtitle),
                    trailing: IconButton(
                      tooltip: L10n.of(sheetContext).unsubscribe,
                      icon: const Icon(Icons.delete_outline),
                      onPressed: () => _remove(symbol),
                    ),
                    onTap: () {
                      Navigator.pop(sheetContext);
                      if (mounted) {
                        _openAsset(symbol);
                      }
                    },
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  void _onChipSelected(String symbol) {
    _view.select(
      _view.state.copyWith(filters: {..._view.state.filters, _tab: _filterSymbol == symbol ? null : symbol}),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    _view.restore(context, 'stocks');

    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: ScopedBuilder<PluginViewStore<_StocksViewState>, _StocksViewState>(
        store: _view,
        onState: (_, _) => Column(
          children: [
            PluginHomeChrome(
              title: l10n.plugin_stocks_title,
              mark: pluginMark(StocksPlugin(), size: 24),
              accent: StocksPlugin().brandColor,
              tabs: [
                PluginHomeTab(
                  label: l10n.plugin_stocks_watchlist,
                  icon: Icons.star_outline,
                  selected: _tab == 0,
                  onTap: () => _selectTab(0),
                ),
                PluginHomeTab(
                  label: l10n.plugin_stocks_trending,
                  icon: Icons.trending_up,
                  selected: _tab == 1,
                  onTap: () => _selectTab(1),
                ),
                PluginHomeTab(
                  label: l10n.plugin_stocks_markets,
                  icon: Icons.public_outlined,
                  selected: _tab == 2,
                  onTap: () => _selectTab(2),
                ),
              ],
              actions: [
                IconButton(tooltip: l10n.plugin_stocks_add, icon: const Icon(Icons.add), onPressed: _addSymbol),
                IconButton(
                  tooltip: l10n.plugin_stocks_watchlist,
                  icon: const Icon(Icons.list),
                  onPressed: _manageWatchlist,
                ),
              ],
            ),
            const Divider(height: 1),
            Expanded(child: _body(l10n)),
          ],
        ),
      ),
    );
  }

  Widget _body(L10n l10n) {
    return ScopedBuilder<TickerQuoteCache, Map<String, TickerQuote>>(
      store: _cache,
      onState: (_, quotes) => ScopedBuilder<StocksWatchlistStore, List<String>>(
        store: _watchlist,
        onError: (_, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: l10n.plugin_stocks_watchlist,
          onRetry: _watchlist.load,
        ),
        onLoading: (_) => const PluginFeedSkeleton(),
        onState: (context, symbols) => ScopedBuilder<CryptoQuoteStore, Map<String, CryptoMarket>>(
          store: _cryptoQuotes,
          onState: (_, crypto) => _tabHome(symbols, {
            ...quotes, for (final entry in crypto.entries) entry.key: entry.value.quote,
          }, l10n),
        ),
      ),
    );
  }

  Widget _tabHome(List<String> symbols, Map<String, TickerQuote> quotes, L10n l10n) {
    if (_tab == 2) {
      return StocksMarketsList(quotes: quotes);
    }
    if (_tab == 1) {
      if (_view.state.loading && _trending.isEmpty) return const PluginFeedSkeleton();
      return _feedHome(symbols: _trending, quotes: quotes, empty: _empty(context, l10n, trending: true));
    }
    if (symbols.isEmpty) {
      return _empty(context, l10n, trending: false);
    }
    return _feedHome(symbols: symbols, quotes: quotes, empty: _empty(context, l10n, trending: false));
  }

  Widget _feedHome({required List<String> symbols, required Map<String, TickerQuote> quotes, required Widget empty}) {
    if (symbols.isEmpty) {
      return empty;
    }

    final query = _filterSymbol == null ? watchlistCashtagQuery(symbols) : watchlistCashtagQuery([_filterSymbol!]);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        StocksWatchlistReel(symbols: symbols, quotes: quotes, selected: _filterSymbol, onSelected: _onChipSelected,
          assets: _watchlist.cryptoAssets, onOpen: _openAsset),
        Padding(
          padding: const EdgeInsetsDirectional.fromSTEB(16, 4, 16, 8),
          child: Wrap(
            spacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              Text(L10n.of(context).tweets, style: Theme.of(context).textTheme.titleMedium),
              if (_filterSymbol != null)
                InputChip(
                  label: Text(_watchlist.labelFor(_filterSymbol!)),
                  deleteButtonTooltipMessage: L10n.of(context).plugin_reader_reset_filters,
                  materialTapTargetSize: MaterialTapTargetSize.padded,
                  onDeleted: () => _onChipSelected(_filterSymbol!),
                ),
            ],
          ),
        ),
        const Divider(height: 1),
        Expanded(
          child: StocksPostsFeed(
            key: ValueKey(query),
            query: query,
            onRefreshQuotes: () async {
              await _refreshQuotes();
              if (_tab == 1) {
                await _loadTrending();
              }
            },
          ),
        ),
      ],
    );
  }

  Widget _empty(BuildContext context, L10n l10n, {required bool trending}) {
    return ListView(
      controller: pluginInnerScrollController(context, widget.scrollController),
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(24, 64, 24, 24),
      children: [
        Icon(trending ? Icons.trending_up : Icons.show_chart, size: 48, color: Theme.of(context).colorScheme.outline),
        const SizedBox(height: 16),
        Text(
          trending
              ? (_view.state.failed ? l10n.plugin_stocks_error : l10n.plugin_stocks_trending_empty)
              : l10n.plugin_stocks_empty,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(
          l10n.plugin_stocks_feed_hint,
          textAlign: TextAlign.center,
          style: Theme.of(
            context,
          ).textTheme.bodyMedium!.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant),
        ),
        const SizedBox(height: 16),
        Center(
          child: FilledButton.icon(
            style: xPrimaryPillStyle(context),
            onPressed: trending ? _loadTrending : _addSymbol,
            icon: Icon(trending ? Icons.refresh : Icons.add),
            label: Text(trending ? l10n.retry : l10n.plugin_stocks_add),
          ),
        ),
        if (!trending && _trending.isNotEmpty) ...[
          const SizedBox(height: 24),
          Text(l10n.plugin_stocks_trending, textAlign: TextAlign.center),
          const SizedBox(height: 8),
          Wrap(
            alignment: WrapAlignment.center,
            spacing: 8,
            children: [
              for (final symbol in _trending.take(8))
                ActionChip(
                  label: Text('\$$symbol'),
                  onPressed: () async {
                    await _watchlist.add(symbol);
                    if (mounted) {
                      await _cache.ensure([symbol]);
                    }
                  },
                ),
            ],
          ),
        ],
      ],
    );
  }
}

class _StocksViewState {
  final int tab;
  final Map<int, String?> filters;
  final List<String> trending;
  final bool loading;
  final bool failed;
  const _StocksViewState({
    this.tab = 0,
    this.filters = const {},
    this.trending = const [],
    this.loading = false,
    this.failed = false,
  });
  _StocksViewState copyWith({
    int? tab,
    Map<int, String?>? filters,
    List<String>? trending,
    bool? loading,
    bool? failed,
  }) => _StocksViewState(
    tab: tab ?? this.tab,
    filters: filters ?? this.filters,
    trending: trending ?? this.trending,
    loading: loading ?? this.loading,
    failed: failed ?? this.failed,
  );
}
