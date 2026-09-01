# Design craft (Threads / Phanpy / Bluesky / Misskey)

Read-only visual hierarchy work. No social writes. Prefer subtraction over new chrome.

## Shipped

1. **Thread connector on status replies** — vertical rail avatar→avatar (`thread_rail.dart` + `threaded_conversation.dart`). Cap visual depth at 2, then “Continue thread”.
2. **Provenance accent** — 2px leading strip on interleaved (non-X) cards via each plugin’s `brandColor` (`provenance_accent.dart`).
3. **Demoted engagement row** — footer chrome uses `onSurfaceVariant`; body text stays loudest. Companion: calm mode hides counts.
4. **Sensitive interstitial that remembers** — “Show” / “Always show for this” → `optionAlwaysShowSensitiveMedia`.
5. **Boost carousel** — consecutive retweet chains collapse into one horizontal row (`boost_runs.dart` + `boost_run_carousel.dart`).
6. **Group identity header** — feed shell shows mark, color, member count.
7. **Sacred scroll** — `FeedSessionCache` + soft refresh + keep-alive already restore place; fixed group-feed race so caught-up restore waits for DB read position (`_feed.dart`). No new floating unread pill (caught-up divider + ↑ on pushed groups cover the “way back”).

## Non-goals

- No purple glow, pill clusters, or card restyle of the hero tweet.
- Keep X-Look tokens; demotion uses `onSurfaceVariant` / outline, not new brand colors on X posts.
