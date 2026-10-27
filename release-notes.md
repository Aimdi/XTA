## XTA — aimdi185

### Home: Following in one bar

- **One bar instead of two rows.** Posts | Media sit at the start; the order and filters, Feeds, search in the loaded posts and Home feed accounts at the end. The order chip shows how many filters are on; its name is in the tooltip and read out.
- **The side menu is in the Feeds sheet.** The avatar has left the bar: "Open navigation menu" under "Add timeline" opens the menu, and a long press on it opens the avatar sheet.
- **Narrow screens and large text.** When the names do not fit, the tabs move closer together; with large text on a small phone they get a row of their own. Nothing is cut off.
- The bar leaves and comes back with the bottom bar, and nothing folds away on its own while you read.

### The profile, flat

- **No pills, rings or boxes.** Settings is a plain icon. Follow says what it is: "Subscribed" with a check, an eye-off icon when the account is hidden from the main feed, or "Subscribe", and it opens the same menu as before.
- **A tighter header.** A bio's paragraphs sit closer together, the link and the join date share a row, and the private note is one flat row: "Add private note".
- **Tabs and filters are words.** Posts | Replies | Media | Saved share the width when they fit, with a rounded line under the selected one. The filters below them are plain words, the selected one in bold.
- **German counts are written out**, as in 1.379 and 62.646, and German profiles say "Seit August 2024" instead of "Seit August 2024 bei Twitter".

### Fixes

- **Spanish, Hindi, Korean, Dutch and Romanian profiles show the join date again.** They said only "Joined", without it.
- **Screen readers say which tab or filter is selected** on Home's Posts | Media and the profile's filters.

Everything else is as in aimdi184.

### Licence note

The downloadable voices use sherpa-onnx, whose library includes espeak-ng under GPL-3.0. The accents on mathematical letters come from a subset of Noto Sans Math under the SIL Open Font License. Both licence texts are in Settings → About → Licences. XTA's own source code stays MIT.

### Installation

For most Android phones, use **`xta-aimdi185_arm64-v8a.apk`**. Universal, ARMv7 and x86_64 APKs are also supplied. The base version code is **400001540**, above every aimdi184 variant. The app ID (`com.aimdi.xta`) and release signing identity are unchanged for in-place updates.

For Linux, use the Arch package (`sudo pacman -U xta-bin-4.12.0.185-1-x86_64.pkg.tar.zst`), the attached `PKGBUILD` (`makepkg -si`) or the portable tarball. **`SHA256SUMS-linux`** lists their checksums. See [docs/desktop.md](https://github.com/Aimdi/XTA/blob/claude/main/docs/desktop.md).

The release workflow checks translations, skill synchronization, analysis and the full Flutter test suite on the tagged source before building. **`release-build.json`** and **`SHA256SUMS`** accompany the APKs, and **`xta-aimdi185-debug-symbols.zip`** and **`xta-aimdi185-linux-debug-symbols.zip`** hold the debug symbols for reading crash traces.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi184)
