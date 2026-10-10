import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';
import 'package:xta/tweet/ticker/ticker_detail_store.dart';
import 'package:xta/tweet/ticker/ticker_news.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_quote_cache.dart';
import 'package:xta/tweet/ticker/ticker_quote_panel.dart';
import 'package:xta/tweet/ticker/ticker_range.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';
import 'package:xta/tweet/tweet_context_scope.dart';
import 'package:xta/ui/reader_chrome.dart';

class TickerScreenArguments {
  /// The ticker without its `$`, e.g. `AAPL`.
  final String symbol;

  TickerScreenArguments({required this.symbol});

  @override
  String toString() => 'TickerScreenArguments{symbol: $symbol}';
}

/// A ticker: what the symbol has done lately, and the posts talking about it.
///
/// The chart is drawn from price data XTA fetches itself rather than embedded
/// from anyone — no third-party page, no scripts, nothing that could carry a
/// tracker into the app. The price service is still an outside request though,
/// so it has a switch, and with it off the posts work exactly as before.
class TickerScreen extends StatelessWidget {
  const TickerScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final args =
        ModalRoute.of(context)!.settings.arguments as TickerScreenArguments;
    return _TickerScreen(symbol: args.symbol);
  }
}

class _TickerScreen extends StatefulWidget {
  final String symbol;

  const _TickerScreen({required this.symbol});

  @override
  State<_TickerScreen> createState() => _TickerScreenState();
}

class _TickerScreenState extends State<_TickerScreen> {
  final TweetFeedController _feed = TweetFeedController();
  final TickerClient _client = TickerClient();
  late final TickerDetailStore _detail = TickerDetailStore(
    symbol: widget.symbol,
    client: _client,
    onDayQuote: _share,
  );
  Timer? _ticker;
  bool _started = false;

  /// A one-day chart is live data; while it is on screen it is re-asked for
  /// this often, without a spinner.
  static const _refreshEvery = Duration(seconds: 60);

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started) return;
    _started = true;
    if (_chartEnabled) {
      _detail.load();
      _ticker = Timer.periodic(_refreshEvery, (_) => _refreshWhileVisible());
    }
    _detail.loadNews();
  }

  @override
  void dispose() {
    _ticker?.cancel();
    _detail.destroy();
    _client.httpClient.close();
    _feed.dispose();
    super.dispose();
  }

  bool get _chartEnabled =>
      PrefService.of(context, listen: false).get<bool>(optionTickerChart) ==
      true;

  void _refreshWhileVisible() {
    final live = _detail.state.range == TickerRange.day;
    if (mounted && live && TickerMode.valuesOf(context).enabled) {
      _detail.load(silent: true);
    }
  }

  /// Today's quote also feeds the watchlist and cashtags in the timeline.
  void _share(TickerQuote quote) {
    if (!mounted) return;
    try {
      context.read<TickerQuoteCache>().remember(widget.symbol, quote);
    } on ProviderNotFoundException {
      // Tests and a missing provider still show the page.
    }
  }

  /// Posts use the cashtag people write — `$SPX`, `$BTC` — not the name the
  /// price host files the symbol under.
  Future<TweetPageResult> _loadPage(String? cursor) async {
    final result = await Twitter.searchTweets(
      '\$${spokenCashtag(widget.symbol)}',
      true,
      cursor: cursor,
    );
    return (chains: result.chains, nextCursor: result.cursorBottom);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return XtaSystemBars(
      child: Scaffold(
        appBar: AppBar(
          title: Text('\$${spokenCashtag(widget.symbol)}'),
          actions: [_WatchlistButton(symbol: widget.symbol)],
        ),
        // Chart scrolls away so the cashtag feed — the StockTwits reason to open
        // a symbol — owns most of the screen.
        body: NestedScrollView(
          headerSliverBuilder: (context, _) => [
            if (_chartEnabled)
              SliverToBoxAdapter(child: TickerQuotePanel(store: _detail)),
            SliverToBoxAdapter(
              child: ScopedBuilder<TickerDetailStore, TickerDetailState>(
                store: _detail,
                onState: (_, state) => state.news.isEmpty
                    ? const SizedBox.shrink()
                    : TickerNewsList(items: state.news),
              ),
            ),
          ],
          body: TweetContextScope(
            child: PaginatedTweetList(
              feed: _feed,
              loadPage: _loadPage,
              username: null,
              firstPageErrorPrefix: l10n.unable_to_load_the_tweets,
              newPageErrorPrefix: l10n.unable_to_load_the_next_page_of_tweets,
              emptyMessage: l10n.no_posts_match_your_search,
            ),
          ),
        ),
      ),
    );
  }
}

/// Star on the ticker page — the getquin "add to watchlist" from a symbol.
class _WatchlistButton extends StatelessWidget {
  final String symbol;

  const _WatchlistButton({required this.symbol});

  @override
  Widget build(BuildContext context) {
    StocksWatchlistStore? store;
    try {
      store = context.read<StocksWatchlistStore>();
    } on ProviderNotFoundException {
      return const SizedBox.shrink();
    }

    final l10n = L10n.of(context);
    return ScopedBuilder<StocksWatchlistStore, List<String>>(
      store: store,
      onState: (_, symbols) {
        final watched = symbols.contains(symbol.toUpperCase());
        return IconButton(
          tooltip: watched
              ? l10n.plugin_stocks_unwatch
              : l10n.plugin_stocks_watch,
          icon: Icon(watched ? Icons.star : Icons.star_outline),
          onPressed: () {
            if (watched) {
              store!.remove(symbol);
            } else {
              store!.add(symbol);
            }
          },
        );
      },
    );
  }
}
