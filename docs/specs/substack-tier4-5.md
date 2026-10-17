# Substack Tiers 4 and 5: what can be done without the open decisions

Status: **in progress.** The owner asked for the remaining tiers (2026-10-17). Tier 3 comes first
(`docs/specs/substack-tier3-features.md`).

## Blocked, and on what

| Item | Tier | Waits on |
|---|---|---|
| Login and subscription import | 4 | Decision 2 (sign-in in scope) and decision 3 (cookie storage) in `docs/substack/audit.md` §10 |
| Entitled paid bodies, expired-session prompt | 4 | the same |
| Comment reply and like, like and restack sync | 4 | the same; and CLAUDE.md allows write actions only on explicit user action |
| Auto-download on Wi-Fi, new-post notifications | 4 | decision 7: new dependencies (a scheduler, a notification plugin) |
| Voiceovers | 4 | the `audio_items` shape, from a HAR |
| Following Notes | 5 | sign-in |
| Share quote to X | 5 | decision 4 |

Footnote sheets and the image viewer (Tier 4) are done in the native reader (`docs/specs/substack-native-reader.md`
step 3).

## 1. OPML import and export (Tier 4)

- **Export** writes OPML 2.0 with one `<outline type="rss">` per followed publication: its name, its site and
  `{base}/feed`. It goes through `saveWithDialog`.
- **Import** reads `xmlUrl` and `htmlUrl` from every `outline` with the `html` parser the plugin already uses. Adding
  `package:xml` would be a new direct dependency.
- Each address resolves through `SubstackClient.fetchPublication` one at a time. The transport's limiter and 429
  rule apply, and a follow is added with `placeSubstackFollow`, so nothing already followed is overwritten.
- The import reports how many were added, already followed, and failed.
- Both live on the Substack settings screen.

## 2. Highlights with Markdown export (Tier 5)

These are native reader only: the web view's selection never reaches Dart.

- **Saving.** A selection's context menu gains *Highlight*. The selected text is split at block boundaries and
  saved as quotes for the post, keyed by `substackPostKey`.
- **Storage.** One preference, bounded to 2,000 quotes in all, oldest dropped first. It is backed up with the
  plugin's other preferences.
- **Display.** A quote is drawn with a background wherever its text occurs in a text block.
- **Managing.** The reader's menu gains *Highlights*: a sheet that lists the post's quotes, removes one, and exports
  them all as Markdown (title, link, then one `>` quote per highlight) through the system share sheet.

## 3. TTS sentence highlighting (Tier 5)

The speech store speaks long text in chunks. It would expose the chunk being spoken, and the native reader would draw
it. This needs the chunking to follow block boundaries, which changes speech for every reader. It is written down
here and left until items 1 and 2 are done and the speech tests are mapped.
