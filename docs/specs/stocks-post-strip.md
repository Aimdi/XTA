# Stocks in posts: the price strip (concept C)

The owner chose concept C, "Kursleiste", of the Stocks design's "Aktien in
Tweets" boards for posts that carry cashtags. It replaces concept A (each
cashtag followed by its move), built in `stocks-redesign.md`.

Read-only as before: prices are looked up, nothing is traded or posted.

## Before

The first four cashtags of a post were each followed by their move in the
text, `$DOT +3,98%`, appearing as the prices arrived.

## After

- **The text** keeps its cashtags as plain links in the accent, without a
  move, so a sentence reads the way it was written.
- **The strip** sits at the foot of the post, above its actions: one line,
  from the text's start to the card's edge, a hairline above and below. It
  scrolls sideways when the entries do not fit.
- **One entry per cashtag**, the first four of the post, in their order. An
  entry is the symbol (14 sp, bold), the day's line as a small sparkline
  (32 × 14, in the colour of the move), the price (14 sp) and the move
  (13 sp, medium, green or red). A thin divider stands between entries.
- **States**: while the price loads, a grey bar where the figures go; with no
  price, **Kein Kurs** in the secondary colour; a closed market's price and
  move in the secondary colour, without the line.
- **Tapping** an entry opens the ticker's page; each entry is a 48 dp target
  and a screen reader hears its symbol, price and move.
- A post without cashtags, and a quoted post inside another, have no strip.
- The cashtag move after the text (concept A) goes, as do its widgets.

## Code

- `lib/tweet/cashtag_quotes.dart`: `CashtagStrip`, the strip and its entries;
  the inline `CashtagText` goes.
- `lib/utils/_entities.dart`, `lib/utils/rich_text.dart`: cashtags are plain
  links again (kept from being scaled twice by the reader's text size).
- `lib/tweet/tweet.dart`: the strip above the actions.

## Tests

- A post with five cashtags shows four entries in order, each with symbol,
  price and move; no entry for the fifth; the cashtags in the text have no
  move.
- Loading, no price and a closed market as above; a tap opens the ticker.
- No strip on a post without cashtags.
- Fits 320 dp at 1.3× text without overflow; renders beside the board.
