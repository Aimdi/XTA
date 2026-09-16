# Approved reading improvements (1, 2, 3, 5, 6, 7, 8)

Integrate the existing verified sharing/notes, loading-recovery and offline-reading branches. Keep the app read-oriented, localized, and Store-driven; no new client/database edits or dependency pin changes.

## Deliverables and acceptance

1. Cached/progressive reading: retain profile and supported source snapshots, show completed plugin/group sources without waiting for the slowest, protect reading position and expose held updates through a New posts action. Cold/stale/partial/error states must be distinguishable.
2. Actionable failures: classify connection, authentication and rate-limit errors; show source-local retry/reconnect/diagnostics actions without replacing readable content.
3. Unified search: one reachable entry searching saved posts, notes, followed accounts, groups and retained article text locally, with selected remote source searches. Local results precede network results; obsolete searches cannot replace current results.
5. Repeated links: conservative canonical article URLs group repeated links in an expandable stack in mixed feeds. Preserve every original post/commentary, source, stable identity, and chronology. Avoid collapsing unrelated posts that link only to a home page or login/tracking host.
6. Discovery feedback: persistent dismiss/more/less preferences, explainable recommendation reasons, and local feedback ranking; AI is optional. Dismissed accounts remain excluded after refresh/restart and can be restored.
7. Searchable archive: index retained article text, add local highlights linked to notes and useful suggested tags, and optional screenshot text extraction through the user's configured AI with explicit per-use action. No image/text is sent automatically. Provide usable offline results.
8. Consistent post actions: Save, Add note, Add to group, Share, then secondary actions where the source supports them. Automatically recover local note drafts, including attachments and target identity, after app interruption. Successful save or explicit discard removes the draft; failed saves retain it.

## Verification

Cover request races, partial success, stable grouping and expansion, offline search, persisted feedback, draft restoration and failed-save retention with meaningful tests. Render normal/dark/large-text layouts and build a debug APK using the pinned GitHub toolchain. Exercise integrated existing tests. Live authenticated service/device behavior must be identified separately from fixture coverage.
