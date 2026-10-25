## XTA — aimdi183

### Abos and Gruppen take less room

- **One header row.** The Gruppen | Abos switch now stands where the title was, beside search, add and the options. The search field is a 🔍 in that row: it filters the list you are looking at, its hint says which one, and Back closes it. When a narrow screen, a long translation or large text leaves too little room, the switch first drops its icons, and only when even the names do not fit does the screen go back to two rows. The first group now starts 86 points from the top of the screen instead of about 212.
- **Kompakt group tiles.** ⚙ → Spalten offers Kompakt beside 2 and 3: each group becomes a slim tile with its faces beside its name and count, 60 points tall instead of 148, so more than twice as many groups fit on the screen. The tile keeps the group's colour, the pin and NSFW marks, the NSFW blur, the unread dot and the ✨ discovery button, and it grows with large text.
- **Dragging a subscription to reorder it works again.** The drag failed as soon as it began.

### X communities

- **A community link opens the community** instead of "unable to open link": its banner, name, topic and members, a Popular tab with its posts, and an About tab with its rules and creation date. The member count opens the list of members.
- **Search has a Communities tab**, and a post written in a community carries a chip that opens it.
- Without an X account only the page itself loads; its posts, its members and the Communities search need one.

### Groups

- **Saved searches in a group mean what they mean in search.** A saved search such as `from:nasa` or `list:123` was sent as a quoted phrase, so the group found nothing for it.
- **Reply and repost filters cover every member.** The members now go to X as one bracketed list, so a group that hides reposts no longer shows some.
- **About a third fewer searches per refresh.** Each search now holds as many members as X's 512-character limit allows, instead of 16, so a group runs into X's rate limit later. Each group loads its first page afresh the next time it opens.
- **Posts from outside a group are flagged in a pill, not a dialog.** The check now compares accounts, not their names, so an account that renamed itself no longer sets it off, and saved searches are left out of it. Its info link works again.

### X search

- **More filters.** The filters screen can pick a language, show only replies or hide them, show only posts with links or hide them, and bring reposts into the results.
- **Typed filters show up.** Operators typed into the search bar, such as `lang:de` or `-filter:replies`, now appear as chips and in the filters screen.

### Posts and profiles

- **Polls with images show.** Each option's image sits at the start of its bar and opens in the media viewer.
- **Long Grok questions unfold** with "Show more" instead of stopping after three lines, and a post no longer leaves an empty line above its Grok card.
- **Bold and italic Unicode letters keep their accents.** Posts written in 𝐛𝐨𝐥𝐝 or 𝑖𝑡𝑎𝑙𝑖𝑐 letters showed their accents as boxes.
- **A protected profile says its posts are protected**, "Only approved followers on X can see @handle's posts", instead of loading empty tabs. Your own protected account still shows its posts.
- **A profile X says does not exist says "User not found"**, and a suspended one says it is suspended, instead of "Oops! Something went wrong" and Retry.

### Fixes

- **Esperanto no longer breaks dates and numbers.** With XTA in Esperanto, an opened post, a profile, the trends list and absolute timestamps failed with "Invalid locale".
- **Afternoon times are stored correctly.** A subscription, group or saved post added at 13:37 was stored as 01:37 and sorted as a morning one. Times stored before this release cannot be repaired: the AM or PM was never saved.
- **Videos have sound on the Linux desktop.** XTA chose Android's audio output there too.
- **A video no longer plays on unseen after full screen.** When its post was scrolled away or the screen rotated during full screen, the player kept running and held one of the three video slots until the app restarted; after three such videos, new ones stayed on their poster.
- **A home tab you just turned on is highlighted when you tap it**, and Discover opened that way searches the network you came from.
- **A group no longer starts a request in the last millisecond of its time budget**, only to have it time out at once.

### Accessibility

- **Every icon-only button has a name** for TalkBack: scroll to top on profiles, export, import, add a location, the group icon picker, and the instance, secret and search buttons of the Mastodon, Threads, Bluesky and Instagram plugins.
- **Each settings switch is read together with its title** and state, as one control, instead of "On, switch".

### Behind the scenes

- A pull request that touches XTA's profile, post or community screens now runs them on an Android emulator against a stand-in for X that answers from recorded responses: a profile, a post, an account that does not exist and a community.
- When the daily live checks find that X changed its request signing, a workflow can ask Claude for a fix as a draft pull request for review. It stays off until a key for it is set.

Everything else is as in aimdi182.

### Licence note

The downloadable voices use sherpa-onnx, whose library includes espeak-ng under GPL-3.0. The accents on mathematical letters come from a subset of Noto Sans Math under the SIL Open Font License. Both licence texts are in Settings → About → Licences. XTA's own source code stays MIT.

### Installation

For most Android phones, use **`xta-aimdi183_arm64-v8a.apk`**. Universal, ARMv7 and x86_64 APKs are also supplied. The base version code is **400001520**, above every aimdi182 variant. The app ID (`com.aimdi.xta`) and release signing identity are unchanged for in-place updates.

For Linux, use the Arch package (`sudo pacman -U xta-bin-4.12.0.183-1-x86_64.pkg.tar.zst`), the attached `PKGBUILD` (`makepkg -si`) or the portable tarball. **`SHA256SUMS-linux`** lists their checksums. See [docs/desktop.md](https://github.com/Aimdi/XTA/blob/claude/main/docs/desktop.md).

The release workflow checks translations, skill synchronization, analysis and the full Flutter test suite on the tagged source before building. **`release-build.json`** and **`SHA256SUMS`** accompany the APKs, and **`xta-aimdi183-debug-symbols.zip`** and **`xta-aimdi183-linux-debug-symbols.zip`** hold the debug symbols for reading crash traces.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi182)
