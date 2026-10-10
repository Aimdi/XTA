# Pixiv — profiles and follows

What batch B5a of the PixEz parity plan (`pixiv-pixez-gaps.md`) built: full
creator profiles, follow lists for anyone, and private follows. Bookmarks and
their organisation are B5b.

## Endpoints

All of them live in `PixivSocialApi` (`pixiv_social_api.dart`), read through
`PixivSocialApi.of(context)` so tests pass a fake with
`pumpPixiv(extraProviders: …)`.

| Call | Path | Notes |
|---|---|---|
| `userProfile` | `GET /v1/user/detail?user_id=&filter=for_android` | Whole `user`, `profile`, `profile_publicity` |
| `userWorks` | `GET /v1/user/illusts?user_id=&type=illust\|manga` | The reader's own works keep R-18 and AI |
| `userBookmarks` | `GET /v1/user/bookmarks/illust?user_id=&restrict=&tag=` | Someone else's follow Show R-18 / Hide AI |
| `userFollowing` | `GET /v1/user/following?user_id=&restrict=public\|private` | `user_previews` with preview works |
| `userFollowers` | `GET /v1/user/follower?user_id=&restrict=public` | Always public |
| `followDetail` | `GET /v1/user/follow/detail?user_id=` | `follow_detail.is_followed`, `.restrict` |
| (writes) | `POST /v1/user/follow/add` (`restrict`), `POST /v1/user/follow/delete` | Through `PixivFollowStore` |

## Model

`PixivUserProfile` (`pixiv_user_profile.dart`) wraps the `PixivUser` and adds
the header image, illustration / manga / novel counts, public bookmarks,
website, X and Pawoo links, gender, region, birthday and occupation. A field
whose `profile_publicity` is `private` is left empty even when Pixiv sends it
(`mypixiv` fields are shown, since Pixiv only sends them to allowed readers),
and `pawoo: false` drops the Pawoo link. Links that are not `http(s)` are
dropped. A missing or reshaped payload parses to empty fields, never a throw.
`pixivBirthdayLabel` writes only the public parts in the reader's language.

## Profile screen

`PixivUserScreen` (`pixiv_user_screen.dart`) loads the profile into
`PixivUserStore` and shows:

- **Header** (`pixiv_user_header.dart`): header image (long press saves it),
  avatar (tap saves it), name, @account, a Premium badge, the follow button
  and Add to group, the counts (works shows the Works tab, following opens the
  list) and the bio as plain text until the caption renderer lands.
- **Tabs** from the `pixivProfileTabs` registry (`pixiv_user_tabs.dart`): Works
  (Illustrations / Manga switch when the creator has both, starting on
  whichever there is more of), Bookmarks (public bookmarks), Following (the
  user list) and Info. `PixivUserScreen(initialTab: id)` opens on a tab by id.
  A feature adds its own tab as a `PixivProfileTab` with `offeredFor`, which
  is how the Novels tab is meant to arrive (shown when the creator has novels
  or on the reader's own profile); each tab keeps its list while another is
  shown.
- **Info** (`pixiv_user_info.dart`): a two-column table built by the pure
  `pixivProfileInfoRows`. The user ID copies, Following and Followers open
  the lists, links open the way the reader opens links, and empty or private
  fields are left out.
- **AppBar** (`pixiv_user_menu.dart`): Share link, and a menu from the
  `pixivProfileMenuEntries` list: Follow privately, Copy profile info (name,
  @account and link), Mute author or Unmute, Open on Pixiv. Your own profile
  has no follow button, Follow privately or Mute.
- **Muted creator**: a placeholder with Show anyway (this visit only) and
  Unmute.

Layout: `ExtendedNestedScrollView` with the pinned AppBar and the tab bar, the
same shape as the X profile. The detail menu and the profile menu share
`PixivOverflowMenu` (`pixiv_overflow_menu.dart`).

## Follow lists

`PixivUserListScreen(kind:, userId:)` (`pixiv_user_list_screen.dart`) lists
whom someone follows or who follows them as `PixivUserPreviewCard`s with the
follow button and Add to group, loading the next page as the list nears its
end. A null `userId` is the signed-in reader; their following list has a
Public / Private switch. Creators the reader muted are left out (pages that
only held muted creators are skipped). The list body, `PixivUserList`, is also
the profile's Following tab. `PixivFollowingScreen`, which Home's people icon
opens, is now this list for the reader.

## Following privately

`PixivFollowButton` follows publicly or unfollows on a tap; a long press opens
the follow dialog (`pixiv_follow_dialog.dart`). It loads the follow detail and
offers a Follow privately switch (on for a creator not followed yet, else the
current follow's setting), Unfollow when following, Cancel, and Save or
Follow. The answer goes through `PixivFollowStore.follow(user, restrict:)` /
`unfollow(user)`, so every button for that creator updates. The work detail's
author row (`pixiv_detail_author.dart`) has the same button, beside the name
or under it on a narrow screen or with large text (`PixivFollowHeader`, shared
with the cards and the profile).
