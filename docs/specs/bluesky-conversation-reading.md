# Bluesky conversation reading and safe cards

## Why
The existing Bluesky reader reconstructs branches but offers no author focus or
reply ordering, and recursively traverses user-controlled reply depth. Generic !warn labels currently hide only attachments while leaving text
visible, and !hide is treated as a dismissible media warning. Several footer controls expose only numbers to accessibility services.

## Changes
- Build cycle-safe, duplicate-safe conversation branches iteratively, retaining
  orphaned replies and stable sibling ordering. Avoid recursive descendant counts.
- Add author-thread focus that retains connecting reply context, sibling ordering
  by original order/date/likes, expand/collapse all, and return to selected post.
- Preserve loaded replies and controls across failed refreshes, ignore work after
  disposal, and prune stale collapse selections when fresh replies arrive.
- Preserve media-only warnings for media labels. Cover full bodies and quoted
  bodies for !warn; do not reveal !hide or !no-unauthenticated content in this
  public reader. Reset reveal state when content identity or revision changes.
  This follows the official moderation label definitions at
  https://github.com/bluesky-social/bsky-docs/blob/main/docs/advanced-guides/moderation.md;
  custom labeler definitions and account moderation settings are out of scope.
- Give navigation/count controls explicit accessible labels, expose quotes as an
  ordinary discoverable action, and describe media thumbnails with supplied ALT.
- Keep all state in flutter_triple stores, use localized strings, and retain
  public read-only APIs with local likes/bookmarks unchanged.

## Validation
Pure tests cover deep/cyclic/duplicate outlines, author focus and stable sorting.
Store tests cover stale results, refresh failures, collapse state and disposal.
Widget tests cover warning resets, quote warnings, controls, accessible labels,
and compact RTL/enlarged layouts with existing Bluesky reading fixtures.
