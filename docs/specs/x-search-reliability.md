# X search reliability and compact layout

## Problem

People search uses Store.execute's delayed cancellation, allowing an old request
to briefly publish results during its 50ms cancellation debounce.
There is no application-level request deadline. Clearing or leaving search
needs immediate invalidation of pending work. On a phone, the field also competes
with Back, library search, Antennas and Follow for one toolbar's width.

## Change

- Give people-search requests a generation and ignore stale success/failure.
- Clear previous results and errors when a new query starts; whitespace-only
  queries clear synchronously without network activity.
- Invalidate requests when the Store is destroyed and retain explicit retry.
- Bound each people-search wait to 30 seconds and permit retry after timeout.
- On compact windows or enlarged text, place the search field in its own
  full-width row above the result tabs. Keep the existing wide-window layout.

## Acceptance

- Older successes and failures cannot change newer results or loading state.
- Clear and disposal cannot revive obsolete results.
- Failed current requests show an error and a later retry can succeed.
- At 320dp with 200% text, Search, Clear, Advanced, library search and Antennas
  remain reachable without layout exceptions.

## Boundaries

Use the existing SearchTimeline backend and flutter_triple Store pattern.
No client, database, dependency, route or localization-key changes. Validate
using controlled Futures and widget tests; authenticated X results are not
available in this environment.
