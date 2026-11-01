## XTA — aimdi189

### Stocks: right numbers, honest rows

- **Indices work.** Every index (`$SPX`, `$NDX`, `$DAX`, …) asked the price service for a symbol it had encoded twice, so it always failed: the dashes in Markets, and `$NDX` showing a crypto token called Indexed Finance. An index name now asks for its index and nothing else, and on a weekend an index shows Friday's close.
- **A row says what it is doing.** Loading shimmers; a closed market shows its last close with "Close Fri" and a muted move; a symbol with no data says "No data · Tap to retry", and tapping retries it. The watchlist and its strip above Tweets show the same.
- **Every row says what it is**: Index, Stock, ETF, Crypto (with the network for a token, "Crypto · Base"), and indices and coins have their names before any price arrives.
- **Each move says what it is measured over**: "24h" for coins and tokens, "1D" against the previous close. Bitcoin and Ether are now measured over the last 24 hours.
- **Prices under one** keep four significant digits, and from three zeros on the zeros are counted (`0,0₃1234`). A screen reader hears the number.
- **A token is named** by its symbol, else its name, else its address cut in the middle (`0x23a2…fe51`), never the whole address.
- **The bar holds the tabs and a ⋮ menu** with "Manage watchlist"; adding a symbol is a round button over Watchlist and Tweets. The candlestick is in the text colour and the selected tab in the app's accent: green and red only mean a price went up or down.
- **In Home**, a plugin whose bar is only tabs (Stocks, Hacker News, RSS) opens its menu from ⋮ instead of the sliders, and the menu no longer repeats the tabs.
- **Posts**: a contract address in any post reads `0x23a2…fe51` and a tap copies all of it; the price chips under a post are small and share a line; posts with a Telegram invite and three or more cashtags go to the end of each page of Tweets and Trend (nothing is hidden).

### Threads, read as well as X

- Search Threads posts, and open tags in the app.
- Save a whole Threads post, with a bookmark on it and its own filter in Saved.
- A guest profile shows only what Meta reported: no made-up counts, no link to itself, and links in the bio open.
- Pull to refresh reaches every followed account, the ones answered longest ago first.
- When Meta says "not now", XTA waits as long as it was told before trying again.
- Replies can be sorted: Top, Newest, Oldest.

### A smaller app: the voice engine downloads with the first voice

The engine that reads downloaded voices aloud was about a third of the phone APK, and only a downloaded voice uses it. It now downloads with the first voice you download (6 MB for most phones), from this release's own files, and XTA checks each library's checksum before using it. The read-aloud settings list it as **Voice engine** under Downloaded voices. Voices downloaded before this update need it once: their row says so, and until then reading aloud uses the system voice.

### Also

- Mastodon, Bluesky and Threads posts take the Ink layout in the feed.
- A feed, search tab or media grid that was reset loads again instead of staying empty.
- Posts that did not change are no longer built again while you scroll.

### Licence note

The voice engine is sherpa-onnx, whose library includes espeak-ng under GPL-3.0. The accents on mathematical letters come from a subset of Noto Sans Math under the SIL Open Font License. Both licence texts are in Settings → About → Licences. XTA's own source code stays MIT.

### Installation

For most Android phones, use **`xta-aimdi189_arm64-v8a.apk`**. Universal, ARMv7 and x86_64 APKs are also supplied. The base version code is **400001580**, above every aimdi188 variant. The app ID (`com.aimdi.xta`) and release signing identity are unchanged for in-place updates. The `xta-voice-engine-sherpa-onnx-1.13.8-*.tar.xz` files are what the app downloads for read-aloud; there is nothing to install by hand.

For Linux:

- **Debian 12+, Ubuntu 24.04+, Linux Mint 22+**: `sudo apt install ./xta_4.12.0.189_amd64.deb`
- **Ubuntu 22.04, Linux Mint 21, Pop!_OS 22.04, Zorin OS 17**: `sudo apt install ./xta_4.12.0.189_ubuntu22.04_amd64.deb`
- **Arch Linux**: `sudo pacman -U xta-bin-4.12.0.189-1-x86_64.pkg.tar.zst`, or build it from the attached `PKGBUILD` with `makepkg -si`
- **Other distributions**: unpack `xta-aimdi189-linux-x86_64.tar.gz` and run `./install.sh` in it for a menu entry

**`SHA256SUMS-linux`** lists their checksums. See [docs/desktop.md](https://github.com/Aimdi/XTA/blob/claude/main/docs/desktop.md).

The release workflow checks translations, skill synchronization, analysis and the full Flutter test suite on the tagged source before building. **`release-build.json`** and **`SHA256SUMS`** accompany the APKs, and **`xta-aimdi189-debug-symbols.zip`** and **`xta-aimdi189-linux-debug-symbols.zip`** hold the debug symbols for reading crash traces.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi188)
