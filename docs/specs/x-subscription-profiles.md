# Followed X accounts keep up with X

The owner asked for subscription names and pictures to stay up to date,
after QuaX 4.20 did the same (Teskann/QuaX#283). Nothing is written to X.

## Before

- A followed X account keeps the handle, name and picture it had when it was
  followed. When the account changes them on X, Subscriptions and group
  member lists show the old ones.
- A new handle is worse: groups and Following search X with `from:<handle>`,
  so a renamed account's posts stop appearing in them.
- Only "Find broken subscriptions" repairs either, with a profile lookup for
  every account.

## After

- Every page of posts X sends names the accounts behind it: each post's
  author and the authors of what it reposts or quotes. When one of them is
  a followed account whose handle, name or picture differs from what XTA
  keeps, XTA saves what X sends now. A profile opened from X does the same
  for its account.
- This needs no request of its own: it reads only answers that arrive anyway.
- Only what X sent is saved: a handle, name or picture X left out stays as
  it was. The same picture at another size (`_normal`, `_400x400`…) is not
  a change.
- Only changed accounts are written, in one batch.
- A new handle also rebuilds the open group feeds, so their searches name
  the account by it. A new name or picture does not: the feeds stay as they
  are.
- Where it happens: the pages of groups and of Following (one hook in the
  group feed, which both use) and an opened profile.

## Code

- `lib/subscriptions/subscription_profiles.dart` (new, pure): the authors
  of a page's posts; which followed accounts changed, and to what.
- `lib/subscriptions/users_model.dart`: `refreshProfiles`, the batch write
  and the reload.
- `lib/group/_feed.dart`: hands each page's authors over.
- `lib/profile/profile.dart`: hands a freshly loaded profile over.

## Tests

- The authors include reposted and quoted posts' authors.
- A changed name, handle or picture is picked up; the same picture at
  another size, an account not followed, or one X sent without a name is
  not; what X left out is kept.
- The model writes only changed rows, tells the feeds to rebuild only for a
  new handle, and leaves the rest of the subscriptions alone.
