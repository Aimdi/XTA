# Choosing groups: the quick pick

The canvas "Collection Picker Concepts" is about the sheet that asks which
groups an account belongs to, as when following someone and putting them in
a group. The owner chose concept C, "Quick save + create from search". The
same sheet opens from a follow button's Add to group, a profile, a post's
menu, a network's account (Pixiv, Bluesky, RSS and the rest), Discover's
Other groups and the one-by-one sorter's Several groups.

## Before

- "Select" and a count, a search field, and a row for every group with its
  mark and a checkbox, the account's groups first.
- Cancel and Save at the foot; only Save changed anything.

## After

- **Title and all groups.** "Add to group" leads the sheet, with "All groups"
  and a chevron at the end of its row while some groups are not shown. With
  large text on a narrow phone the link goes under the title.
- **Search or create.** "Search or create" narrows the groups to the names
  holding what is typed, under "Matches", or says "No group found". While no
  group has exactly the typed name, whatever its case, a row reads "Create
  "Japan" and add": a tap creates the group with the default mark, ticks it
  for the account and empties the field.
- **Groups are chips** carrying their own marks. Without a search the sheet
  shows the account's groups first, then the groups picked last in this
  sheet, then the rest in the Groups tab's order: six chips, or as many as the
  account has groups. The order is fixed while the sheet is open, and a group
  created in it goes first. The section reads "Recently used", and "All
  groups" once they all show.
- **A tap ticks or unticks.** A ticked chip is outlined in the accent with a
  check. A status line at the foot says where the account is: "In Anime",
  "In Anime and Art (2)", "In 3 groups" or "Not in a group", and a screen
  reader reads it out as it changes.
- **The choice applies on closing**, however the sheet closes: a tap outside
  it, a swipe down or Back. There is no Save or Cancel; closing with the
  groups it opened with changes nothing, so an account is not followed by
  opening the sheet and leaving it. Following first, as Add to group does for
  an account not yet followed, happens as before once the groups changed.
- **Recently used** remembers the groups added in the sheet, newest first,
  twelve at most, on this device.

Targets are 48 dp where the canvas drew 44. The chips' thumbnails become the
groups' own marks.

Unchanged: who calls the sheet and what they do with the answer, following
first, the global undo after saving, the group editor.

## Shared with the folder sheet

The save-to-folder sheet built from the same canvas uses the same pieces:
the title row, the section label, the create row, the chip and the status
line (`lib/ui/quick_pick_sheet.dart`), and the search, ordering and create
rules (`lib/utils/quick_pick.dart`). Its strings for the search, "Recently
used" and "Matches" become shared ones.

## Strings

New, in every language: `group_sheet_all_groups` "All groups",
`group_sheet_no_match` "No group found", `group_sheet_create`
"Create “{name}” and add", `group_sheet_in_none` "Not in a group",
`group_sheet_in_one` "In {name}", `group_sheet_in_two`
"In {first} and {second}", `group_sheet_in_many` "In {count} groups"
(plural).

Renamed to be shared: `save_sheet_search_hint` → `picker_search_or_create`,
`save_sheet_recent` → `picker_recently_used`, `save_sheet_matches` →
`picker_matches`.

Reused: `add_to_group`.

## Tests

- The order: the account's groups, then recent picks, then the rest, each
  once; as many chips as the account has groups when that is more than six.
- Recent picks: added groups lead, twelve at most, gone groups skipped.
- Ticking and unticking change the status line; closing returns the choice,
  and nothing when it is the one the sheet opened with.
- Create ticks a new group and empties the field; a taken name offers no row.
- All groups shows the rest.
- Add to group for an account not yet followed follows it and saves its
  groups once the sheet closes with a change, and does neither when it
  closes unchanged.
- The sorter's Several groups places the account in the groups ticked.
