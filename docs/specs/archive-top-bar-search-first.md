# Archive: the top bar, search first (option A)

The owner chose option A, "Suche zuerst", of the "Archiv Redesign" boards
("Obere Leiste: drei Varianten") for the top of the Archive. The list of
posts and the bottom bar do not change.

Everything here stays on the device, as before: nothing is sent.

## Before

- An app bar with the title and four icons: search everywhere, sort and
  select, show the search field, and ⋮ (new folder, manage folders,
  downloads, offline library, find broken bookmarks, settings).
- A row of chips: the network picker, then All, Unfiled, Likes, Notes and
  the folders, each with an icon, then Media.
- A line of small print saying saves stay on this device.
- The search field only after its icon is tapped.

## After

Three rows, 152 dp in all:

1. **Title row** (52 dp): the title at 28 sp, bold, and beside it a small
   **lock**: tapping it, or a screen reader, gives the line the small print
   used to say (saves, likes or notes stay on this device). **⋮** at the end
   keeps its entries and gains **Search your library**, the search across
   every reader that had its own icon.
2. **Search row** (52 dp): the **search field**, always there, 44 dp tall
   and round, with the magnifier and "Search saved" ("Im Archiv suchen"). A
   clear button appears once something is typed. At its end, after a thin
   divider, **Filter and sort** opens a menu: newest or oldest saved first,
   **Media only**, and **Select**. Sorting and selecting are for saved
   posts; Media only for saved posts and likes; on notes the button is off.
3. **Chip row** (48 dp), scrolling sideways: the **network picker** as a
   filled chip with its mark, "All networks" or the network, and a chevron;
   a thin divider; the **folders** as plain chips — All, Unfiled, Likes,
   Notes, then the reader's folders, in the order chosen in settings — the
   selected one filled with the accent at 18 % and outlined in it, in bold;
   and a dashed **+** that creates a folder. Likes keeps its second tap
   that groups likes by group, with its chevron; a long press on a folder
   still opens its menu.

Chips are 32 dp to the eye and 48 dp to the touch, like every target here.

**Selecting** replaces the three rows with the selection bar (close, the
count, select all, move, delete), as it replaced the app bar before.

When the Archive is shown without its title (embedded), the title row is
left out and the other two stay.

## Strings

- New: "Search saved", "Filter and sort", "Media only", in all 29
  languages.
- The eight Archive strings for sorting and selecting (newest or oldest
  first, the selected count, select all, clear selection, move, the remove
  dialog) were English in every other language; they are translated now.

## Not in this pass

Options B (folders as tabs) and C (a compact picker), and the "after" board's
post list, are not built.

## Code

- `lib/saved/saved_header.dart`: the three rows — title with the lock, the
  search field with Filter and sort, the chips with the network picker,
  folders and +.
- `lib/saved/saved_screen.dart`: the header in place of the app bar, the
  control bar, the small print and the search toggle; a controller for the
  search field.
- `lib/saved/saved_chrome.dart`: ⋮ gains Search your library; the old control
  bar, chip and search field go.
- `lib/saved/saved_source_filter.dart`: the network picker draws as the
  filled chip.

## Tests

- Title, lock with the right line for saves, likes and notes, ⋮ with
  Search your library.
- The field is there without a tap, filters as before, and clears.
- Filter and sort: the sorts, Media only and Select, and which apply where.
- Network chip, folder chips with the selected one marked, + creates a
  folder, Likes groups on a second tap, long press opens a folder's menu.
- Selecting shows the selection bar instead.
- Every target 48 dp; 320 dp at 2× text without overflow.
- Renders in German and English, Lights Out and light, beside the board.
