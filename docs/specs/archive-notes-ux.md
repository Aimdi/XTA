# Archive notes and editing

Base: XTA Aimdi129, `e6551526`.

## Problems

The saved-post note editor expands inline and shifts reading position. It has no
cancel or busy/error state. The local note composer changes height as text and
attached posts grow, puts save below the scrollable content, rebuilds media
previews on each keystroke, and ignores reduced motion. Archive notes repeat an
X account header and render unbounded bodies. Note threads have no stable keys
and indent in physical left coordinates.

## Implementation

- Replace saved-post inline editing with a reusable modal note editor shared by
  plugin post actions. Keep a compact, clearly labeled note preview in archive.
- Use a bounded keyboard-aware note sheet with fixed title/actions, scrollable
  editor/context, visible close/save, discard protection, and recoverable errors.
- Manage composer state with flutter_triple Store. Preserve public local note
  composer API and stored records; retain all attachment, quoted-post, reply and
  backup behavior.
- Render local notes as notes: compact local-note/date header, readable body,
  clear edit/reply actions, bounded archive previews and full thread text.
- Keep list items keyed and scroll state stable; honor app/system reduced motion
  in sheet transitions. Avoid whole-list size animations.
- Localize all new strings in every supported locale. Do not edit frozen client,
  database, generated code or dependency pins.

## Verification

Exercise draft dirty detection, busy state and failure/retry behavior. Exercise
keyboard-visible editor layout and canceled edit state where Flutter is present.
Run existing local-note logic and relevant saved UI tests and analyzer; record
unavailable toolchain checks explicitly.

## Verification result

- `python scripts/validate_arb.py`: passes (existing unrelated translation
  placeholder warnings only); all 29 ARBs contain every new key and metadata.
- `l10n.report_missing`: all locales complete; all five new keys are referenced.
- `git diff --check`: passes. Editor feature state uses Store exclusively.
- Added four draft-store tests and three widget interaction/layout tests for CI.
- This container has no FVM/Flutter/Dart. `python l10n.py` stops at missing `fvm`;
  formatter, localization generation, analyzer and Flutter tests must run in
  the pinned GitHub Actions environment before merging.

Saved-post notes now share `openSavedNoteEditor` with plugin post actions.
Existing composer arguments and storage schema are unchanged. Attachment cleanup
runs after the database write succeeds, preserving saved files after write failure.
