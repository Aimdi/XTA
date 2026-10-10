# Pixiv — settings, accounts, history, mute and links

What batch B7 of the PixEz parity plan (`pixiv-pixez-gaps.md`) built. The
plugin as a whole is described in `pixiv-plugin.md`.

## Settings page

`PixivSettingsScreen` is a list of section widgets (`pixivSettingsSections`):

| Section | File | Contents |
|---|---|---|
| Account | `pixiv_settings_account.dart` | Stored accounts (switch, remove), Add account, Sign out, refresh-token paste and test |
| Content | `pixiv_settings_content.dart` | Show R-18, Hide AI, and the account's own AI setting read from Pixiv |
| Browsing | `pixiv_settings_browsing.dart` | Start section, viewing history and its pause switch, Copy info template, "Open pixiv links in XTA" (Android) |
| Mute | `pixiv_settings_mute.dart` | Tags (typed, with patterns), artists, works, comments, novels |

## Viewing history

- Opened works are kept **on the device only**: a JSON list in
  `LocalJsonStore` under `pixiv-history:illusts` (the app's reader-state
  folder). Settings backups and exports never read it. Novels get their own
  key (`PixivHistoryStore(key: …)`).
- Each entry is `{id, title, userId, userName, thumbUrl, tags, viewedAt,
  width, height, bookmarks, bookmarked}`; newest first, one entry per work
  (reopening moves it to the top), at most 500. Tags are kept so a muted tag
  still gates a work reopened from history; the size and bookmarks keep the
  tile's shape and count.
- A visit is recorded in `pixivIllustRoute` (`pixiv_link_open.dart`) once the
  work's detail has loaded — not while it waits behind the mute notice, and
  not for a work Pixiv no longer has.
- `plugin.pixiv.history_paused` stops recording. The history screen (More →
  Viewing history, or Settings) is the shared `PixivIllustGrid` (mute filter
  off, long press forgets a work) under a header with the title/artist filter
  and the pause switch, all in one scroll view so large text never squeezes
  the works out. Forgetting a work and Clear history both ask first.
- Uninstalling the plugin clears the history.

## Mute

- **Artists keep their names.** `plugin.pixiv.muted_authors` holds
  `{id, name}` objects; the old list of bare ids still loads.
- **Typed tags and patterns.** A tag can be typed on the mute page. An entry
  written `r'pattern'` is a case-insensitive regular expression tested against
  each tag and against all of a work's tags joined as `#a#b`, so one rule can
  need several tags together. Patterns are stored exactly as written (plain
  names are lower-cased, without the `#` the list shows, so `#cat` mutes
  `cat`); one that does not compile is refused in the field.
  `PixivTagMatcher` compiles the entries once per `PixivMuteState`.
- **Page.** Tags (with the add field), Artists (name · id), Works, Comments,
  Novels. Tapping an entry asks before unmuting; the × unmutes at once; a long
  press copies a tag.
- **Muted-work notice.** `PixivMuteGate`, built into `pixivIllustRoute`, shows
  a work that is muted by id, artist or tag behind a notice naming why, with
  *Show this time* and *Mute settings*. Unmuting there shows the work. A work
  already on screen is never swapped for the notice.

## Accounts

- `plugin.pixiv.accounts` is a JSON list of
  `{userId, name, account, avatar, isPremium, refreshToken}`. It is in
  `secretPrefKeys`, so exports, backups and crash reports never carry it.
- The active account is still the one in the single-account keys
  (`refresh_token`, `user_id`, `is_premium`, …). `PixivClient.switchTo`
  copies another account into them and clears the access token, so the next
  request refreshes one. A token refresh that was in flight for the old
  account is discarded and asked again for the account now in use.
- **A token belongs to the stored user id.** Every path that sets the refresh
  token sets `user_id` with it; a token typed or pasted in Settings clears
  `user_id` until a token check names its owner. A user is only kept in the
  list with the token while `user_id` is theirs, so a check that lands after
  a switch, or a pasted token, never overwrites another account's entry.
- Before switching or adding, the active entry takes the refresh token now in
  use, in case Pixiv rotated it. An account never checked since it signed in
  joins the list by id (its name fills in at the next check); one with no id
  on record is checked first.
- Signing in (PKCE) adds or updates the account; a working token check does
  too, which is how an account signed in before this batch joins the list.
- Sign out asks first, then forgets only the active account; another stored
  account takes over when there is one.
- Switching, adding a different account and signing out first drop what was
  loaded for the last account (`pixivAccountDataForgetter`), so nothing
  reloaded for the new one is dropped after it. The Pixiv screen compares the
  account its lists were loaded for with the one in use when it is built and
  whenever preferences change, so a switch made anywhere (the More pane, the
  plugin's page in Settings, the failure screen) empties and reloads them.
- Confirmations (sign out, remove an account, forget or clear history,
  unmute, mute) share `confirmPixivAction` (`pixiv_confirm.dart`).

## Pixiv account AI setting

`PixivAccountApi.showsAiWorks()` reads `GET /v1/user/ai-show-settings`
(`show_ai`). The content section shows *Shown* or *Partially hidden* with a
*Change on pixiv.net* link. XTA never writes the setting.

## Caption

`PixivHtmlText` renders Pixiv's HTML (caption, and later bios and novel
captions) as selectable text: line breaks, bold and links kept. Pixiv links
(`pixiv://`, paths, pixiv.net) open in XTA through `openPixivLinkRef`, and go
straight to the browser when the work cannot be fetched; `/jump.php?<url>` is
unwrapped; other links go through `openLink`.

## Copy info

The detail menu's *Copy info* fills `plugin.pixiv.copy_template` (empty means
the localised default: title, artist, work ID). Placeholders: `{title}`,
`{illust_id}`, `{user_id}`, `{user_name}`, `{tags}`, replaced in one pass.
The editor (Settings → Copy info template) has insert chips for each and for
the work and artist links, and a reset.

## Links

`parsePixivLink` reads, besides artworks and users:

| Form | Ref |
|---|---|
| `/i/<id>`, `/u/<id>`, `/n/<id>` | artwork, user, novel |
| `member.php?id=`, `member_illust.php?id=` | user |
| `novel/show.php?id=`, `/novel/series/<id>` | novel, novel series |
| `/user/<uid>/series/<id>` | illust series |
| `/tags/<tag>` | tag (opens search) |
| `i.pximg.net/…/img-original|img-master/…/<id>_p0.png` | artwork |
| `pixiv://illusts|users|novels/<id>` | artwork, user, novel |
| `pixivision.net/<lang>/a/<id>` | pixivision article |
| `pixiv.me/<name>` | short link |

A pixiv.me link is resolved with one request that does not follow the
redirect; only a pixiv.net `Location` is trusted, else it opens in the
browser. Series, novels and pixivision articles open in the browser until
their screens exist. `plugin_url.dart` lets pixiv.net, pixiv.me, i.pximg.net
and `pixiv://` links reach the plugin (pixivision.net joins with its screen).

## Opening links from other apps

- The manifest's VIEW filter covers `pixiv.net`, `www.pixiv.net` and
  `pixiv.me`. From Android 12 they only come once the reader adds them under
  *Open by default*; the Browsing section's tile opens that page
  (`APP_OPEN_BY_DEFAULT_SETTINGS`, else the app's info page). Before
  Android 12 Android offers XTA beside the browser in a chooser.
- A Pixiv link that no plugin opened (the plugin is off, the page is one XTA
  has no screen for, or the work did not load) goes to the browser through
  `openUri`; X's link parser never sees it (`readsAsXLink`), since it would
  read `pixiv.net/en/` as the X profile `@en`.
- `openUri` asks Android who would open a Pixiv page; when that is XTA itself
  or a chooser that lists it, the page goes to a named browser instead, so a
  link XTA cannot show never comes straight back.
- Shared text (`shared_links.dart`) yields Pixiv links while the plugin is
  on; a share that is only a number opens Pixiv search with it.

## More hub and navigation

- More is a hub: the account header (avatar, name, @account, Premium badge,
  switcher sheet with Add account), then `pixivMoreEntries` — Viewing
  history, Downloads, Preferences, Mute, Manage account on pixiv.net — About
  and Sign out. A batch adds its line to `pixivMoreEntries`.
- `plugin.pixiv.start_section` (`home`, `ranking`, `favorites`, `search`)
  picks the section the Pixiv screen opens on.
- Tapping the section, Home source, Favorites visibility or ranking mode
  already shown scrolls that list to the top. Embedded in Home, the More list
  scrolls with Home's controller, so tapping More again works there too.

## Preferences added

| Key | Default | Backed up |
|---|---|---|
| `plugin.pixiv.accounts` | `[]` | Never (secret) |
| `plugin.pixiv.start_section` | `home` | Yes |
| `plugin.pixiv.copy_template` | `''` | Yes |
| `plugin.pixiv.history_paused` | `false` | Yes |
