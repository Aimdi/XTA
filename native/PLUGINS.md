# Downloadable plugins

XTA Native ships as a small app that reads X. Every other network (Reddit,
Bluesky, Mastodon, Threads, Substack, Booru, Pixiv, EhViewer, Instagram,
TikTok, RSS, Hacker News, web sources, Stocks, Immich, Deepmarks, Karakeep)
is a **plugin the reader downloads** from the plugin store, on Android and on
the Linux desktop. Uninstalling removes its code and everything it stored.
Bluesky is the first (`plugins/bluesky`); the others still read from
`networks/` until each moves (see [Moving a network into a plugin](#moving-a-network-into-a-plugin)).

It keeps the rules of XTA's own plugin store (`lib/plugins/plugin.dart`,
`plugin_catalogue.dart`, `lib/settings/_plugin_store.dart`):

- A **catalogue** published from this repository says what is offered. It can
  withdraw a plugin (`available: false`) without a release; it never takes
  away one that is installed.
- **Installed** plugins stay installed and keep working whatever the
  catalogue says, or when it cannot be reached.
- **Uninstall** erases the plugin's storage, settings, home tab and group
  memberships, as XTA's `uninstall` does.
- Private plugins appear only when "show private plugins" is on.
- Plugins only read their network; nothing is posted there.

## What a plugin is

A Gradle module `native/plugins/<id>/` (a Kotlin Multiplatform library with
Compose, android + desktop) holding the network's reading layer (package
`com.aimdi.xtanative.networks.<id>`, as it was in `networks/`) and its screens
and entry class (package `com.aimdi.xtanative.plugins.<id>`). The entry class
extends `XtaPlugin`, the plugin API in `:ui`'s package
`com.aimdi.xtanative.ui.plugins`. A plugin is compiled against the app's
libraries without bundling them: the app provides Compose, Material 3,
coroutines, OkHttp, jsoup, kotlinx-serialization, Coil, `:core`, `:store`,
`:services`, `:networks`' shared types and `:ui` (the plugin API and the shared
components) at run time.

Its text comes from Kotlin (a table per language generated at build time from
the plugin's `strings/values*/strings.xml`), not from Android or Compose
resources, so it loads from any class loader.

Each artifact carries `xta-plugin.json`: `id`, `version`, `apiVersion` (the
plugin API version it was built against, `PLUGIN_API_VERSION`) and `entry`
(the class extending `XtaPlugin`).

## The plugin API

`PLUGIN_API_VERSION` is 9. Version 1 is what native-0.1.41 shipped; 2,
which native-0.1.45 shipped, added `cashtagStrip` and `routeForCashtag` and
the kit's pieces of that time (the article reader, `FeedReads`, X posts in a
plugin, the chip tabs, the shelf, the recent searches, the action row,
`ZoomableImage`); 3 adds `cashtagActions` (the held cashtag's entries),
`postExtras`, `routeForAccount`, `subscriptions` with `PluginSubscription`,
`PluginHost.unfollowed` and `RssReads`; 4 adds `contractNetworks` (the
networks of the contracts a plugin watches, for X posts' text),
`postCard`, `routeForOffline`, `discovery` with `PluginDiscovery` (its part
of a group's Discover), `PluginHost.likedOnDevice`, a long piece of work's
progress (`PluginHost.withProgress` with `PluginProgress`, and `downloadAll`
with its `progress`), `PluginHost.saveFileAndWait`, `SwipeToUnfollow`,
`rememberImageChooser`, `rememberAppLinkSettings`, the search's landing
(`PluginSearch.Landing`, `landsInDiscover`, `sharesRecentSearches`, `sent`)
with `PluginRecentSearchRow`, `settingsTitle`, the viewer's Open post
(`PluginHost.openMedia(media, start, openPost)`, `MediaPager`'s
`onOpenPost`), a picture's own download and share address
(`MediaItem.downloadUrl`, `shareUrl`), the tiles' ALT and long press
(`LocalMediaSave`), `HomeTimelineSwitch` in a plugin's own words,
`ShowTabSwitch` and `PluginSettings.pluginId`; and in `ui.common`
`rememberSwitchedHaptics`, the tabs' swipes (`swipeTabs`,
`LocalParentTurnsPages`, `TabSwipes`), `AltBadge` with `AltTextDialog`,
and the search pill's look (native-0.1.54); 5 adds `followable` with
`PluginFollowable`, what a group's add box can follow on the plugin's
network (native-0.1.56); 6 adds X's search with its `includeReplies` and
`conversations` switches (`XClient.search`); 7 adds a link a plugin may claim
only after asking the network (`mightRouteLater`, `routeForLater`, both
defaulting to no). The app asks only off the desktop, for a few seconds, and
a plugin built before simply never is; 8 adds a home timeline's reading
controls (`HomeReading`, `rememberHomeReading`, `ReadingControlsBar`,
`ReadingControlsPinItem` and `ReportsReadingScroll`'s three lists), so a
plugin's own timeline keeps them as XTA's does. A plugin's `xta-plugin.json`
names the version it was built against.

The app loads the versions from `OLDEST_PLUGIN_API` (2) to its own, so a
plugin built before keeps working until its update comes, for as long as what
it uses is still here: CI's desktop job checks the plugins published in
`native-plugins` against what the build ships (`tool/plugin_abi.py`). Version
1's plugins are no longer published, so they can no longer be checked, and
the app offers them their update instead of loading them. So add
to the API (new members with defaults, new classes) rather than change what
is there; a change that leaves the published plugins behind has to move
`OLDEST_PLUGIN_API` up with it, and the store then offers those plugins their
update. Changing anything below in a way a plugin already built would notice
means a new version, and so does any addition once a prerelease has shipped
the version: an app of that prerelease would be offered the plugins that use
it. CI checks that too: while this build's version is the last prerelease's,
its plugins have to link against that prerelease's app.

`XtaPlugin`, what a plugin is:

| Member | What it is |
|---|---|
| `id` | as in the catalogue and `xta-plugin.json`: `bluesky` |
| `brandColor`, `icon` | its mark (an `ImageVector`) and the colour it is drawn in |
| `category`, `isPrivate` | the store's group for it; offered only with private plugins shown |
| `title(language)`, `description(language)` | its name and one line, in a BCP 47 language |
| `attach(host)`, `detach()` | given its `PluginHost` once, before anything else; stopped when uninstalled or updated |
| `homeTab` | its timeline on Home beside Following and X (a `PluginBody`), or none |
| `clientTab` | what it shows opened on its own from the store, when not its home tab |
| `settings` | its settings screen body, or none |
| `settingsTitle(language)` | that screen's title ("Booru settings"); its name by default |
| `search` | a `PluginSearch` (`hint(language)`, `Results(query, frame)`; `Landing(frame, send)`, what its search shows until a query is sent, `landsInDiscover` where Discover shows that too, `sharesRecentSearches` where its recent searches are the app's, `sent(query)` to keep its own, `ownScreen(query)` naming the plugin's own search screen where its field is its own: the app's search of the plugin then opens that route), or none |
| `routeFor(url)` | the route of its own screen for a link (a post, a profile), or null |
| `screen(route)` | its own whole screen for a route (`PluginScreen`), or null |
| `opensAsCard(route)` | whether that screen is an opened post, which the app then opens as its own (XTA's card_page_route.dart): a card that slides in over the page it came from and is dragged, thrown or sent back, `PluginHost.back` sliding it out; false by default (Threads' conversation) |
| `backupSections` | `PluginBackupSection`s (name, export, import) of what it keeps |
| `footprint()` | what it keeps on the device, counted in the store |
| `feed` | a `PluginFeed`: its followed accounts' posts for Home's timelines and the groups ([below](#posts-in-homes-timelines)), or none |
| `subscriptions` | the accounts it follows, as they change, for the app's Subscriptions list ([below](#accounts-in-the-apps-groups)), or none |
| `followable(text)` | what a reader typed into a group's add box names on its network (a subreddit, a handle, a Fediverse address, a feed's or a newsletter's address), as a `PluginFollowable` (its row's `label`, and `follow`, which follows it in the plugin and answers it as its group members name it, throwing when the network has none); asked only of the plugins the text could belong to, as XTA's group_add_sources.dart reads it; or null |
| `discovery` | its part of a group's Discover: the accounts its members in a group point at on its network (a `PluginDiscovery`, [below](#a-groups-discover)), or none |
| `postActions` | where its posts lead where the app draws them (Home, groups, Saved), with their bookmark and menu, or none |
| `postExtras(post)` | what it adds to the shared row where the app draws one of its posts (a `NetworkPostExtras`: Mastodon's poll results), or none |
| `postCard(post)` | its own card for one of its posts where the app draws it among others' (Home's timelines, the groups), in place of the shared row, as XTA draws RSS's article row with its read mark there; the card draws its line under it, the app its colour down its side; or none |
| `archived(content)` | one of its posts the archive kept, read back as a `NetworkPost` for Saved ([below](#saved-notes-and-likes)), or null |
| `cashtagStrip` | what it draws under an X post that names tickers (Stocks' price strip), or none |
| `routeForCashtag(symbol)` | its own screen for a cashtag tapped in an X post (Stocks' ticker), or null: the tap searches X |
| `cashtagActions(symbol)` | its entries (`PluginCashtagAction`s) in the sheet a long press on a cashtag opens, below the cashtag itself (Stocks' Add to watchlist), or none |
| `contractNetworks` | the networks of the tokens it watches by their contract addresses, as they change: an X post's text cuts a contract address in the middle and writes a watched token's network before it (Stocks' "Base · 0x23a2…fe51"), the app matching addresses as `:networks`' `CryptoAddresses` compares them; or none |
| `routeForAccount(account)` | its own screen for one of its accounts as a group holds it (`<id>:<account>`), XTA's destinationFor: the app opens a member there (`AppPlugins.openMember`), or null |
| `routeForOffline(article)` | its own screen for an article the app's offline collection keeps (an `OfflineArticle`, whose `kind` says whose), as XTA's offline library opens a kept RSS item or Substack post in its reader: the app opens it there (`AppPlugins.openOffline`), or shows the copy itself when no plugin answers |
| `postSheetActions(post)` | its entries in the post sheets for a `PluginSharedPost` ([below](#sending-a-post-to-the-readers-own-server)), or none |
| `folderOption`, `filed(post)` | its switch in Saved's folder dialog, and a post filed in a folder with it on, as a `PluginFiledPost` ([below](#sending-a-post-to-the-readers-own-server)) |

A `PluginBody` is drawn in a `PluginFrame`: `padding` (the list's content
padding, clear of the app's bars), `toTop` (grows when the reader asks for
the top: `ScrollsToTop`) and `refresh` (grows when the reader asks for it to be
read again, the desktop's F5: `RefreshesOn`).

`PluginHost`, what the app gives a plugin:

| Member | What it is |
|---|---|
| `http` | HTTP through the app's client and connection pool |
| `store` | its own `StoreDir` (`plugin-data/<id>/`), erased on uninstall |
| `language` | the reader's language, a BCP 47 tag |
| `online` | whether there is a connection |
| `x`, `session` | the app's X client and the X session (accounts and follows imported from XTA) |
| `repairs` | XTA's published repairs in force (`repairs.json`), which its clients take fragile values from (`core/repairs`) |
| `open(route)`, `back()` | its own screen pushed on the app's back stack; back |
| `openLink(url)` | a link, opened as the app opens links (a plugin's screen, X's, else the web) |
| `openPost(id)`, `openProfile(screenName)` | X's post and profile screens |
| `openMedia(media, start)` | the media viewer for `MediaItem`s; `openMedia(media, start, openPost)` with Open post in its bar |
| `openSearch(query)`, `openSettings()` | its own search and settings |
| `settings` | the app's settings, all to read and its own (`plugin.<id>.*`) to change; `inHomeFeed()`, `setInHomeFeed(on)` |
| `saved`, `toggleSaved(post)`, `chooseFolder(post)`, `editNote(post)`, `setLiked(post, liked)` | the reader's archive: what is in Saved, the bookmark, the save sheet, the note, the likes |
| `likedOnDevice()` | a like the plugin keeps itself (Bluesky's, Threads', Substack's hearts): the app says once that likes stay on this device |
| `savedPosts` | its own posts in Saved with their notes, as they change, for its own saved list (`PluginSavedScreen`) |
| `addToGroup(account, follow)` | the app's group picker for one of its accounts |
| `unfollowed(account)` | one of its accounts unfollowed: the app takes it out of every group |
| `openPostMenu(menu)` | the shared post menu over the screen |
| `download(media, fileName, subfolder)`, `downloadAll(files, subfolder)`, `downloadAll(files, subfolder, progress)`, `queueDownloads(files, subfolder)`, `saveFile(bytes, fileName, mimeType, subfolder)`, `saveFileAndWait(…)`, `shareFile(bytes, fileName, mimeType)`, `openDownloads()` | the app's downloads (a file, a batch saved together, one watched file by file with Cancel, a batch queued), a file it made saved (waited for) or shared, the Downloads screen |
| `tell(text)`, `working(text, work)`, `withProgress(first, work)` | a line at the foot of the window over any screen, as XTA's snack bars; one shown while `work` runs; one with a ring, a note and Cancel that `work` keeps up to date (`PluginProgress`) |

The shared pieces, in the same package, drawn in the app's theme. Use them
rather than a copy of your own; a piece two plugins need goes here once,
with a test.

- Posts: `NetworkPostCard` (with `NetworkPostExtras` for what a network adds),
  `NetworkFocalPost`, `QuotedNetworkPost` (a `NetworkPost`; behind a warning it
  opens in place), `LocalPostNetwork` (whose posts are drawn, which the app
  says around a plugin's screens and rows: a link card then opens its page
  with its post in the browser's bar, `NetworkPost.linkContext`), `LinkedText`,
  `NetworkCount`, `NetworkHeaderButton`, `PostLines` (a post's time and
  counts in words), `NetworkBookmark` (below) and `DrawnPosts` (the posts
  its shared rows drew lately, found again by id).
- Media: `MediaTiles` (ALT on a described picture; a long press shows the
  description or saves a photo where the app gives `LocalMediaSave`, as it
  does around a plugin's screens), `MediaPager` (the viewer, with qualities,
  Open post, the description, a download of `MediaItem.downloadUrl` where it
  names the original, and Share link of `shareUrl`), `ZoomableImage`, and
  `rememberImageRequest` for pictures that need request headers.
- Reads and lists: `Fetch` (one read, kept on screen through a failure),
  `MergedFeed` (accounts' pages merged as they come), `FeedReads` (what a
  feed follows read a few at a time, one failing without the others),
  `PagedItems` with `PagedList`,
  `NetworkProblemView` and `NetworkProblemRow` with `problemOf`, `RetryRow`,
  `MessageText` and `EmptyPane`.
- Articles and X posts: `RssReaderScreen`, the article reader RSS and the web
  sources open (its place kept, an offline copy), with `RssReads`, what it
  finished as XTA's RssReadStore keeps it for both (`plugin.rss.read_ids`);
  its `ArticleReaderControls` and `KeepsReaderPlace`, which Substack's own
  reader draws with too; and X posts drawn in a plugin with
  `rememberXPostActions` and `XPostMedia`.
- Screens: `PluginScaffold`, `PluginRoute` (a screen and its arguments as
  text, for `open`), `PluginChipTabs` over a home tab, `WithSettings`,
  `PluginShelf` (what one screen hands the next), `rememberKept` (state kept
  while the screen is on the back stack) and `PluginNotices` with
  `NoticeHost`.
- Rows and fields: `PluginSwitchRow`, `PluginActionRow`, `PluginMenuRow`,
  `PluginSectionTitle`, `PluginTabs` (its body turned by sideways swipes with
  `ui.common`'s `swipeTabs`, which leaves them to Home's timelines),
  `PluginCounts`, `NetworkPersonRow`, `PluginRecentSearchRow`,
  `SwipeToUnfollow` (a followed account's row swiped away after a confirm,
  as TikTok's and Instagram's lists), `SecretField` (a cookie, a token, a
  key) and `PluginIcons`.
- The reader's own server's settings: `PluginAddressField`, `PluginConnection`
  with `PluginConnectionControls` (the connection test, Test and Save, the
  result line), `PluginLinkButton` and `answeredOr`
  ([below](#sending-a-post-to-the-readers-own-server)).
- The app's features: `HomeTimelineSwitch` (in the app's words, or the
  plugin's own), `ShowTabSwitch` (its page on Home's bar), `AddToGroupButton` with
  `addToGroupText`, `PluginSubscription` (an account in Subscriptions, with
  `siteOf`), `PluginPostMenuRows` (the shared post sheet's entries in
  a plugin's own sheet) and `PluginSavedScreen` (below), and
  `PluginSearchHistory` (recent searches, as XTA keeps them).
- Leaving and files: `rememberOpenOutside`, `rememberShareText`,
  `rememberAppLinkSettings` (Android's Open by default page for the app;
  none on the desktop), `rememberTextFileChooser`, `rememberImageChooser`
  and `rememberTextFileSaver` (with `onSaved` and `onFailed`, it tells a file
  that could not be written from a reader who backed out).
- Marks and first loads: `brandMarkIcon` (a network's own glyph fitted to the
  live area every mark spans, as XTA's PluginBrandMark) and, in `ui.common`,
  `FeedSkeleton` and `GridSkeleton` (post- and tile-shaped bones while a
  first page loads, in place of a spinner).
- XTA's plugin chrome (version 9): `PluginHomeTabs` with `PluginHomeTab` (a home tab's own tabs as icon
  and label buttons, the one shown underlined in the plugin's colour, folding
  into `PluginSectionPicker` when they do not fit; opened on its own, the tabs
  and their actions go into the screen's one bar, `PluginClientBar`, as
  `PluginCompactTabs`), which `PluginChipTabs` now draws too; `PluginTabFit`,
  the rules of that; `LocalPluginSurface` with `PluginSurface` and
  `pluginAccent` (the plugin whose screen is drawn, as the app says it);
  `RetryNotice` (Retry with XTA's ⓘ More info sheet, which
  `NetworkProblemView`, `NetworkProblemRow`, `RetryMessage` and `RetryRow`
  now end in) and `PartialFailureButton`; `PluginFeedSkeleton` and
  `PluginGridSkeleton` (what a feed paints while its first page is on the
  way; `pagedRows` draws the first); `PluginFilterChip` (a check while
  selected); `XThreadRow` with `rememberXThreadStacks` (X posts sharing one
  article stacked, as the app's feeds); `HomeTimelineSwitch` and
  `PluginSwitchRow` with a leading icon, `HomeTimelineIcon`;
  `AddToGroupButton` in a plugin's own words, `AddToGroupIcon`; and
  `XtaPlugin.brandsChrome` (false: its mark on Home follows the text colour).
- Words: `PluginStrings` with `pluginString` and `pluginPlural`
  (`pluralCategory` gives each language's plural forms).

`PluginSettings` reads a preference with `get(pref)` and `observe(pref)` and
changes one of its own with `set(pref, value)`; its own are the keys under
`plugin.<id>.` and the few its network's NetworkPrefs keeps where XTA kept
them (`keysOf`, Stocks' `other.ticker_chart`), another network's among them
where XTA's writes it (`NetworkPrefs.shared`: the web sources' read marks
in RSS's `plugin.rss.read_ids`).

`:ui`'s `ui.common` (avatar, hairline, loading row, times, `longPressOver`
for a long press over a whole card, its tappable parts included, `swipeTabs`
for tabs that turn with a sideways swipe, a picture's marks as XTA draws
them: `MediaBadge`, `AltBadge` with `AltTextDialog`, and `ShowMediaTile`
with `mediaShown` for Settings › Media's "Load media manually",
`rememberCopyContract` for a contract address copied whole, and
`rememberSwitchedHaptics`, the haptics behind the reader's Haptic feedback
switch, which a sheet's own window needs: everywhere else
`LocalHapticFeedback` is behind it already) is there too: its avatar asks
for a picture as the tiles do, with the headers its
server wants (Pixiv's Referer). A plugin may draw with the app's other
public building blocks where they fit:
its icon sets (`PostIcons`, `DiscoverIcons`, `GroupIcons`), `ThreadRows` for
X posts, `rememberRowDrag` for a list dragged into order, and `CardView(card,
from, modifier)`, a link card whose page opens in the app's browser with the
post it came from in its bar (a `LinkFrom` over :services' `LinkPostContext`),
as Threads' card does. Whatever a plugin
uses is part of the plugin API: `tool/plugin_abi.py` checks that plugins
built before still find it. The pieces' words are the app's, in all its
languages: a plugin draws them without strings of its own.
### Posts in Home's timelines

XTA mixes the posts of what a network follows into Following when the
reader turns on its "show in the home timeline" switch (its X timeline, For
you, stays X's alone), and the posts of a group's members of other networks
into that group's feed (`SubscriptionSource`). A plugin offers them with `XtaPlugin.feed`, a
`PluginFeed`:

| Member | What it is |
|---|---|
| `followed()` | the accounts followed in the plugin, as `posts` reads them and its group members name them |
| `posts(accounts, kinds, force)` | the newest posts of `accounts`, newest first, as `NetworkPost`s with their `createdAt` |

`kinds` is a `FeedKinds` (the feed's reply and repost settings, XTA's
`FeedPostKinds`: `takes(isReply, isRepost)`); `force` reads past the plugin's
caches, as a pull does. A plugin reads a page of each account a few at a
time, keeps only a few posts (Reddit ten of each community, Bluesky ten in
all, as XTA), lets an account that fails add nothing and throws only when
none answered, and reads through the same pages and caches as its own tab:
`FeedReads.each(accounts, FeedReads.ACCOUNTS_AT_ONCE) { account -> … }` reads them so, four at a time.

The switch is the app's setting `plugin.<id>.in_home_feed`, where XTA keeps
it (`:store`'s `NetworkPrefs.inHomeFeed`): `PluginSettings.inHomeFeed()` reads
it, and `HomeTimelineSwitch(host.settings, name)` is the row a plugin's
settings show for it, in the app's words, or
`HomeTimelineSwitch(host.settings, title, description)` in the network's own,
as each of XTA's networks words it ("Mix followed feeds into Following").
Off until the reader turns it on.

The app does the rest, as XTA does (`ui/feed`'s `FeedSources`, `PluginPosts`,
`FeedMix`):

- **Who is asked**: on Following, every running plugin with a feed whose
  switch is on, for its followed accounts; on a group's feed (its screen, its
  page on Home's bar and its deck column), each plugin for its members in the
  group, its nested groups and those read alongside, all with the feed's own
  reply and repost settings.
- **Its place**: a post goes above the first X thread older than it (a
  thread sits at its newest post); one older than every thread read so far
  rides at the end until paging reaches it. With nothing from X, or X failing,
  the plugins' posts are shown alone under why.
- **What is new**: what a plugin answers while the reader is down the list
  is held, counted in the new-posts pill, and shown when they come back to
  the top or tap the pill, so nothing moves under their eyes.
- **Failures**: each plugin is read on its own; one that fails says so in a
  pill naming it, with a retry, and the others' posts stay. A plugin that is
  uninstalled, switched off, or loses its members leaves with its posts.
- **Reading again**: a pull or the refresh button reads every plugin past its
  caches; the connection coming back reads the ones that failed.

A post is drawn with the shared row, the plugin's mark on its avatar and
what `postExtras` adds to it, and leads where `postActions` says (its link
otherwise); or, where the plugin draws its own card (`postCard`, as XTA's
timelines draw RSS's, the web sources', Reddit's, Threads' and Substack's
cards), as that card with the plugin's colour down its side, XTA's
provenance strip.

### Accounts in the app's groups

XTA's groups hold other networks' accounts. A group member written
`<plugin id>:<account>` is a plugin's (a saved search with a colon stays a
search); the account is what the plugin's feed reads it by (Bluesky's DID,
a community's name in lower case). `host.addToGroup(account, follow)` opens the
app's group picker (XTA's group membership sheet) for it; the choice applies
as it closes, and `follow` runs first once a group is picked, for the plugin
to follow the account, as XTA's picker does. `AddToGroupButton(host,
account, follow)` is that as a bar's button, and `addToGroupText()` its words
for a button of the plugin's own; the shared post menu offers it for a
post's author. The group's feed reads its members through their
plugins ([above](#posts-in-homes-timelines)), and counts them among its
members; uninstalling a plugin takes its members out of every group.

Wherever a plugin unfollows one of its accounts it says so,
`host.unfollowed(account)`, and the app takes that member out of every
group, as XTA's stores do when they remove an account.

`XtaPlugin.subscriptions` is what Subscriptions lists of it beside X's
accounts and saved searches, in the list's order, search and filters, as
XTA's list shows a network's (`subscription_look.dart`): a
`PluginSubscription` per account, its `id` as its group members name it,
`name`, `handle` (what the search and the order by handle read), the
`subtitle` under its name (`@handle` unless it says another: `r/dartlang`,
`tag:sky`, a feed's site, `PluginSubscription.siteOf`), `avatarUrl` (drawn
with the plugin's mark at its foot, the mark alone without one), `route`
(its own screen a tap opens), `followedAt`, and `unfollow`. The row has
Add to group and its menu (Unsubscribe, Add to group); the group editor
lists the accounts too, and the board's tiles show their faces after X's.

XTA's groups hold Threads, Bluesky, Mastodon, Reddit, Substack and RSS
accounts bare, by the subscription's own id (`:store`'s `NetworkMembers`),
and every other network's as `<plugin>:<id>`. Restoring an XTA backup makes
a bare member that is one of those networks' rows (the backup's, those kept
for a plugin not running, and what the running plugins follow) that
plugin's member, and so does a plugin's held-back rows reaching it later; an
export writes them back bare. Where a plugin reads an account by another
name than XTA keeps, `PluginSubscription.backupId` is XTA's (Bluesky: the
DID read, the handle kept).

### A group's Discover

A group's Discover (XTA's group_discovery_sources.dart) suggests accounts
its members point at that the reader does not follow: X's from what the
group feed kept, the members' latest posts and their Following lists, and
every other network's through its plugin's `XtaPlugin.discovery`, a
`PluginDiscovery` (version 4):

| Member | What it is |
|---|---|
| `around(account)` | what one of its group members (as the group holds it) points at: `PluginDiscovered`s; it throws when that could not be read |
| `perScan` | how many members one open asks about (6; twice that on Scan more) |
| `scope` | what the answers depend on besides the member (the account signed in, the reader's filters): a change asks again |

A `PluginDiscovered` is the account (`id` as its group members name it,
`handle`, `name`, `avatarUrl`, `bio`), its `signal` (`DiscoverySignal`:
reposted, quoted, replied, mentioned, followed, suggested, related) and
`pointedAt`, the post that shows it (`text`, `postUrl`, `postedAt` and
`post`, drawn with the shared row when the reader opens the row), its
`route` (the plugin's screen for it) and `follow` (followed in the plugin,
as adding it to a group does first). The app picks the members, the least
lately asked first, keeps each answer ten minutes, leaves out the group's
members and what the reader follows, merges what every network found and
ranks it as XTA does (the members vouching, the strength of their signals,
the reader's "more / less like this", the newest), its AI ordering it on
"Rank with AI". Bluesky answers the AppView's suggestions for a member, else
what it reposted and quoted; Mastodon what a member boosted and quoted;
Pixiv the creators it relates to a member.

### Saved, notes and likes

The reader's archive is the app's: Saved with its folders and the note on a
post, and the likes. A plugin files a post there as a `PluginPostArchive`:
`id` unique across networks (`bluesky:<uri>`, `reddit:<id>`), `authorId` whose
it is, and `content`, XTA's record of such a post where XTA has one
(`{"xtaPlugin": "bluesky", "post": …}`), else XTA's link record,
`PluginPostArchive.link(source, url, author, text, images)`, so a backup
reads the same in XTA (`PluginPostArchive.ofLink(content, source)` reads one
back, as `archived` does). Nothing reaches the network.

| `PluginHost` | What it does |
|---|---|
| `saved` | the ids in Saved, as they change |
| `savedPosts` | its own posts in Saved, newest first, as they change: those whose record names it (`xtaPlugin`, or a link record's `source`), each a `PluginSavedPost` with the reader's note; null until Saved has been read |
| `toggleSaved(post)` | the bookmark: saved where a plain tap files (the folder remembered), or taken out |
| `chooseFolder(post)` | the save sheet, as a long press on the bookmark opens it |
| `editNote(post)` | the note on it in Saved, in the app's editor; writing one saves the post first |
| `setLiked(post, liked)` | in the archive's likes or out of them: a network's own like mirrored there, as XTA mirrors Reddit's upvotes |
| `likedOnDevice()` | a like the plugin keeps in its own likes, as XTA's Bluesky, Threads and Substack keep theirs: on the reader's first like anywhere, the app says that likes stay on this device, as `setLiked` does by itself |
| `openPostMenu(menu)` | the shared post menu (XTA's plugin post actions): save or remove, the note, Add to group for its author, Share link, Save to folder…, Reposted by and Quotes where the plugin lists them (`PluginPostMenu.reposts`, `quotes`), Open in browser; then the running plugins' entries, which take its `title` ([below](#sending-a-post-to-the-readers-own-server)) |

The shared row does this by itself when a plugin's `NetworkPostActions` say
how: `archive` (a post's record) puts the bookmark at the end of its counts,
a tap saving and a long press opening the save sheet, and `more` is what a
long press on the row opens (`host.openPostMenu`). A plugin that draws its
own footer (`NetworkPostExtras.footer`) puts `NetworkBookmark(archive)` in it,
where XTA's footer has its bookmark, and its `onLongPress` comes before
`more`. The sheets open over the app's screens, one at a time. A plugin
whose own sheet has entries of its own above the shared ones, as XTA's
Pixiv's does, draws those in it with `PluginPostMenuRows(host, menu,
onDone, row)`: each closes the sheet (`onDone`) before it acts, drawn as
`row` draws the sheet's own entries.

Saved draws a plugin's post through its plugin: `XtaPlugin.archived(content)`
reads the record back, and the shared row draws it with the plugin's
`postActions`. A record the plugin does not read, or a plugin that is not
installed, gets Saved's own card, whose link opens as the app opens links,
in the plugin's screen once it is there (`routeFor`, `screen`).

A plugin's own saved list (XTA's plugin bookmarks, a "Saved" in its menu) is
`PluginSavedScreen(host, read) { post -> … }`: Saved's notice that saves stay
on this device, then `savedPosts`, each drawn by the plugin from what `read`
makes of its record (Saved's own card for one it does not read), with its
note under it (a tap edits it) and Keep offline where it has media. A
plugin that lists them its own way reads `savedPosts` itself.

### Downloads and files

| `PluginHost` | What it does |
|---|---|
| `download(media, fileName, subfolder)` | a `MediaItem` saved through the app's downloads, with their notices: a photo, or a video's own file (a stream has none) |
| `downloadAll(files, subfolder)` | `PluginDownload`s (an address, a file name) saved together, as XTA saves a work's pages, as many at a time as the download setting allows, with one notice for them all; answers, once each has ended, whether it was saved |
| `downloadAll(files, subfolder, progress)` | the same, the foot of the window saying which file is on its way in the plugin's words ("Downloading page 2 of 5…") over a ring that fills, with Cancel, which starts no more and cancels those under way; then how many were saved |
| `queueDownloads(files, subfolder)` | `PluginDownload`s queued to run on their own, as XTA's Download all queues a feed's posts, without those the downloads hold or are saving; answers a `PluginQueued` (how many queued, how many left out), or null where the download setting asks where each file goes |
| `saveFile(bytes, fileName, mimeType, subfolder)` | a file the plugin made (an exported animation, a novel's text) saved where the download setting says, with the same notices |
| `saveFileAndWait(bytes, fileName, mimeType, subfolder)` | the same, waited for and without the working notice (the plugin's `withProgress` says what is under way): whether it was saved |
| `shareFile(bytes, fileName, mimeType)` | such a file handed to the share sheet (Android's, through the app's file provider); the desktop has none, so it is saved instead, the notice saying where |
| `openDownloads()` | the app's Downloads screen |

A file is named `<plugin>-<its own name>` unless the plugin names it, as
XTA names a plugin's media; a made file without an extension gets the one
its type stands for. The media viewer a plugin opens (`openMedia`) has the
same Download in its bar.

### Sending a post to the reader's own server

XTA's save targets (Karakeep, Deepmarks, Immich) send what the reader keeps
to the reader's own account: the only writes any plugin makes, and never to
the network a post came from. Two hooks carry them; the app's code names no
plugin.

**The post sheets.** (XTA offers its save targets in an X post's share
sheet; the app's shared plugin post menu offers them too, and a plugin's own
sheet that embeds the shared rows does not, as XTA's Pixiv sheets do not.)
`XtaPlugin.postSheetActions(post)` lists a plugin's
entries for a post, `PluginPostAction`s (an icon, a label in a language, and
`run`), as XTA's share sheet offers "Save to Karakeep". The app asks every
running plugin as a sheet opens and draws their entries in the plugins'
order: in an X post's share sheet (`ui/post`'s `PostShareSheet`), after its
own and before its Cancel, where XTA has them (Deepmarks, then Karakeep), and
below the shared plugin post menu's own (`PluginPostMenu`). The post is a
`PluginSharedPost`: its `url` (an X post's as its Share gives it, a plugin
post's `PluginPostMenu.url`), its `title` (an X post's author and words on
one line, `PluginSharedPost.titleOf`, as XTA titles the bookmark; a plugin
post's `PluginPostMenu.title`, empty when not given) and its `media` (the
archive's `SavedMedia` of the post's record, what downloads and uploads
take). A tap closes the sheet, then the entry runs in the app's scope,
beyond the screen; it says how it went with `host.tell`.

**Saved's folders.** A folder keeps one upload switch, XTA's `auto_upload`
(`SavedFolder.autoUpload`), which XTA's backup carries. The first running
plugin with a `folderOption` (`PluginFolderOption`: a title and a
description in a language) has it: the folder dialog shows the switch under
its own auto-download, in those words, and only while such a plugin runs; a
folder keeps its stored switch either way, the dialog leaving it as it is
when it offers none. When a post is filed in a folder (the bookmark, the
save sheet, a note), the app hands it on after its own downloads, and while
the folder's switch is on that plugin's `filed(post)` runs in the app's
scope with a `PluginFiledPost`: the post's archive id, the folder's name and
the post's `SavedMedia` (every picture, video and GIF of an X post, another
network's pictures), as XTA's filing hands them to Immich. Moving posts
between folders in Saved files nothing, as in XTA.

**Notices.** `host.tell(text)` says a line at the foot of the window over
whatever screen is up, as the app's own notices and XTA's snack bars;
`host.working(text) { … }` says one while the work runs ("Uploading to
Immich…") and takes it away when it ends.

**Settings and secrets.** The server's address, key or token and options
are the app's settings under XTA's keys (`:store`'s `KarakeepPrefs`,
`DeepmarksPrefs`, `ImmichPrefs`), so XTA's backup brings the addresses and
options back. The keys are among `SecretPrefs`, which no export, backup or
log carries and no restore writes; under `plugin.<id>.`, uninstalling erases
them with the rest. The kit draws XTA's settings of such a server:
`PluginAddressField` and `SecretField`, then `PluginConnectionControls` with
a `PluginConnection` (XTA's PluginConnectionStore: Test connection keeps the
fields and then tests them, Save keeps them and closes the screen, and what a
test said goes away as a field changes), and `PluginLinkButton` for "About
…". `PluginSettings.tryUpdate` writes without throwing, `current()` reads
them now, and `answeredOr` turns a request's failure into the plugin's words.

## A plugin module

```
native/plugins/<id>/
  build.gradle.kts        one comment: plugins/build.gradle.kts builds every plugin alike
  plugin.json             {"id": "<id>", "version": "1.0.0", "entry": "<class>", "category": "social", "private": false}
  strings/values/strings.xml       English: plugin_name, plugin_description, the screens' text
  strings/values-de/strings.xml    each other language
  strings/values-be-rXL/strings.xml  Latin Belarusian, filled in by tool/belarusian_latin.py (SCREENS.md)
  src/commonMain/kotlin/com/aimdi/xtanative/networks/<id>/   the reading layer
  src/commonMain/kotlin/com/aimdi/xtanative/plugins/<id>/    the screens and the entry class
  src/desktopTest/kotlin/...      tests (JUnit 5, plain Kotlin), resources in src/desktopTest/resources/<id>/
```

`settings.gradle.kts` includes every folder of `plugins/` with a build file as
`:plugins:<id>`. `plugins/build.gradle.kts` configures each one: the
multiplatform and Compose plugins, the provided modules as `compileOnly`,
`Strings` generated from `strings/` into the entry class's package
(`text("name")`, `plural("name")`), and these tasks:

| Task | Makes |
|---|---|
| `:plugins:<id>:pluginJar` | `build/xta-plugin/desktop/xta-plugin-<id>-<version>.jar`, `xta-plugin.json` at its root, signed with `jarsigner` |
| `:plugins:<id>:pluginApk` | `build/xta-plugin/android/xta-plugin-<id>-<version>.apk`, code only, `xta-plugin.json` in its assets, signed like the app (only where an Android SDK is installed) |
| `:plugins:<id>:pluginEntry` | `build/xta-plugin/<id>.json`, the plugin's catalogue entry (names and descriptions in each language) |
| `:plugins:desktopPlugins`, `:plugins:androidPlugins` | every plugin's file and entry in `plugins/build/out/desktop` or `android` |
| `:plugins:test` | every plugin's `desktopTest` |

Both files are signed with the release key when `KEY_PATH` (with
`KEY_STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) names it, else with the debug
key (`~/.android/debug.keystore`, made by `:signingKey` when missing), as the
app is. `-PpluginBuild=<n>` numbers a build: 1.0.0 becomes 1.0.0.<n>, newer
than every build published before it. A folder without `plugin.json` is a
network whose screens have not come yet: its reading layer builds and is
tested, and nothing is packaged.

## Artifacts and catalogue

CI builds, for each plugin:

| Platform | Artifact | Signed with | Verified by the app |
|---|---|---|---|
| Android | `xta-plugin-<id>-<version>.apk`: code only, never installed, loaded with `DexClassLoader` | the app's signing key | its signing certificates equal the app's own (`PackageManager.getPackageArchiveInfo`) |
| Linux | `xta-plugin-<id>-<version>.jar` | `jarsigner`, the app's key | every entry signed by the certificate whose SHA-256 the desktop build bakes in (`:desktop:pluginCertificate`, from the same key) |

and publishes them with `plugins.json` (the catalogue, made by
`tool/plugin_catalogue.py` from the entries) as assets of a rolling
`native-plugins` release, which is never the latest release:

```json
{"apiVersion": 1, "plugins": [{
  "id": "reddit", "version": "1.0.3", "apiVersion": 1, "available": true,
  "private": false, "category": "social",
  "name": {"en": "Reddit", "de": "Reddit"}, "description": {"en": "..."},
  "android": {"url": "...apk", "sha256": "...", "size": 412345},
  "desktop": {"url": "...jar", "sha256": "...", "size": 398765}
}]}
```

Installing checks the size and SHA-256 from the catalogue, then the
signature; a plugin that fails either is deleted, never loaded. On Android the
file is made read-only before loading (Android 14 requires it). A local build
without the release key bakes in the debug certificate and says so; it loads
only plugins built on the same machine.

## In the app

`:plugin-host` (plain Kotlin) reads the catalogue, downloads, verifies and
keeps plugins under the app's private directory
(`plugins/<id>/<version>/`), knows what is installed, offers updates, and
uninstalls. Loading a class is a platform function it is given (Android:
`DexClassLoader`; desktop: `URLClassLoader`), both with the app's class loader
as parent. A plugin whose `apiVersion` the app does not support is offered as
"needs a newer XTA Native", not loaded.

`AppPlugins` (`:ui`, `data/plugins`) runs them beside the app graph: each shell
makes it with its platform (Android's `ApkSignatureVerifier` and
`DexClassLoaderFactory`, the desktop's `JarSignatureVerifier`), loads what is
installed at start and after each install, casts it to `XtaPlugin`, attaches
its `PluginHost`, and erases what it kept on uninstall (its folder, its place
among Home's timelines and its top bar's pins, its accounts' group memberships). In
the screens:

- the **store** is the route `plugins`, from Settings and the account menu:
  installed plugins first, then what is on offer by category, with install,
  update and uninstall in XTA's words, progress, failures in plain words, the
  show-private switch, each plugin's settings, and its pin on Home;
- a plugin's **home tab** is one of Home's timelines (`LocalHomeNetworks`),
  turned to by a drag or picked in the timeline picker, once the reader pins
  it, in the plugin timelines sheet or the store;
- a **link** goes to a plugin's screen before any other rule
  (`LinkScreens.plugin`, asked by the app's `LinkOpener`);
- its **screens** (client, settings, search, its own routes) are pushed on
  the app's back stack under `plugin/<id>/…`, its media in the viewer under
  `plugin-media/…`;
- its **search** is one of Discover's chips and is offered by the search of
  the reader's library;
- its **backup sections** go in the app's full backup, under
  `pluginSections` by name (`PluginBackups`); a section named by one of
  XTA's network keys (`redditSubscriptions`, `booruSubscriptions`: `:store`'s
  `BackupKeys.NETWORKS`) holds that network's rows in XTA's format and goes
  under its key, where XTA reads it back. A restore hands each to the running
  plugin that keeps it (XTA's rows written over what the plugin keeps, as
  XTA's import writes them), once; one for a plugin that is not running
  waits in `backup/kept.json`, carried by every export, until that plugin
  runs and takes it.

A debug or CI build can read another catalogue: `-PpluginCatalogue=<url>` on
Android, `XTA_NATIVE_PLUGIN_CATALOGUE` on the desktop.

## CI

`native-prototype.yml`:

- **build** tests every plugin (`:plugins:test`) and builds the Android
  plugins, with their sizes and SHA-256 in the summary;
- **desktop** builds the desktop plugins with the certificate the app bakes
  in, then installs the first one from a catalogue the runner serves,
  without a tap: `XTA_NATIVE_PLUGIN_INSTALL`, `_PIN` and `_UNINSTALL` name
  plugins to install, pin on Home and uninstall at start, `XTA_NATIVE_OPEN`
  the screen to open (`plugins`, `plugin:<id>`); a screenshot of each start;
- **screens** builds the APK with `-PpluginCatalogue` naming the runner's
  catalogue and walks the chain on the emulator (`tool/plugin_screens.sh`):
  install from the store, open, pin, Home's timeline picker, uninstall; it fails on a
  crash or a plugin that was not loaded and uninstalled;
- **prerelease** (`[prerelease]`) writes `plugins.json` and uploads it with the
  plugins to the `native-plugins` release (`gh release upload --clobber`),
  creating the release when missing and deleting files no build made.

## Checking without Gradle

Where Gradle cannot reach Maven Central:

- `tool/jvmcheck.sh plugins/<id>` compiles the reading layer and runs its
  tests (and fails on a test JUnit would skip);
- `tool/plugincheck.sh <id>` generates `Strings`, compiles `:ui` and then the
  plugin's commonMain with the Compose compiler (`UI_CLASSES` reuses `:ui`'s
  classes from a `uicheck.sh` run).

Neither builds the Android half; CI does.

## Moving a network into a plugin

A port lands in `networks/src/main/kotlin/com/aimdi/xtanative/networks/<id>/`
first, with its tests under `networks/src/test/`. To make it a plugin:

1. `git mv` the reading layer to
   `plugins/<id>/src/commonMain/kotlin/com/aimdi/xtanative/networks/<id>/`, its
   tests to `plugins/<id>/src/desktopTest/kotlin/com/aimdi/xtanative/networks/<id>/`
   and its fixtures to `plugins/<id>/src/desktopTest/resources/<id>/`. The
   package does not change. Nothing in the app may use it any more: what the
   app shares with every network stays in `networks/` (the package
   `com.aimdi.xtanative.networks` itself).
2. Add `plugins/<id>/build.gradle.kts` (one comment) and, once its screens
   come, `plugin.json` and `strings/values*/strings.xml` with `plugin_name`
   and `plugin_description`.
3. Write the entry class in `com.aimdi.xtanative.plugins.<id>`: it extends
   `XtaPlugin`, keeps what it reads with in `attach`, and draws with the
   shared pieces. Bluesky's (`BlueskyPlugin`) is the example.
4. Check it with `tool/jvmcheck.sh plugins/<id>` and `tool/plugincheck.sh <id>`;
   CI builds, signs and publishes it with the others.

## Release builds

The Android app is shrunk with R8, which would rename what plugins call. The
app keeps, unrenamed, every public class a plugin may use (`:ui` with the
plugin API, its `data` package, `:core`, `:store`, `:services`, the shared
types of `:networks` and the libraries above); a network's own package
(`networks.<id>`) is not kept, so what is a plugin is not in the APK and what
is not yet one goes unless the app uses it. The APK grows by what is kept; it
is measured in CI.
