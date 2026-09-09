# Pixiv continuous reading and X broadcast browsing

Base: XTA Aimdi129 (`e6551526`). Scope: Pixiv illustration viewing and the
broadcast-only media view on X profiles; keep existing clients and storage.

## Pixiv

- Offer a visible down-arrow “Read vertically” action below multipage artwork.
- Open a dedicated reader with lazy, uncropped full-width pages in source order,
  suitable for manga/manhwa collections with dozens of images.
- Keep reading progress visible; provide a page picker to jump directly and a
  horizontal/vertical mode switch that retains the selected page.
- Decode images at screen width, retain Pixiv request headers, and expose loading
  and retry states. Respect reduced motion and system insets.
- Use a flutter_triple Store for all new reader state and existing dependencies.

## X profile livestreams

- In the broadcasts-only filter, replace narrow masonry tiles with aligned
  episode cards: a consistent 16:9 preview, title/post excerpt, account, date,
  broadcast/Space kind, and an explicit open-post action.
- Keep broadcast playback routing, content sensitivity, manual-media-loading
  settings, paging, retry, and refresh behavior intact.
- Use the existing localized labels where possible. Translate every new reader
  label in all supported ARB locales and run the repository localization script.

## Verification

Exercise reader page bounds/mode retention and long-collection lazy rendering.
Check broadcast-card structure and routing against existing media tests. Run
format, localization generation, focused Flutter tests and analyzer if the pinned
SDK is available. No changes to lib/client, lib/database, or pinned dependencies.
