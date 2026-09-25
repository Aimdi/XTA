# X reader completeness pass — 2026-09-25

Baseline: `claude/main` at `1d1ca2100979e888153858c3c0f42d9138ba2cbf`
(released as aimdi136).

## Selected improvements

| Gap found in the current app | Implemented response |
| --- | --- |
| X source offers only an inert For you tab | A real search entry and named Subscriptions, Saved and Accounts destinations |
| X plugin does not advertise its existing search ability | Implement the shared plugin search contract |
| Advanced search offers only a media yes/no switch | Exclusive All/Media/Photos/Videos/Links choices and independent reply/repost exclusions |
| People search has an unbounded wait and delayed request invalidation | Immediate cancellation, stale-result guards, a finite deadline and retry coverage |
| Search input competes with several actions on narrow screens | Give input its own full-width row on compact screens and at enlarged text |
| Incomplete post data can crash save, like or sharing | Disable identity-dependent actions and retain usable text/image sharing |
| Post More menu omits existing retweeter navigation | Wire the existing Reposts destination |
| Profile suffix comparison confuses different handles | Compare full handles case-insensitively |

Implementation and acceptance details are in `x-reader-hub.md`,
`x-search-filters.md`, `x-search-reliability.md` and `x-post-actions.md`.

## Further gaps considered

- Conversation refresh is still hard to discover on a normally loaded thread.
  A follow-up must retain offline fallback and guard thread-cache/paging work
  against refresh races, rather than simply exposing cache deletion.
- Search media pages can repeat thumbnails if X returns overlapping pages.
  A follow-up should characterize cross-page identity and refresh resets.
- A separate Following feed in the X plugin would currently reuse mixed
  subscription/group state. It needs an explicit source scope before adding a
  tab that claims to show X only.

Existing profile search, follower/following pages, media subtypes, local notes,
saved posts and local likes already exist; this pass exposes and repairs the
existing reader instead of duplicating those screens.

## Boundaries and evidence

X remains read-only. No client/database edits, new network endpoints, dependency
upgrades or SDK changes. The unrelated open Plugin Store, Antennas and RSS-cover
PRs are outside this branch. Automated validation uses controlled fixtures;
authenticated X response behavior and physical-device checks remain distinct
from these tests.

## Validation

- Pinned Flutter 3.44.4 / Dart 3.12.2.
- Complete Flutter suite: 2,629 passed; five opt-in live tests skipped.
- Focused search, X navigation and footer suites: 83 passed, including actual
  destination/back navigation and 320dp layouts at 200% text size.
- Flutter analysis: no errors or warnings; informational lints remain.
- New-file formatting, ARB integrity, skill-tree sync and whitespace checks pass.
- `l10n.py` ran. The new Links label is present in all 29 locale files; its report
  still identifies 17 unrelated existing missing keys per non-English locale.
  Locale ordering churn was excluded from the patch.
- Frozen client/database paths and dependency/SDK pins are unchanged.

No authenticated-X or physical-device test was performed; no new APK is included
in this feature branch.
