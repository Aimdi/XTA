# Article reading continuity (aimdi128)

RSS and Substack gain a shared compact reading surface. The existing icons,
article links, publication/group navigation, local save/like, podcasts and TTS
remain reachable. Reader appearance moves into a sheet with persistent text
size and line spacing, shared across the two plugins. A thin progress indicator
and an explicit finish action distinguish opening an article from finishing it.

Opening never marks an item read. Automatic completion requires an actual user
scroll to the end of a full article and at least twelve seconds of active
reading; previews, empty content, and live web fallbacks require the explicit
finish action. Completion still updates the existing plugin read-ID stores.

A bounded local reading journal stores the visible paragraph and its offset,
with proportional fallback if the article changes, and restores on reopening
including after process restarts. Typography changes retain the paragraph.
Cached article bodies render without a refresh replacing the text mid-session.
State is managed by Triple Stores. Existing client/database and dependencies
stay untouched. Offline pinning hooks are integrated with the parallel storage
work; no cache expiration is presented as durable offline availability.

Validation covers preference restore and malformed input, paragraph continuity,
completion thresholds and preview exclusion, concurrent journal writes, and the
compact controls at narrow widths and large text. HTML sanitization regressions
remain part of the existing tests. Flutter validation runs on the pinned CI SDK.
