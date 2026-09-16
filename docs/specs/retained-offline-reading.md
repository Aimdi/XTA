# Retained offline reading

Base: aimdi128 (fb5eb74309390c211e3885096006bf076f6c25cb).

Implement explicit retained offline copies for readable RSS/Substack articles and
saved post media, accessible from Saved and settings. Keep article text plus
successfully fetched images; report partial image availability accurately. Store
files under application support (not the temporary cache), atomically persist a
versioned manifest, bound individual transfers and total storage, and provide
remove/clear controls with current bytes. Failed and interrupted operations must
not be labeled available. Original links remain available. Existing Saved records,
folders and database schema are unchanged. Article readers use shared offline
adapters to hydrate retained bodies and render local image bytes after sanitizing.

Use flutter_triple Store for observable state, injected storage/HTTP in tests,
existing icons and translated labels. Never fetch hidden sensitive media merely
because a card renders; retention is an explicit user action. No SDK/dependency
changes. Main integrates isolated article/Bluesky/download/playback module commits
and checks cold restart, interrupted writes, bounded downloads and removal, plus
production widget layouts in light/dark/large text.
