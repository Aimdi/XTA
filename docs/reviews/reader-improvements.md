# Reader improvements — review checkpoint

Branch: `feat/reader-improvements`, targeting `claude/main` in `Aimdi/XTA`.

This branch combines the existing reading/offline work (#261), Android sharing and post-style notes (#271), and bounded timeline/profile request fixes (#272), then implements the seven approved improvements from `docs/specs/reader-improvements.md`.

## Implemented

- Mixed feeds load plugin sources independently, keep bounded disk snapshots, show source status/retry controls, and hold arriving plugin posts behind a New posts action when reading away from the top. Profile identity snapshots survive restart and remain readable after refresh failure. Existing X timeline caches and reading-position handling remain in use.
- Common error classification distinguishes connection, timeout, session, rate-limit and unavailable-endpoint failures. Existing readable content stays visible.
- Library search is available from Discover and Saved. It searches saved posts and annotations, local notes, followed accounts, groups, and retained article text. Explicit network search chips open the chosen source with the current query; local queries themselves do not contact those networks.
- Mixed feeds group conservative matching article URLs into expandable stacks. Original commentary remains available. Unrelated boost runs keep their existing carousel behavior.
- Discovery supports persistent per-group dismissal and more/less feedback, with an explanation, undo and clearing preferences.
- Retained articles have a text/annotation view with selected-passage notes and locally suggested tags. Accepted tags, highlights, annotations and extracted screenshot text participate in library search. Optional screenshot transcription accepts PNG/JPEG up to 8 MB and asks before using the user's configured AI endpoint; accuracy and image support depend on that model.
- Common post menus expose Save, Note, supported Group actions, Share, then secondary actions. Local note drafts persist text, attachment references and stable target keys; successful save or explicit discard removes the draft, while failed saves keep it.

## Verification completed

- All 130 changed/integrated Dart files parsed using standalone Dart 3.12.2. This checks syntax, not Flutter type resolution or runtime behavior.
- Nine executable article-URL identity/grouping checks passed, including tracking removal and retention of article-identifying query parameters.
- ARB integrity: 1,616 keys across the English reference and 28 translated locales; no errors. Existing interpolation warnings in unrelated keys remain.
- `python l10n.py --check-only`: all locales complete.
- Skill-tree synchronization and `git diff --check` passed.
- Added tests for source races, partial loading, stale snapshots, held updates, draft recovery/discard, archive annotation persistence, discovery feedback, profile stale refresh, local search and expanded-link layouts at light/dark and large text sizes.

## GitHub validation and release gates

PR #273 integrates the approved work. The user explicitly authorized merging and publishing the signed APK.

- The Flutter analyzer passes after integration fixes.
- All 55 focused reader/composer tests pass; light/dark note renders were inspected.
- The Android debug APK built successfully in review run 35085350199.
- The complete regression suite is required before merge. The signed release workflow reruns analyzer and the full suite against the exact tagged source before publishing.

Live authenticated-network and physical-device behavior have not been exercised in this environment.
