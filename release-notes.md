## XTA — aimdi176

### Smoother, closer to X

- **Full refresh rate.** On phones with a 90 or 120 Hz screen, XTA now asks for the fastest mode, so scrolling runs at the screen's full rate instead of 60 Hz. You can check it in Developer options → "Show refresh rate".
- **Opening a post** is a quick fade instead of a slide. While the replies load, grey placeholders show where they will appear, instead of spinners.
- **Photos in a timeline** wait on a quiet grey box and fade in, instead of a black box with a spinner in each one.
- **The home header gets out of the way.** It leaves with the bottom bar when you scroll down and comes back when you scroll up. The setting is now "Hide top and bottom bars while scrolling".
- **The reply order** ("Relevant ▾") under an opened post now has a row of its own, and the thread line no longer runs through it.

### Reposts on profiles

X moved reposts out of a profile's posts into a separate Reposts list, so XTA showed none. Profiles show them again:

- **All** shows posts and reposts together, newest first, with the pinned post on top.
- **Retweets** shows only reposts.
- **Posts** shows only the account's own posts.

### Faster with several accounts

- **Least-used account first.** Each request goes to the account with the most requests left for it, so your accounts are used evenly and fewer requests are refused near the limit.
- **More searches at once.** The Following feed runs up to six searches at a time with three or more accounts, two per account. With one account it runs three, as before. Large Following lists load in fewer rounds.

### Linux desktop

The Linux desktop build gets all of the above, except the refresh rate, which is Android only.

### Licence note

The downloadable voices use sherpa-onnx, whose library includes espeak-ng under GPL-3.0. Its licence text is in Settings → About → Licences. XTA's own source code stays MIT.

### Installation

For most Android phones, use **`xta-aimdi176_arm64-v8a.apk`**. Universal, ARMv7 and x86_64 APKs are also supplied. The base version code is **400001450**, above every aimdi175 variant. The app ID (`com.aimdi.xta`) and release signing identity are unchanged for in-place updates.

For Linux, use the Arch package (`sudo pacman -U xta-bin-4.12.0.176-1-x86_64.pkg.tar.zst`), the attached `PKGBUILD` (`makepkg -si`) or the portable tarball. **`SHA256SUMS-linux`** lists their checksums. See [docs/desktop.md](https://github.com/Aimdi/XTA/blob/claude/main/docs/desktop.md).

The release workflow checks translations, skill synchronization, analysis and the full Flutter test suite on the tagged source before building. **`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi175)
