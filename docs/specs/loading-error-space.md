# Loading and errors take no room of their own

Status: **in progress** (2026-10-20). The owner: "Errors or loading sometimes take a bit of extra space. I don't like
that."

## The problem, as the code had it

An inventory of `lib/` (2026-10-20) found these kinds:

- About 25 screens showed loading with `if (loading) LinearProgressIndicator()` inside a `Column` or a list. While
  something loaded, a 4 px bar (2 px in some places) was laid out above the content, so everything below moved down,
  and moved back up when loading stopped.
- Three places kept a 2 px gap for that bar even while nothing loaded: the home feed's batch line, group discovery,
  and (as a divider, which stays) the in-app link browser.
- On the home feed both happened at once: a reserved 2 px line for X's batches, plus another 2 px line inserted for
  plugin sources, so the posts moved by 2 px and sat 2 to 4 px lower than they needed to.

## Rule 1: a loading line is drawn over the content, never above it

`LoadingHairline` (`lib/ui/loading_hairline.dart`) draws a 2 px line over the top edge of what is loading (or the
bottom edge of a header whose content loads below it). It is laid out in a `Stack` with `StackFit.passthrough`, so
the content is laid out exactly as without it: nothing moves when loading starts or stops, and no room is kept while
nothing loads.

- The line has no track, so only its moving part covers the content, and two lines in one place (a feed and the
  plugin posts around it) both stay visible.
- A determinate line at zero runs as indeterminate: an empty line says nothing.
- It keeps the semantics label the bar had.

Kept as they were: bars that show a value (downloads, storage, reading progress, poll results, the cleanup dialog),
the accounts list's placeholder (the bar is the whole list until it loads), TikTok's line, which already floats over
its web view, and the link browser's line, which takes the place of the divider under its toolbar.

A spinner where the next items will appear (the end of a list loading more) stays: it fills room the new items take,
and nothing above it moves.

A button that starts a load shows it inside itself, as the note editor's Save does: the Substack add screen's Preview
button carries the spinner instead of a 28 px gap opening under it.

## Rule 2: nothing is kept for a status that is not showing

A footer that shows "loading more", a failure or a "load more" button collapses to nothing while it has none of
them. Found: the plugin activity list kept 48 px, Substack's Home, Inbox and Notes feeds 32 px, Bluesky's follows
24 px.

## Rule 3: a failure that leaves the content readable floats over it

A refresh failure, a partial failure (some sources or batches failed) or a stale-cache notice, shown while the
content stays on screen, floats over the content's top edge on the frosted pill Home's navigation bar uses
(`FloatingNotice` with `NoticePill`, `lib/ui/floating_notice.dart`), instead of being laid out above it. Nothing
moves when it comes or goes; it is announced as a live region, since nothing moving draws the eye. Notices nested
inside one another (the home feed's plugin chips over its X batch notice over its cached posts) float one below
the other rather than on top of each other.

A failure that replaces the content (a first page that never loaded) keeps its full-screen layout: there is
nothing to push.

## Rule 4: a failure where the next items would be is one compact row

At the end of a list, where a page failed to load, the failure is `ReaderFailureNotice`: one 48 px row with Retry,
details and, where it applies, dismiss. A full-page error layout (`FullPageErrorWidget`, about 250 px, with the
stack trace when one is passed) is never a list row.

## Rule 5: a counting-down notice is no taller than an idle one

While a surface retries on its own, the Retry button carries the countdown (its icon turns, the sentence is its
tooltip and spoken label) instead of a sentence beside it. Measured with Inter on a 360 px phone in German, the
notice was 88 px during the countdown and 48 px otherwise; it is now 48 px throughout.

## Rule 6: refreshing what is shown uses the line, not a spinner row

Content already on screen that is reloading (a thread's replies, comments, a profile) shows the loading line over
it. A spinner row between a header and its content pushed the content down by 68 to 84 px on every refresh.
