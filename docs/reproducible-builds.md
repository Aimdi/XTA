# Reproducible builds

Upstream's FAQ names this as the unsolved blocker for F-Droid:

> The only way XTA could be on F-Droid without these drawbacks is through a
> reproducible build — a process that lets F-Droid build the app from source
> while still producing a binary identical to the one signed by the developer.
> This is something we haven't gotten around to setting up yet.

A reproducible build means anyone can rebuild the published APK from this
source and get the same bytes. That gets you two things: F-Droid can distribute
the developer-signed APK rather than re-signing it with its own key, and anyone
can verify that a release contains only what the source says it does.

## What is already in place

| Input | Pinned where | Value |
|---|---|---|
| Flutter SDK | `.fvmrc` | 3.44.4 |
| Gradle | `android/gradle/wrapper/gradle-wrapper.properties` | 8.14 |
| Android Gradle Plugin | `android/settings.gradle` | 8.11.1 |
| Kotlin | `android/settings.gradle` | 2.2.20 |
| NDK | `android/app/build.gradle` | 29.0.14206865 |
| compileSdk / targetSdk | `android/app/build.gradle` | 37 |
| Java source/target | `android/app/build.gradle` | 17 |
| JDK running Gradle | `.github/workflows/*.yml` | 21.0.10 |
| Icon rasteriser | `requirements.txt` | pillow 12.3.0 |

`dependenciesInfo { includeInApk = false }` is already set in
`android/app/build.gradle`. Without it every APK embeds a Play-Store
dependency blob that is signed by Google and differs between builds — the most
common reason a Flutter app fails F-Droid's reproducibility check.

The JDK pin was `21.x` until now. A floating minor is enough to break
reproducibility on its own, because javac output can differ between patch
releases.

## Verifying

```bash
scripts/verify_reproducible.sh
```

It builds the release APK twice from a clean tree and diffs the contents,
ignoring `META-INF/` since signatures never match. Exit code 0 means the two
builds agree.

To check a *published* release against your own build, compare your APK's
contents with the downloaded one the same way. `apksigcopier` is the usual tool
for transplanting the official signature onto a local build so the whole file
can be compared, which is what F-Droid itself does.

## Known risks not yet ruled out

None of these are confirmed problems — they are the things to look at first if
`verify_reproducible.sh` comes back red. **The script has not yet been run
against this repository**: it needs an Android SDK and a full release build,
which the environment this was written in could not provide.

- **Build path in the AOT snapshot.** Dart can embed absolute paths in the
  release snapshot. If it does, builds only match when run from the same
  directory; F-Droid's builder uses a fixed path, so record the path used for
  releases and build there. `--split-debug-info` is worth testing as a fix.
- **Timestamps.** AGP normalises zip entry times, but any file generated during
  the build (`lib/generated`, `lib/oss_licenses.dart`, the icon assets) must not
  embed a date. Set `SOURCE_DATE_EPOCH` if one turns up.
- **R8.** Deterministic for a given version, which the AGP pin fixes — but
  `shrinkResources` output should be checked rather than assumed.

Icon generation *was* on this list and is now ruled out: `requirements.txt` was
unpinned, so a fresh `pip install` could rasterise different PNGs. With the
versions pinned, running `generate_icons.py` twice produces byte-identical
output (checked).

## Submitting to F-Droid

Once the script passes, the metadata recipe is roughly:

```yaml
Builds:
  - versionName: 4.12.0
    versionCode: 400001040
    commit: v4.12.0
    subdir: android/app
    sudo:
      - apt-get update
      - apt-get install -y python3-venv
    init:
      - python3 -m venv .venv
      - .venv/bin/pip install -r requirements.txt
      - .venv/bin/python generate_icons.py
    gradle:
      - yes
    srclibs:
      - flutter@3.44.4
    prebuild:
      - $$flutter$$/bin/flutter config --no-analytics
      - $$flutter$$/bin/flutter pub get
      - $$flutter$$/bin/dart run dart_pubspec_licenses:generate
      - $$flutter$$/bin/dart run intl_utils:generate
      - $$flutter$$/bin/dart run flutter_iconpicker:generate_packs --packs material
    scanignore:
      - android/app/build.gradle
```

`AllowedAPKSigningKeys` must carry the fingerprint from
`certificate-fingerprints.txt` so F-Droid only accepts APKs signed with this
fork's key.
