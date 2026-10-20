# Loading and errors take no room of their own

Status: **in progress** (2026-10-20). The owner: "Errors or loading sometimes take a bit of extra space. I don't like
that."

## The problem, as the code had it

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
