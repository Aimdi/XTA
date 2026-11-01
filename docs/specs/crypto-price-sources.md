# Stocks: crypto prices from several sources

The owner asked for coin prices from more than one source, so a refusal
from one host never empties the crypto rows.

Read-only as before: public price endpoints, no key, no account.

## Before

- A **coin** (`$BTC`, `$ETH`, … any `XXX-USD`) is asked of Yahoo Finance's
  chart host — the host every stock, index and chart uses. The row takes the
  week chart and measures the last 24 hours. When Yahoo refuses (429, 403) or
  is down, every coin row, the strip in posts, the tape and the coin's page
  say there is no price, and the coins spend Yahoo's budget that the stocks
  need. (CoinGecko is not used.)
- A **token** (a contract on a chain) is priced by DexScreener alone.

## After

### Coins

- Four sources, tried in order: **Coinbase**, **Kraken**, **Binance**, then
  **Yahoo Finance**. The first that answers gives the chart; its bars are
  the line, the last close the price, and the move is measured the same way
  as before (24 hours for a row, the chart's span on the coin's page).
- An exchange's chart is drawn as Yahoo draws a coin's: the day from
  midnight UTC against the close before it, a longer chart over its span.
  The day's open, high and low are since midnight UTC. One venue's volume is
  not the market's, and a year's range needs a year of bars, so with an
  exchange answering the coin's page shows a dash for volume and the 52-week
  high and low, as the grid does for any figure a source did not send.
- Each exchange is asked for the bars that fit the chart: the day in
  5-minute bars, five days in 30-minute (Coinbase: hourly) bars, months in
  daily bars, five years in weekly bars. An exchange that cannot cover a
  span in one answer (Coinbase past six months) is skipped for that chart.
- A coin is found under each exchange's own name: `BTC-USD` at Coinbase,
  `XBTUSD` at Kraken (and `XDG` for Dogecoin), `BTCUSDT` at Binance. Binance
  quotes in USDT, and the coin's page says so.
- **Resting**: a source that refuses (429, 403, 451, any 5xx), times out or
  answers with something that is not its format rests for 2 minutes,
  doubling up to 30, and is skipped meanwhile; one answer ends the rest. A
  source that does not list a coin is not asked for that coin again this
  session, without resting for the others.
- Only when every source that covers the chart said it has no such coin is
  the cashtag tried as a listed security, as before; when any of them failed
  for another reason, the row says there is no price instead of showing a
  share of the same letters.
- Yahoo stays last for coins, so its budget goes to stocks and indices, which
  have no other source.

### Tokens

- DexScreener first, **GeckoTerminal** second, with the same resting: the
  token's best pool where it is the base token gives the price, the moves
  over 1, 6 and 24 hours, liquidity, volume, market cap and FDV.

## Not in this pass

- CoinGecko: its keyless API allows a handful of calls a minute.
- Spreading coins across sources by turns: a coin keeps the source that
  answers it, so its price does not jump between venues.

## Code

- `lib/tweet/ticker/price_http.dart`: one GET for every price host, mapping
  answers to the existing error kinds.
- `lib/tweet/ticker/coin_sources.dart`: the chart window, the exchanges'
  URIs, names and parsers, the resting, and the chain that tries them.
- `lib/tweet/ticker/ticker_client.dart`: a coin pair goes through the chain;
  everything else to Yahoo as before.
- `lib/plugins/stocks/crypto_client.dart`, `crypto_asset.dart`:
  GeckoTerminal behind DexScreener.

## Tests

- Each parser against its documented answer: bars ascending, price, the move
  over the span and over 24 hours, an unknown pair as "not found".
- The chain: order, a refusal rests the source and the next answers, the rest
  ends after its time and one answer clears it, an unlisted coin skips that
  source only, every "not found" falls through to the listed security and
  any other failure does not.
- Tokens: GeckoTerminal answers when DexScreener refuses.
- Live: each source answers for BTC, GeckoTerminal for a known token, in the
  daily live checks.
