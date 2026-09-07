# Playback continuity

Base: XTA aimdi128. Scope: the shared Substack podcast session and switching an
existing pooled video's quality. Existing icons, player UI and dependencies stay
in place; client and database code are outside this change.

## Podcast restart behavior

- Retain one bounded local record containing episode URL, title, elapsed time and
  known duration. Accept only well-formed HTTP(S) episode URLs and sane times.
- Restore the episode's controls paused after restart, without creating a native
  player or starting a network request. The next explicit Play resumes it.
- Persist progress periodically with serialized writes; flush when the app moves
  to the background and when the store closes. Completion and explicit Stop clear
  the retained episode. A changed episode must not receive stale events or seeks.
- Preserve the existing app-wide media session, with native playback kept lazy.

## Video quality behavior

- Inspect media_kit's open/seek behavior. Load the requested variant paused and
  preserve a finite, in-range position after its duration becomes available.
- Bound the wait and verify seek progress. If the decoder cannot restore the
  position, fall back to a working stream at the beginning rather than hanging.
- Serialize source mutations, let the latest choice win, and cancel restoration
  when the pooled player is disposed. Restore only valid volume, rate and play
  intent; do not let stale async work restart a disposed or superseded player.
- Keep the original request headers and show recoverable existing error UI if
  loading a variant fails.

## Verification

Use injectable playback adapters and storage to test paused cold restoration,
completion, corrupt/oversized records, queued writes, late events, quality seek
success and fallback, rapid selections and disposal. Run focused tests and the
repository verification/build gates on pinned Flutter 3.44.4. Android/libmpv
device validation remains necessary; fake tests do not claim native seek proof.
