**XTA Native is a prototype.** It is XTA's X reader written in Kotlin and Compose instead of Flutter, for Android and the Linux desktop from the same screens, so you can compare size, start time and feel with XTA itself. It is not a release and may be withdrawn.

### Trying it

1. Install the APK below. It installs **next to XTA** (its own app id, `com.aimdi.xtanative`) and shares nothing with it: XTA and its data are untouched.
2. In XTA, open **Settings → Data → Export**, switch on **"Export accounts?"**, and export.
3. Open XTA Native and choose that file. Your X accounts, the accounts you follow and your groups come with it.

Like XTA, it only reads X: nothing is posted, liked or followed there. Following an account in XTA Native stays in XTA Native. Your X sessions stay on the phone, in storage that Android neither backs up nor moves to a new phone.

### On Linux

- **Any distribution:** unpack `xta-native-desktop-…-linux-x64.tar.gz` and run `bin/xta-native`. It brings its own Java runtime.
- **Arch:** `sudo pacman -U xta-native-…-x86_64.pkg.tar.zst` puts it in your application menu (the PKGBUILD beside it builds the same package).

It keeps its data in `~/.local/share/xta-native`, a folder only you can open. Videos open in your browser or video player; the feed shows their posters.

### What is in it

- The Following feed and one tab per group, merged in time order, with pull to refresh and endless scrolling
- It opens where you left off, on the posts it showed last; new posts go above you, and a pill says how many
- Videos and GIFs play muted in the feed as they come into view (Settings can turn this off)
- Profiles with Posts (with what they reposted and the pinned post first), Replies and Media
- Follow and unfollow on a profile, choose whether an account is in the Following feed, put it in groups or make a new group
- Opened posts with what they answer and their replies; search (Top and Latest), and "Go to @name" for a handle
- Photos you can pinch and double-tap to zoom, videos and GIFs
- When the connection comes back, a feed that had none reads again by itself
- x.com and twitter.com links open in it (once allowed under the app's "Open by default" settings)
- Replies and reposts switches; English and German; black when the phone is dark

Everything else XTA does (saved posts, likes, notes, translation, the other networks, the many settings) is left out on purpose.

### Comparing it with XTA

- **Size:** this APK against XTA's.
- **Start:** close both apps fully, then open each from the launcher. Both show the last feed from disk.
- **Scrolling:** scroll the same feed in both. Developer options → "Profile HWUI rendering" draws a bar per frame; bars above the green line are dropped frames.
- **Memory:** Settings → Apps → each app → Memory after the same use.

The first few starts of any new install run slower while Android compiles the app in the background; compare after a day of use, or after the phone has been idle on the charger once. The emulator check in CI measures both apps the same way and puts the numbers in its log.
