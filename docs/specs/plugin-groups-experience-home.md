# Home sources and selection controls

Base: Aimdi 129, `e6551526750bdef1070c3f4cbe8c214344ca8b70`.

## Requested behavior

- Home keeps Following as the combined local-subscription timeline. X becomes
  a dedicated, always-available source beside the optional plugins. For you
  lives inside X, with its existing account selection and explicit refresh.
- Restore the legacy `foryou` preference as X so existing readers keep their
  chosen destination. No account or database migration is required.
- The avatar drawer uses a stable account header, separate navigation actions,
  searchable groups, pinned groups first, recognizable group marks and counts.
- The account/group filter becomes a bounded sheet with named sections, search,
  visible inclusion state and a fixed Done action. Preserve the last-account
  guard and existing filtering semantics.

## Constraints and validation

Keep API clients, database schema, pinned dependencies, icons and local-only
behavior. Feature state uses flutter_triple Stores; all copy uses L10n.
Verify source migration, group ordering/search, filter toggles and layout at
narrow widths and large text. Run the pinned GitHub formatter, analyzer, full
tests and a debug APK build before marking the implementation validated.
