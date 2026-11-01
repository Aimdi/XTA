# Profile: filters as icons

The owner's design "Profil-Filter als Icons" (boards "Profil – Filter als
Icons" and "Icon-Zuordnung & Zustände") replaces the words of the profile's
filter rows with icons. It builds on `profile-flat.md`, whose tabs, filter
row position and behaviour stay.

## Before

Each tab with a filter has a row of words directly above its content: Posts
(All, Tweets, Retweets), Media (All, Photos, Videos, Broadcasts) and Archive
(All, Likes, Bookmarks). The selected word is bold and in the text colour.

## After

- Every choice is an **icon button**, 48 dp tall and at least 52 dp wide,
  six apart, in a row with 8 dp above and below:
  - All: layers; Tweets: a speech bubble; Retweets: the repost arrows.
  - Photos: a picture; Videos: a camera; Broadcasts: radiating waves.
  - Likes: a heart; Bookmarks: a bookmark.
- **The selected choice** sits on a quiet pill (the theme's floating
  surface: ten percent above black in Lights Out) with its icon in the text
  colour; the others have no fill and the icon in the secondary colour. The
  fill as well as the colour marks it, so colour is never the only sign.
- **Names stay**: long-pressing or hovering a button shows its name as a
  tooltip, and a screen reader hears the name, that it is a button, and
  whether it is selected.
- The tabs above, the content below and what each choice filters do not
  change.

## Not in this pass

The design's other active styles (accent tint, underline) and the label
beside the active icon are options it shows, not its default; they are not
built.

## Code

- `lib/profile/profile_chrome.dart`: `ProfileFilterOption` gains its icon;
  `ProfileFilterBar` draws icon pills.
- `lib/profile/profile.dart`: the icon for each choice.
- `lib/ui/x_look_theme.dart`: `xLookFloatingSurfaceOf`, the floating surface
  of the current theme, shared with the reorder lift.

## Tests

- Every choice is a 48 dp button with its name as tooltip and semantics, the
  selected one marked selected and on the pill, the others not.
- Tapping a choice selects it.
- Renders of the profile in German and English at 390 dp and German at
  320 dp ×2, and of the Media and Saved rows in English.
