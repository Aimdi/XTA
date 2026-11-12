**XTA Native is a prototype.** It is XTA written again in Kotlin and Compose instead of Flutter, for Android and the Linux desktop from the same screens, so you can compare size, start time and feel with XTA itself. It is not a release and may be withdrawn.

### Trying it

1. Install the APK below. It installs **next to XTA** (its own app id, `com.aimdi.xtanative`) and shares nothing with it: XTA and its data are untouched.
2. Sign in to X in the app, from its first screen or **Settings → Accounts → +**: X's own sign-in page on Android, XTA's cookie form on Linux.
3. Or bring everything over from XTA: in XTA, open **Settings → Data → Export**, switch on **"Export accounts?"**, and export; then choose that file in XTA Native (later, **Settings → Data → Import**). Your X accounts, the accounts you follow, your groups, saved posts, notes and settings come with it.

Like XTA, it only reads: nothing is posted, liked or followed on X or on the other networks, and follows, likes and saves stay on the device. The one exception is XTA's too: once you sign in with Pixiv, your bookmarks, follows and watchlist changes go to your Pixiv account. Your sessions stay on the phone, in storage that Android neither backs up nor moves to a new phone.

### The other networks

Every network but X is a plugin you add from **Settings → Plugin store**, and remove there just as easily. In this prerelease: **Bluesky, Mastodon, Threads, Instagram, Reddit, Hacker News, Pixiv, Substack, Booru, RSS, the web sources and Stocks**, each with XTA's screens and settings, and **EhViewer and TikTok**, private as in XTA: the store lists them once **Show private plugins** is on, in its menu. With Stocks, X posts show the price strip under their cashtags, and a cashtag opens its ticker. **Karakeep and Deepmarks** add "Save to …" to a post's sheets and **Immich** an upload switch to Saved's folders, sending to your own servers, as in XTA.

A network's follows in your XTA backup wait for its plugin and arrive when you add it; an export from XTA Native writes them where XTA reads them back. **Subscriptions** lists every network's followed accounts beside your X accounts, each with Add to group and Unsubscribe, and your groups keep the other networks' accounts your backup put in them. Where XTA lets a network join Home's timelines, groups, Saved and downloads, its plugin does too: Reddit, Bluesky, Mastodon, Threads, Booru, RSS and the web sources join Home's timelines and the groups; Instagram, TikTok, Hacker News, Pixiv and Substack the groups; and their posts are saved, noted and downloaded through the app.

### On Linux

- **Any distribution:** unpack `xta-native-desktop-…-linux-x64.tar.gz` and run `bin/xta-native`. It brings its own Java runtime.
- **Arch:** `sudo pacman -U xta-native-…-x86_64.pkg.tar.zst` puts it in your application menu (the PKGBUILD beside it builds the same package).

It keeps its data in `~/.local/share/xta-native`, a folder only you can open. Videos, GIFs, podcasts and offline videos play in the app through mpv's library, as XTA's do: the Arch package installs `mpv`; with the tarball, install your system's (`libmpv2` on Debian and Ubuntu, `libmpv1` on Ubuntu 22.04, `mpv-libs` on Fedora). Without it, videos open in your system player. Downloaded voices read aloud here too: the tarball and the package carry the voice engine, as XTA's do.

### What is in it

- Home as in XTA: the bottom bar with the pages you pick in Settings › Home; Following, X, your groups and the networks you pin, turned by a sideways drag or picked in the timeline picker, with Mastodon, Bluesky and Threads in one; Following's bar (Posts or Media, the order, filters); posts linking one article stacked, reposts in runs, opening where you left off; a group's Media, and its Discover across every network with Rank with AI; the deck of columns on the desktop
- Posts as XTA draws them: threads, quotes, polls, link cards, community notes, translation, read aloud; the footer with likes and bookmarks kept on the device, the share sheet (the post as a picture too) and the ⋯ sheet; pictures in a sideways row with their descriptions; videos playing in place under XTA's controls, full screen and, on Android, picture in picture; and the media viewer with zoom
- Signing in to X in the app; XTA's screens for a read that failed, with Add account and the wait for X's rate limit; Diagnostics with the recent reads and each account's health
- Reading aloud with the phone's voices or with voices downloaded to the device (Settings › Read aloud); on Android the voice engine downloads with the first voice, from this prerelease or else XTA's latest release, and while it reads, or a podcast plays, the notification and the lock screen carry its controls
- Profiles with their tabs and filters, notes on accounts, Subscribe and one group sheet everywhere, Unsubscribe with undo, all kept on the device
- Search with its tabs and advanced search; Discover with trends; communities, lists and antennas
- Saved posts with folders, likes on this device, notes, the archive, the offline collection and downloads
- Every section of XTA's settings, its backup export and import, WebDAV sync, and 29 languages; an opened post that swipes back as XTA's card, confirm close, the check for a newer prerelease and the licences page
- Links open in the app's own browser on Android, or in the browser you pick; x.com and the networks' links open in the app, and so do t.co, fxtwitter, vxtwitter and fixupx links; a link nothing reads says so
- New in this prerelease:
  - Threads conversations show their replies again (0.1.79 showed none to a guest), nested under what they answer, and load more replies as you scroll
  - A Threads profile's threads keep loading as you scroll; where Threads gives a guest no next page (its replies, media and reposts tabs, search and topics) the list ends in Open in browser
  - Threads opens on For you the first time; an empty Home offers For you, Add account and accounts to follow; Add account takes several handles or links at once
  - Threads reads through many followed accounts without repeated pulls, stops at once when it is throttled, and says when a refresh failed over what is shown
  - Threads says what a session adds and how to fix a parked one, and its media covers carry Threads' own warning words
- In 0.1.79:
  - Threads conversations read Threads' new post pages: the real replies, nested under what they answer, with "Show fewer replies", related threads apart, and the whole conversation read aloud
  - Threads profiles show the exact follower count on a tap, the verified badge, the full bio with tappable mentions and links, its links listed, a Reposts tab, and a private note of your own
  - Threads search has People and Topics tabs, topic pages with their emoji, and suggestions while you type from what is on your device
  - A Threads post covers spoilers until tapped, shows polls with their results, Pinned, AI info and Threads' own warning over media; ⋯ offers Copy text and Copy link; long posts fold with Show more
  - Threads' Home reopens where you stopped and shows "N new posts" above; muted words hide posts in Home and For you; the author's replies are marked; a conversation's pictures swipe through as one gallery
  - Threads saves pictures and videos under names saying whose they are; a long press on a picture offers its description, Download, Download all and the link
  - Threads reads its pages several times faster, and no longer loses a profile's threads when a post mentions "<html>"
  - Bluesky's and Mastodon's Filters sit in the tab's bar; a Bluesky post labelled for its media also covers its quote and link card; accounts without a picture show their initial
  - Reddit: folded in Home, its followed communities sit in a menu; its own search screen with a follow button when you search a community; comments, covers and the communities sheet read better
  - Hacker News: links in stories, comments and profiles are tappable; a story's sheet opens from its bar
  - Pixiv: the column reader zooms; pictures that fail offer Retry; novel chapters replace each other instead of piling up. EhViewer names saved pages with their page number; a Booru picture that failed asks again from its mark
  - Instagram says a profile is hidden from your network rather than missing; Substack says when its bot check turns you away, with Open in browser; Substack's cards and highlights match XTA at every text size
  - RSS finds a walled site's feed, says why an address failed, imports OPML faster and shows the last timeline at once; web sources say when a next page failed, with Retry, and dim posts you read to the end
  - Stocks rests closed markets and says why a chart is missing; Immich says why an upload failed and stops when the server refuses; Karakeep and Deepmarks say they are saving
  - The plugin store offers a failed update again, says when a plugin is too old for the app, and gives Retry when its list cannot be read
- In 0.1.73:
  - Threads: a profile you opened before shows at once while it is read again; GIFs loop in place; accounts without a picture get a face with their initial; a reload keeps the conversation and says so in a notice with Retry
  - Threads search and topic links open in the plugin, and a recent search can be forgotten
  - Threads says what went wrong in its own words: a missing account or post, when its wait for too many reads ends, and a page it shows only to signed-in readers
  - On Linux, a Substack post the reader cannot read opens in your browser; Substack's pins, logos and publication header look as XTA draws them
- In 0.1.72:
  - Threads has a For you feed of its own recommended posts, kept on the device and shown at once on opening, and sharper pictures: the largest Threads offers, scaled down smoothly on Linux
  - Threads plays videos in place, at the quality Settings › Media picks; a row of pictures has its dots; described pictures carry ALT; an opened conversation is a card you drag back
  - Bluesky, Mastodon, Threads, Hacker News, Reddit and Pixiv have one bar on their own screens, their tabs in it, folding into a section picker on Home
  - Mastodon's and Bluesky's content warnings are their own Show and Hide covers, in Home, groups and Saved too; nothing held back is drawn until opened
  - A Bluesky post opened from a link shows at once while it loads; a quoted post shows its pictures over its text; "… reposted" opens who reposted. A Mastodon post is laid out in XTA's order
  - The plugin store shows each plugin's own mark before it is installed; RSS, EhViewer, TikTok and Substack keep the tab you chose
  - A Reddit picture saved from the viewer is named after who posted it; Hacker News' empty lists and searches show their glyph
- In 0.1.69:
  - On Android, a post link on a site you do not follow is checked before the browser opens. It shows "Checking the link…", and opens in Substack's reader only when the site confirms it
  - X's own answers are said in X's words: a private profile, a missing page, a missing account, a suspended account, and access forbidden. Those are not tried again. A thrown-away guest token still says so, and is tried again
  - A failed load names what failed: the post, the next page of replies, who reposted, and the trends for a place
  - A failed refresh of a Substack article leaves the copy you are reading, and says you can keep reading it
  - Moving someone between groups can be undone. Home's account filter offers Add account when you have none
  - Pixiv keeps a private tag you picked, and a caption no longer turns a javascript link into a Pixiv page
  - Bluesky's Following opens where you left it, keeps what it showed when a read fails, and drops an old account's posts when a handle names someone else
  - A loading Reddit feed shows bones. A thread's buttons are easier to hit, and an upvote says whether it is on
  - Substack's podcast keeps its place more closely, and leaving an import stops it
- In 0.1.60:
  - Group feeds bring you back to your last read post after a long absence and never mark posts read past a gap; a feed reopened in the same session comes back as you left it, without reading X again; chunks show as they answer; a feed out of sight reads nothing; a search that stalls falls back to the members' profiles
  - Reads from X keep to one 30-second budget, a changed endpoint is reported at once instead of retried, reading without an account no longer needs request signing, and search results come grouped by conversation
  - The networks' videos follow Settings › Media (manual loading, mute, loop, quality, autoplay) as X's do; Bluesky remembers where you left Following; the language switches without a restart
  - Backups carry groups' icons and colours, read positions with their time (For you too), accounts' health and each feed's catch-up mode both ways between XTA and XTA Native
  - On Linux: one copy at a time (a second start raises the window), the window's size remembered, automatic downloads in Pictures, Videos or Downloads by type, and software centres can list the app
  - On Android: shared text handled as XTA does, and a record of main-thread stalls in the diagnostics report
- In 0.1.56:
  - Live broadcasts and Spaces play in the app
  - The networks' posts in Home's timelines show at once from the last visit while they load again; a source that fails says why, offers what fixes it, and tries again by itself; a feed shown from disk says when it was saved; posts load under skeletons
  - Profiles open by account, so a renamed one still opens, and show the copy kept on the device while X is read again; profile links open their tab; people lists carry every badge
  - Add to group takes a subreddit, a Bluesky, Threads or Mastodon handle, a newsletter or a feed; a network's timeline on Home opens its plugin from its menu; the drawer opens with a swipe from the edge
  - The intro installs the networks you pick, with its drawings and page dots back
  - Pixiv's bookmarks (private ones too), follows and watchlists on your Pixiv account
  - Substack's articles in the web reader on Android, with the native reader a switch away, and "Speak from here"
  - Long-press a hashtag to follow the topic; crash reports filed on GitHub when you set them up; headset buttons; the phone's fastest refresh rate
  - Without an X account the app opens on Home and asks once, as XTA does

### Comparing it with XTA

- **Size:** this APK against XTA's.
- **Start:** close both apps fully, then open each from the launcher. Both show the last feed from disk.
- **Scrolling:** scroll the same feed in both. Developer options → "Profile HWUI rendering" draws a bar per frame; bars above the green line are dropped frames.
- **Memory:** Settings → Apps → each app → Memory after the same use.

The first few starts of any new install run slower while Android compiles the app in the background; compare after a day of use, or after the phone has been idle on the charger once. The emulator check in CI measures both apps the same way and puts the numbers in its log.
