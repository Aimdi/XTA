# Quiet recovery for temporary read failures

The reader should recover from a short connection failure or server outage
before presenting an error. Existing posts remain visible; an initial load keeps
its normal loading state. A persistent failure must still stop loading and offer
the existing manual retry or account action.

## Design

- A shared retry budget permits at most two extra attempts, after 400 ms and
  1,200 ms. Nested read scopes and HTTP helpers share this budget, preventing
  multiplicative retries within one logical read.
- Establish the same budget around standalone plugin fallback walks and
  multi-source refreshes, including Substack formats and HN story/author fanout.
  These boundaries run the operation once; only eligible GETs are repeated.
- Retry connection exceptions, returned timeouts and HTTP 408/500/502/503/504.
  Do not retry authentication/permission failures, missing content, rate limits,
  parsing/programming errors or cancellation. A wrapped X bootstrap failure is
  eligible only when its cause is a transient connection/timeout failure.
- Use the existing `ReadRequestScope` for X feeds, profiles, search and groups.
  Cache reads and post-write snapshots retain their current single-attempt
  behavior. Existing deadlines and generation/cancellation guards remain.
- Add an HTTP GET extension for plugin clients. It preserves headers, response
  parsing and existing timeout values, and aborts a request on its deadline.
  GET retries occur before plugin errors reach stores. POST/PUT/DELETE, uploads,
  login/token refresh and explicit local writes are unchanged.
- Threads retries re-enter its existing departure queue, request spacing,
  jitter and session cooldown checks. Cancellation also clears a pacing wait.
- Respect HTTP `Retry-After` minimum delays. If the server's delay cannot fit
  inside the existing deadline, return the failure without an early retry.
  Existing visible-screen recovery also respects this minimum delay.
- Do not add a setting, new UI text, background refresh service or dependency.
  Flutter remains 3.44.4; `lib/client/` and `lib/database/` remain unchanged.

## Implementation and verification

1. Add tested pure retry policy/budget and explicit GET helper. Cover temporary
   success, persistent failure, authentication/rate-limit exclusions, response
   headers, Retry-After seconds/date, deadline abort, cancellation and shared
   nested budgets.
2. Connect `ReadRequestScope.start` and verify feed loading/error transitions,
   retained posts/cursors, profile/search recovery, replacement and disposal.
3. Use the GET helper across plugin client read methods. Preserve method-specific
   timeout and fallback/pacing behavior. Test representative plugin boundaries
   and ensure POST/upload behavior remains one attempt.
4. Verify visible-screen retries honor Retry-After. Run focused and full Flutter
   tests, analyzer, independent review and CI before publishing the final status.

Existing X account rotation and its one transport retry are preserved. Existing
screen recovery has a separate bounded budget for later failures and network
handover; this patch does not create retry loops or retry hidden screens.
If a request consumes its entire deadline, it fails within that deadline rather
than starting another long wait. This improves transient failures; it cannot
guarantee success during outages or with expired credentials.

## References

- [HTTP method/retry semantics and Retry-After](https://www.rfc-editor.org/rfc/rfc9110.html)
- [Dart HTTP package](https://pub.dev/packages/http): inspected pinned HTTP 1.6.0
  locally, including abortable requests and response-stream handling.
