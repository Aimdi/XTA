# Mastodon conversation reader

## Problem

Long public conversations already have nested reply cards, but readers cannot
isolate the selected author's continuation, fold a whole conversation, or return
to the selected post without manually scrolling. Hidden branches obscure whether
the author continued through another person's reply. An empty context currently
looks unfinished, and the parent preview assumes the server orders every ancestor.

## Design

- Add an author's-thread filter alongside all replies. Keep intermediary replies
  visible and label them as context so the reply chain remains understandable.
- Normalize the reply forest once, preserve sibling order, and compute branch
  sizes without repeatedly walking every subtree. Missing parents, duplicate IDs,
  self references, and cycles must remain finite and readable.
- Offer expand/collapse-all controls and an always available return-to-selected
  action. Restore the selected card by closing ancestor context and scrolling to
  the beginning; respect the system's reduced-motion preference.
- Order known ancestors by their actual reply links and identify the direct
  parent by ID. Preserve disconnected context supplied by the server.
- Explain empty public context and an empty author filter. Keep already loaded
  content readable on refresh failures and allow retry.
- Keep filtering and expansion in `MastodonThreadStore`, preserve user choices
  during refresh, ignore superseded requests, and stop updates after disposal.

## Constraints

Read-only, no new endpoints or dependencies, no changes to frozen client/database
folders, and no generated localization edits. All added copy uses ARB keys in
every locale. This work owns the Mastodon thread screen/store, pure outline helper,
and focused conversation tests.

## Verification

Pure tests cover branch order, missing parents, duplicate/cyclic payloads, deep
threads, author context preservation, and ancestor ordering. Store tests cover
folding/filtering while a refresh is pending, retry after failure, stale responses,
and disposal. Widget tests cover thread controls, empty states, navigation back to
the selected post, and compact enlarged-text rendering.

## Implemented validation

- Twelve focused conversation cases cover author context, malformed/cyclic data,
  a 10,000-level conversation, refresh lifecycle, both empty states, branch menus,
  return navigation, and a 320 px RTL route with 200% text scaling.
- The focused thread suite plus existing reading/archive suites completed
  successfully (25 cases) on Flutter 3.44.4. The existing archive menu taps emit
  hit-test warnings; there were no thread warnings or failing assertions.
- Analysis of the thread screen, store, outline, and tests reports no issues.
