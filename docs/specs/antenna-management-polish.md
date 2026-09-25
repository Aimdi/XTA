# Antenna management polish

Improve the local Antennas management flow without changing search queries,
feed behavior, storage, routes, or XTA's read-oriented product boundary.

## Opportunity and priority

Discover now links directly to Antennas, but the first management experience is
still difficult to complete:

1. The empty state explains that there are no antennas but offers no action.
2. The editor is a fixed-height column inside a keyboard-aware bottom sheet, so
   a small window or 200% text can hide controls or overflow.
3. The two-option segmented scope control cannot adapt when its translated
   labels no longer fit.
4. The model supports deleting an antenna, but the management screen does not.
5. Antenna rows stretch edge to edge on tablets and expose an unlabeled edit
   icon rather than named management actions.

This ranks above broader visual restyling because it completes an existing,
Discover-linked feature with low implementation risk and no live-service,
database, or client changes.

## Assumptions and low-cost checks

| Assumption | Experiment | Success threshold |
|---|---|---|
| First-time users need a direct creation path | Render the empty state and activate its primary action | The New antenna action is visible, semantic, and invokes the editor callback |
| The editor must remain usable with keyboard and large text | Render at 375dp, 200% text, reduced motion, and a simulated keyboard inset | The form scrolls, scope choices stack, motion is disabled, and no layout exception occurs |
| A segmented scope selector is useful when space permits | Render at 720dp and normal text | The compact segmented selector remains available |
| Antennas must be manageable from their list | Open the row menu and invoke Settings/Delete | Both named actions are reachable and call the expected callback |
| Wide screens need a readable measure | Render the management body at tablet width | Content stays centered within the existing 720dp settings measure |

## Implementation

- Add presentation-only Antenna widgets for the list tile, empty state, editor
  form, and centered list body.
- Keep the existing `AntennaModel` Store as the feature state owner.
- Use `LayoutBuilder` and actual parent constraints. Show the segmented scope
  selector only when it fits; otherwise use vertically stacked radio choices.
- Make the editor scrollable above `MediaQuery.viewInsets`, cap its readable
  width, keep labeled fields, and preserve logical focus order.
- Replace the unlabeled edit icon with a standard overflow menu containing the
  existing localized Settings and Delete actions.
- Confirm deletion before calling the model's existing `deleteAntenna`.
- Give the empty state a localized New antenna primary action.
- Keep list construction lazy and constrain the list to the feature's 720dp
  content-width token.
- Add isolated light, dark, and 200% text Widget Previewer configurations that
  do not instantiate storage, native APIs, or live services.

## Tests

- The empty state exposes and activates New antenna.
- Antenna row Settings and Delete callbacks remain independently reachable.
- At 375dp, 200% text, reduced motion, and with a keyboard inset, the editor
  uses its stacked scope layout and reports no overflow.
- At wide width and normal text, the editor retains the segmented selector.
- The management list remains lazy and width-bounded.

## Boundaries

- No changes to `lib/client/`, `lib/database/`, query construction, feed
  loading, navigation contracts, dependencies, SDK pins, or localization keys.
- No remote writes or telemetry.
- No performance claim without a representative physical Android device.
- Device follow-up should cover TalkBack focus order, keyboard traversal,
  landscape, and destructive-action confirmation.
