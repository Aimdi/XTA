## XTA — aimdi170

### Compact group header

- A group's member count no longer takes a row of its own under the title. The group's mark, its name and the count now share the top bar, so every group timeline starts higher up the screen.
- The Recent / Popular / Custom / Discover / Media row is slimmer and loses its doubled divider line; every chip keeps a full-size touch target.
- On a pushed group, the whole title (mark, name and count) opens the group switcher.

### X and Reddit share the compact Home header

- In Home, X and Reddit now open with the same single row as Pixiv, Substack and Hacker News: the service logo (tap it to switch source), Reddit's Following / Popular / All, search, and the options button. Everything else (X's refresh, loaded-post search, account filter, Subscriptions, Saved, Accounts; Reddit's sort, communities, saved, reading route and settings) is in the options sheet. Following is unchanged.
- The options button shows a dot while an X account or group is filtered out of Home.
- Home's Reddit tab now opens on the section you last chose (Following, Popular, All or a community) instead of always the merged Following list, and changing Reddit's sort or reading route in Home now refetches straight away instead of up to ten minutes later.

### X tries again by itself

- When something on X can't be loaded (a profile's posts, a timeline, followers, retweeters), XTA now tries again on its own, waiting a little longer each time (2, 5, 15, 30, 60 seconds) and showing "Versuche es in 5 s erneut …" with the manual retry and ⓘ still beside it. After a rate limit it tries once the limit resets. Nothing retries while you are offline, in another app or on another screen, and a failing retry can't loop.
- Before, only connection drops and timeouts were retried (three times); errors like the one on a profile's posts tab never were. Sign-in problems and private, suspended or missing accounts still wait for you.

### Messages leave sooner

- Error and info messages at the bottom now leave after 3 seconds instead of 4, and a new one replaces the one on screen instead of waiting its turn, so a burst of errors no longer holds the bottom of the screen.
- "Changes saved – Undo" no longer stays until you tap it: it leaves after 5 seconds. With TalkBack on it still waits for you. Download progress still stays until the download ends.

### Booru: tag search that keeps your searches

- Searches are saved again, however you start them, and recent searches come back whenever you tap the search field, even over results. Each entry runs again with one tap and can be removed on its own (✕ or swipe).
- Several tags at once: each tag becomes a chip. Space finishes a tag, a suggestion is added instead of replacing what you typed, backspace removes the last chip and tapping a chip edits it. Quick buttons add Exclude (-), Or (~), rating and sort-by-score; "Edit as text" shows the whole query.
- Suggestions show the tag's category colour and post count.
- The star saves the whole search (not just its last tag); saved searches sit above the history and feed the Following tab.
- Related tags from the loaded posts appear above the results (tap to add, hold to exclude), and a post opened from search can add its tags to that search.

### Comments in coloured bubbles

- Reddit and Hacker News comments each sit in their own rounded, tinted bubble. The colour follows the reply depth (blue, violet, green, amber, pink, orange, then again), so a reply is easy to tell from the comment it answers; the thin lines at the left are gone. Text keeps full contrast on every tint, in light, dim and true-black themes.
- Tapping anywhere on a Reddit comment now folds it; the avatar opens the profile like the name does. Reddit's "more replies" and deleted comments are outlined instead of filled.

### Telegram-style bottom bar

- The bottom bar is a frosted, slightly see-through pill floating above the page, like Telegram's: posts scroll on behind it and show through faintly. The selected tab sits on a soft tinted highlight that covers its icon and label and slides when you switch.
- The bar shows icons only and is 48dp tall, a single touch target's height. With Settings → Theme → Show navigation labels it grows to 54dp to fit them.
- The last post of every list, plugin feeds included, scrolls fully clear of the bar, and Trends' and Saved's floating buttons stay above it.

### Mastodon & Bluesky

- The "Microblogs" section is now called "Mastodon & Bluesky", with a logo of the two marks stacked: Bluesky's butterfly at the top left, Mastodon's mark at the bottom right. Threads stays in the section, it just isn't named.

Everything from aimdi169 is included: the new icon, the profile filter chips and the diagnostic for saves that stop after adding someone to a group.

### Installation

For most Android phones, use **`xta-aimdi170_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001390**, above
every aimdi169 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi169)
