# Sort ungrouped: one by one

Subscriptions → menu → Sort ungrouped places accounts that are in no group by
the words they share with a group: its name and its members' names. The owner
found accounts it leaves behind and chose, among three ways to sort them, to
go through them one at a time.

## Before

- An account whose handle and name share no word with any group, such as
  `@mk_t0927`, stays under "Left ungrouped": a line of handles with nothing to
  do with them.
- When no account matched at all, the screen said "No results" above that
  line, with no action.
- Apply took every guess, then closed the screen; the accounts left over were
  still ungrouped.

## After

- **Left ungrouped leads somewhere.** Under its handles, "Sort one by one"
  goes through them. When nothing matched by name, it is the screen's main
  button, at the foot where Apply sits otherwise. After Apply, the screen goes
  on to the accounts it could not place, if there are any, instead of closing.
- **One account at a time.** A card shows the avatar, the name, the handle and
  Open profile, to see who the account is; coming back from the profile shows
  the same card. An X account's picture sits on a disc that shows while it
  loads or when there is none. The bar says how far along the sorting is:
  "3 of 41".
- **A tap places and moves on.** The groups are chips carrying their own marks,
  in the order of the Groups tab. A tap puts the account in that group, saved
  at once, and shows the next account.
  - "Several groups" opens the group picker, with its search, for more than
    one group or for one among many. Saving it places the account in the
    groups ticked; saving none ticked, or closing it, changes nothing.
  - "Create group" asks for a name, creates the group with the default mark
    and puts the account in it. The new group joins the chips for the
    accounts after it.
- **Skip and Undo.** Skip moves on and leaves the account where it is. Undo
  takes the last choice back and shows that account again, as often as there
  were choices. It takes the account out of the groups that choice put it in;
  a group created on the way stays, so it can take the next account.
- **The end** says how many were placed and how many skipped, with Finish.
  When some were skipped, "Sort the skipped ones" goes through those again.
  Undo still takes the last choice back from there.
- **Leaving keeps what was placed.** Every choice is saved as it is made.
  Coming back to the plan from the sorter, Sort ungrouped plans again from
  what is left.
- If saving fails, the card stays and a notice says so.
- No "Changes saved · Undo" notice after each choice: the sorter's own Undo
  does that work.

Placing an account gives the selection click when Haptic feedback is on. The
next card fades in and slides a little from the end of the reading direction,
and from the other side after Undo; with reduced motion it simply appears. A
screen reader reads the next account's name as its card arrives. Every target
is at least 48 dp.

Unchanged: how Sort ungrouped guesses, Sort with AI, Apply taking the whole
plan, the group picker and the group editor.

## Strings

New: `sort_one_by_one` "Sort one by one", `sort_one_progress`
"{current} of {total}", `sort_one_several_groups` "Several groups",
`sort_one_skipped` "Skipped {count}" (plural), `sort_one_sort_skipped`
"Sort the skipped ones", in every language.

Reused: `sort_ungrouped`, `skip`, `reader_undo`, `group_discovery_open_profile`,
`create_subscription_group`, `name`, `create`, `cancel`, `finish`,
`sort_ungrouped_applied`, `sort_ungrouped_empty`, `sort_ungrouped_failed`.

## Code

- `lib/subscriptions/group_ungrouped_sorter.dart`: the sorting state (the
  accounts, where the sorter stands, the choices made) as pure transitions,
  and a Store that saves each choice through injected writers.
- `lib/subscriptions/group_ungrouped_sorter_screen.dart`: the card, the
  chips and the end.
- `GroupsModel` gains `addToGroups`, `removeFromGroups` and `createGroup`,
  which write without offering the app-wide undo. The new-group row is
  written in one place for `saveGroup`, `applyUngroupedPlan` and
  `createGroup`.
- Opening a subscription's screen, as a row of the subscriptions list does,
  becomes `openSubscription`, for the list and the card. `subscriptionAvatar`
  takes a size for an X account too, for the card's 72 dp picture; without
  one it draws as before.
- Undo and Skip share the footer while they fit, and stack with large text on
  a narrow phone.

## Tests

- The state: placing and skipping move on; Undo goes back and forgets the
  choice; the skipped round holds only the skipped accounts.
- The Store: only placements write; Undo removes exactly what its choice
  added; a failed write keeps the card; a second tap while saving is ignored.
- `GroupsModel`: adding keeps other memberships, removing takes only the given
  ones, and a created group comes back with its id and no undo offered.
- The screen: a chip places the account and shows the next with the progress;
  Skip, Undo, Several groups and Create group; the end's counts and the
  skipped round; Open profile.
- The plan: Left ungrouped leads to the sorter; with no match the main button
  sorts one by one; Apply goes on to the accounts left over.
