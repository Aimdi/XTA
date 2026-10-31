## XTA — aimdi187

### Web sources: new sites without an update

- **A new plugin, Web sources.** Add simple sites from the list this project publishes (Lobsters, DEV Community, Lemmy.world, NASA Images and Tildes to start) or from a link to a source file. Each opens on its lists as tabs, with its search, articles in XTA's reader and pictures in a grid. Follow a list to see it in Subscriptions and groups, and, if you turn it on, in the Following timeline.
- **You decide what it reads.** Before a source is added, XTA lists every site it will contact, and nothing is fetched from them until you agree. A source only reads, only from the sites it lists, and an update that asks for a new site waits for your OK.
- **Anyone can write one**: a source is a short file of rules, not code. [docs/web-sources.md](https://github.com/Aimdi/XTA/blob/claude/main/docs/web-sources.md) explains how.

### Fixes that arrive without an update

- **Threads, Instagram, TikTok, Pixiv, Reddit and the stock quotes can be repaired from this project's side.** When one of them changes an app id, a request id, a version or the browser it expects, a published correction now reaches XTA within a day, without a new release. A correction can only replace a value of the same kind: it can never send a request anywhere else.
- **X's request ids**: every install now reads X corrections from the same place, so older and newer releases get the same fixes.

### X

- **Followed accounts stay current.** When an account you follow changes its name, handle or picture, XTA updates it from the posts it already loads, with no extra requests.

### Danbooru

- **Tag suggestions work as on the site**: other names, aliases and typos lead to the right tag (kancolle → kantai_collection), with the matching part in bold.
- **Searches with more tags than Danbooru allows now run.** XTA sends what the site accepts and sorts out the rest on your device.

### The PC version (Linux)

- **Packages for Debian, Ubuntu and Linux Mint.** One for Debian 12+, Ubuntu 24.04+ and Mint 22+, and one for Ubuntu 22.04, Mint 21, Pop!_OS 22.04 and Zorin OS 17, where aimdi186 could not start. Install with `sudo apt install ./xta_*.deb`.
- **The tarball installs itself**: `./install.sh` adds XTA to your application menu without root, and `./install.sh --uninstall` removes it.
- **One window**: starting XTA again brings its window to the front instead of opening a second copy, and the window reopens at the size you left it.
- **Mouse and keyboard**: the mouse's back button, Alt+← and Esc go back; Home has a refresh button, and F5 or Ctrl+R refreshes the timeline; ← and → turn the pictures in the viewer.

Everything else is as in aimdi186.

### Licence note

The downloadable voices use sherpa-onnx, whose library includes espeak-ng under GPL-3.0. The accents on mathematical letters come from a subset of Noto Sans Math under the SIL Open Font License. Both licence texts are in Settings → About → Licences. XTA's own source code stays MIT.

### Installation

For most Android phones, use **`xta-aimdi187_arm64-v8a.apk`**. Universal, ARMv7 and x86_64 APKs are also supplied. The base version code is **400001560**, above every aimdi186 variant. The app ID (`com.aimdi.xta`) and release signing identity are unchanged for in-place updates.

For Linux:

- **Debian 12+, Ubuntu 24.04+, Linux Mint 22+**: `sudo apt install ./xta_4.12.0.187_amd64.deb`
- **Ubuntu 22.04, Linux Mint 21, Pop!_OS 22.04, Zorin OS 17**: `sudo apt install ./xta_4.12.0.187_ubuntu22.04_amd64.deb`
- **Arch Linux**: `sudo pacman -U xta-bin-4.12.0.187-1-x86_64.pkg.tar.zst`, or build it from the attached `PKGBUILD` with `makepkg -si`
- **Other distributions**: unpack `xta-aimdi187-linux-x86_64.tar.gz` and run `./install.sh` in it for a menu entry

**`SHA256SUMS-linux`** lists their checksums. See [docs/desktop.md](https://github.com/Aimdi/XTA/blob/claude/main/docs/desktop.md).

The release workflow checks translations, skill synchronization, analysis and the full Flutter test suite on the tagged source before building. **`release-build.json`** and **`SHA256SUMS`** accompany the APKs, and **`xta-aimdi187-debug-symbols.zip`** and **`xta-aimdi187-linux-debug-symbols.zip`** hold the debug symbols for reading crash traces.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi186)
