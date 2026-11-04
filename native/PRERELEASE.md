**XTA Native is a prototype.** It is XTA written again in Kotlin and Compose instead of Flutter, for Android and the Linux desktop from the same screens, so you can compare size, start time and feel with XTA itself. It is not a release and may be withdrawn.

### Trying it

1. Install the APK below. It installs **next to XTA** (its own app id, `com.aimdi.xtanative`) and shares nothing with it: XTA and its data are untouched.
2. In XTA, open **Settings → Data → Export**, switch on **"Export accounts?"**, and export.
3. Open XTA Native and choose that file (later, **Settings → Data → Import**). Your X accounts, the accounts you follow, your groups, saved posts, notes and settings come with it.

Like XTA, it only reads: nothing is posted, liked or followed on X or on any other network. Follows, likes and saves stay on the device. Your sessions stay on the phone, in storage that Android neither backs up nor moves to a new phone.

### The other networks

Every network but X is a plugin you add from **Settings → Plugin store**, and remove there just as easily. In this prerelease: **Bluesky, Mastodon, Threads, Instagram, Reddit, Hacker News, Pixiv, Substack and Booru**, each with XTA's screens and settings. TikTok, EhViewer, RSS, the web sources, Stocks and the Immich, Karakeep and Deepmarks saves come in later prereleases.

A network's follows in your XTA backup wait for its plugin and arrive when you add it; an export from XTA Native writes them where XTA reads them back. Where XTA lets a network join Home's timelines, groups, Saved and downloads, its plugin can too; Reddit and Bluesky already do, the others follow.

### On Linux

- **Any distribution:** unpack `xta-native-desktop-…-linux-x64.tar.gz` and run `bin/xta-native`. It brings its own Java runtime.
- **Arch:** `sudo pacman -U xta-native-…-x86_64.pkg.tar.zst` puts it in your application menu (the PKGBUILD beside it builds the same package).

It keeps its data in `~/.local/share/xta-native`, a folder only you can open. Videos open in your system player.

### What is in it

- Home as in XTA: Following, For you and a tab per group, merged in time order, opening where you left off; the deck of columns on the desktop
- Posts as XTA draws them: threads, quotes, polls, link cards, community notes, translation, read aloud, and the media viewer with zoom and video
- Profiles with their tabs and filters, notes on accounts, follows and groups kept on the device
- Search with its tabs and advanced search; Discover with trends; communities, lists and antennas
- Saved posts with folders, likes on this device, notes, the archive, the offline collection and downloads
- Every section of XTA's settings, its backup export and import, WebDAV sync, and 28 languages
- Links open in the app's own browser on Android, or in the browser you pick; x.com and the networks' links open in the app

Still to come: the networks above, Home's bottom bar and some of Following's controls, downloaded voices speaking (they download but do not speak yet) and lock-screen media controls.

### Comparing it with XTA

- **Size:** this APK against XTA's.
- **Start:** close both apps fully, then open each from the launcher. Both show the last feed from disk.
- **Scrolling:** scroll the same feed in both. Developer options → "Profile HWUI rendering" draws a bar per frame; bars above the green line are dropped frames.
- **Memory:** Settings → Apps → each app → Memory after the same use.

The first few starts of any new install run slower while Android compiles the app in the background; compare after a day of use, or after the phone has been idle on the charger once. The emulator check in CI measures both apps the same way and puts the numbers in its log.
