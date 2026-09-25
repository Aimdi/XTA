# X post action reliability

## Problem

X may return visible posts without an author handle or a post ID. Several footer
actions currently assert those optional fields, so sharing, local likes, and
saving can fail after a tap. Profile navigation also treats a handle suffix as
the current profile, making an unrelated author such as `malice` inert while
viewing `alice`.

## Intended behavior

- A post with an ID shares its configured base URL plus its author status path;
  missing/empty handles use the canonical `/i/status/{id}` path.
- Link sharing and integrations use the same link, with no literal null fields.
- Without a nonempty post ID, conversation, quotes, local likes, local saves,
  folder selection, and the post actions menu are disabled. Content and image
  sharing remain available; link-dependent share options are disabled.
- Local like accessibility exposes whether its button is enabled. All existing
  local-only actions remain local and retain existing localized labels.
- The More menu exposes the existing reposted-by screen as well as quotes,
  opening the correct read-only tab without new endpoints or strings.
- The share sheet scrolls on a short viewport or with large text, keeping its
  actions and cancellation reachable.
- A profile is suppressed only for an exact case-insensitive current handle
  match. A different handle with the same suffix still opens.

## Scope and checks

Change only the tweet footer, local like button, navigation helper, and focused
tests. Keep API clients, persistence, pinned dependencies, and generated files
unchanged. Cover partial identity interactions, outgoing shared URLs/content,
short-screen share sheet reachability, and same/different handle navigation.
Run the focused Flutter tests and analyzer when the pinned SDK is available.
