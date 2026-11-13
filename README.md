<div align="center">

# XTA

**A reader for X, social feeds, articles and artwork, for Android and the Linux desktop.**

Follow locally. Build your own groups. Keep the posts worth coming back to.

[![Latest release](https://img.shields.io/github/v/release/Aimdi/XTA?style=flat-square&logo=github)](https://github.com/Aimdi/XTA/releases/latest)
[![License: MIT](https://img.shields.io/github/license/Aimdi/XTA?style=flat-square)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Linux x86_64](https://img.shields.io/badge/Linux-x86__64-FCC624?style=flat-square&logo=linux&logoColor=black)

**[Download the latest release](https://github.com/Aimdi/XTA/releases/latest)** ·
[Add to Obtainium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Aimdi/XTA) ·
[Report a bug](https://github.com/Aimdi/XTA/issues)

</div>

XTA brings a configurable Home feed, local subscriptions, groups, Discover and
Saved into one app. Optional plugins add readers for other networks,
newsletters, artwork and markets, and connections to your own services.

This is **XTA Native**: XTA written again in Kotlin and Compose
Multiplatform, one set of screens for Android and the Linux desktop. It
replaces the earlier Flutter app, whose source is in this repository's history
and whose releases (the `aimdi*` tags) stay downloadable.

XTA only reads. It does not publish posts, replies, quotes, reposts or likes,
and does not follow anyone, on X or on the other networks. Following someone
or liking a post in XTA is a local action, kept on your device and separate
from your accounts' own follows and likes. (One exception: once you sign in
with Pixiv, its bookmarks, follows and watchlist changes go to your Pixiv
account.)

## Read your way

| Area | What you can do |
|---|---|
| **Home** | Following, X, your groups and the networks you pin, turned by a sideways drag or picked in the timeline picker; Posts or Media, the order and filters; it reopens where you left off. On the desktop, a deck of columns. |
| **Groups** | Organise local follows into groups across networks, with their Media and a Discover that can rank with AI. |
| **Discover** | Search posts and people with advanced search, trends, communities, lists and antennas. |
| **Reading** | Threads, quotes, polls, link cards, Community Notes, translation and reading aloud; videos in place and, on Android, picture in picture; the media viewer with zoom. |
| **Saved** | Saved posts with folders, likes on this device, notes, the archive, the offline collection and downloads. |
| **Settings** | Themes (True Black included), backup export and import, WebDAV sync, and 29 languages. |

## Plugins

Every network but X is a plugin you add from **Settings → Plugin store**, and
remove there just as easily: **Bluesky, Mastodon, Threads, Instagram, Reddit,
Hacker News, Pixiv, Substack, Booru, RSS, the web sources and Stocks**.
**EhViewer and TikTok** are listed once **Show private plugins** is on, in the
store's menu. **Karakeep and Deepmarks** add "Save to …" to a post's sheets,
and **Immich** uploads a Saved folder, to servers of your own.

Plugins are downloaded from this repository's `native-plugins` release and
signed with the app's key; the app refuses any other. Web sources read simple
sites from a published list ([writing one](docs/web-sources.md)) and only the
sites they name, after you agree. How plugins are built is in
[`native/PLUGINS.md`](native/PLUGINS.md).

## Install and update

Open the [latest release](https://github.com/Aimdi/XTA/releases/latest) and
expand **Assets**:

| File | Use it for |
|---|---|
| `xta-native-<version>.apk` | Android 8.0 or newer. |
| `xta-native-desktop-<version>-linux-x64.tar.gz` | Any Linux on x86_64: unpack it and run `bin/xta-native`. It brings its own Java runtime. |
| `xta-native-<version>-1-x86_64.pkg.tar.zst` | Arch Linux: `sudo pacman -U xta-native-*-x86_64.pkg.tar.zst` puts it in your application menu. The `PKGBUILD` beside it builds the same package. |

Each release carries **`SHA256SUMS`**; the APK, the plugins and the desktop
app's plugin certificate are signed with the key in
[`certificate-fingerprints.txt`](certificate-fingerprints.txt). The app checks
GitHub for a newer release once a launch (Settings → General → Check for
updates). Obtainium can track [`Aimdi/XTA`](https://github.com/Aimdi/XTA).

XTA Native installs as its own app, `com.aimdi.xtanative`, next to the
Flutter XTA (`com.aimdi.xta`), and shares nothing with it. To bring
everything over, open **Settings → Data → Export** in the old XTA, switch on
**"Export accounts?"**, export, and choose that file in XTA Native's first
screen or **Settings → Data → Import**: X accounts, follows, groups, saved
posts, notes and settings come with it.

On Linux the app keeps its data in `~/.local/share/xta-native`. Videos,
GIFs and podcasts play in the app through the system's libmpv: the Arch
package installs `mpv`; with the tarball, install your system's (`libmpv2` on
Debian and Ubuntu, `libmpv1` on Ubuntu 22.04, `mpv-libs` on Fedora). Without
it, videos open in your system player. An X account is added on the desktop
with your x.com session cookies.

## Accounts and privacy

- **X access:** without an account the app reads as a guest, which X limits.
  Add an X account (X's own sign-in page on Android) for signed-in reading.
  Other plugins have their own account needs.
- **Local data:** subscriptions, groups, saved posts, notes and local likes
  stay on your device. Sessions are kept in storage Android neither backs up
  nor moves to a new phone; use Export and Import to move them yourself.
- **Network requests:** reading contacts X or the network you open. Those
  services see the requests and any credentials you give them; local follows
  do not make browsing anonymous. There are no trackers or analytics.
- **Optional connections:** AI features use the provider, URL and key you
  configure (OpenRouter, Grok, OpenAI or any OpenAI-compatible server).
  Karakeep, Deepmarks, Immich and WebDAV send data only to the server you
  set up.
- **Downloaded voices:** Settings → Read aloud can download an on-device voice
  from k2-fsa's [sherpa-onnx `tts-models` release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models),
  only when you tap Download, each checked against a pinned SHA-256. Reading
  aloud with it runs entirely on the device.

XTA reads unofficial APIs. Upstream changes, rate limits and expired sessions
can interrupt loading; Settings → Diagnostics shows the recent reads and each
account's health. When reporting a problem, include the version, your Android
version or Linux distribution, the source and the steps to reproduce it.

## Build locally

You need Git and JDK 17; the Android app also needs the Android SDK (with the
NDK for the voice shim). Everything builds from `native/` with Gradle:

```bash
git clone https://github.com/Aimdi/XTA.git
cd XTA/native

# The plain modules' tests, the stand-in for X included
./gradlew :core:test :networks:test :store:test :services:test :plugin-host:test :plugins:test :mock-x:test

# The Android app (where an Android SDK is installed)
./gradlew :app:assembleRelease          # app/build/outputs/apk/release/

# The Linux app and its tests
./gradlew :ui:desktopTest :desktop:run
./gradlew :desktop:createDistributable  # desktop/build/compose/binaries/main/app/
```

Without a signing key the build signs with the debug key. Where Gradle cannot
reach Maven, [`native/tool/README.md`](native/tool/README.md) has scripts that
compile and test the modules with a standalone `kotlinc`. CI
([`.github/workflows/native-prototype.yml`](.github/workflows/native-prototype.yml))
builds, tests, walks every screen on an emulator and the desktop app under a
virtual display, and publishes releases.

## Contributing and project history

Open issues and pull requests in **[Aimdi/XTA](https://github.com/Aimdi/XTA)**;
see [CONTRIBUTING.md](CONTRIBUTING.md) and, before changing code,
[CLAUDE.md](CLAUDE.md). XTA is developed with assistance from AI coding
agents; tests, the emulator walk and device testing support review.

XTA started as a Flutter app built on [QuaX by Teskann](https://github.com/Teskann/QuaX),
which builds on [Quacker](https://github.com/TheHCJ/Quacker) and
[Fritter](https://github.com/jonjomckay/fritter). Their work is the foundation
of this app, and their MIT notices are in [`licenses/`](licenses). XTA Native
was ported screen by screen from that app; [`native/PARITY/`](native/PARITY)
records the port.

XTA's source is MIT-licensed ([LICENSE](LICENSE)). The downloadable voices run
on [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Apache-2.0) and
onnxruntime (MIT), whose native library statically links
[espeak-ng](https://github.com/espeak-ng/espeak-ng) (GPL-3.0-or-later). On
Android that engine is not in the APK: it downloads with the first voice from
this repository's releases, and is distributed under the GPL-3.0's terms, as
are the Linux packages, which bundle it. The in-app licence page lists them.
