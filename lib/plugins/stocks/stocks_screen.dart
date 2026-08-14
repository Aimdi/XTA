import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/stocks/stocks_add_sheet.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_markets.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';
import 'package:xta/plugins/stocks/stocks_watchlist_query.dart';
import 'package:xta/plugins/stocks/stocks_watchlist_reel.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_quote_cache.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';
import 'package:xta/tweet/tweet_context_scope.dart';
import 'package:xta/ui/errors.dart';

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

  /// 0 watchlist, 1 trending, 2 markets.
  int _tab = 0;

  /// Null = whole watchlist feed; otherwise posts for that one cashtag.
  String? _filterSymbol;

  List<String> _trending = const [];

  TickerQuoteCache get _cache => context.read<TickerQuoteCache>();

  StocksWatchlistStore get _watchlist => context.read<StocksWatchlistStore>();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      await _watchlist.load();
      if (!mounted) return;
      await Future.wait([_refreshQuotes(), _loadTrending()]);
    });
  }

  Future<void> _refreshQuotes() async {
    if (!mounted) return;
    await _cache.ensure([
      ..._watchlist.state,
      ...kMarketIndexSymbols,
      ..._trending,
    ]);
  }

  Future<void> _loadTrending() async {
    try {
      final raw = await _client.fetchTrending();
      if (!mounted) return;
      setState(() {
        _trending = [for (final symbol in raw.take(16)) spokenCashtag(symbol)];
      });
      await _cache.ensure(_trending);
    } on TickerException {
      if (mounted && _trending.isEmpty) {
        setState(() {});
      }
    }
  }

  Future<void> _addSymbol() async {
    final entered = await showStocksAddSheet(context, client: _client);
    if (entered == null || entered.isEmpty || !mounted) return;

    final symbol = StocksWatchlistStore.normaliseTicker(entered);
    if (symbol == null) {
      showSnackBar(
        context,
        icon: '⚠️',
        message: L10n.of(context).plugin_stocks_error,
      );
      return;
    }

    await _watchlist.add(symbol);
    if (mounted) {
      await _cache.ensure([symbol]);
    }
  }

  Future<void> _remove(String symbol) async {
    await _watchlist.remove(symbol);
    if (mounted && _filterSymbol == symbol) {
      setState(() => _filterSymbol = null);
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
                    title: Text('\$$symbol'),
                    trailing: IconButton(
                      tooltip: L10n.of(sheetContext).unsubscribe,
                      icon: const Icon(Icons.delete_outline),
                      onPressed: () => _remove(symbol),
                    ),
                    onTap: () {
                      Navigator.pop(sheetContext);
                      if (mounted) {
                        openTicker(context, symbol);
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
    setState(() {
      _filterSymbol = _filterSymbol == symbol ? null : symbol;
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: Column(
        children: [
          PluginHomeChrome(
            tabs: [
              PluginHomeTab(
                label: l10n.plugin_stocks_watchlist,
                icon: Icons.star_outline,
                selected: _tab == 0,
                onTap: () => setState(() => _tab = 0),
              ),
              PluginHomeTab(
                label: l10n.plugin_stocks_trending,
                icon: Icons.trending_up,
                selected: _tab == 1,
                onTap: () => setState(() => _tab = 1),
              ),
              PluginHomeTab(
                label: l10n.plugin_stocks_markets,
                icon: Icons.public_outlined,
                selected: _tab == 2,
                onTap: () => setState(() => _tab = 2),
              ),
            ],
            actions: [
              IconButton(
                tooltip: l10n.plugin_stocks_add,
                icon: const Icon(Icons.add),
                onPressed: _addSymbol,
              ),
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
        onLoading: (_) => const Center(child: CircularProgressIndicator()),
        onState: (context, symbols) => _tabHome(symbols, quotes, l10n),
      ),
    );
  }

  Widget _tabHome(
    List<String> symbols,
    Map<String, TickerQuote> quotes,
    L10n l10n,
  ) {
    if (_tab == 2) {
      return StocksMarketsList(quotes: quotes);
    }
    if (_tab == 1) {
      return _feedHome(
        symbols: _trending,
        quotes: quotes,
        empty: _empty(context, l10n, trending: true),
      );
    }
    if (symbols.isEmpty) {
      return _empty(context, l10n, trending: false);
    }
    return _feedHome(
      symbols: symbols,
      quotes: quotes,
      empty: _empty(context, l10n, trending: false),
    );
  }

  Widget _feedHome({
    required List<String> symbols,
    required Map<String, TickerQuote> quotes,
    required Widget empty,
  }) {
    if (symbols.isEmpty) {
      return empty;
    }

    final query = _filterSymbol == null
        ? watchlistCashtagQuery(symbols)
        : watchlistCashtagQuery([_filterSymbol!]);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        StocksWatchlistReel(
          symbols: symbols,
          quotes: quotes,
          selected: _filterSymbol,
          onSelected: _onChipSelected,
        ),
        const Divider(height: 1),
        Expanded(
          child: _WatchlistPostsFeed(
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
      controller: widget.scrollController,
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(24, 64, 24, 24),
      children: [
        Icon(
          trending ? Icons.trending_up : Icons.show_chart,
          size: 48,
          color: Theme.of(context).colorScheme.outline,
        ),
        const SizedBox(height: 16),
        Text(
          trending
              ? l10n.plugin_stocks_trending_empty
              : l10n.plugin_stocks_empty,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(
          l10n.plugin_stocks_feed_hint,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.bodyMedium!.copyWith(
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 16),
        Center(
          child: FilledButton.icon(
            onPressed: _addSymbol,
            icon: const Icon(Icons.add),
            label: Text(l10n.plugin_stocks_add),
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

/// Posts about the watchlist (or one filtered ticker), owned as its own feed
/// so changing the query remounts a fresh [TweetFeedController].
class _WatchlistPostsFeed extends StatefulWidget {
  final String query;
  final Future<void> Function() onRefreshQuotes;

  const _WatchlistPostsFeed({
    super.key,
    required this.query,
    required this.onRefreshQuotes,
  });

  @override
  State<_WatchlistPostsFeed> createState() => _WatchlistPostsFeedState();
}

class _WatchlistPostsFeedState extends State<_WatchlistPostsFeed> {
  late final TweetFeedController _feed = TweetFeedController();

  @override
  void dispose() {
    _feed.dispose();
    super.dispose();
  }

  Future<TweetPageResult> _loadPage(String? cursor) async {
    final result = await Twitter.searchTweets(
      widget.query,
      true,
      cursor: cursor,
    );
    return (chains: result.chains, nextCursor: result.cursorBottom);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return TweetContextScope(
      child: PaginatedTweetList(
        feed: _feed,
        loadPage: _loadPage,
        username: null,
        onRefresh: widget.onRefreshQuotes,
        firstPageErrorPrefix: l10n.unable_to_load_the_tweets,
        newPageErrorPrefix: l10n.unable_to_load_the_next_page_of_tweets,
        emptyMessage: l10n.no_posts_match_your_search,
      ),
    );
  }
}
