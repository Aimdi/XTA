# Substack post identity (BUG-02)

Base: `645f288`. Implements BUG-02 from `docs/substack/audit.md` §7.

## Problem

A post read through the API has Substack's numeric id (`4242`). The same post read through RSS, which the client falls
back to when the API fails, takes its slug as id (`hello`): Substack's RSS carries no numeric id, only the post URL
(the repository's RSS fixture for a Substack publication has `<guid>https://www.slowboring.com/p/hello-there</guid>`).
Every place that compares ids treats the two copies as two posts:

- read state: a post read during an outage shows unread once the API is back, and the reverse;
- local likes and saves: the heart and bookmark of one copy are off on the other;
- the feed and the publication page: a load-more page from RSS can repeat the API page;
- comments: the RSS copy asks `/api/v1/post/{id}/comments` with its slug, where the endpoint takes the numeric id
  (UNVERIFIED live: the egress policy blocks Substack).

A bare slug is also no identity on its own: two publications can both have `/p/welcome`.

## Change

- **One key per post**, `substackPostKey(post)`: the canonical URL without scheme, `www.`, query, fragment or trailing
  slash (`platformer.news/p/hello`). The API's `canonical_url` and the RSS item's `<link>` agree, and the key is the
  same whichever host the copy was fetched from. Without a canonical URL it is the publication host plus `/p/{slug}`,
  and without a slug the post's own id.
- **One comparison**, `sameSubstackPost(a, b)`: the same key, or the same numeric id.
- **Read state.** Marking a post read stores its key. A post counts as read when its key or its own id is stored, so
  every read recorded before this change keeps working. One entry per read, so the 400-entry cap still means 400
  posts. Marking unread removes both.
- **Likes and saves** compare with `sameSubstackPost`. Stored snapshots carry their canonical URL, slug and base URL,
  so existing entries match the other copy too.
- **Feed and publication page** merge copies by key and keep the API copy, which has the numeric id and the counts.
- **Comments.** A copy without a numeric id asks the API for it (`fetchPost` by slug) before loading comments.

The id-based store methods stay, as the primitives under the post-level ones; their tests are unchanged.

Unchanged on purpose:

- `substackFeedPostKey`, used for widget keys and Home's interleaved item ids;
- offline article ids: the offline lookup already falls back to the canonical URL;
- reading-position ids, already slug-based.

## Limits

- A read recorded through RSS before this change is stored as a bare slug. It still marks the RSS copy, as before,
  and not the API copy: a bare slug cannot say which publication it belongs to.
- The "keep offline" button of one copy does not show that the other copy was kept. Opening either copy offline
  still finds the kept article through its canonical URL.

## Tests

- The BUG-02 probe flips: the two copies share a key and are the same post.
- Unit tests for the key: canonical URL normalisation, the host and slug fallback, the id fallback, and two
  publications with the same slug.
- Read, like and save across copies, including entries stored before the change.
- Feed dedupe of an API page followed by an RSS page.
- Comments of an RSS copy: the numeric id is fetched first.

No dependency, schema, frozen-client or localization changes.
