# Compact subscriptions screen

## Problem
On a phone the Abos screen spends 178 dp above the first group: a title row
that says "Abos" (56), the Gruppen | Abos tabs, whose second tab says "Abos"
again (48), and a search field (64). Each row of group tiles then takes 158
dp, so about four rows fit. The owner chose two of the proposed concepts: one
header row, and a compact tile as a choice.

## 1. One header row
- The Gruppen | Abos switch stands where the title stood, labelled as today,
  with search, add and options beside it in the same row: 52 dp
  (`pluginToolbarHeight`), growing with large text. A full-width hairline
  stays under the row.
- When the labelled switch and the actions do not fit beside each other, the
  tab icons go first; when even the labels do not fit (a narrow screen, a long
  translation, large text), the screen falls back to today's two rows. The
  choice is a pure function of the measured widths, so no layout overflows.
- Search moves into the row as an icon. Tapping it turns the row into a
  search field (focused) with a way back; the visible tab's list filters as
  the reader types: groups by name, people by name and handle, as before.
  Back or system back closes the search and clears it. Swiping between tabs
  keeps the query and filters the other list; the hint says which list is
  searched.
- The query lives in a `ReaderValueStore<String?>` the screen owns (null while
  search is closed); both pages read it instead of keeping their own field.
- When the screen is pushed (from the X reader) the Back button stays at the
  start of the row.

## 2. Compact group tiles
- ⚙ → Spalten offers 2, 3 and Kompakt. Kompakt keeps two columns on a phone
  (more on wide windows, as today) and lays each tile out sideways: the
  member mosaic at 40 dp on the left, the name and the count on the right,
  60 dp tall at normal text size and growing with the text scale.
- The tile keeps everything the large tile shows: the chosen colour's border
  and wash, an explicit group mark instead of faces, the NSFW blur, the pin
  and NSFW marks, the unread dot, the AI discovery button (vertically centred
  at the end, with the text kept clear of it), the press feedback and the
  same semantics label.
- Stored as `subscription_groups.compact` (bool, default false); picking 2 or
  3 turns it off. The list layout is untouched.

## Not changing
The list layout, the people list's rows, sorting, reordering, the menu's
other entries, the Home hosting of the screen, X requests and storage.

## Verification
- Pure tests for the header layout choice (fits with icons, labels only, two
  rows).
- Widget tests: one row in English and German at 412 dp; two rows at 320 dp
  with 2x text and no overflow; Back when pushed; search filters groups and
  people and closes on back.
- Compact tile: 60 dp at 1x, no overflow at 2x, text clear of the discovery
  button, semantics unchanged; the menu chip switches the board.
- Analyzer clean; group board, subscriptions and X reader suites pass.
