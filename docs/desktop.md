# XTA on the Linux desktop

XTA builds for Linux (x86_64) from the same code as the Android app. Every
release carries, beside the APKs:

| Asset | What it is |
|---|---|
| `xta_<version>_amd64.deb` | The package for Debian 12 and later, Ubuntu 24.04 and later, Linux Mint 22 and later, and the systems built on them. |
| `xta_<version>_ubuntu22.04_amd64.deb` | The same, built on Ubuntu 22.04 for the systems built on it: Ubuntu 22.04, Linux Mint 21, Pop!_OS 22.04, Zorin OS 17, elementary OS 7. |
| `xta-bin-<version>-1-x86_64.pkg.tar.zst` | The Arch Linux package. |
| `PKGBUILD` | The recipe that builds that package from the tarball below. |
| `xta-<tag>-linux-x86_64.tar.gz` | The portable bundle, with `install.sh` for the application menu. |
| `SHA256SUMS-linux` | Checksums of all of the above. |

The two Debian packages differ only in the mpv library they were built
against: Ubuntu 22.04 and the systems built on it ship mpv's older library
(`libmpv1`), and the main build cannot start without the newer one
(`libmpv2`).

## Install on Debian, Ubuntu and Linux Mint

Download the package for your system from the
[latest release](https://github.com/Aimdi/XTA/releases/latest) and install it
with apt, which also installs what XTA needs (GTK 3, mpv's library, the
desktop portal):

```bash
sudo apt install ./xta_*_amd64.deb
```

XTA then appears in your application menu, or runs as `xta` from a terminal.
It installs to `/opt/xta`. Update by installing the next release's package the
same way; your data is kept. Remove with `sudo apt remove xta`.

## Install on Arch Linux

Download the package from the [latest release](https://github.com/Aimdi/XTA/releases/latest)
and install it with pacman:

```bash
sudo pacman -U xta-bin-*-x86_64.pkg.tar.zst
```

Or build it yourself from the release's `PKGBUILD`: it downloads the tarball
from the same release and checks it against the pinned SHA-256.

```bash
mkdir xta && cd xta
curl -LO https://github.com/Aimdi/XTA/releases/latest/download/PKGBUILD
makepkg -si
```

XTA then appears in your application menu, or runs as `xta` from a terminal.
The package installs to `/opt/xta` and pulls in `gtk3`, `mpv` (video playback
through libmpv), `xdg-utils` and `xdg-desktop-portal`. For save and open
dialogs also install the portal backend for your desktop,
`xdg-desktop-portal-gtk` or `xdg-desktop-portal-kde`.

Update by installing the next release's package the same way; your data is
kept. Remove with `sudo pacman -R xta-bin`.

## Other distributions

Unpack the tarball anywhere and run `xta` inside it. It needs glibc 2.34 or
later, GTK 3 and mpv's library 0.35 or later (`libmpv.so.2`: `mpv-libs` on
Fedora, `libmpv2` on openSUSE). To add it to your application menu, run
`./install.sh` in the unpacked folder: it adds the menu entry and icons for
your user only, with no root, and `./install.sh --uninstall` removes them.
Move the folder first if you want it elsewhere, since the menu entry runs XTA
from where it was when you ran the script.

## Where your data lives

Settings, accounts, subscriptions and saved posts are under
`~/.local/share/com.aimdi.xta/`, as is `window.ini`, the size the window
reopens at. Export and import work as on Android, so a backup made on the phone
can be imported on the desktop and the other way round.

Downloads saved in the background go into your Pictures, Videos and Downloads
folders, under `XTA/` (read from `xdg-user-dirs` when installed). A download
folder chosen in Settings is used instead when set.

## With a mouse and keyboard

- **Back**: the mouse's back button, Alt+← and a keyboard's Back key go back,
  as Android's back gesture does. Esc goes back from a page too, after closing
  a dialog, sheet or menu first, and not while you are typing.
- **Refresh**: Home's timeline has a refresh button on the desktop, since a
  mouse cannot pull it down, and F5 or Ctrl+R refreshes the timeline in view
  (Home, a group, a deck column).
- **Pictures**: in the full-screen viewer, ← and → turn the pictures of a
  post, and the buttons at the sides do the same.
- **One window**: starting XTA again brings its window to the front rather than
  opening a second copy over the same settings. The window reopens at the size
  you left it, maximized if it was.

## What works differently from Android

The desktop has no platform web view, which some Android screens are built on:

- **Adding an X account.** Instead of X's login page, sign in to x.com in your
  web browser, open the developer tools (F12) and copy the `auth_token` and
  `ct0` cookies (Storage → Cookies → `https://x.com`). Paste them as
  `auth_token=…; ct0=…` with your username. They are stored only on this
  computer, as the Android app stores the cookies of its login page.
- **Links, articles, RSS, Substack and TikTok posts** open in your default
  browser instead of an in-app reader.
- **Pixiv and Reddit sign-in** need the Android app's login page. Pixiv can still
  sign in with a refresh token pasted in its settings.
- **Not on the desktop:** sharing links into XTA from other apps,
  picture-in-picture video, blocking screenshots, and Android's system
  text-to-speech voices. Read aloud uses the downloadable on-device voices
  instead (sherpa-onnx ships in the bundle).

Everything that reads and stores locally (timelines, profiles, search,
subscriptions, groups, saved posts, downloads, plugins and backups) is the
same code as on Android.

## Build from source

Install the Linux toolchain next to the [usual setup](../README.md#build-locally).
On Arch: `sudo pacman -S --needed base-devel clang cmake ninja pkgconf gtk3 mpv`.
On Debian and Ubuntu: `sudo apt install clang cmake ninja-build pkg-config libgtk-3-dev libmpv-dev`.

```bash
fvm flutter pub get
fvm dart run intl_utils:generate
fvm dart run flutter_iconpicker:generate_packs --packs material
fvm flutter build linux --release --no-tree-shake-icons
build/linux/x64/release/bundle/xta
```

To package it as a release does:

```bash
scripts/package_linux.sh build/linux/x64/release/bundle <tag> build/linux-release
# On Arch, also build the package:
scripts/package_linux.sh build/linux/x64/release/bundle <tag> build/linux-release --makepkg
# The Debian package of that tarball (add a flavour, such as ubuntu22.04, when
# the bundle was built there):
scripts/package_deb.sh build/linux-release/xta-<tag>-linux-x86_64.tar.gz <tag> build/linux-release
```

The tarball and the Debian package are reproducible: the same bundle and
commit give the same bytes. The release workflows run
[`linux-desktop.yml`](../.github/workflows/linux-desktop.yml) after the APKs
are published; it builds on Ubuntu 24.04 (the tarball, the Arch package and
the main Debian package) and on Ubuntu 22.04 (its Debian package). On every
pull request `verify.yml` does the same, then installs each Debian package on
its own Ubuntu and starts XTA on a virtual display with
`scripts/smoke_linux_package.sh <package> <out-dir>`, which fails unless the
window opens, XTA keeps running, and a second start hands over to it.

To check the window's behaviour by hand on a virtual display, start XTA twice
under `dbus-run-session` (one process and one window should remain), and
resize the window, close it, and start it again (it reopens at that size).

Code that needs an Android-only service checks `isDesktop`
(`lib/utils/desktop.dart`) and takes the desktop route. `flutter test` runs on a
desktop host but keeps the Android paths, so set `debugDesktopOverride` in a
test that covers a desktop one.
