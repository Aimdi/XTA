**XTA Native is a prototype.** It is XTA's X reader written for Android alone, in Kotlin and Jetpack Compose instead of Flutter, so you can compare size, start time and feel with XTA itself. It is not a release and may be withdrawn.

### Trying it

1. Install the APK below. It installs **next to XTA** (its own app id, `com.aimdi.xtanative`) and shares nothing with it: XTA and its data are untouched.
2. In XTA, open **Settings → Data → Export**, switch on **"Export accounts?"**, and export.
3. Open XTA Native and choose that file. Your X accounts, the accounts you follow and your groups come with it.

Like XTA, it only reads X: nothing is posted, liked or followed there. Your X sessions stay on the phone, in storage that Android neither backs up nor moves to a new phone.

### What is in it

- The Following feed and one tab per XTA group, merged in time order, with pull to refresh, endless scrolling, and the last feed shown at once when the app opens
- Opened posts with what they answer and their replies; profiles with their posts; search (Top and Latest)
- Photos you can pinch and double-tap to zoom, videos and GIFs
- x.com and twitter.com links open in it (once allowed under the app's "Open by default" settings)
- Replies and reposts switches; English and German; black when the phone is dark

Everything else XTA does (saved posts, likes, notes, translation, the other networks, the many settings) is left out on purpose.

### Comparing it with XTA

- **Size:** this APK against XTA's.
- **Start:** close both apps fully, then open each from the launcher. Both show the last feed from disk.
- **Scrolling:** scroll the same feed in both. Developer options → "Profile HWUI rendering" draws a bar per frame; bars above the green line are dropped frames.
- **Memory:** Settings → Apps → each app → Memory after the same use.

The first few starts of any new install run slower while Android compiles the app in the background; compare after a day of use, or after the phone has been idle on the charger once.
