## XTA — aimdi181

### Reddit works again without an account

This year Reddit closed the ways XTA read it without an account. old.reddit.com now sends logged-out readers to a login page, and Reddit's public `.json` endpoints refuse them. XTA asked old.reddit first and stopped at that login page, so every subreddit, thread and search failed.

- **Subreddits, Home's followed feed, threads, search and profiles are read from www.reddit.com.** XTA uses the parts the site loads as you scroll, which Reddit still serves without an account.
- **Posts keep their pictures, galleries, videos, link previews, flair and text.** The next page loads as before, and sort orders and Top's time window work.
- **Big threads show all the comments Reddit sends**, and the "more" row at the top loads the next ones into the thread. If Reddit refuses, the row opens the thread on Reddit as before.
- **Posts opened from search or from a link** now show their full text, pictures, video and link, not just a title.
- **Comments and posts keep their paragraphs**, line breaks, lists and code blocks instead of running together.
- **NSFW posts are recognised** from every mark Reddit gives them, so the NSFW setting holds.
- **A community's picture and description** come from Reddit's community search. Subscriber counts are no longer shown, because Reddit shows logged-out readers weekly visitors instead.
- **Pinned posts no longer appear** at the top of a subreddit, because Reddit does not include them in what it serves this way.
- Reading with your own Reddit client ID or sign-in works as before.

### X keeps reading where x.com asks for a browser check

X needs a key from its own web pages before it answers a signed-in account. Since October, x.com answers those pages with Cloudflare's "Just a moment…" browser check for some connections, VPNs among them, and XTA showed 🧾 for everything X. XTA now falls back to x.com's sign-in page, which is spared the check and holds the same key. Your session is not sent to that page.

### Bluesky post search works again

Bluesky's public server stopped answering post search for readers without an account. Search now goes to Bluesky's own API, and scrolling for more loads older posts.

### Motion from X's new app

X rebuilt its Android app this July. XTA now moves the way it does:

- **Liking a post** plays X's heart burst. The like still stays on your device.
- **Photos** grow out of the post into the viewer and shrink back when you close or pull them down.
- **Pages** fade in while settling into place. On Android 14 and newer, the back gesture moves the page with your finger and shows the page behind it.

The "Remove animations" setting, Android's own animation setting, calm mode and zen mode keep things still.

Everything else is as in aimdi180.

### Licence note

The downloadable voices use sherpa-onnx, whose library includes espeak-ng under GPL-3.0. Its licence text is in Settings → About → Licences. XTA's own source code stays MIT.

### Installation

For most Android phones, use **`xta-aimdi181_arm64-v8a.apk`**. Universal, ARMv7 and x86_64 APKs are also supplied. The base version code is **400001500**, above every aimdi180 variant. The app ID (`com.aimdi.xta`) and release signing identity are unchanged for in-place updates.

For Linux, use the Arch package (`sudo pacman -U xta-bin-4.12.0.181-1-x86_64.pkg.tar.zst`), the attached `PKGBUILD` (`makepkg -si`) or the portable tarball. **`SHA256SUMS-linux`** lists their checksums. See [docs/desktop.md](https://github.com/Aimdi/XTA/blob/claude/main/docs/desktop.md).

The release workflow checks translations, skill synchronization, analysis and the full Flutter test suite on the tagged source before building. **`release-build.json`** and **`SHA256SUMS`** accompany the APKs, and **`xta-aimdi181-debug-symbols.zip`** and **`xta-aimdi181-linux-debug-symbols.zip`** hold the debug symbols for reading crash traces.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi180)
