**XTA Native is XTA now.** XTA is written again in Kotlin and Compose Multiplatform, for Android and the Linux desktop from the same screens, and it replaces the Flutter XTA, whose last release stays downloadable under its `aimdi` tag. It reads X and the other networks as XTA did. Nothing is posted, liked or followed on any network: follows, likes, saves and notes stay on your device.

**New in 1.0.14:** **One look everywhere**: every screen and plugin is flat like a profile, with hairlines instead of boxes, actions as accent words, chips without fills, and sheets in the page’s own colour. **Home has one sheet**: every timeline’s icon (and a hold of Home) opens it, with the shown timeline’s entries, Following, X and your groups as a row of words, and your networks four to a row. **Threads** gains its own Media tab, more reposts and a For you that keeps loading for guests; with a session, **Liked**, **Following** and your own **For you**, private accounts you follow, and posts or tabs Threads keeps from guests, each read with your account only when you open it; Activity picks out quotes, reposts and new followers; tabs that fail say why, with Retry. **Faster**: liking or saving writes one post, not the whole library (your library is converted on first start; an older XTA will not see likes and saves made after that); feeds are merged off the screen’s thread, cards do far less while you scroll, a post or a Threads profile no longer freezes as it opens, and the app starts with less work. Also: the hidden status bar’s space is used by the app; reading aloud with a downloaded voice starts quickly and keeps pace; a cashtag in **Stocks** means the token you watch under it; **Reddit** shows picture comments, says when a feed failed, and covers spoilers’ words; **Substack**’s refresh always ends and its reader is lighter. Update the plugins in the plugin store to get their changes. **In 1.0.13:** the desktop deck of columns, Settings › Theme › Hide the status bar on Android, safer files and restores, and security fixes for backups, links, plugins and redirects.

### Installing

- **Android 8.0 or newer:** install `xta-native-….apk`. It installs as its own app, `com.aimdi.xtanative`, **next to the Flutter XTA** (`com.aimdi.xta`), and shares nothing with it: the old app and its data are untouched until you remove them.
- **Any Linux on x86_64:** unpack `xta-native-desktop-…-linux-x64.tar.gz` and run `bin/xta-native`. It brings its own Java runtime.
- **Arch Linux:** `sudo pacman -U xta-native-…-x86_64.pkg.tar.zst` puts it in your application menu; the `PKGBUILD` beside it builds the same package.

`SHA256SUMS` lists every file's checksum. The APK and the plugins are signed with XTA's release key (`certificate-fingerprints.txt` in the repository).

### Moving over from the Flutter XTA

1. In the old XTA, open **Settings → Data → Export**, switch on **"Export accounts?"**, and export.
2. In XTA Native, choose that file on the first screen (later, **Settings → Data → Import**).

Your X accounts, the accounts you follow, your groups, saved posts, notes, read positions and settings come with it. A network's follows wait for its plugin and arrive when you add it from **Settings → Plugin store**. An export from XTA Native can be read back by the old XTA too. Once everything is there, you can uninstall the old app.

Without a backup, sign in to X from the first screen or **Settings → Accounts → +**: X's own sign-in page on Android, the cookie form on Linux.

### What is in it

- Home: the bottom bar with the pages you pick; Following, X, your groups and the networks you pin, turned by a sideways drag or picked in Home’s sheet; Posts or Media, the order and filters; posts linking one article stacked, reposts in runs, opening where you left off; a group's Media and its Discover across every network with Rank with AI; a deck of columns on the desktop
- Posts: threads, quotes, polls, link cards, Community Notes, translation and reading aloud; likes and bookmarks kept on the device; the share sheet (the post as a picture too); pictures with their descriptions; videos in place, full screen and, on Android, picture in picture; the media viewer with zoom
- Profiles with their tabs and filters, notes on accounts, Subscribe and one group sheet everywhere, Unsubscribe with undo
- Search with its tabs and advanced search; Discover with trends; communities, lists and antennas
- Saved posts with folders, likes on this device, notes, the archive, the offline collection and downloads
- Reading aloud with the system's voices or voices downloaded to the device (Settings › Read aloud); on Android the voice engine downloads with the first voice, from this release, and the notification and lock screen carry the controls
- Signing in to X in the app; clear screens when a read fails, with Add account and the wait for X's rate limit; Diagnostics with the recent reads and each account's health
- Every section of XTA's settings, backup export and import, WebDAV sync, themes with True Black, and 29 languages; a check for a newer release once a launch
- Links open in the app's own browser on Android, or the browser you pick; x.com and the networks' links open in the app, and so do t.co, fxtwitter, vxtwitter and fixupx links

### The other networks

Every network but X is a plugin you add from **Settings → Plugin store**, and remove there just as easily: **Bluesky, Mastodon, Threads, Instagram, Reddit, Hacker News, Pixiv, Substack, Booru, RSS, the web sources and Stocks**, and **EhViewer and TikTok** once **Show private plugins** is on, in the store's menu. **Karakeep and Deepmarks** add "Save to …" to a post's sheets and **Immich** uploads a Saved folder, to your own servers. Reddit, Bluesky, Mastodon, Threads, Booru, RSS and the web sources join Home's timelines and the groups; Instagram, TikTok, Hacker News, Pixiv and Substack the groups; their posts are saved, noted and downloaded through the app. Once you sign in with Pixiv, its bookmarks, follows and watchlist changes go to your Pixiv account, as in XTA.

**Threads** has its own For you feed and sharp pictures; conversations with their replies nested and more loading as you scroll; profiles that keep loading, with the exact follower count, the verified badge, bio links and a Reposts tab; People and Topics search; spoilers, polls, labels and Threads' own media warnings; muted words; Home that reopens where you stopped, reads many followed accounts without one slow account holding up the rest, and names any account it could not read and why. On Android you can sign in on threads.com itself; a Retry waits out Threads' rate limit instead of inviting a ban.

### On Linux

The app keeps its data in `~/.local/share/xta-native`, a folder only you can open. Videos, GIFs, podcasts and offline videos play in the app through mpv's library: the Arch package installs `mpv`; with the tarball, install your system's (`libmpv2` on Debian and Ubuntu, `libmpv1` on Ubuntu 22.04, `mpv-libs` on Fedora). Without it, videos open in your system player. Downloaded voices read aloud here too: the tarball and the package carry the voice engine. Only one copy runs at a time, and the window remembers its size.

### Privacy

Your sessions stay on the phone, in storage Android neither backs up nor moves to a new phone. There are no trackers or analytics; the app contacts only the networks you read, GitHub for plugins and updates, and the services you set up yourself (AI, Karakeep, Deepmarks, Immich, WebDAV).
