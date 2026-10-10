## XTA — aimdi172

### The bottom bar gets out of the way while you read

- Scroll down and the floating bar slides off the bottom edge; scroll up a little and it comes back. It returns on its own at the top and at the end of a list and whenever you switch tabs. It does not come back just because you stop — a bar that pops up every time the finger lifts is in the way while reading.
- It hides only after about 24 dp of downward scrolling and returns after 12 dp upward, so small wobbles while reading don't make it flap and a short upward flick is enough. The slide takes 180 ms with the app's usual easing; nothing in the list moves when it goes (the space under it stays reserved), and taps go through where it was.
- With "Remove animations" it jumps instead of sliding; with TalkBack it never hides. Settings → Theme → "Hide bottom bar while scrolling" turns it off.

Everything from aimdi171 is included: the first-launch cards and the Discover rework.

### Installation

For most Android phones, use **`xta-aimdi172_arm64-v8a.apk`**. Universal, ARMv7
and x86_64 APKs are also supplied. The base version code is **400001410**, above
every aimdi171 variant. The app ID (`com.aimdi.xta`) and release signing identity
are unchanged for in-place updates.

The release workflow checks translations, skill synchronization, analysis and
the full Flutter test suite on the tagged source before building.
**`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi171)
