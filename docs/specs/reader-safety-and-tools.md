# Reader safety and tools

Implement the seven approved follow-ups on the existing profile-recovery branch.
Keep client/database files and pinned dependencies unchanged.

- Cleanup removes only confirmed missing/suspended subscriptions. Transport,
  parsing and repair failures remain unreachable, with bounded lookups.
- Search a snapshot of the current feed's loaded/cached X and plugin posts by
  text, author and links. It must never fetch pages or change the original scroll.
- Observe Flutter memory pressure and release only unreferenced video players,
  including pending creations; never dispose a player attached to a widget.
- WebDAV uses persisted, target-specific strong ETags and conditional writes.
  Unknown/newer remote versions require download/review. Archive each replaced
  document and offer history restore; servers without safe validators fail closed.
- Download retries retain partial files with validators, use Range/If-Range,
  validate Content-Range and lengths, and restart safely on changed/unsupported
  representations. Explicit cancellation removes partial data.
- Rate-limit UI shows known relevant reset times and disables retries during
  the corresponding window, with lifecycle-safe timers and no auto-retry burst.
- Live canaries run and report per service, keep complete sanitized failure
  context, and distinguish setup failures from service failures.

Use Store state, localized labels, focused regression tests, full CI and an
Android build. Device-only performance verification requires an attached phone.
