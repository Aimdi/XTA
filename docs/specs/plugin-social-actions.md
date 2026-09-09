# Social reading interactions

Base: Aimdi129 (e6551526).

- Add a shared long-press post sheet with device-only bookmark, folder and note actions. Reuse the existing saved table and archive APIs.
- Add full Bluesky archive snapshots, source filtering and render dispatch; keep Mastodon local bookmarks reachable from cards and action sheets.
- Read Bluesky repost authors and quote posts through public AppView endpoints, with paginated in-app results. Read Mastodon boost authors and quotes through the instance API, and explain instance refusal or unsupported endpoints with retry/browser access.
- Make repost attribution a generous accessible profile target, retaining stable Bluesky DID attribution as well as handles.
- Keep Bluesky following/discovery/list timelines in place when re-entered. First load, deliberate refresh, feed selection and follow changes may fetch; elapsed cache time or a successful empty result must not trigger tab-entry polling.
- Use the Store pattern for new feature state. Add all visible labels through ARB. No writes to social networks, frozen client/database edits or dependency changes.

Verification: HTTP fixture tests for endpoint parameters and paging, saved snapshot/source/media tests, and store lifecycle regressions proving tab entry leaves cached timelines unchanged. Run available formatting, localization generation and analysis.

## Implementation and checks

Source bookmark buttons open the matching local archive. Bluesky and Mastodon keep complete saved snapshots. Threads, Instagram, TikTok, Pixiv, Substack, Hacker News and booru long-press actions can file a portable reading snapshot with source link, author, text and available images. Reddit gains a note action alongside its existing archive controls. Sources without a supported public repost/quote endpoint expose local actions and browser access.

Repost/quote readers use the official Bluesky AppView lexicons and Mastodon status APIs:
- https://github.com/bluesky-social/atproto/blob/main/lexicons/app/bsky/feed/getQuotes.json
- https://github.com/bluesky-social/atproto/blob/main/lexicons/app/bsky/feed/getRepostedBy.json
- https://docs.joinmastodon.org/methods/statuses/#reblogged_by
- https://docs.joinmastodon.org/methods/statuses/#quotes

Mastodon quote lists require a user token on standard Mastodon. The account-free plugin explains refusal and keeps the browser action available; it does not claim a denied response means nobody quoted the post. Instance fallback includes both status resolution and the activity endpoint; subsequent pages follow the answering instance's Link header. A status ID from another server is accepted only after its canonical URL matches.

Added HTTP fixture, pagination, archive snapshot and tab-entry lifecycle regressions. Local checks: ARB JSON and key coverage across all 29 locales; git diff --check. Local fvm/Flutter are unavailable: `python3 l10n.py` reports missing fvm, so formatting, intl generation, analysis and Flutter tests are delegated to integrated pinned CI. The shared note editor comes from the archive module (`openSavedNoteEditor`).
