# Timeline and profile loading recovery

The reported endless loading has two concrete paths: authenticated setup caches an unfinished future for six hours, and pinned-account HomeTimeline fetches omit the normal request timeout. Group search recovery can also wait for every member sequentially in small batches.

- Bound authenticated setup; evict failed or timed-out initialization, and ignore obsolete completions.
- Use abortable HTTP GET requests with one retry for transient connection/502/503/504 failures, within the original time budget. Preserve authentication and rate-limit responses.
- Apply one request budget to setup, account rotation, and pinned-account timeline requests.
- Bound each group chunk's profile fallback, preserving completed/cached results and stopping new fallback work after the budget expires.
- Add deterministic regression coverage for stalled initialization/HTTP, recovery, retry exclusions, and partial fallback results.

This is a focused repair of failing API reads, not a client or database rewrite. No endpoints, account credentials, database schemas, dependency pins, or UI text change.
