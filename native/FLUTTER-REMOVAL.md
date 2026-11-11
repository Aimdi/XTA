# Removing the Flutter app

The plan for the commit that deletes XTA's Flutter app once XTA Native has
everything. Nothing here is done yet: the Flutter app, its workflows and its
docs are all still in the repository. What is done is the preparation:
`native/` and `.github/workflows/native-prototype.yml` no longer read or run
anything the deletion removes, except where section (d) says so.

Checked against the tree at the commit that added this file; run the checks of
"Before and after" again when the day comes, since both sides keep moving.

## What was made independent

| XTA Native used | Now |
|---|---|
| Flutter in the `screens` and `desktop` jobs, to run the Dart stand-in for X (`tool/mock_x`, `lib/utils/mock_x_urls.dart`, `test/fixtures`) | `native/tool/mock-x`, the Gradle module `:mock-x`: the same routes, port and answers on the JDK's HTTP server, with copies of the nine fixtures it serves |
| `scripts/voice_engine_archives.py` (reads `lib/speech/voice_engine.dart` and `pubspec.lock`) for the engine Android downloads | `native/tool/voice_engine.py archives`, reading `VoiceEngine.kt`'s pins; byte for byte the same archives |
| `pubspec.lock`, for the SHA-256 of pub.dev's `sherpa_onnx_*` archives | `PUB_ARCHIVES` in `native/tool/voice_engine.py` |
| `lib/l10n/intl_{en,be,be_Latn}.arb`, read by `native/tool/belarusian_latin.py` | copies in `native/tool/l10n/` |
| `assets/licenses/espeak-ng.txt`, `licenses/fritter.txt`, `licenses/quacker.txt`, read by `native/gradle/licences.gradle.kts` (and silently left out of the app's licence list when missing) | copies in `native/gradle/licences/` |
| The latest XTA release's APK, for the start, memory and size comparison | still compared while a release has `xta-<tag>_x86_64.apk`; otherwise XTA Native is measured alone |
| Nothing ran native CI when `repairs.json` changed, though two native tests hold it to what the build ships | `repairs.json` is one of the workflow's paths |

## (a) What the deletion commit removes

Top-level paths, all Flutter-only:

```
lib/  test/  integration_test/  test_driver/  android/  linux/  tool/
.maestro/  fastlane/  packaging/  assets/
pubspec.yaml  pubspec.lock  .fvmrc  .metadata  analysis_options.yaml  devtools_options.yaml
l10n.py  merge_arb_conflicts.py  generate_icons.py  requirements.txt
generate-release-notes.sh  generate_changelog_and_release_notes.py
changelog.md  release-notes.md
.editorconfig        (its one rule is for *.dart)
.gitattributes       (its one rule is the ARB merge driver)
```

- `assets/` goes whole only after README.md stops showing `assets/icon-source.jpg`
  and `assets/readme/**` (see (c)); `assets/licenses/espeak-ng.txt` is already
  copied.
- `packaging/` is the Flutter build's Arch and Linux packaging; XTA Native's is
  `native/packaging/`.
- `fastlane/` is the store listing of `com.aimdi.xta`.

`scripts/`, every file:

```
adb_wireless_connect.sh  cloud_install.sh  cloud_verify.sh
package_deb.sh  package_linux.sh  smoke_linux_package.sh  verify_reproducible.sh
setup_git_merge_drivers.sh  validate_arb.py
check_apk_contents.py  release_integrity.py  voice_engine_archives.py
report_canary.py  test_report_canary.py
tests/  (test_check_apk_contents, test_merged_release_tag, test_release_integrity,
         test_release_workflows, test_voice_engine_archives)
check_skill_sync.sh  check_design_skills.py  vendor_design_skills.py   (see the open point on the design skills)
```

`.github/`, 25 of the 27 workflows and the one composite action:

| Workflow | Why it is Flutter-only |
|---|---|
| `ci.yml` | builds and records the Flutter release APKs |
| `verify.yml` | ARB validation, the Flutter analyse and tests, the Flutter Linux build; its `skills` job checks `.claude/skills` (open point) |
| `release.yml`, `build-release.yml`, `attach-release-apks.yml`, `publish-apks.yml`, `linux-desktop.yml`, `new_version.yml` | build, version (`pubspec.yaml`) and publish XTA's releases |
| `release-integrity.yml` | tests `scripts/release_integrity.py` and the release workflows above |
| `repoint-release-tags.yml` | repairs `aimdi*` tags against their release branches; no new `aimdi*` release will be cut. Keep it instead if a tag is still known to point at the wrong commit |
| `emulator-qa.yml` | the Maestro flows on the Flutter APK, with the Dart stand-in |
| `api-canary.yml`, `x-bootstrap-check.yml`, `x-signing-autofix.yml` | probe X and the other networks with `flutter test test/live/…`, and repair `lib/client` (open point: the daily probe) |
| `compact-home-check.yml`, `unified-home-check.yml`, `reader-android-check.yml` | Flutter tests and builds for one screen each |
| `app-reading-review.yml`, `home-plugin-review.yml`, `home-timeline-review.yml`, `mastodon-client-review.yml`, `plugin-experience-review.yml`, `reader-improvements-review.yml`, `share-notes-review.yml` | Flutter review runs, each tied to one long-gone branch name |
| `install-xta-design-skills.yml` | a one-time bootstrap for the branch `chore/xta-design-skills`; it says of itself that it is removed after vendoring |
| `.github/actions/build-linux/` | the Flutter Linux build, used by `verify.yml` and `linux-desktop.yml` only |

`.claude/`:

```
.claude/hooks/format-dart.sh  guard-pinned-deps.sh  session-start.sh
.claude/skills/parse-api/  port-from-squawker/  translate/
```

and `.claude/settings.json` is rewritten ((c)): every permission and hook in
it names `lib/` or one of those three hooks.

`docs/`: everything that describes the Flutter app, which is all of it but the
three files (b) keeps: `XTA.md`, `aimdi137-release-preparation.md`,
`app-development-audit-2026-09.md`, `bluesky-reader-upgrade-validation.md`,
`cloud-testing.md`, `desktop.md`, `fix-x-signing.md`, `grok-rewrite-plan.md`,
`home-plugin-redesign-checklist.md`, `long-session-loading-investigation.md`,
`mock-x.md`, `perf-baseline.md`, `reader-session-checks.md`,
`release-notes-through-aimdi143.md`, `reproducible-builds.md`, `signing.md`,
`substack-declutter.md`, `substack-reader-upgrade-validation.md`,
`xta-design-skills.md`, `xta-improvement-areas.md`, `booru/`, `reviews/`,
`substack/`, `superpowers/`, and `specs/` but for the two specs below. The
specs are what `native/PARITY/` and many comments under `native/` cite as the
reference for a screen; git history keeps them, and section (c) lists where
the citations are.

## (b) What stays, and why

| Path | Why |
|---|---|
| `native/` | the app |
| `.github/workflows/native-prototype.yml` | its CI and prereleases |
| `.github/workflows/cleanup-artifacts.yml` | deletes day-old artifacts of any workflow; XTA Native's runs make the large ones |
| `.github/no-response.yml` | issue housekeeping, nothing to do with either app |
| `endpoints.json`, `repairs.json`, `sources.json`, `sources/`, `plugins.json` | published documents that installs read from the default branch at `raw.githubusercontent.com/Aimdi/XTA/HEAD/…`: XTA Native reads the first four (`XEndpoints.OVERRIDES_URL`, `PublishedDocument.REPAIRS_URL`, the web sources plugin), and every installed XTA reads all five, `plugins.json` included, for as long as it is on a phone. `NetworkRepairsTest` and `TikTokRepairsTest` read `repairs.json` from the checkout |
| `LICENSE` | the repository's licence; `native/gradle/licences.gradle.kts` reads it (`../LICENSE`) for the app's licence list |
| `licenses/` (`fritter.txt`, `quacker.txt`) | the MIT notices of the apps XTA comes from. The build reads its own copies, but the notices belong to the repository as long as XTA Native is XTA written again |
| `certificate-fingerprints.txt` | the release key's fingerprints: XTA Native's APK, its plugins and the desktop app's pinned certificate are signed with the same key (`SIGNING_KEY`) |
| `CODE_OF_CONDUCT.md` | not about either app |
| `docs/web-sources.md`, `docs/specs/published-repairs.md`, `docs/specs/downloadable-voice-engine.md` | the formats of `sources.json` and `repairs.json` (their own `_comment` points there) and of the voice engine's archives, which both apps' installs read. They can move under `native/` in the same commit, with the comments that name them |
| The release tags and releases `aimdi*` | XTA stays installable, and the comparison in the `screens` job and the voice engine's second address (`releases/latest/download/xta-voice-engine-…`) read XTA's latest release. Tags and releases are not files: a `git rm` does not touch them, and nothing should delete them |
| The `native-plugins` release and the `native-*` prereleases | the plugin store's catalogue and files (`PluginManager.CATALOGUE_URL`), and what the `desktop` job checks this build's plugin API against |
| The secrets `SIGNING_KEY`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS` | the native workflow signs with them |
| `.claude/skills/impeccable/`, `.claude/skills/ui-ux-pro-max/` | design skills, not Flutter code (open point) |

## (c) What is rewritten, not removed

- **`README.md`**: it describes the Flutter app from its first line. Keep what
  is true of XTA Native (what it reads, plugins, accounts and privacy,
  licences and history), replace "Install and update", "Linux desktop" and
  "Build locally" with XTA Native's (`native/PRERELEASE.md` has the wording),
  and replace or drop the pictures under `assets/readme/` and
  `assets/icon-source.jpg`, which is what lets `assets/` go.
- **`CLAUDE.md`**: all of it is the Flutter app's (FVM, the Store pattern,
  `lib/client` frozen, ARB files, the hooks). Write XTA Native's: the modules
  of `native/settings.gradle.kts`, the read-only rule, `native/tool/README.md`
  for checking without Gradle, `PLUGINS.md`, `SCREENS.md`.
- **`CONTRIBUTING.md`**: its bug report and pull request sections name the
  Flutter build.
- **`.gitignore`**: drop the Flutter, Dart, pub, FVM and `lib/generated`
  lines, `/android/…`, `assets/icon-*.png` and `.venv/`; `native/.gitignore`
  already covers `native/`.
- **`.claude/settings.json`**: drop the `lib/**` permissions and the three
  hooks; what is left is an empty object, or permissions for `native/`.
- **Issue templates**: the repository has none (`.github/ISSUE_TEMPLATE` does
  not exist); `CONTRIBUTING.md` is where a report is described.
- **`endpoints.json`**: its `_comment` names `lib/client/endpoints.dart`;
  point it at `native/core/…/x/XEndpoints.kt`. `sources.json` and
  `repairs.json` name the two docs (b) keeps.
- **`.github/workflows/native-prototype.yml`**: its header and the `desktop`
  job's comments still say "beside the Flutter app" and "as XTA ships its
  own"; the trigger is the branch `claude/admiring-mayer-bdfg95`, to change to
  the default branch when XTA Native becomes the app. The name "Native
  prototype" and the release titles ("prototype") go at the same time.
- **Docs under `native/` that point into the Flutter tree as the reference**,
  to reword once the reference is history:
  - `native/PORTING.md`, `native/MIGRATION.md`, `native/PLUGINS.md`,
    `native/SCREENS.md`, `native/PRERELEASE.md`, `native/tool/README.md`;
  - the parity ledgers `native/PARITY/app.md`, `microblogs.md`, `pixiv.md`,
    `plugins-rest.md`, `reddit-ig-tt.md`, `substack-feeds.md`, `widgets.md`,
    `x-posts.md`, which list XTA's tests and widgets by their `.dart` file
    (about 1,200 lines name one). They are a record of the port: keep them as
    they are with a line saying which commit they refer to, or drop them;
  - `native/settings.gradle.kts`' opening comment ("beside the Flutter app");
  - comments in about 900 source and string files under `native/` that name
    the Dart file a piece was written from ("as XTA's `feed_ink.dart`…",
    "XTA's wording (lib/l10n)"). They are still true as history; nothing
    reads them. Leave them, or sweep them in a commit of their own.

## (d) What could not be made independent, and open points

1. **Nothing in XTA Native's build or CI still needs a Flutter-side file**,
   except the published documents and `LICENSE` of (b), which stay by design.
   Only CI can confirm the workflow itself: the `:mock-x` module, the route
   answers, the archives and the licence list were run on a development
   machine, but the emulator walk (`screens`) and the desktop job against the
   Kotlin stand-in were not.
2. **The daily probe goes with the Flutter workflows.** `api-canary.yml` reads
   X and every other network once a day, checks that the published
   `endpoints.json`, `repairs.json` and `sources.json` are readable, and
   `x-signing-autofix.yml` opens a repair when X's signing changes. XTA
   Native's `live` job runs the same kind of reads but only on a push to
   `native/**`. Before the deletion, give `native-prototype.yml` a schedule
   for `live` (or a small workflow of its own); the signing auto-fix has no
   native counterpart and would have to be written.
3. **`endpoints.json`, `sources.json` and `sources/` are checked by Flutter
   tests only** (`test/live/published_repairs_test.dart`,
   `web_sources_smoke_test.dart`, the endpoint override tests). `repairs.json`
   has native tests; the other three would be edited unchecked. A native test
   that reads each from the checkout, as `NetworkRepairsTest` does, closes it.
4. **`plugins.json` at the root is the Flutter app's catalogue.** XTA Native's
   store reads the `native-plugins` release instead and only recognises the
   root address as a former default (`CatalogueAddress`). It stays for
   installed XTA builds; once those no longer matter it can go, with
   `PublishedDocuments.PLUGINS` changed in the same commit.
5. **XTA's releases as "latest".** XTA Native's prereleases are never marked
   latest, so `releases/latest` stays XTA's last release: the comparison and
   the voice engine's fallback address rely on that release keeping its
   assets. The first XTA Native release marked latest must carry the three
   `xta-voice-engine-*.tar.xz` archives (the `prerelease` job already attaches
   them), and from then on the comparison finds no `xta-<tag>_x86_64.apk` and
   is skipped; installed XTA builds that check GitHub for updates will also
   see that release.
6. **The design skills.** `impeccable` and `ui-ux-pro-max` are not Flutter
   code, but what keeps them whole is: `scripts/check_skill_sync.sh` and
   `scripts/check_design_skills.py` (which looks up the Flutter stack), run by
   `verify.yml`'s `skills` job and by `scripts/release_integrity.py`. Either
   keep the two scripts and move the `skills` job into a small workflow, or
   remove the skills with the rest. `docs/xta-design-skills.md` goes with the
   choice.
7. **XTA's three ARB files are frozen copies** in `native/tool/l10n/`. They
   feed one tool, which only fills strings that are missing; they need no
   upkeep, and can be deleted with the tool once Latin Belarusian is complete
   and maintained by hand.

## Before and after the deletion commit

```bash
# Nothing under native/ or in its workflow reads a path the commit removes:
grep -rnE 'scripts/|tool/mock_x|pubspec|\.fvmrc|flutter|dart run' .github/workflows/native-prototype.yml
grep -rnE '"\.\./[A-Za-z]|NATIVE\.parent|REPO = |parents\[2\]' native --include='*.kts' --include='*.py' --include='*.sh' | grep -v /build/
#   only native/gradle/licences.gradle.kts with "../LICENSE", and app/build.gradle.kts with "../services/…" (inside native/)

# The build and the plain modules' tests, the stand-in included:
cd native && ./gradlew :core:test :networks:test :store:test :services:test :plugin-host:test :plugins:test :mock-x:test :desktop:appLicences

# Then push with [prerelease] off and read the screens and desktop jobs.
```
