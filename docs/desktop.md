# XTA on the Linux desktop

XTA builds for Linux (x86_64) from the same code as the Android app. Every
release carries, beside the APKs:

| Asset | What it is |
|---|---|
| `xta-bin-<version>-1-x86_64.pkg.tar.zst` | The Arch Linux package. |
| `PKGBUILD` | The recipe that builds that package from the tarball below. |
| `xta-<tag>-linux-x86_64.tar.gz` | The portable bundle, for any glibc distribution. |
| `SHA256SUMS-linux` | Checksums of the three files above. |

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

Unpack the tarball anywhere and run `xta` inside it. It needs GTK 3 and libmpv
(`libgtk-3-0` and `libmpv2` on Debian and Ubuntu). For a menu entry, copy
`com.aimdi.xta.desktop` to `~/.local/share/applications/`, point its `Exec=` at
the unpacked `xta`, and copy `com.aimdi.xta.png` to
`~/.local/share/icons/hicolor/512x512/apps/`.

## Where your data lives

Settings, accounts, subscriptions and saved posts are under
`~/.local/share/com.aimdi.xta/`. Export and import work as on Android, so a
backup made on the phone can be imported on the desktop and the other way round.

Downloads saved in the background go into your Pictures, Videos and Downloads
folders, under `XTA/` (read from `xdg-user-dirs` when installed). A download
folder chosen in Settings is used instead when set.

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
```

The tarball is reproducible: the same bundle and commit give the same bytes.
The release workflows run [`linux-desktop.yml`](../.github/workflows/linux-desktop.yml)
after the APKs are published, and `verify.yml` builds the bundle and the Arch
package on every pull request.

Code that needs an Android-only service checks `isDesktop`
(`lib/utils/desktop.dart`) and takes the desktop route. `flutter test` runs on a
desktop host but keeps the Android paths, so set `debugDesktopOverride` in a
test that covers a desktop one.
