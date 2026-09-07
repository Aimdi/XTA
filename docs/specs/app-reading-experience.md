# App reading experience

Base: XTA Aimdi 127 / claude/main a0b3d436, followed by the Home timeline
and Mastodon client drafts. This work is stacked on the Mastodon draft.

The accepted scope contains six improvements, implemented incrementally:

1. Mastodon profile media becomes a responsive thumbnail grid. The profile
   identity and local Follow / group actions remain accessible. Conversations
   distinguish the selected post, ancestors and reply branches; branches can
   collapse without losing context. Content warnings apply to thumbnails too.
2. Installed plugins gain an explicit Open action and retain a separate gear
   for configuration. Use the existing client launcher and session ownership.
3. Mastodon joins the existing device-local Saved archive: folders, text search,
   source filtering and opening the conversation. Reuse the existing schema;
   no network write actions or database-module changes.
4. Mastodon restores its selected timeline and reading content after a restart.
   Persist bounded snapshots and positions keyed by surface and server. Restore
   before fetching; explicit refresh replaces content. Never move a reader's
   visible post merely because newer content exists.
5. Discover gains recent queries and useful Mastodon account/topic entry points,
   with inline Mastodon results and consistent submit behavior.
6. Settings search indexes individual controls and opens the right section at
   that control. Preserve translated labels and existing preference storage.

Mastodon implementation boundary: lib/plugins/mastodon and small shared seams.
New feature state uses flutter_triple Store. Keep the existing icons, theme,
large-text / RTL support and reduced-motion behavior. Embedded feed chrome
continues to reappear only at the top. Pinned dependencies, lib/client and
lib/database are unchanged.

Verification: meaningful tests for branch topology / collapse, media warning
handling, persistence isolation / malformed data / restart, archive roundtrips,
source filtering, search races and settings result navigation. Render populated
profile, conversation, Saved, Discover and settings-search screens. Run analysis,
full tests, ARB integrity, skill sync and a debug APK build on the pinned SDK.
Publish a draft PR and review APK; merging and publishing a release are separate.
