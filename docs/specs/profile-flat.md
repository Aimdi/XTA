# Profile: flat, without pills

The owner's design canvas "QuaX Home – kompakter Header" sets the current X
profile beside three concepts under "Profil ohne Pills". They chose concept 1,
"Flach": the profile keeps its order and everything it offers, but nothing
sits in a pill, a ring or a box any more.

## Before

- Settings and follow were two accent icons in 48 dp rings beside the avatar.
- A bio's paragraphs stood a whole blank line apart.
- In German the join date read "Seit August 2024 bei Twitter" and took its own
  line under the link.
- German counts were neither abbreviated nor grouped: "1379 Folgt".
- The private note sat in a bordered, tinted box.
- Each tab had an icon, the second read "Tweets & Antworten", and the marker
  was as wide as the tab.
- Filters were outlined pills with icons, the selected one tinted and checked.

## After

- **Settings and follow, flat, beside the avatar.** Settings is its icon alone,
  in the text colour. Follow becomes a labelled menu button: a check,
  "Abonniert" and a chevron when subscribed, an eye-off icon in place of the
  check when the account is hidden from the main feed, and a plus with
  "Abonnieren" in the accent colour when not subscribed. It opens the same
  menu as before: Subscribe or Unsubscribe, Add to group, and Show in or Hide
  from the main feed. When the label does not fit beside the avatar, as with
  large text on a narrow phone, the button keeps its icon and chevron and
  drops the label; its tooltip and a screen reader still say it.
- **Avatar** 80 dp with a 3 dp ring in the background colour, half over the
  banner (it was 88 with 4).
- **Bio.** A blank line between paragraphs takes a third of a line, about
  6 dp at normal text size, so a bio of three short paragraphs is barely
  taller than its text. Copying the bio still copies the blank lines.
- **Location, link and join date** wrap as one row. German says "Seit August
  2024"; Mastodon and Bluesky profiles, which share the string, lose their
  "bei Twitter" too.
- **Counts** are grouped wherever the language writes them in full: German
  1.379 and 62.646, Japanese 1,379. Where the language abbreviates, they stay
  as they were: 1.38K, 62.6K.
- **Private note**: a flat row across the whole width between two hairlines:
  the note icon, "Private Notiz hinzufügen" or the note itself, and the
  pencil.
- **Tabs**: names only. The second tab takes the name X gives it, "Antworten"
  ("Replies"), so the four are "Tweets | Antworten | Medien | Archiv". When
  every name fits, the tabs share the width evenly; when one does not, they
  scroll from the start as before. The marker is a 3 dp bar with rounded ends
  under the selected name, as wide as the name, as in Home's bar.
- **Filters**: plain words in one row, with no pill, icon or check. The
  selected one is bold in the text colour, the others medium weight in the
  secondary colour, and a screen reader says which is selected. Every choice
  stays: Posts has All, Posts and Retweets; Media has All, Photos, Videos and
  Broadcasts; Saved has All, Likes and Bookmarks. The row scrolls when the
  words do not fit.
- Touch targets stay 48 dp, Android's minimum, where the canvas drew 44.

What each control does is unchanged, and so are the top bar (back, search,
share, and the banner behind the name once the header has scrolled away), the
banner, the name and its badges, the handle, "Gefolgt von …" and the posts.
Community and Pixiv profiles, which share the tab bar, keep its look.

## Strings

- New in every language: `subscribed` ("Subscribed", "Abonniert") and
  `profile_note_add` ("Add private note", "Private Notiz hinzufügen").
- Changed: German `joined`, from "Seit {date} bei Twitter" to "Seit {date}".
- Reused: `plugin_profile_replies` for the second tab, which plugin profiles
  already call "Replies".

## Not in this pass

- Concept 2, "Kanten", and concept 3, "Kompakt".
- The Pixiv profile in the Kanten style, also on the canvas.

## Tests

- Settings and follow are flat 48 dp targets beside the avatar, with no ring.
  Follow says "Subscribed" with a check when subscribed, "Subscribe" when not,
  and opens the same menu; at 320 dp with text ×2 it drops the label, keeps
  its tooltip, and every header target stays separate.
- A bio's blank line is about a third of a line tall, scaled with the text,
  and the selectable text still holds the blank line.
- Counts: German 1.379 and 62.646, English 1.38K and 62.6K, Esperanto as
  English.
- The note row spans the width between two hairlines and shows "Add private
  note" or the note.
- The tabs are names only, the second "Replies"; they share the width evenly
  when the names fit and scroll when they do not; the selected one has the
  marker. Community's and Pixiv's tab bars are unchanged.
- The filters show every choice as text only, mark the selected one in
  bold and to a screen reader, and select on a tap.
