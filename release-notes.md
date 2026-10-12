## XTA — aimdi177

### Groups and feeds no longer get stuck loading

aimdi176 sent each X request to the account with the most requests left. It also counted an account whose usage was not known yet as the best choice. An account whose sign-in had expired or been locked never reports its usage, so it stayed "best" and every request went to it first and failed. Groups and feeds then stopped loading partway after a while.

- **An account with unknown usage takes its turn.** It is no longer always first, so one broken account cannot block everything.
- **Requests are spread evenly.** Each request goes to the account with the fewest requests in progress, then to the one with the most left.
- **Search pauses are respected.** An account X has paused on search no longer counts towards how many Following searches run at once.

If one of your accounts has stopped working, Settings → Diagnostics shows it. Remove it and add it again.

Everything else is as in aimdi176.

### Licence note

The downloadable voices use sherpa-onnx, whose library includes espeak-ng under GPL-3.0. Its licence text is in Settings → About → Licences. XTA's own source code stays MIT.

### Installation

For most Android phones, use **`xta-aimdi177_arm64-v8a.apk`**. Universal, ARMv7 and x86_64 APKs are also supplied. The base version code is **400001460**, above every aimdi176 variant. The app ID (`com.aimdi.xta`) and release signing identity are unchanged for in-place updates.

For Linux, use the Arch package (`sudo pacman -U xta-bin-4.12.0.177-1-x86_64.pkg.tar.zst`), the attached `PKGBUILD` (`makepkg -si`) or the portable tarball. **`SHA256SUMS-linux`** lists their checksums. See [docs/desktop.md](https://github.com/Aimdi/XTA/blob/claude/main/docs/desktop.md).

The release workflow checks translations, skill synchronization, analysis and the full Flutter test suite on the tagged source before building. **`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi176)
