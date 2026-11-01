## XTA — aimdi188

### A new look for the feed

The feed takes the "Ink" design: flat, on black, close to X.

- **Posts in two columns, as on X.** The avatar sits in a column of its own, and the name, text, pictures and actions line up beside it. Name, @handle and time share one line: when it is too narrow the handle shortens first, and with large text the handle and time move to a second line. Threads and the replies in a conversation line up the same way; the post you open and quoted posts look as before.
- **Reddit posts look like X posts.** The subreddit's picture as a rounded square, "r/sub u/author · 15 min" on one line, the title as the post's text, the picture inside the column with the same thin border as X pictures, and upvote, comments, save and open in browser across the bottom.
- **Where a post came from, on its avatar.** In a mixed timeline, posts from Reddit, Mastodon, Bluesky and Threads carry a small badge in the network's colour on the avatar instead of a coloured strip and a label. Substack, RSS, booru and web-source posts keep the strip.
- **The Home header.** Posts | Media a little larger, with a thicker marker. Order and filters is now a filter icon with the number of active filters on it. Home feed accounts is a person with a red dot when accounts or groups are turned off; a screen reader still says how many.
- **A flat bottom bar** across the bottom of the screen, with a thin line on top, icons only and the selected one filled. "Show navigation labels" in Settings still adds the names.

On a narrow phone (360 dp) the narrower text column means a post's view count is the first thing left out of its actions.

Everything else is as in aimdi187.

### Licence note

The downloadable voices use sherpa-onnx, whose library includes espeak-ng under GPL-3.0. The accents on mathematical letters come from a subset of Noto Sans Math under the SIL Open Font License. Both licence texts are in Settings → About → Licences. XTA's own source code stays MIT.

### Installation

For most Android phones, use **`xta-aimdi188_arm64-v8a.apk`**. Universal, ARMv7 and x86_64 APKs are also supplied. The base version code is **400001570**, above every aimdi187 variant. The app ID (`com.aimdi.xta`) and release signing identity are unchanged for in-place updates.

For Linux:

- **Debian 12+, Ubuntu 24.04+, Linux Mint 22+**: `sudo apt install ./xta_4.12.0.188_amd64.deb`
- **Ubuntu 22.04, Linux Mint 21, Pop!_OS 22.04, Zorin OS 17**: `sudo apt install ./xta_4.12.0.188_ubuntu22.04_amd64.deb`
- **Arch Linux**: `sudo pacman -U xta-bin-4.12.0.188-1-x86_64.pkg.tar.zst`, or build it from the attached `PKGBUILD` with `makepkg -si`
- **Other distributions**: unpack `xta-aimdi188-linux-x86_64.tar.gz` and run `./install.sh` in it for a menu entry

**`SHA256SUMS-linux`** lists their checksums. See [docs/desktop.md](https://github.com/Aimdi/XTA/blob/claude/main/docs/desktop.md).

The release workflow checks translations, skill synchronization, analysis and the full Flutter test suite on the tagged source before building. **`release-build.json`** and **`SHA256SUMS`** accompany the APKs, and **`xta-aimdi188-debug-symbols.zip`** and **`xta-aimdi188-linux-debug-symbols.zip`** hold the debug symbols for reading crash traces.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi187)
