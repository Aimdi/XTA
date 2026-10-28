# Save to folder: quick save and create from search

The owner's design canvas "Collection Picker Concepts" sets three concepts
for the sheet that saves a post into a collection. They chose concept C,
"Quick save + create from search". In XTA the collections are the folders of
saved posts, and this is the sheet a long press on the bookmark opens.

## Before

- A bookmark icon and "Save to folder...", then the "Remember this folder"
  switch.
- A row for Unfiled and one for every folder, the post's folder with a check.
  A tap filed the post, closed the sheet and said "Saved to Art" in a notice.
- "Create new folder" opened the folder dialog; saving it filed the post
  there.

## After

- **Title and all folders.** "Save to" leads the sheet. "All folders" with a
  chevron sits at the end of the title row while the sheet shows fewer
  folders than there are, and shows them all.
- **Search or create.** A field, "Search or create", narrows the folders to
  the names holding what is typed, under "Matches", or says "No folder found".
  While no folder has exactly the typed name, whatever its case, a row above
  the chips reads "Create "Japan" and save": a tap creates the folder, files
  the post in it and empties the field.
- **Folders are chips.** Without a search the sheet shows up to six folders
  under "Recently used": those of the latest saved posts first, then the rest
  in their own order. The order is fixed while the sheet is open, so a chip
  never moves from under a finger; a folder created in the sheet goes first.
  Each chip is a pill with a round folder mark and the name, outlined in the
  accent with a check while the post is in that folder.
- **A tap files at once.** A tap files the post in that folder, saving it
  first if it was not saved, and the check moves there. A tap on the checked
  chip takes the post out of the folder; it stays saved, without one. The
  sheet stays open so the change shows; a tap outside it, a swipe down or Back
  closes it. What a folder does on filing, downloading photos or sending them
  to Immich, is unchanged.
- **A status line** at the foot says where the post is: "Saved to Art",
  "Saved without a folder" or "Not saved yet". A screen reader reads it out as
  it changes. It takes the place of the "Saved to Art" notice.
- **Remember this folder** stays, as a switch after the chips, and remembers
  each folder picked in the sheet as before.
- The sheet rises with the keyboard, so the field and the create row stay in
  view while typing.

Targets are 48 dp where the canvas drew 44. The canvas's thumbnails become a
folder mark, since folders have no cover.

The canvas lets an image sit in several collections at once. A saved post in
XTA is in one folder, and changing that needs a database change, which the
project's rules keep out of a redesign; so the chips act as one choice. A
created folder starts without automatic download or Immich upload; its
editor under Saved still sets them.

Unchanged: a plain tap on the bookmark, the folder dialog, the Saved screen
and the folder editor.

## Strings

New, in every language: `save_sheet_title` "Save to", `save_sheet_all_folders`
"All folders", `save_sheet_no_match` "No folder found", `save_sheet_create`
"Create “{name}” and save", `save_sheet_not_saved` "Not saved yet",
`save_sheet_saved_unfiled` "Saved without a folder". Shared with the group
picker: `picker_search_or_create` "Search or create", `picker_recently_used`
"Recently used", `picker_matches` "Matches".

Reused: `saved_to_folder`, `save_folder_remember`,
`save_folder_remember_description`.

## Code

- `lib/saved/save_sheet_model.dart`: the order the folders open in.
- `lib/saved/save_sheet.dart`: the sheet. `showSaveToFolderSheet` keeps its
  signature, so every caller opens the new sheet.
- The search, the order, the create rule and the sheet's parts are shared
  with the group picker: `lib/utils/quick_pick.dart` and
  `lib/ui/quick_pick_sheet.dart` (see `group-sheet-quick-pick.md`).

## Tests

- The order: the latest saved posts' folders first, each once, the rest
  after, six at most; created folders lead.
- Matches ignore case; the typed name is taken whatever its case.
- A chip files the post, saving it first when needed; the checked chip unfiles
  it; the status line follows.
- Create files the post in a new folder of that name and empties the field;
  no create row for a name that exists.
- All folders shows every folder; Remember this folder still remembers a pick.
