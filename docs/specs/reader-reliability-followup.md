# Reader reliability follow-up

Approved scope: progressive X group batches, cooperative cancellation, complete
annotation backups, connection/resume recovery, undo subscription/group changes,
read-operation diagnostics, and repeatable Android session checks.

Keep the existing profile recovery fix. Do not edit client/database code or
pinned dependencies. Network cancellation stops subsequent application work;
already-issued shared HTTP requests finish without closing shared clients.

Group batches publish filtered previews, retain successful results for selective
retry, and preserve displayed order once scrolling begins. Recovery applies only
to visible connection/timeout failures, once per connectivity/resume event, with
debouncing. Never automatically repeat mutations or authentication/rate failures.

Backups explicitly include durable article annotations (not profile/feed
caches); legacy imports remain valid. Undo restores exact removed subscriptions
and memberships, without replacing unrelated or subsequently edited records.
Diagnostics retain a bounded in-memory list of operation/stage/outcome/timing,
without request URLs, usernames, tokens, post bodies, or exception messages.

Validate with focused regressions plus full CI and an Android build. Device
journeys must report actual measurements only when a device is attached.
