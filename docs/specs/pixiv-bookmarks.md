# Pixiv — bookmarks and bookmark organisation

What batch B5b of the PixEz parity plan (`pixiv-pixez-gaps.md`) built: one
write path for bookmarks with the reader's defaults, the bookmark editor with
tags and visibility, the tag picker and tag filter for Favorites, the
after-bookmark and after-save automations, and haptic feedback. Profiles and
follows are B5a (`pixiv-profiles.md`).

## Endpoints

`PixivBookmarkApi` (`pixiv_bookmark_api.dart`) is the only place a bookmark
is written. Screens read it through `PixivBookmarkApi.of(context)`, so tests
pass `FakePixivBookmarkApi` (`test/support/pixiv_bookmark_fakes.dart`) with
`pumpPixiv(extraProviders: [api.provider])`.

| Call | Path | Notes |
|---|---|---|
| `detail` | `GET /v2/illust/bookmark/detail?illust_id=` | `bookmark_detail.is_bookmarked`, `.restrict`, `.tags[name, is_registered]` |
| `add` | `POST /v2/illust/bookmark/add` | `illust_id`, `restrict`, and every tag space-joined in one `tags[]` field, which is how Pixiv's form takes a list; no tags, no field |
| `delete` | `POST /v1/illust/bookmark/delete` | `illust_id` |
| `tags` | `GET /v1/user/bookmark-tags/illust?user_id=&restrict=` | `bookmark_tags[name, count]`, paged by `next_url` |

Favorites still loads through `PixivClient.bookmarks`, which now takes `tag`
(`未分類`, `pixivUnclassifiedTag`, for untagged bookmarks). B5a's
`PixivSocialApi.userBookmarks` reads the same endpoint for profiles; once both
are in, Favorites can move onto it and `PixivClient.bookmarks` can go.

Every parser is null-safe through `Json`: a missing or reshaped
`bookmark_detail` reads as a public work not bookmarked, and a reshaped tag
list as an empty last page. Pixiv tags hold no spaces, so a typed `blue sky`
becomes the two tags Pixiv would make of it (`pixivTagsFromInput`).

## One write path

`PixivBookmarkActions` (`pixiv_bookmark_actions.dart`) is what the heart, the
editor and bookmark-after-save call:

- `bookmark(illust, {restrict, tags})` adds or re-files. Without a `restrict`
  it uses the default visibility; without `tags` it sends the auto-tags. A
  bookmark that is new may then save the work and follow its author.
- `unbookmark`, `toggle` (the heart) and `ensureBookmarked` (after a save).
- `PixivBookmarkStore` keeps the session's overrides and which works have a
  write on its way; `exclusive` runs one write per work at a time, so a second
  tap while the first is in flight does nothing.
- `PixivBookmarkFeedback` is captured before the write and reports after it:
  a light haptic, `Followed <name>` when the author was followed, or the
  error in a snack bar.

`pixivAutoBookmarkTags` is the work's own tag names without Pixiv's popularity
tags (`\d+users入り`, which also catches `東方100users入り`).

## Settings

A Bookmarking section (`pixiv_settings_bookmarks.dart`) in the Pixiv settings
page; all are off by default except haptics, and all are reset with the plugin:

| Switch | Preference | Effect |
|---|---|---|
| Bookmark privately | `plugin.pixiv.default_private_bookmark` | Bookmarks made without the editor are private |
| Tag bookmarks automatically | `plugin.pixiv.auto_tag_bookmarks` | Bookmarks made without the editor carry the work's tags; the editor pre-checks them on a work not bookmarked yet |
| Follow when bookmarking | `plugin.pixiv.follow_after_bookmark` | A new bookmark follows an author the reader does not follow yet, publicly (never the reader themself); a failed follow leaves the bookmark |
| Save when bookmarking | `plugin.pixiv.download_after_bookmark` | A new bookmark saves every page through `downloadAllPixivPages` |
| Bookmark when saving | `plugin.pixiv.bookmark_after_download` | A page or all-pages save from the work's page (detail or reader) that saved something bookmarks a work not bookmarked yet, with the default visibility, auto-tags and follow-after, and never saves again |
| Haptic feedback | `plugin.pixiv.haptics` | Light on a bookmark, medium when a long press opens the bookmark editor or a page's actions |

None of these are secrets; they travel with settings backups like the other
Pixiv switches.

## Heart and editor

`PixivBookmarkButton` (tiles and the detail AppBar) taps through
`togglePixivBookmark` and opens the editor on a long press; its hit area is
48 dp with a Semantics label and long-press hint, and the tile heart keeps its
scrim for true-black images. The detail overflow menu's former Bookmark folder
item is now Edit bookmark (entry `bookmark`, its own `_editBookmark` function).

`showPixivBookmarkEditor` (`pixiv_bookmark_editor.dart`) opens a sheet over
`PixivBookmarkEditorStore` (`pixiv_bookmark_editor_store.dart`). It loads the
bookmark detail (a failure offers Retry) into a `PixivBookmarkDraft` and shows:
a Private switch, Select all / Clear all, an add-tag field (typed tags go on
top, checked) that suggests up to eight of the reader's own tags as they type,
the checklist, Remove when the work is bookmarked, Cancel and Save. Save
re-posts the add with the visibility and the checked tags, which is also how
an existing bookmark is edited; a failed save keeps the sheet open with the
reason. The draft's transforms are pure and unit-tested. Loads still on their
way when the sheet closes land before the store is destroyed
(`PixivInFlight`, `pixiv_in_flight.dart`).

## Favorites tag filter

Favorites shows a tag chip beside Public and Private: All, Uncategorized or
the tag. It opens `showPixivBookmarkTagPicker` (`pixiv_bookmark_tag_picker.dart`):
Public and Private tabs of the reader's tags with counts, each loading lazily
and paging as it scrolls, with All and Uncategorized on top. Typing shows up
to eight case-insensitive matches and a `Use “…”` entry for any typed tag. The
picker returns `(restrict, tag)`; the chip's clear button goes back to All, and
switching Public / Private starts on All. The choice is session state in
`PixivViewState.bookmarksRestrict` / `bookmarkTag`, and the loader sends it.

## Haptics

`PixivHaptics` (`pixiv_haptics.dart`) plays `HapticFeedback` light or medium
when `plugin.pixiv.haptics` is on, at most once every 100 ms (`pixivHapticGap`).
Android also drops it when the system's touch feedback is off. Other batches
call `playPixivHaptic(context, kind)` for their own actions.

## For the batches that follow

- B6b's tile long-press save and ugoira export call
  `bookmarkPixivAfterSave(context, illust)` once a save lands, and its tile
  bookmark action calls `togglePixivBookmark` (the store no longer has a
  `toggle`).
- `PixivDownloader.savePage` and `downloadAllPixivPages` now answer whether
  something was saved; `downloadUriToPickedFile` and `downloadPluginMediaItem`
  likewise.

## Tests

`pixiv_bookmark_api_test.dart` (MockClient payloads), `pixiv_bookmark_actions_test.dart`
(default visibility, auto-tag, follow-after, save-after, bookmark-after-save),
`pixiv_bookmark_store_test.dart`, `pixiv_bookmark_editor_test.dart` (draft and
sheet), `pixiv_bookmark_tag_picker_test.dart` (picker, chip, Favorites
loader), `pixiv_bookmark_after_save_test.dart` (detail saves, settings) and
`pixiv_haptics_test.dart`.
