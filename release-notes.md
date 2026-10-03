## XTA — aimdi168

### Profile filters next to what they filter

- On a profile, the Posts, Media and Archive filters are no longer a funnel in the top bar with a drop-down menu. Each tab shows its choices as a row of chips directly above its content: tap one to switch. The selected chip is filled and checked.

Also contains everything from aimdi167: the diagnostic below.

### Finding why saving stops after adding someone to a group

Adding an X account to a group with the "+" on its avatar in the timeline can leave every later save waiting until the app restarts: likes, bookmarks and further group changes. The app's own code does not hang when the same steps run in tests, so the cause is on the phone, and this build records it.

- **Settings → Diagnose** now runs a few local checks, each limited to five seconds so the page always finishes: an Android system call, a database read on each connection, and a save that changes nothing. A stuck step shows up as "still waiting after 5s".
- A **watchdog** watches Android's main thread from launch. If it stops responding for five seconds, the exact place it is stuck is written down and shown under "android main thread".

To help: add someone to a group with the "+" until likes stop working, then open Settings → Diagnose, wait for it to load, copy the report and send it.

### Installation

For most Android phones, use **`xta-aimdi168_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001370**, above
every aimdi167 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi167)
