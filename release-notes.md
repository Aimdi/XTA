## XTA — aimdi171

### First-launch cards

- A fresh install now opens on five short cards instead of an empty Home with a dialog over it: what XTA is (a reader that only reads), that subscriptions, groups, likes and saved posts stay on the phone, a look and accent colour to pick (the whole intro re-themes as you tap), an optional X account (with "Not now" and "Import backup"), and the sources to put on Home, each with a one-tap tile. Each card has its own illustration drawn from XTA's mark, the source logos and its placeholder posts, so it looks right in light, dim and lights-out.
- Skip is on every card; Back steps to the previous card and, on the first one, asks before closing as Home does. Existing installs never see the cards; Settings → About → "Show intro again" replays them.

### Discover in groups finds far more, and says why

- Discover used to read only 6 of a group's members, always the same six, and ranked by words in the group's name, so a group called "🍫" got a list sorted by date. It now mines the posts the group feed has already loaded for every member (no extra requests), reads a rotating sample of members on each refresh, and adds accounts the members follow (X), Bluesky's own "suggested for" and Pixiv's related creators. "Scan more" reads the next batch; the header shows "37 of 149 members".
- Accounts are ranked by how many members point at them, and each row says why: "Reposted by @a, @b and 3 more", "Followed by 4 members", "Quoted by …", "Bluesky suggests this for @member". Rows are compact (about five per screen) with a 2-line snippet that expands to the full post, a one-tap "Add to this group" button (other memberships are kept, undo in the snackbar), and a menu with More/Less like this, Hide, Other groups, Open profile and Open post.
- Results appear as each source answers; a slow or failing source no longer throws away the others and is named with a retry of its own. While Discover is open the chip row shows only "Discover ✕", Back returns to the feed, and leaving Discover no longer rewrites the group's order. The AI sparkle re-ranks the loaded list instead of fetching everything again.

Everything from aimdi170 is included: the group-add freeze fix, the compact group header, the frosted bottom bar, the comment bubbles, the Pixiv pages, the booru search, the automatic X retry and the Mastodon & Bluesky section.

### Installation

For most Android phones, use **`xta-aimdi171_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001400**, above
every aimdi170 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi170)
