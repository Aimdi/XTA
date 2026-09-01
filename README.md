<div align="center">
<img src="assets/readme/icon.png" height="100">

# XTA

[![GitHub release](https://img.shields.io/github/v/release/aimdi/xta?style=for-the-badge&logo=github&color=2dba4e)](https://github.com/Aimdi/XTA/releases)
[![Upstream](https://img.shields.io/badge/upstream-Teskann%2FQuaX-1565C0?style=for-the-badge&logo=github)](https://github.com/Teskann/QuaX)
[![License: MIT](https://img.shields.io/github/license/aimdi/xta?style=for-the-badge&logo=opensourceinitiative&logoColor=FFFFFF&color=750014)](/LICENSE)
[![Build Status](https://img.shields.io/github/actions/workflow/status/aimdi/xta/ci.yml?style=for-the-badge&logo=github)](https://github.com/Aimdi/XTA/actions)
![Minimum Android version](https://img.shields.io/badge/Android-7.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Flutter version](https://img.shields.io/badge/Flutter-3.44.4-54C5F8?style=for-the-badge&logo=flutter&logoColor=white)

**A privacy-focused, read-only Android client for X** — local feeds, saves, and
optional plugins.

[![Get it on GitHub](assets/readme/get-it-on-github.png)](https://github.com/Aimdi/XTA/releases)
[![Get it on Obtainium](assets/readme/get-it-on-obtainium.png)](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/Aimdi/XTA)

To verify a release APK, use [these signing certificate fingerprints](./certificate-fingerprints.txt).

</div>

> [!IMPORTANT]
> An X account is needed so XTA can *fetch* content. Subscriptions, saves, likes,
> and settings stay on your device — they are not X's social graph.
>
> **XTA does not post, reply, quote, repost, or like on X.** Footer actions open
> conversations or quotes, or store a like locally. Nothing is written back to X.

> [!IMPORTANT]
> Application id is `com.aimdi.xta`, so XTA installs **alongside** upstream QuaX
> (`com.teskann.quax`) and older Aimdi builds. Export a backup from the old app
> before switching. Releases use this fork's keystore — see
> [`docs/signing.md`](docs/signing.md).

## Screenshots

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="200" alt="Feed">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="200" alt="Groups">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="200" alt="Profile">
</p>

## What you get

Based on upstream **QuaX v4.12.0**, plus:

- ✅ Group feeds with media-only grids, Recent/Popular order, and SFW/NSFW filters
- ✅ Gap filling and dedup so long absences and overlapping pages stay coherent
- ✅ Zen mode — hide counts, gate replies, newest-first with a per-author cap
- ✅ Advanced search, quotes screen, Community Notes in feeds
- ✅ Subscribe-from-timeline, conversation translate, long-press image download
- ✅ Remember reading position on Following, For You, and groups
- ✅ Broken subscription / bookmark cleanup; per-user hide-reposts
- ✅ X Look — Light / Dim / Lights Out + accent colours
- ✅ No trackers; Android backups disabled so session tokens stay off cloud backups

## Plugins

Optional, off by default, installed from Settings → plugin store (catalogue:
[`plugins.json`](./plugins.json)). They do not turn XTA into a poster.

| Plugin | What it does |
|---|---|
| **Reddit** | Read subreddits account-free; mix into home/groups; local upvotes |
| **Substack** | Follow publications; reader + TTS; free preview when available |
| **Stocks** | StockTwits-style watchlist strip + cashtag post feed; charts on ticker pages |
| **Immich** | Upload a bookmark folder's photos/videos to your Immich server |
| **Karakeep** | One-tap save to a self-hosted Karakeep instance |
| **Deepmarks** | Save bookmarks over Nostr |

## About this fork

This fork is **vibe coded**: changes on top of QuaX are mostly written by AI
coding agents, directed and tested by a human, not line-by-line reviewed the way
upstream is. Expect rougher edges; features land by using the app.

Want the carefully maintained experience? Use
[Teskann/QuaX](https://github.com/Teskann/QuaX) — all credit for the core app
belongs there. Issues here are welcome; fixes will also be vibe coded.

A new-post notification experiment was tried and removed — unreliable by design
for this reverse-engineered client.

## More information

- [FAQ / wiki](./docs/XTA.md)
- [Release signing](./docs/signing.md)
- [Cloud testing notes](./docs/cloud-testing.md)
- [LICENSE](./LICENSE)
- [Contributing](./CONTRIBUTING.md)
- [Changelog (upstream, up to v4.12.0)](./changelog.md)

## Build locally

Prerequisites: Python and [FVM](https://fvm.app/). The Flutter SDK is pinned in
[`.fvmrc`](./.fvmrc).

```bash
fvm install
fvm use

python -mvenv .venv
bash -c '
  source ./.venv/bin/activate
  pip install -r requirements.txt
  python generate_icons.py
'

fvm flutter pub get
fvm dart run flutter_launcher_icons
fvm dart run intl_utils:generate
fvm dart run flutter_iconpicker:generate_packs --packs material
fvm flutter build apk --debug
```

> `dart_pubspec_licenses:generate` is intentionally omitted — it fails under
> Flutter 3.44.4 + FVM, and the app uses Flutter's built-in license page instead.

### ARB merge driver

Every branch appends strings to all 29 files in `lib/l10n/`, so parallel
branches conflict in all of them even when no key overlaps.
[`.gitattributes`](./.gitattributes) routes those files through a `merge=arb`
driver that merges them by key instead of by line. Git keeps merge drivers in
`.git/config`, so **a fresh clone must register it once** — it does nothing
otherwise:

```bash
bash scripts/setup_git_merge_drivers.sh   # also run by scripts/cloud_install.sh
```

Without it merges fall back to the normal text merge; `python3
merge_arb_conflicts.py` then resolves the conflicted files still sitting in the
index. Either way, keys both sides changed to different values are left as a
real conflict.

## More information

For Cursor Cloud agents, see [`docs/cloud-testing.md`](docs/cloud-testing.md)
(`scripts/cloud_install.sh` bootstraps FVM and the Android SDK on a cold VM).

## Credits

QuaX is made by [Teskann](https://github.com/Teskann), building on
[Quacker](https://github.com/TheHCJ/Quacker) and
[Fritter](https://github.com/jonjomckay/fritter). XTA only adds vibe-coded
changes and plugins on top of their work.
