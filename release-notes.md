## XTA — aimdi182

### Groups load all their posts again

Two things left groups with part of their posts, or none, and a Retry notice.

- **A group keeps loading while you look at something else.** XTA paused a group's reading whenever the app lost focus: on the desktop that was every time another window was in front, and on Android the notification shade, a system dialog or a split-screen app did the same. A group opened beside another window never loaded, and one that was loading lost its batches. Now only a hidden or backgrounded app stops reading.
- **One signed-out account no longer fails batches.** When an X account's session ends, X answers "Could not authenticate you". XTA showed that as an error for every batch it handed that account, instead of trying another one. It now moves on to another account, and after a few such answers that account goes to the back of the queue. Sign that account in again, or remove it, in Settings → Accounts.

### Wide windows

- **Posts stay in a reading column.** On a desktop or a tablet in landscape, every post and every picture used to stretch across the whole window, so one tall picture filled the screen. Feeds and conversations now keep posts in a centred column up to 720 points wide. The mouse wheel and the scrollbar still work across the whole window.
- **The Groups board adds columns.** Your choice of two or three columns now applies to phones. A wide window adds columns until each group is a compact tile, so a 1080p desktop shows 24 groups at once instead of 2.

### Smoother

- **Coming back to a feed far down no longer freezes the app.** Reopening a group puts you back where you were, and that used to happen in one jump, which held the app for as long as it took to lay out every post in between. It now moves there over several frames and stops as soon as you scroll.
- **The desktop app no longer reports an error when it closes.** A screenshot-blocking call meant for Android ran on the desktop at shutdown.

Everything else is as in aimdi181.

### Licence note

The downloadable voices use sherpa-onnx, whose library includes espeak-ng under GPL-3.0. Its licence text is in Settings → About → Licences. XTA's own source code stays MIT.

### Installation

For most Android phones, use **`xta-aimdi182_arm64-v8a.apk`**. Universal, ARMv7 and x86_64 APKs are also supplied. The base version code is **400001510**, above every aimdi181 variant. The app ID (`com.aimdi.xta`) and release signing identity are unchanged for in-place updates.

For Linux, use the Arch package (`sudo pacman -U xta-bin-4.12.0.182-1-x86_64.pkg.tar.zst`), the attached `PKGBUILD` (`makepkg -si`) or the portable tarball. **`SHA256SUMS-linux`** lists their checksums. See [docs/desktop.md](https://github.com/Aimdi/XTA/blob/claude/main/docs/desktop.md).

The release workflow checks translations, skill synchronization, analysis and the full Flutter test suite on the tagged source before building. **`release-build.json`** and **`SHA256SUMS`** accompany the APKs, and **`xta-aimdi182-debug-symbols.zip`** and **`xta-aimdi182-linux-debug-symbols.zip`** hold the debug symbols for reading crash traces.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi181)
