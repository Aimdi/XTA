## XTA — aimdi189

### Stocks, redesigned

- **A new look, after the owner's design.** Two rows on top: Stocks with **+** and the sliders (Manage watchlist, Stock chart, Fold signal-group posts), then the tabs as text: Watchlist · Tweets · Trending · Markets. A row is the name over `$SPX · Index`, and the price over the move in green or red with what it is measured over ("24h", "1D", or the last close for a closed market); a small line of the day while the market is live. Green and red only ever mean a price went up or down.
- **Prices in posts, as a strip.** A post with cashtags ends in one line of entries: the symbol, a small line of its day, the price and the move. Tapping one opens the ticker. Cashtags in the text stay plain links.
- **Crypto prices from several sources.** Coins ask Coinbase, Kraken and Binance before Yahoo Finance; a source that refuses rests while the next one answers, so one refusal no longer empties every coin row. Tokens ask DexScreener, then GeckoTerminal.
- **On a ticker, people you follow first.** Picking a ticker, or opening its page, shows up to ten posts from the last week by accounts you follow under "Accounts you follow", then everyone else.
- **Indices work.** Every index (`$SPX`, `$NDX`, `$DAX`, …) asked the price service for a symbol it had encoded twice, so it always failed. An index now asks for its index and nothing else, and on a weekend shows Friday's close.
- **A row says what it is doing**: grey bars while it loads, a closed market's figures in grey, and when the source has no price the row says so, with a retry button.
- **One symbol, two things.** When a watchlist symbol is both a listing and a coin (`$NDX`: the Nasdaq-100 or Indexed Finance), its row asks which and keeps the pick, for the row, posts and the ticker's page.
- **The watchlist** ends in **+ Add ticker**, and a long press lifts a row to drag it; the order is kept.
- **Prices under one** keep four significant digits, and from three zeros on the zeros are counted (`0,0₃1234`). A screen reader hears the number.
- **Tokens and addresses**: a token is named by its symbol, else its name, else its address cut in the middle (`0x23a2…fe51`). An address in a post reads the same way, with its network for a watched token, and a tap copies all of it.
- **Trending**: a likely signal-group post (a Telegram invite and three or more cashtags) carries an amber banner with **Hide**, which folds it, and "Fold signal-group posts" folds them all. They still go to the end of each page.

### Threads, read as well as X

- **Search** posts by words or a tag. The results page's title is a search field: other words or a #tag search again there, and an @handle or a post link opens that page.
- **Pictures open in X's viewer** (pull down to close, double-tap to zoom), and several lie along one row, as on X. **Videos** offer every quality Meta sends ("720p"), and the video quality setting picks among them.
- **The opened post** says its time and date under it, as X's does, and its text can be selected. A conversation opens at the post you tapped, and replies can be sorted: Top, Newest, Oldest.
- **Every post has a ⋯ button** for its actions, and opens as the card you drag back to the feed.
- **#tags** in any caption open their posts, and **a quoted post shows its link card**.
- **Double-tap to like** works on Threads posts too when it is on in Settings → Gestures. Likes stay on this device.
- **Feeds**: in Following and groups each followed account brings up to ten posts, so one busy account no longer crowds out the rest. A feed opened offline shows Threads posts as Threads cards. Pull to refresh reaches every followed account, the ones answered longest ago first, and followed accounts keep their current name and picture.
- **Saving**: save a whole Threads post, with a bookmark on it and its own filter in Saved, and keep its pictures and videos offline.
- **Profiles** show only what Meta reported (no made-up counts, no link to itself), and links in the bio open.
- When Meta says "not now", XTA waits as long as it was told before trying again. On the desktop, F5 and Ctrl+R refresh the Threads tab.

### Reddit: the thread view, redesigned

- One quiet column: the post with its author, title, media and text; a row of actions (upvote on this device, comments, share, save); "N comments" with the sort; and comments hanging off thin coloured thread lines instead of tinted bubbles.
- A comment's header folds it ("+N replies" while folded), and its ⋮ holds the author's posts, copy, share and open in browser. The post's author is marked **OP**, comments written since your last visit are marked new, and a removed comment says what happened to it.
- A round button jumps to the next top-level comment. "Load more comments" no longer repeats a thread already shown.

### Profile and Archive

- **A profile's filters are icons** (all, posts, reposts; photos, videos, broadcasts; likes, bookmarks), each with its name as a tooltip and for screen readers.
- **The Archive's top, search first**: the title with a lock that says saves stay on this device, a search field that is always there with **Filter and sort** beside it, and one row with the network picker, the folders and a **+** for a new folder. Sorting and selecting in the Archive are now translated in every language.

### A smaller app: the voice engine downloads with the first voice

The engine that reads downloaded voices aloud was about a third of the phone APK, and only a downloaded voice uses it. It now downloads with the first voice you download (6 MB for most phones), from this release's own files, and XTA checks each library's checksum before using it. The read-aloud settings list it as **Voice engine** under Downloaded voices. Voices downloaded before this update need it once: their row says so, and until then reading aloud uses the system voice.

### Also

- Mastodon, Bluesky and Threads posts take the Ink layout in the feed.
- In Home, a plugin whose bar is only tabs (Hacker News, RSS) opens its menu from ⋮ instead of the sliders, and the menu no longer repeats the tabs.
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
