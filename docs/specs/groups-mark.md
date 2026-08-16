# Groups mark — Iteration 3 (color-dominant + GroupMark)

Verified Phase 0 recon on `claude/xta-repo-setup-shh2mn` (post aimdi42
grid). Replaces Iteration 2's full tonal fill + large glyph/monogram with a
**color-tinted cell** and a single **tonal chip** whose contents resolve through
one `GroupMark` path.

Parent product constraint: XTA is a **read-oriented** X frontend — no
compose / reply / like-on-X. Groups are local subscription folders only.

## Phase 0 — verified facts (authoritative)

| Item | Confirmed |
|---|---|
| Package import root | `package:xta/…` (not squawker/fritter) |
| Groups tab body | `lib/subscriptions/_groups.dart` |
| Current tile | `lib/subscriptions/_group_tile.dart` |
| Identity helpers | `lib/subscriptions/group_identity.dart` |
| Edit sheet | `lib/subscriptions/_groups_edit.dart` |
| Model / store | `lib/group/group_model.dart` (`GroupsModel`, `defaultGroupIcon`) |
| Entity | `lib/database/entities.dart` → `SubscriptionGroup` |
| Table | `subscription_group` / `subscription_group_member` |
| "All" group id | `'-1'` (excluded from Groups tab query) |
| `icon` type | **`String`** — `flutter_iconpicker` JSON (`{"pack":…,"key":…}`), **not** an int codepoint |
| `defaultGroupIcon` | `'{"pack":"custom","key":"rss_feed"}'` |
| `color` type | `Color?` / DB `INT` ARGB nullable |
| `numberOfColumns` | **does not exist** |
| `emoji` / `mark_style` | **do not exist** yet |
| Deps present | `flutter_iconpicker`, `dynamic_color`, `flutter_material_color_picker` |
| Deps absent | `material_symbols_icons`, `flex_seed_scheme`, `emoji_picker_flutter`, `flutter_boring_avatars` |
| Themes | Default Light/Dark, True Black, Fairy Forest, Pitch Black, X-look presets |

### Why Iteration 2 still fails

Three identity systems on one screen (outline Material icons, 2-letter
monograms, stray symbols), painted large on a saturated fill (or a 4dp accent
bar on black themes). Color — the most reliable identity — was demoted.
Monograms collide (`Art (2)` / `Art NSFW` → `AR` / `AR`).

### Open-question defaults (this spec)

1. **Keep stored icons in the DB**; Phase 1 **does not render them** on the
   tile (Concept C base). Phase 2 remaps via `mark_style`.
2. **Emoji blank until set** → initial fallback (no auto-assign).
3. **System emoji only** when Phase 2 lands — do not bundle Noto Color Emoji.

## Decision — Phase 1 (ship now, no migration)

**Concept C base:** tinted `surfaceContainerHigh` cell + 40dp tonal chip with a
**single dominant initial**. Name/count use `onSurface` / `onSurfaceVariant`.

Resolver (Phase 1 subset of the full ladder):

1. ~~emoji~~ (no column yet)
2. **single initial** of the name (always, for Phase 1)
3. ~~stored Material icon~~ (deferred to Phase 2 `mark_style=2`)
4. ~~generated beam~~ (rejected as primary; not needed while names exist)

Cell chrome:

- Fill: `Color.alphaBlend(seed.withValues(alpha: 0.12), surfaceContainerHigh)`
- Radius 16; padding 10
- Pitch Black / True Black / X lights-out: **1dp** `outlineVariant` border
  (no 4dp accent bar)
- Chip: 40×40, radius 12, `primaryContainer` / `onPrimaryContainer` from
  `ColorScheme.fromSeed(seedColor: seed, brightness:)`
- Initial: 20sp `w700`, first *letter* grapheme (`\p{L}`), else first grapheme,
  else `?`
- Name: `titleSmall` `w600` `onSurface`, 2-line ellipsis
- Count: `bodySmall` `onSurfaceVariant`
- Pin: small trailing icon in the chip row (`onSurfaceVariant`)
- Semantics: one `Semantics(button, label: '$name, $count')`; chip
  `ExcludeSemantics`

Do **not** add `material_symbols_icons`, `flex_seed_scheme`, or generative
avatar packages in Phase 1. Keep `flutter_iconpicker` in the edit sheet.

## Phase 2 — additive migration (shipped, DB v35)

- `emoji TEXT` + `mark_style INTEGER NOT NULL DEFAULT 0`
  (`0` auto, `1` emoji, `2` symbol, `3` generated → initial fallback)
- Backfill existing non-default icons → `mark_style=2`
- New groups → `mark_style=0`
- `GroupMark` resolver: style-aware emoji / initial / symbol

## Phase 3 — edit sheet (shipped)

- Segmented control: Auto / Emoji / Icon (`group_mark_style_*` ARB)
- Emoji via system-keyboard dialog (no Noto bundle, no emoji_picker package)
- Icon via curated ~40 Material set (`serializeCuratedGroupIcon`); unrestricted
  `showIconPicker` removed from the sheet
- `flutter_iconpicker` kept only for `deserializeIconData` of legacy JSON

## Phase 4 — L10n (shipped)

Keys: `group_mark_style_label`, `group_mark_style_auto`,
`group_mark_style_emoji`, `group_mark_style_icon`, `choose_emoji`,
`choose_icon`. Removed unused `pick_an_icon` / `no_results_for`.

## Still deferred

- Optional Concept A ("Minimal", no chip) style toggle
- `material_symbols_icons` / FILLED axes
- Dropping `flutter_iconpicker` entirely (needs a local deserializer)

## Phase 5 — unread dots (shipped)

A small accent dot on the board tile, list row, and drawer shortcut when
`groupHasUnread` is true: reading position (or per-group catch-up) is on,
the group has cached X chunks, and the newest `feed_group_chunk.created_at`
is after `feed_read_position.updated_at` (or there is no position yet).
Popular feeds do not track. Hashes come from `feed_chunk_hash.dart` so they
cannot drift from `SubscriptionGroupFeedChunk.hash`. No new migration.

## Out of scope

- Generative marks as primary identity
- Bundling Noto Color Emoji
- Rewriting `lib/client/` or posting affordances
- In-grid drag-reorder

## Acceptance (Phase 1)

- `Art (1)` / `Art (2)` / `Art NSFW` all show **A** (disambiguated by color)
- `German & EU` → **G** (not `€` / `GE`)
- `Über` → **Ü**
- Empty name → `?`
- No white text on raw saturated fill; no large outline glyph on the cell
- Pitch Black cells show a hairline outline
- `fvm flutter test` green; no new ARB keys required for Phase 1
