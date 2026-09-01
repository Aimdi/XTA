import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/tweet/ticker/ticker_chart.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';
import 'package:xta/tweet/ticker/ticker_news.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_quote_cache.dart';
import 'package:xta/tweet/ticker/ticker_range.dart';
import 'package:xta/tweet/ticker/ticker_search.dart';
import 'package:xta/tweet/ticker/ticker_stats.dart';
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

  TickerQuote? _quote;
  bool _quoteFailed = false;
  bool _loadingQuote = false;
  List<TickerNewsItem> _news = const [];
  bool _newsRequested = false;

  TickerRange _range = TickerRange.month;

  /// The point under the reader's finger, if any. While it is set the header
  /// reports that moment rather than the latest price — which is the whole
  /// point of being able to touch the chart.
  TickerPoint? _scrubbed;

  @override
  void dispose() {
    _feed.dispose();
    super.dispose();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_quote == null && !_quoteFailed && !_loadingQuote && _chartEnabled) {
      _loadQuote();
    }
    if (!_newsRequested) {
      _newsRequested = true;
      _loadNews();
    }
  }

  bool get _chartEnabled =>
      PrefService.of(context, listen: false).get<bool>(optionTickerChart) ==
      true;

  Future<void> _loadQuote() async {
    final range = _range;
    setState(() {
      _loadingQuote = true;
      _quoteFailed = false;
    });
    try {
      final quote = await _client.fetchQuote(
        widget.symbol,
        range: range.range,
        interval: range.interval,
      );
      if (range != _range) {
        // The reader moved on while this was in flight; the newer request owns
        // the screen.
        return;
      }
      if (mounted) {
        setState(() {
          _quote = quote;
          _loadingQuote = false;
        });
        try {
          context.read<TickerQuoteCache>().remember(widget.symbol, quote);
        } on ProviderNotFoundException {
          // Tests and a missing provider still show the page.
        }
      }
    } on TickerException {
      // A missing price is not worth an error screen: the posts below are the
      // reason the ticker was tapped, and they are unaffected.
      if (mounted) {
        setState(() {
          _quoteFailed = true;
          _loadingQuote = false;
        });
      }
    }
  }

  Future<void> _loadNews() async {
    try {
      final news = await _client.fetchNews(widget.symbol);
      if (mounted) {
        setState(() => _news = news);
      }
    } on TickerException {
      // Headlines are extra; the posts below still work.
    }
  }

  Future<TweetPageResult> _loadPage(String? cursor) async {
    final result = await Twitter.searchTweets(
      '\$${widget.symbol}',
      true,
      cursor: cursor,
    );
    return (chains: result.chains, nextCursor: result.cursorBottom);
  }

  /// StockTwits-style symbol header: cashtag large, price loud, day's move as
  /// a coloured pill — then the chart and the community feed below.
  Widget _quoteHeader(BuildContext context, TickerQuote quote) {
    final theme = Theme.of(context);
    final scrubbed = _scrubbed;
    final price =
        scrubbed?.close ?? quote.displayPrice ?? quote.points.last.close;
    final percent = quote.changePercent;
    final change = quote.change;
    final colour = stockChangeColour(quote.isUp);

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '\$${widget.symbol.toUpperCase()}',
            style: theme.textTheme.titleLarge!.copyWith(
              fontWeight: FontWeight.w900,
              letterSpacing: 0.2,
            ),
          ),
          if (quote.shortName != null)
            Text(
              quote.shortName!,
              style: theme.textTheme.bodyMedium!.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          if (quote.isPreMarket || quote.isAfterHours)
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Text(
                quote.isPreMarket
                    ? L10n.of(context).plugin_stocks_pre_market
                    : L10n.of(context).plugin_stocks_after_hours,
                style: theme.textTheme.labelSmall!.copyWith(
                  color: theme.colorScheme.outline,
                ),
              ),
            ),
          const SizedBox(height: 4),
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Text(
                stockMoneyFormat.format(price),
                style: theme.textTheme.headlineMedium!.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
              if (quote.currency != null) ...[
                const SizedBox(width: 6),
                Text(
                  quote.currency!,
                  style: theme.textTheme.bodyMedium!.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
              const Spacer(),
              if (scrubbed != null)
                Text(
                  DateFormat.yMMMd().add_Hm().format(scrubbed.at),
                  style: theme.textTheme.bodySmall,
                )
              else if (percent != null)
                _ChangeBadge(
                  changeLabel: change == null ? null : stockChangeLabel(change),
                  percentLabel: stockPercentLabel(percent),
                  colour: colour,
                ),
            ],
          ),
        ],
      ),
    );
  }

  /// The ranges, as a row of chips. Changing one reloads the chart and leaves
  /// the posts below untouched.
  Widget _rangePicker(BuildContext context) {
    return SizedBox(
      height: 40,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12),
        children: [
          for (final range in TickerRange.values)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: ChoiceChip(
                label: Text(tickerRangeLabel(context, range)),
                selected: range == _range,
                onSelected: (_) {
                  if (range == _range) return;
                  setState(() {
                    _range = range;
                    _quote = null;
                    _scrubbed = null;
                  });
                  _loadQuote();
                },
              ),
            ),
        ],
      ),
    );
  }

  Widget? _chartSection(BuildContext context) {
    if (!_chartEnabled || _quoteFailed) {
      return null;
    }

    final quote = _quote;
    if (quote == null) {
      // Keeping the picker visible while a range loads means the row does not
      // vanish under the finger that just tapped it.
      return Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(
            height: 200,
            child: Center(child: CircularProgressIndicator()),
          ),
          _rangePicker(context),
        ],
      );
    }

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        _quoteHeader(context, quote),
        const SizedBox(height: 8),
        TickerChart(
          quote: quote,
          onScrub: (point) => setState(() => _scrubbed = point),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
          child: TickerStats(quote: quote, showCurrency: false),
        ),
        _rangePicker(context),
        const SizedBox(height: 4),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final chart = _chartSection(context);

    return XtaSystemBars(
      child: Scaffold(
        appBar: AppBar(
          title: Text('\$${widget.symbol.toUpperCase()}'),
          actions: [_WatchlistButton(symbol: widget.symbol)],
        ),
        // Chart scrolls away so the cashtag feed — the StockTwits reason to open
        // a symbol — owns most of the screen.
        body: NestedScrollView(
          headerSliverBuilder: (context, _) => [
            if (chart != null) SliverToBoxAdapter(child: chart),
            if (_news.isNotEmpty)
              SliverToBoxAdapter(child: TickerNewsList(items: _news)),
          ],
          body: TweetContextScope(
            child: PaginatedTweetList(
              feed: _feed,
              loadPage: _loadPage,
              username: null,
              firstPageErrorPrefix: L10n.of(
                context,
              ).unable_to_load_the_tweets,
              newPageErrorPrefix: L10n.of(
                context,
              ).unable_to_load_the_next_page_of_tweets,
              emptyMessage: L10n.of(context).no_posts_match_your_search,
            ),
          ),
        ),
      ),
    );
  }
}

/// Day's move as a filled pill — StockTwits's quick read of up vs down.
class _ChangeBadge extends StatelessWidget {
  final String? changeLabel;
  final String percentLabel;
  final Color colour;

  const _ChangeBadge({
    required this.changeLabel,
    required this.percentLabel,
    required this.colour,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: colour.withValues(alpha: 0.16),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Text(
            percentLabel,
            style: Theme.of(context).textTheme.titleSmall!.copyWith(
              fontWeight: FontWeight.w800,
              color: colour,
            ),
          ),
          if (changeLabel != null)
            Text(
              changeLabel!,
              style: Theme.of(context).textTheme.labelSmall!.copyWith(
                fontWeight: FontWeight.w600,
                color: colour,
              ),
            ),
        ],
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
