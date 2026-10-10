## XTA — aimdi175

### XTA on the Linux desktop

XTA now runs on Linux PCs too, built from the same code as the Android app. This release carries the desktop build beside the APKs:

- **Arch Linux:** download `xta-bin-4.12.0.175-1-x86_64.pkg.tar.zst` and install it with `sudo pacman -U xta-bin-4.12.0.175-1-x86_64.pkg.tar.zst`, or build it yourself from the attached `PKGBUILD` with `makepkg -si`. XTA then appears in your application menu, or runs as `xta`.
- **Other distributions:** unpack `xta-aimdi175-linux-x86_64.tar.gz` and run `xta` inside it. It needs GTK 3 and libmpv.
- Timelines, profiles, search, subscriptions, groups, saved posts, plugins, downloads and backups work as on the phone. A backup exported on the phone can be imported on the PC and the other way round.
- **Adding an X account:** sign in to x.com in your web browser, open its developer tools (F12) and copy the `auth_token` and `ct0` cookies into XTA together with your username. They are stored only on that computer.
- Links, X articles, RSS, Substack and TikTok posts open in your default browser. Pixiv and Reddit sign-in need the Android app; Pixiv also takes a pasted refresh token.
- Downloads go to your Pictures, Videos and Downloads folders, under `XTA/`. Save and open dialogs use the desktop portal: install `xdg-desktop-portal-gtk` or `xdg-desktop-portal-kde`.
- Not on the desktop: sharing links into XTA from other apps, picture-in-picture, blocking screenshots, and the phone's system voices.

The full guide is in [docs/desktop.md](https://github.com/Aimdi/XTA/blob/claude/main/docs/desktop.md). This is the first desktop release: please report anything that does not work on your PC, with your distribution and desktop.

### Android

Nothing changes on the phone in this release: the Android app reads and behaves as in aimdi174.

### Licence note

The downloadable voices use sherpa-onnx, whose library includes espeak-ng under GPL-3.0. Its licence text is in Settings → About → Licences. XTA's own source code stays MIT.

### Installation

For most Android phones, use **`xta-aimdi175_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001440**, above
every aimdi174 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

For Linux, use the Arch package, the `PKGBUILD` or the portable tarball described
above; **`SHA256SUMS-linux`** lists their checksums.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi174)
