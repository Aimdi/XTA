# Stocks, redesigned

The owner's design "XTA Stocks Redesign" (five boards: Märkte, Watchlist,
Tweets, Trend, Zeilen-Zustände) restyles the Stocks plugin on top of
`stocks-markets-correctness.md`, whose data and states stay. Read-only as
before: quotes are shown, nothing is traded.

The design is drawn on black in Roboto. XTA keeps its own theme: the design's
greys map to the theme's text, secondary-text and divider colours, its purple
to the app accent, its green and red to `stockTrendColour` (readable on light
themes too), its amber to a warning colour with the same contrast check. Its
sizes and weights carry over. Touch targets stay 48 dp where it draws 44.

## After

### Header and tabs

- Two rows. The first: **Stocks** as a title, and on the right **+** (add a
  ticker) and the **sliders** (Stocks settings). The second: the four tabs as
  text, **Watchlist · Tweets · Trend · Märkte**, equal widths, the selected one
  in the accent with a 2 dp underline, a divider under the row. When the four
  labels do not fit (large text on a narrow phone) the row becomes the labelled
  section picker the other plugins use.
- In Home the same two rows replace the one-row plugin bar for Stocks. The title
  opens the source picker, with a small ▾ so it reads as one; the sliders open
  Home's sheet for Stocks (sources, the settings below, pinning).
- The floating + of the previous pass goes: adding is the header's + and a
  button at the end of the watchlist.
- **Stocks settings** (the sliders): Manage watchlist, Price charts (the
  existing chart switch), Fold signal-group posts.

### Rows

One row widget for Märkte and the watchlist, 56 dp (60 with a sparkline):

- **Left:** the name (16 sp, medium) over `$SPX · Index` (13 sp, secondary). A
  symbol without a known name shows the symbol alone over its type. A token
  shows its symbol and a filled network chip (`DOT [Base]`) over its address cut
  in the middle.
- **Right:** the price (15 sp) over the move as text (13 sp, medium): green or
  red. No pill. Beside the move a caption (11 sp, secondary): `24 h`, `1T`, or
  `Schluss Fr.` for a closed market. In Märkte the section says it instead.
- **Closed market:** the price and the move in the secondary colour.
- **Sparkline** (64 × 24, dashed reference line) only for a live row with bars.
- **Loading:** grey bars where the figures go (76 × 14 over 52 × 18), and over
  the name too when there is no name to show yet.
- **No data:** the second line says **Datenquelle liefert keinen Kurs**, and on
  the right **↻ Erneut versuchen** in the accent retries that row.
- **Two readings** (a watchlist symbol that is both a listing and a coin): the
  second line says **Zwei Treffer, bitte Quelle wählen** in amber, and two
  outlined choices under it, name over kind (`Nasdaq-100 · Index`,
  `Indexed Finance · Krypto`); the reading shown now is outlined in the accent.
  The pick is kept per symbol and decides what its row, chip, cashtags in posts
  and ticker page show.

### Märkte

- Sections **Indizes** and **Krypto**, each titled (15 sp, bold) with its state
  on the right (12 sp, secondary, after a dot): **Geschlossen, Schluss Fr.,
  30.10.** with a grey dot when no index trades now (the date of the newest
  close); **Live, Änderung zum Vortag** with a green dot while they do;
  **Live, Änderung 24 h** for coins.
- Index rows have no per-row caption; the section states it once.

### Watchlist

- Titled **Meine Watchlist** with the count on the right (`2 Ticker`).
- After the last row an outlined **+ Ticker hinzufügen** button, and under it
  **Gedrückt halten, um die Reihenfolge zu ändern**: a long press lifts a row
  and dragging moves it; the order is kept. A long press no longer copies a
  token's address; its page still has Copy contract.

### The strip above Tweets and Trend

- Flat entries divided by thin lines, no chips: the symbol (14 sp, bold) and a
  token's network (12 sp, secondary) over the price and the move (13 sp, the
  move coloured); **Kein Kurs** when there is none; a grey bar while loading.
- The entry filtering the feed is in the accent with a 2 dp underline.

### Posts

Superseded for cashtags by `stocks-post-strip.md`: the owner chose concept C,
a price strip at the foot of the post, over the move after each cashtag.

- A cashtag in a post is followed by its move (13 sp, medium, green or red),
  `$DOT +3,98%`, for the first four cashtags of a post, as their prices arrive.
  The chips under the post go.
- A contract address reads in the secondary colour: `Base · 0x23a2…fe51` for a
  watched token (its network), else the address cut in the middle. A tap still
  copies all of it.

### Trend

- A signal-group post (a Telegram invite and three or more cashtags) has an
  amber banner over it: **Mögliche Signal-Gruppe: Telegram-Einladung und 4
  Cashtags** and **Ausblenden**, which folds the post behind that line (a tap
  opens it again). With **Fold signal-group posts** on, they arrive folded.
  They still go to the end of each page.

## Not in this pass

- The ticker page and the token page keep their layout.
- Other plugins keep their bars.

## Code

- `lib/plugins/stocks/stocks_header.dart` (new): the two rows, standalone and
  in Home.
- `lib/home/_feed.dart`: Home uses it for Stocks, two rows tall.
- `lib/plugins/stocks/stocks_quote_row.dart`, `stocks_row_state.dart`: rows.
- `lib/plugins/stocks/stocks_markets.dart`: sections and their state.
- `lib/plugins/stocks/stocks_watchlist_list.dart`, `stocks_store.dart`: title,
  count, add button, reordering kept in prefs.
- `lib/plugins/stocks/stocks_watchlist_reel.dart`: the flat strip.
- `lib/plugins/stocks/stocks_instruments.dart` (new) and
  `lib/tweet/ticker/ticker_quote_cache.dart`: two readings and the pick.
- `lib/plugins/stocks/stocks_section_header.dart` (new): a list's title and
  its state or count.
- `lib/plugins/stocks/stocks_signal_banner.dart` (new): the banner and the
  folds the reader chose.
- `lib/utils/_entities.dart`, `lib/utils/rich_text.dart`,
  `lib/tweet/cashtag_quotes.dart`, `lib/tweet/tweet.dart`: moves after
  cashtags, addresses, no chips under posts.
- `lib/tweet/paginated_tweet_list.dart`: a banner over a chain.
- `lib/plugins/stocks/stocks_posts_feed.dart`, `stocks_screen.dart`: signal
  banners and folding, settings, wiring.

## Tests

- Header: title, + and sliders, four text tabs with the accent on the selected;
  the picker when they do not fit; in Home the title opens the sources.
- Rows in every state, light and black, German at 320 dp ×1.3: name over
  `$SYM · Type`, no pill, captions, closed in the secondary colour, the retry
  button retries, two readings offer both and keep the pick.
- Märkte sections and their state lines.
- Watchlist count, add button, a dragged row stays where it was dropped after
  a reload.
- Strip entries flat, `Kein Kurs`, the selected entry in the accent.
- Posts: a cashtag followed by its move, only the first four; no chips; a
  watched token's address with its network.
- Trend: the banner over a signal post, Ausblenden folds it, the setting folds
  them all.
- Renders of the five boards beside the design.

## As built

Where the build differs from the boards, and why:

- **Two readings** are offered for the cashtags that are coins first
  (`$SOL`, `$LINK`, `$ETH`, …) once a listed security of the same letters
  answers; the board's `$NDX` cannot have two any more, since an index name
  only ever asks for its index. The listing is looked up once per symbol, and
  only for the watchlist. The pick is kept in prefs
  (`other.ticker_instruments`), and the ticker page's own Crypto/Stocks
  choice keeps it too.
- **The signal banner** is over signal-group posts in Tweets as well as
  Trend: both are the same feed. What the reader folded or opened is kept for
  the session, so a post folded and scrolled away is still folded on the way
  back.
- **Crowded rows** at large text: the token's network chip gives way before
  the name; the retry button becomes the arrow alone, with the words as its
  tooltip, when the words would take more than half the row; a reason or a
  caution may take two lines; a row with a caution draws no sparkline.
- **The tabs** fall back to the labelled section picker when a label does not
  fit its quarter of the row less 8 dp (German at 320 dp ×1.3).
- **Posts**: a cashtag and its move, and a hashtag, were scaled twice by the
  reader's text size (once by the paragraph, once by their own text); they are
  drawn at the size of the words around them now. A cut address is one unit
  on the line, with the network of a watched token.
- **A loading row** shows its symbol where the name will be, rather than a
  grey bar over it: the symbol is known before anything is asked, and says
  more than a bar.
- The German tab keeps the existing translation, **Im Trend**.

Renders of the five boards (black in German, light in English, German at
320 dp ×1.3): `flutter test test/stocks_render_test.dart
--dart-define=RENDER_STOCKS=true`, written to `review-artifacts/renders/`.

