**XTA Native is XTA now.** XTA is written again in Kotlin and Compose Multiplatform, for Android and the Linux desktop from the same screens, and it replaces the Flutter XTA, whose last release stays downloadable under its `aimdi` tag. It reads X and the other networks as XTA did. Nothing is posted, liked or followed on any network: follows, likes, saves and notes stay on your device.

**New in 1.0.13:** On the desktop, the **deck** is a multi-column reader: any timeline is a column (Following, For you, a group, or a network such as Reddit or Bluesky, with its feeds under its name), added from its **+** and moved or removed from each column’s menu; columns are narrow, medium or wide, fill the window while they fit and scroll sideways past that; a post or profile opened from a column opens beside them, and Escape closes it. On Android, **Settings › Theme › Hide the status bar** gives XTA the whole screen; a swipe from the top shows the time and battery a moment. Your data is safer: a settings or follows file that cannot be read is kept aside instead of overwritten, every file is flushed to disk before it replaces the old one, and a restore or a WebDAV sync no longer brings back older copies of notes, profile notes or reading places, nor deletes a draft’s pictures. Security: a backup can no longer send your API keys or logins to a server it names, the in-app browser is no longer signed in to X, Android opens only web links, plugins come only over https and the desktop checks their signature at every start, and a redirect to another site never carries your session. Fixed: a crafted post link or a full disk no longer closes the app; Reddit’s account line, Hacker News’ “Updated” time, Substack’s narrowed Home and Mark all as read, Bluesky’s and Stocks’ feed rows say what is shown; RSS’s and Stocks’ Add sits in the plugin sheet; Substack’s unread count shows on every pane; feeds do less work per post while scrolling. Update the plugins in the plugin store to get their fixes. **In 1.0.12:** Every network on Home has one header (its icon, name and status over its feeds and filter) and a plugin sheet behind its icon (plugin API 14).

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

- Home: the bottom bar with the pages you pick; Following, X, your groups and the networks you pin, turned by a sideways drag or picked in the timeline picker; Posts or Media, the order and filters; posts linking one article stacked, reposts in runs, opening where you left off; a group's Media and its Discover across every network with Rank with AI; a deck of columns on the desktop
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
