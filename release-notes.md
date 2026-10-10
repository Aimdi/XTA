## XTA — aimdi173

### Pixiv catches up with PixEz

XTA's Pixiv plugin now covers almost everything the PixEz app offers a reader. PixEz was used only as a reference for what each feature does; no PixEz code is in XTA. XTA stays a reader: the only things it changes on your Pixiv account are follows, bookmarks and the watchlist.

**Novels**
- A novel mode next to illustrations, switched with one button beside the tabs: recommended, following (public or private), rankings with every board, your bookmarks, series pages and the novel watchlist.
- A reader that renders Pixiv's markup: pages, chapters, ruby over the text, links between pages and novels, embedded artworks and uploaded pictures. It uses XTA's reading text size and spacing, remembers where you stopped, lets you select and translate text, and exports the novel as a .txt file.
- Novel search with the same filters as artworks, novels on creator profiles, novel comments and novel history.

**Search**
- Filters for where to search (tags partial or exact, title and caption), sort, posting date with quick ranges or a custom range, bookmark-count tiers, AI and ugoira. Oldest first works for everyone; Premium adds the popularity-by-audience orders, and without Premium "Popular" shows Pixiv's free preview.
- Paste a link or ID to open it straight away, favourite tags, translated trending tags, and reverse image search through SauceNAO. The search sheet says that the picked image goes to saucenao.com.

**Browsing**
- Rankings with every mode and a date picker, a manga feed, Pixivision articles you can read in the app, illustration and manga series pages with previous and next, and the manga watchlist.
- Full profiles: works split into illustrations and manga, their public bookmarks, who they follow, their info and links. Following and follower lists use cards with three recent works and a follow button. Long-press follow to follow privately.
- Comments with replies and stickers (reading only), with local muting of a comment or its author.

**Bookmarks**
- Bookmark tags: an editor with suggestions, filtering your own bookmarks by tag, and a default visibility. Optionally add the work's tags, follow the artist or save the images when you bookmark, and bookmark when you save. Long-press the heart for the editor.

**Viewing and saving**
- Image server choice (Pixiv, the i.pixiv.re mirror or your own address), image quality, grid columns for portrait and landscape, swipe between works, a side-by-side layout on wide screens, zoom in the vertical reader, and tap to retry a failed image.
- Ugoira export as GIF or ZIP, file-name templates, per-artist and R-18 subfolders, a "downloaded" badge, and choosing which pages to save.
- Downloads anywhere in XTA now run two at a time by default; Settings → Media offers one to four.

**Settings and the rest**
- Viewing history for works and novels. It stays on this device, is never part of a backup, and can be paused or cleared.
- Several Pixiv accounts with quick switching. Refresh tokens never go into backups or exports.
- Mute by artist name, tag patterns (r'…'), work, novel or comment, with a screen that explains why a muted work is hidden.
- Pixiv links open in XTA: works, users and their tabs, tags, series, novels, Pixivision, pixiv.me short links and image file names. On Android, a settings shortcut lets pixiv.net links open in XTA by default.
- Tags arrive translated into XTA's language, and tapping the current tab again scrolls back to the top.

**Fixes**
- A revoked or expired Pixiv login is now refreshed and retried instead of leaving you stuck. A dropped connection is retried once, and Pixiv's rate limit is reported as a rate limit, not as a rejected token.
- Profiles show works, following and My pixiv friends. The follower number was always wrong, because Pixiv never sends it.
- Group Discover no longer suggests Pixiv creators through works hidden by Show R-18 or Hide AI.

Everything from aimdi172 is included: the bottom bar that hides while you scroll down.

### Installation

For most Android phones, use **`xta-aimdi173_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001420**, above
every aimdi172 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi172)
