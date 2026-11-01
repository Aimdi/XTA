# Stocks: right numbers, honest states, plain navigation

A review of the Stocks plugin (Watchlist, Tweets, Trend, Markets) found its
biggest problems in data correctness and state handling, then navigation, then
polish. This pass fixes them in that order. Read-only as before: quotes are
shown, nothing is traded.

## Found

### Data

- **`$NDX` showed Indexed Finance at 0.00181.** `tickerCandidates('NDX')` tries
  `NDX`, then `^NDX`, then `NDX-USD`, and `NDX-USD` is a crypto token. An index
  falls through to it whenever the first two do not answer with a price.
- **Why `^NDX` never answered.** `TickerClient.chartUri` encoded the symbol
  itself and then handed it to `Uri.https`, which encodes the path again:
  `^NDX` went out as `%255ENDX`, a symbol nobody has. Every index failed, every
  day: the dashes in 8 of 10 rows of Markets (the two coins have no `^`), and
  Indexed Finance for `$NDX`. Found while testing this pass; no test had
  looked at the URL of an index.
- **A day without bars was no quote.** `TickerQuote.fromChartJson` returned
  null when a chart had no bars, even when the host sent the last price and
  the close before it. Null counts as "no such symbol", which sends the lookup
  on to the next candidate.
- **"—" means three things.** A row that is loading, one whose fetch failed and
  one whose market is closed look the same: dashes and an empty pill. The quote
  cache swallows failures, so a row cannot tell them apart. The pill was as
  wide as the figures column: its Container was aligned, which makes a
  Container take all the width it is offered.
- **Index rows have no name** until the host answers, and none when it fails.
- **The change has two meanings in one list.** A coin's is not measured against
  a previous close the way an index's is, and nothing says which is which.
- **Sub-cent prices** show three significant digits (`0,00181`).
- **A token without a known symbol** is labelled by its whole contract address,
  cut at the end (`0x23a2...fe...`), while its chip shows `0x23a2…fe51`.

### Navigation and colour

- In Home, the Stocks bar holds the four tabs, `+` and the dock's options
  button (the sliders). Its sheet repeats the four tabs and lists "Watchlist"
  twice: the tab, and the action that manages the list, under the tab's name.
- The candlestick mark wears the plugin's green, so it reads as selected beside
  the tab that is. The selected tab is green too, the same green as a rising
  price, while the sheet and cashtags use the app's purple.

### Tweets and Trend

- A token's chip in the watchlist strip has a second line with its network and
  address; a cashtag's has one.
- A contract address in a post is a 42-character word that takes two lines.
- The quote chips under a post repeat its cashtags at full size and stack.
- Signal-group posts (a Telegram invite, several cashtags, leverage screenshots)
  lead Trend.

## After

### Data

- **An index symbol only ever asks for its index**, encoded once (`%5ENDX`).
  `SPX`, `NDX`, `DJI`, `RUT`, `VIX`, `DAX`, `FTSE`, `N225` (and the other names
  `kTickerAliases` maps) ask for `^GSPC`, `^NDX`, … and nothing else. If the
  index does not answer, the row says so; it never shows another instrument.
- **A day without bars is still a quote.** When the host sends a price and the
  close before it but no bars, the quote has the price, the change against that
  close and no sparkline.
- **Every row says what it is**, with a small badge: Index, Stock, ETF, Crypto
  (with its network for a token: "Crypto · Base"), Fund, Future, Currency, as
  the host types the instrument. A symbol known to be an index or a coin shows
  its badge before any data arrives.
- **Three states instead of "—":**
  - *loading*: a shimmer where the price and pill will be;
  - *market closed* (no session now: night, weekend, holiday): the last close,
    a small "Close Fri" tag (the day of that close) and a muted pill;
  - *no data* (the fetch failed): one line, "No data · Tap to retry", and no
    pill; tapping retries that row. Where the line does not fit beside the
    name (German on a 393 dp phone) it breaks at the dot, never mid-phrase,
    and the name keeps the room it needs.
  The same rows serve Markets and the Watchlist, and the watchlist strip on the
  Tweets tab shows the same three states.
- **Rows have names without data**: S&P 500, Nasdaq-100, Dow Jones, Russell
  2000, VIX, DAX 40, FTSE 100, Nikkei 225, Bitcoin and Ethereum come from a
  fixed map, before and instead of the host's spelling.
- **Each change says its window**, in a caption by the pill: "24h" for a
  rolling 24-hour change, "1D" ("1T" in German) for a change against the
  previous close. Coins on the chart host (BTC, ETH, …) are now measured over
  the last 24 hours (the week chart's half-hour bars, against the bar 24 hours
  before the last), so they are labelled "24h"; DEX tokens already are. A
  coin's one-day chart from the ticker page, which starts at midnight, no
  longer replaces its row's quote.
- **Prices under 1 keep four significant digits**: `0,1234`, `0,01234`,
  `0,001810`; from three zeros after the decimal comma on, the zeros are
  counted: `0,0₃1234`. A screen reader hears the plain number, on the rows,
  the chips, the ticker page and the token page.
- **A token is named by its symbol, else its name, else its address cut in the
  middle** (`0x23a2…fe51`), the same everywhere; its row's second line is its
  name when known, else that address.

### Navigation and colour

- **The bar holds only the tabs** and a ⋮ menu. Adding a symbol is a button
  floating over the Watchlist and Tweets tabs (an empty watchlist keeps its own
  add button instead). The menu holds "Manage watchlist" (renamed from
  "Watchlist", which is the tab), and so does the sheet it opens.
- **In Home**, a bar with tabs and no filters (no reading or following
  controls of its own: Stocks, and so Hacker News and RSS too) gets the ⋮
  instead of the sliders, and its sheet no longer repeats the tabs, which stay
  in the bar. Plugins with filters keep the sliders and their sheet as they
  are.
- **The mark is neutral**, in the text colour, as X's is: a plugin can say its
  colour is not its chrome's (`XtaPlugin.brandsChrome`), and Stocks does.
- **Selection is the app's accent** (purple by default) on Stocks' tabs, chips
  and sheets; green and red only ever mean a price went up or down.

### Tweets and Trend

- **A token's chip has no network line**, like a cashtag's: its label over its
  price and move. Its network and address stay on its row and page.
- **A contract address in a post**, wherever posts are shown, reads cut in the
  middle (`0x23a2…fe51`, a Solana mint `EKpQ…zcjm`) in the link colour, on one
  line; tapping copies the whole address. A long word without a digit stays a
  word.
- **The quote chips under a post** are compact and share rows: symbol, price
  and move in a small outlined chip, as many to a line as fit (at 360 dp two of
  three share the first line, where all three stacked). Only the move is green
  or red; each chip keeps a 48 dp target.
- **Posts with a Telegram link and three or more cashtags go to the end** of
  each loaded page of Tweets and Trend, in their order; nothing is hidden.

## Not in this pass

- The ticker page and the token page keep their layout. On a day without a
  session the ticker page now shows an index's last close and move, over an
  empty one-day chart; the other ranges draw as before.
- Other plugins' bars and colours are unchanged, beyond the ⋮ for a bar that is
  only tabs.

## Code

- `lib/tweet/ticker/ticker_symbol.dart`: index-only candidates, fixed names.
- `lib/tweet/ticker/ticker_quote.dart`: quotes without bars, the instrument
  type, the last regular trade time, the change window.
- `lib/tweet/ticker/ticker_client.dart`: the symbol encoded once.
- `lib/tweet/ticker/quote_failures.dart` (new): which lookups found nothing,
  for both quote stores.
- `lib/tweet/ticker/ticker_quote_cache.dart`: per-symbol failures; coins over
  24 hours.
- `lib/plugins/stocks/crypto_quote_store.dart`: per-token failures.
- `lib/plugins/stocks/stocks_format.dart`: prices and how they are read aloud.
- `lib/plugins/stocks/stocks_row_state.dart` (new): the row states, the close
  tag, the window caption, the badge.
- `lib/plugins/stocks/stocks_quote_row.dart`, `stocks_markets.dart`,
  `stocks_watchlist_list.dart`, `stocks_watchlist_reel.dart`: rows and chips.
- `lib/plugins/stocks/crypto_asset.dart`: a token's label.
- `lib/plugins/stocks/stocks_screen.dart`: the button, the menu.
- `lib/plugins/plugin.dart`, `plugin_marks.dart`, `plugin_compact_header.dart`,
  `plugin_home_dock.dart`: neutral mark, accent selection, ⋮ for a tabs-only bar.
  `stocks_plugin.dart` opts out of brand chrome.
- `lib/utils/rich_text.dart`: addresses in posts.
- `lib/tweet/cashtag_quotes.dart`: compact chips.
- `lib/plugins/stocks/stocks_posts_feed.dart`: signal-group posts last.

## Tests

- `NDX` and the other index names only ever ask for their `^` symbol, encoded
  once; a failing index never resolves to a `-USD` pair.
- A chart answer with a price and no bars is a quote; one with neither is not.
- The quote cache remembers a failed symbol until it is retried or answers; a
  coin is asked for over the week chart and measured over its last day; the
  ticker page's day chart of a coin is not taken.
- Row states: loading shimmer, closed with its day tag and muted pill, no data
  with retry (tapping retries); names and badges before data.
- The change window: "24h" for coins and tokens, "1D" for the rest; a coin's
  24-hour change from two days of bars.
- Prices: four significant digits, subscript zeros, the spoken form.
- Token labels: symbol, else name, else a middle-cut address.
- The bar: tabs and ⋮ only, the floating add button, "Manage watchlist";
  in Home the ⋮ and a sheet without the tabs; the neutral mark; the accent on
  the selected tab.
- A contract address in a post is cut in the middle and copies on tap.
- Compact chips share a row at 360 dp.
- Signal-group posts move to the end of their page, others keep their order.
- German at 320 dp ×1.3 fits.
