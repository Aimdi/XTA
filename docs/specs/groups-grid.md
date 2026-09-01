# Groups grid redesign — "Abos / Gruppen" (Option A)

Verified against `claude/xta-repo-setup-shh2mn` (Phase 0 recon). Replaces the
current **dense list** of groups with a **compact 3-column tonal-tile grid** so
the full set of groups is scannable at a glance. Image-free by default.

Parent product constraint: XTA is a **read-oriented** X frontend — no
compose / reply / like-on-X. Groups are local subscription folders only.

## Why (user axioms)

1. A vertical list destroys at-a-glance overview (~handful of rows / screen).
   A 2D grid lets the eye parallel-scan the collection.
2. PFP fetching fails for an unpredictable subset of accounts. Avatar-cluster
   previews therefore render as grey blobs and look broken. Tiles must be
   **complete with zero images loaded**; avatars are omitted from the default
   tile entirely.

## Phase 0 — verified facts (authoritative)

### Paths (actual, not inferred)

| Role | Path |
|---|---|
| Subscriptions home (Groups \| People tabs) | `lib/subscriptions/subscriptions.dart` (`SubscriptionsScreen`) |
| Groups tab body | `lib/subscriptions/_groups.dart` (`SubscriptionGroupsPage`) |
| Current row widget | `lib/subscriptions/_group_list_item.dart` (`GroupListItem`) |
| Already-present hash fallback | `groupFallbackColor()` in `_group_list_item.dart` |
| Feed / group screen | `lib/group/group_screen.dart`, `group_model.dart` |
| Entities | `lib/database/entities.dart` |
| Migrations / tables | `lib/database/repository.dart` |
| Per-feed read cursor | `lib/group/feed_read_position.dart` (`FeedReadPosition`) |

There is **no** `lib/group/_groups.dart` grid. The Groups UI lives under
`lib/subscriptions/`.

### Models / schema

`SubscriptionGroup` fields (verified): `id`, `name`, `icon` (serialized Material
icon), `color` (`Color?`), `numberOfMembers`, `createdAt`, `pinned`, plus
transient `memberAvatarUrls` for the list preview.

Tables: `subscription_group`, `subscription_group_member`, `subscriptions`.
Special group id `'-1'` = "All" (seeded with icon `rss_feed`).

**Already migrated (do NOT re-add):** `pinned BOOLEAN`, `position INTEGER`
(migration ~v34 in `repository.dart`). Pin toggle + drag-reorder in custom sort
already ship on the list UI.

**Not on `subscription_group`:** `last_seen_id`. Unread/caught-up uses the
separate `feed_read_position` table (`FeedReadPosition` / `readFeedReadPosition`).

There is **no** `numberOfColumns` field on the current model.

### Current UI (what we replace)

- `TabBar` + `TabBarView`: Groups | Subscriptions (`l10n.groups` /
  `l10n.subscriptions`).
- Groups: `SearchBar` (when >5 groups) + `ListView` / `ReorderableListView` of
  `GroupListItem`.
- `GroupListItem`: colored `CircleAvatar` (icon or monogram), name, member
  count, **`_AvatarCluster` of member PFPs**, pin button, chevron / drag handle.
- Empty icon fallback path uses `defaultGroupIcon` (`Icons.rss_feed` via
  `group_model.dart`).
- Contrast today: raw fill + `ThemeData.estimateBrightnessForColor` → black/white
  (not a tonal M3 pair).

### Packages already present

`flutter_staggered_grid_view ^0.7.0`, `flutter_material_color_picker`,
`flutter_iconpicker`, `dynamic_color`, `flutter_triple`, `sqflite` +
`sqflite_migration_plan`. Prefer `GridView.builder` +
`SliverGridDelegateWithFixedCrossAxisCount` for Option A; staggered package is
available if variable-height names need masonry later.

### Themes

Default Light / Dark, True Black (`optionThemeTrueBlack`), Fairy Forest,
Pitch Black, plus X-look presets. Tile contrast must work on all of these.

### L10n already present (reuse)

`groups`, `subscriptions`, `search`, `pin`, `unpin`,
`subscription_group_member_count`, `create_subscription_group`,
`no_subscription_groups_yet`, `no_subscription_groups_description`, …

## Decision — Option A (ship)

**Compact 3-column tonal-tile grid** as the Groups tab default.

Density (360dp phone, 8dp padding/gutters): tile ≈109dp wide; at 4:3 ≈82dp tall;
~15–21 groups visible before scroll. Touch target ≥48dp.

Deferred:

- **Option B** (quilted mosaic) — only after explicit demand; needs a rule for
  which tiles enlarge (pinned already exists).
- **Option C** (chip field) — future compact-mode toggle only (48dp risk).

## Tile design (image-free)

```
┌────────────┐
│ 🎬      •  │  glyph top-left (~28–32dp); Badge/dot top-right (optional unread)
│ Anime      │  name max 2 lines, ellipsis
│ 15 Abos    │  count via existing plural ARB
└────────────┘
```

| Element | Rule |
|---|---|
| Fill | `ColorScheme.fromSeed(seedColor: group.color ?? hashed, brightness:).primaryContainer` |
| Foreground | matching `onPrimaryContainer` |
| True Black / Pitch Black | **neutral `surfaceContainer` + left/corner accent** in seed color (not full saturated fill) |
| Glyph | stored Material icon if `icon != defaultGroupIcon`; else 1–2 letter monogram (`characters`) |
| Never | RSS grey empty tile; avatar cluster; raw white-on-saturated text |
| Optional 3rd line | handle preview `"@alice, @bob +14"` only if height allows — text only, no PFPs |
| Pin | small icon badge or long-press / existing swipe — do not reintroduce a large trailing pin that eats the tile |

Tap → existing `routeGroup` / `GroupScreenArguments`. Long-press → existing
`openSubscriptionGroupDialog`. Keep swipe-to-pin / swipe-to-delete or move pin
into the edit sheet if the tile chrome gets crowded.

## Implementation phases

### Phase 1 — Visual redesign (this PR family; no new migration)

1. Extract / expand identity helpers into
   `lib/subscriptions/group_identity.dart` (or `lib/group/group_identity.dart`
   if shared with feed chrome):
   - `hashedSeedColor(String key)` — move/replace `groupFallbackColor`
   - `tonalPair(BuildContext, Color seed)` → `(container, onContainer)`
   - `monogram(String name)` — umlaut-safe grapheme clusters
   - `groupGlyph(SubscriptionGroup, {size})`
   - `bool useAccentTileVariant(BuildContext)` — true for Pitch Black /
     True Black (and optionally X lights-out)
2. Add `lib/subscriptions/_group_tile.dart` → `SubscriptionGroupTile`
   (`InkWell` + tonal/`accent` container, radius 16, min height 48, semantics).
3. In `SubscriptionGroupsPage`, replace `ListView` /
   `ReorderableListView` of `GroupListItem` with
   `GridView.builder`:
   ```dart
   SliverGridDelegateWithFixedCrossAxisCount(
     crossAxisCount: 3,
     mainAxisSpacing: 8,
     crossAxisSpacing: 8,
     childAspectRatio: 4 / 3,
   )
   ```
   Keep client-side `SearchBar` filter. Preserve empty state.
   Custom `position` order still applies via SQL `ORDER BY`; in-grid
   drag-reorder is deferred (no new dependency in Phase 1). Pin remains
   visible as a tile mark; toggle via long-press edit sheet.
4. **Remove** `_AvatarCluster` / `memberAvatarUrls` from the default Groups
   presentation (stop fetching avatars in `GroupsModel.reloadGroups` for this
   screen, or stop attaching them to tiles — prefer stop attaching to avoid
   wasted IO).
5. Optional polish (same phase if small): swap Groups|Subscriptions `TabBar`
   for `SegmentedButton` in the body; keep `TabController` or replace with a
   simple enum in state. Lowest risk: leave `TabBar`, ship the grid first.
6. Unread dot: **optional in Phase 1**. If cheap, batch-read
   `feed_read_position` for visible group ids and show a label-less `Badge`
   when the feed has unseen content; otherwise hide the dot until a follow-up.
   Do **not** add `last_seen_id` on `subscription_group`.
7. ARB only for genuinely new strings (e.g. `groups_search_hint` if SearchBar
   hint should say "Search groups" instead of generic `search`; tile semantics
   if TalkBack needs a dedicated key). Prefer existing
   `subscription_group_member_count`. Run `/translate` +
   `fvm dart run intl_utils:generate`.

### Phase 2 — Already largely done; remaining optional

| Item | Status |
|---|---|
| `pinned` / `position` columns | **Done** |
| Pin toggle / save positions | **Done** (wire into grid chrome) |
| Quilt (Option B) | Not started — gate on user demand |
| Chip compact mode (Option C) | Not started — gate on user demand |
| Per-group unread Badge from `feed_read_position` | Open |

No new `sqflite_migration_plan` entries required for Option A.

## Out of scope

- Changes to `lib/client/` or feed GraphQL.
- Rewriting the group feed (`lib/group/_feed.dart`).
- New dependencies unless Phase 2 quilt/reorderable-grid package is explicitly
  chosen (`ReorderableListView` already covers custom-order on the list; for
  grid reorder prefer evaluating `flutter_reorderable_grid_view` only if
  drag-on-grid is required).

## Testing

- Unit: `hashedSeedColor` stable; `monogram` for `"Über"`, compounds, empty,
  emoji.
- Widget: tile at `textScaler` 1.0 / 1.3 / 2.0 with long DE name — no
  `RenderFlex` overflow; min 48dp tap target.
- Semantics: label includes name + member count (+ unread when present).
- Manual / golden across Default Light/Dark, True Black, Fairy Forest, Pitch
  Black, X-look: tonal vs accent variant as specified.
- `fvm flutter analyze` + `fvm flutter test` green; debug APK builds.

## Acceptance (Phase 1)

- Groups tab shows a 3-column tile grid; ~15+ groups fit before scrolling on a
  typical phone.
- Every tile looks intentional with **no** grey RSS empty state and **no**
  avatar cluster.
- Text on tiles meets contrast via tonal pair (or accent variant on true/pitch
  black).
- Tap / long-press / create / edit / delete / pin / custom reorder still work.
- People (subscriptions) tab behavior unchanged.
