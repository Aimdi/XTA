# Mastodon feed reliability

Public timelines, following merges, and Explore must keep the most recent read
authoritative when refreshes overlap or the reader changes instances. Forgetting
or disposing a store invalidates pending callbacks immediately. Failed refreshes
retain readable content and the public timeline's original source and cursor.

Public timeline pagination exposes a recoverable error and explicit retry while
keeping posts on screen. Start and completion notify observers without replacing
the list with a loading page. Paging uses the server timeline entry identifier,
including the outer boost identifier, and stops when a server repeats its cursor.
Duplicates collapse by canonical post URL, because remote IDs are server-local.

Following merge caches are isolated when instance configuration changes or a
newer forced refresh supersedes a pending one. Reading an account subset for a
mixed Home/group feed must not overwrite the dedicated Following timeline.
Successful independent requests remain usable by their own callers.

Implementation stays within `mastodon_store.dart` and regression tests. No
authentication, dependencies, database, or write endpoints change. Tests cover
out-of-order completion, forget/disposal, preserved content, retry notifications,
duplicate canonical URLs, repeated cursors, and independent mixed-feed reads.

Validation: 21 dedicated reliability regressions pass, together with 33 existing
instance, client journey, and reading restoration tests. Targeted Dart analysis
reports no issues. This covers fixture-driven behavior; no live instance or
Android device validation is claimed.
