# Reader recovery and controls

Build incrementally on aimdi135. Keep the existing visual language, local-only
actions, localisation, and pinned toolchain. No client or database changes.

## Recovery

- Bound mixed-feed cache and network work; a cache failure cannot prevent the
  network attempt or leave loading active. Coalesce retries per source and
  invalidate cancelled work before it can publish.
- Automatically retry temporary failures at bounded intervals while visible.
  Pause offline/covered/background views, recover after returning, and avoid
  nested recovery controls issuing duplicate requests. Honour known rate-limit
  deadlines. Session and parsing failures remain manual.
- Bound video startup and first-frame waits. Preserve posters and feed state;
  safely retire late native work and limit automatic retries.

## Performance

- Reuse the sorted mixed-feed items on status-only changes.
- Coalesce cache trimming, and avoid decoding unrelated sidecars for prefix reads.
- Stage Home account/group filters and apply them once. Preserve the last usable
  account and lazily build large lists. Cancel dismisses the draft.

## Controls

- Pin Reset/Apply below the filter list and show an explicit active-filter count.
- Share the For You refresh path between pull and toolbar without remounting the
  feed on ordinary refresh. Keep failed-pagination retry beside the failure.
- Long-press Home opens the source/group picker.
- Keep profile Follow/settings and follow counts independently tappable.

## Verification

Regression tests cover stalled/throwing cache reads, overlapping/late retries,
visibility and lifecycle recovery, exhausted retry budgets, native startup
timeouts, staged filter apply/cancel, long-press navigation, and narrow/large-text
layouts. Run the existing related tests and repository verification gates.
Physical Android performance and native decoder behaviour require device checks;
do not claim measured speedups from static changes alone.
