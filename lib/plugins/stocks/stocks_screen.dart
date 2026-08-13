import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';
import 'package:xta/plugins/stocks/stocks_watchlist_query.dart';
import 'package:xta/plugins/stocks/stocks_watchlist_reel.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/tweet_context_scope.dart';
import 'package:xta/ui/errors.dart';

/// StockTwits-shaped home for the Aktien tab: a watchlist strip over a feed of
/// posts about those tickers.
///
/// The symbols come from [StocksWatchlistStore] — they outlive the screen. The
/// quotes do not: they are one screen's view of a public price service, thrown
/// away with it, so they live in [State] rather than pretending to be app state.
class StocksScreen extends StatefulWidget {
  final ScrollController scrollController;

  const StocksScreen({super.key, required this.scrollController});

  @override
  State<StocksScreen> createState() => _StocksScreenState();
}

class _StocksScreenState extends State<StocksScreen> {
  final TickerClient _client = TickerClient();
  final Map<String, TickerQuote> _quotes = {};

  /// Null = whole watchlist feed; otherwise posts for that one cashtag.
  String? _filterSymbol;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      await context.read<StocksWatchlistStore>().load();
      if (mounted) {
        await _refreshQuotes();
      }
    });
  }

  /// Every symbol at once, and a symbol that fails keeps whatever it had.
  Future<void> _refreshQuotes() async {
    final symbols = context.read<StocksWatchlistStore>().state;
    final fetched = await Future.wait(symbols.map(_fetchQuote));
    if (!mounted) return;

    setState(() {
      _quotes
        ..removeWhere((symbol, _) => !symbols.contains(symbol))
        ..addEntries(fetched.nonNulls);
      if (_filterSymbol != null && !symbols.contains(_filterSymbol)) {
        _filterSymbol = null;
      }
    });
  }

  Future<MapEntry<String, TickerQuote>?> _fetchQuote(String symbol) async {
    try {
      return MapEntry(symbol, await _client.fetchQuote(symbol));
    } on TickerException {
      return null;
    }
  }

  Future<String?> _askForSymbol() {
    final controller = TextEditingController();

    return showDialog<String>(
      context: context,
      builder: (dialogContext) {
        final l10n = L10n.of(dialogContext);
        return AlertDialog(
          title: Text(l10n.plugin_stocks_add),
          content: TextField(
            controller: controller,
            autofocus: true,
            autocorrect: false,
            textCapitalization: TextCapitalization.characters,
            decoration: const InputDecoration(hintText: 'AAPL'),
            onSubmitted: (value) => Navigator.pop(dialogContext, value.trim()),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: Text(l10n.cancel),
            ),
            TextButton(
              onPressed: () =>
                  Navigator.pop(dialogContext, controller.text.trim()),
              child: Text(l10n.ok),
            ),
          ],
        );
      },
    );
  }

  Future<void> _addSymbol() async {
    final entered = await _askForSymbol();
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

    await context.read<StocksWatchlistStore>().add(symbol);
    if (mounted) {
      await _refreshQuotes();
    }
  }

  Future<void> _remove(String symbol) async {
    await context.read<StocksWatchlistStore>().remove(symbol);
    if (mounted) {
      setState(() {
        _quotes.remove(symbol);
        if (_filterSymbol == symbol) {
          _filterSymbol = null;
        }
      });
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
      // Tap again to clear the filter and return to the whole watchlist feed.
      _filterSymbol = _filterSymbol == symbol ? null : symbol;
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final store = context.read<StocksWatchlistStore>();

    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: Column(
        children: [
          PluginHomeChrome(
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
          Expanded(
            // Pull-to-refresh lives on the feed (PaginatedTweetList), so the
            // watchlist strip and the posts refresh together — same as
            // StockTwits's home swipe.
            child: ScopedBuilder<StocksWatchlistStore, List<String>>(
              store: store,
              onError: (_, error) => FullPageErrorWidget(
                error: error,
                stackTrace: null,
                prefix: l10n.plugin_stocks_watchlist,
                onRetry: store.load,
              ),
              onLoading: (_) =>
                  const Center(child: CircularProgressIndicator()),
              onState: (context, symbols) => symbols.isEmpty
                  ? _empty(context, l10n)
                  : _watchlistHome(symbols),
            ),
          ),
        ],
      ),
    );
  }

  Widget _watchlistHome(List<String> symbols) {
    final query = _filterSymbol == null
        ? watchlistCashtagQuery(symbols)
        : watchlistCashtagQuery([_filterSymbol!]);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        StocksWatchlistReel(
          symbols: symbols,
          quotes: _quotes,
          selected: _filterSymbol,
          onSelected: _onChipSelected,
        ),
        const Divider(height: 1),
        Expanded(
          child: _WatchlistPostsFeed(
            key: ValueKey(query),
            query: query,
            onRefreshQuotes: _refreshQuotes,
          ),
        ),
      ],
    );
  }

  Widget _empty(BuildContext context, L10n l10n) {
    return ListView(
      controller: widget.scrollController,
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(24, 64, 24, 24),
      children: [
        Icon(
          Icons.show_chart,
          size: 48,
          color: Theme.of(context).colorScheme.outline,
        ),
        const SizedBox(height: 16),
        Text(l10n.plugin_stocks_empty, textAlign: TextAlign.center),
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

  Future<void> _onRefresh() async {
    await widget.onRefreshQuotes();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return TweetContextScope(
      child: PaginatedTweetList(
        feed: _feed,
        loadPage: _loadPage,
        username: null,
        onRefresh: _onRefresh,
        firstPageErrorPrefix: l10n.unable_to_load_the_tweets,
        newPageErrorPrefix: l10n.unable_to_load_the_next_page_of_tweets,
        emptyMessage: l10n.no_posts_match_your_search,
      ),
    );
  }
}
