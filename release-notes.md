## XTA — aimdi174

### Reading posts

- Replies, quotes and reposts can be sorted. On X, replies sort by Relevant, Recent or Most liked, quotes by Recent or Top, and reposts by Recent or Most followers when X sends follower counts. Bluesky and Mastodon sort replies, quotes and reposts on the phone where their data allows it. The choice lasts until the app closes.
- The post menu is a horizontal ⋯ right beside the translate icon. The footer reads reply, repost, like, views, then bookmark and share together at the end, with counts in your language (9,1K, 1,2 Mio.). An opened post shows "time · date · 77K Views" under its text.
- Gold checks for organisations, grey checks for governments, and the organisation's small logo after the check for affiliated accounts, on posts, profiles and people lists. Tapping the logo opens the organisation.
- Article links show as a compact card (picture or link icon, domain, title). They open in an in-app browser whose bottom bar keeps the post the link came from, with its counts and a way back to it. Third-party cookies are refused and tracking parameters removed. If you chose an external browser, links go there.
- Image carousel cards and Grok share cards are drawn instead of left blank, and Grok share links open instead of failing.
- Deleted posts offer "Open on web.archive.org". Posts for paid subscribers no longer break the whole page; they show what X gives and say who can read the rest.
- Poll results stay readable at large text sizes, and a poll nobody voted on no longer marks every option as the winner.
- The follow + on avatars sits on the avatar's rim like on Threads. One tap follows (the + turns into a check), a long press offers "Add to group".
- Profiles can show "Followed by A, B and N others you subscribe to", from follow lists XTA has already read (group Discover, opened Following lists). No extra requests are made, so it often stays hidden at first.

### Videos

- Mastodon and Bluesky videos play in the timeline with the same player as X, and their GIFs loop silently.
- Feed videos no longer show a loading spinner; the picture waits until the video starts. A small frosted mute button is always in the corner. Full-screen live broadcasts and TikTok keep their spinner.
- Downloading a video saves the quality you are watching, and GIFs are saved as MP4 instead of a still picture.
- Video players nothing can come back to are freed sooner, and the profile media grid plays at most as many GIFs as the player pool holds.

### Home, groups and navigation

- The bottom bar follows your finger: it slides away pixel by pixel as you scroll down, comes back as you scroll up, and settles fully in or out when you let go. It no longer fades.
- Anything in a source's ⚙︎ sheet can be pinned to its top bar ("Pin to top bar"), per source, shown as space allows.
- Following uses the same compact header as the other sources, with a new icon.
- The Home account and group filter has slim checkbox rows, All and None, and a long press keeps only that one on.
- Feeds load behind a thin line instead of chips and a "batches checked" row; chips appear only for a failure or cached posts.
- Groups now honour "include replies" and "include reposts" for Bluesky, Mastodon and Threads members, not only X.
- The Archive network filter opens below its button with each network's logo.

### Stocks and crypto

- Prices load for every row, the daily change is really daily, and the colours are readable on light and black. $BTC, $ETH and other major coins open as the coin, with a Crypto/Stocks switch for ambiguous cashtags. Pre-market and after-hours are shown, prices refresh while on screen, and numbers follow your language.
- New tabs: Watchlist, Posts, Trending, Markets, with dense rows (symbol, name, small chart, price, change). The ticker page shows open, previous close, day range, volume and the 52-week range.
- Follow a token by its contract address: paste an 0x… or Solana address, or an explorer link, in the add sheet. Its posts search the address, optionally together with $SYMBOL.

### Downloads, sharing and read-aloud

- Downloads save in the background: pictures to Pictures/XTA, videos to Movies/XTA, everything else to Download/XTA, with "Saved to …" and an Open button. Existing installs move from "Always ask" to this once, unless a folder was ever chosen; Android 9 and older keep the save dialog. "Save to a folder" works again.
- Sharing anything to XTA from another app opens it: X links natively, Bluesky, Mastodon, Threads, Reddit and Substack links in their XTA screens when those are on, other links in the browser, and plain text as a search.
- Read-aloud in Substack no longer goes silent, reads each article with a voice in its own language, and keeps the voice you picked.
- Downloadable voices that read entirely on the phone: Settings → Read aloud → "Downloaded voices" offers German (Thorsten, about 23 MB) and English (LibriTTS-R, about 23 MB), downloaded only when you tap and checked against a fixed fingerprint. Without a matching voice, the phone's own voice is used.

### Under the hood

- X requests use the query ids and switches x.com uses today, and profiles and authors are read from X's newer response shape. The profile Media tab loads exactly as before.
- Signing in with Google now completes, and the sign-in page no longer reloads itself or breaks with "Remove animations".
- XTA notices when X says an account's request budget is spent and moves to another account before X starts refusing.

### Licence note

The downloadable voices use sherpa-onnx, whose Android library includes espeak-ng under GPL-3.0. Its licence text is in Settings → About → Licences. XTA's own source code stays MIT.

Everything from aimdi173 is included: Pixiv catching up with PixEz. Pixiv downloads follow the download setting too, so in background mode a work's pages land in Pictures/XTA, or in the subfolder set in Pixiv's file-name settings. An ugoira export still asks for a folder.

### Installation

For most Android phones, use **`xta-aimdi174_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001430**, above
every aimdi173 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates. The APKs are larger than before because of
the on-device voice engine.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi173)
