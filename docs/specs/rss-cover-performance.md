# RSS cover decode performance

Bound RSS article-cover decoding to the size each cover is painted at. Preserve
the current card layout, read state, navigation, failure fallback, and lazy feed
construction.

## Observed problem

`RssItemCard` renders every cover with `ExtendedImage.network` but does not set
`cacheWidth` or `cacheHeight`. The normal feed slot is only 88dp square, while
RSS feeds commonly publish much larger article artwork. Flutter can therefore
retain a source-sized decoded bitmap for every visible cover and evict other
images from the shared cache while the reader scrolls.

This is static diagnosis, not a device benchmark. The current cloud environment
has no physical Android device, so frame time, jank rate, and peak-memory changes
remain unverified.

## Measurement contract

| Item | Value |
|---|---|
| Flow | Scroll an RSS timeline containing cover images |
| Static baseline | RSS cover provider has no requested decode width |
| Test target | 88dp cover at 2x DPR requests a 176px decode width |
| Device target | Mid-range Android phone, profile build, same feed and fling script |
| Behavior guard | Card layout, tap navigation, read-state emphasis, large-text layout, and failure fallback are unchanged |

For illustration only, a 1200×630 ARGB decode occupies about 3.0 MB before
cache overhead. A proportional 176px-wide decode is about 65 KB. Actual savings
depend on each feed's source image and must be measured on a device.

## Implementation

- Derive the requested decode width from the cover's real `LayoutBuilder`
  constraint multiplied by the current device-pixel ratio.
- Leave the decode width unset when the constraint is unbounded or non-positive.
- Keep the existing `ExtendedImage` disk cache, `BoxFit.cover`, clipped radius,
  and load-failure placeholder.
- Do not change feed cache extent, prefetching, dependencies, Stores, routes,
  localization, `lib/client`, or `lib/database`.

## Verification

- Add a widget test that renders the normal 88dp RSS cover at 2x DPR and inspects
  the image provider's 176px resize request.
- Keep the existing narrow-phone RSS overflow coverage green.
- Run the focused RSS/card tests, changed-file analysis, formatting, and the
  repository verification workflow.
- On a representative device, compare at least three profile-mode runs for
  build/raster frame times and steady/peak memory using the same image-heavy RSS
  feed. Record the median and p95 before making a numeric performance claim.

