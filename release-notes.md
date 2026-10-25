## XTA — aimdi184

### Motion, as in X's new app

- **Counts roll.** When X sends newer numbers, an opened post's views, replies, reposts and likes roll to them: the old number slides up and out as the new one comes in. A refreshed profile's Following and Followers roll too, and so does the media viewer's "1 / 4" as you swipe.
- **The profile bar takes the banner.** Once a profile's header has scrolled away, the top bar shows its banner, blurred and darkened, behind the name. Scrolling back to the top clears it.
- **A dragged row lifts.** Reordering subscriptions, groups, home tabs, saved folders, Pixiv tags or Booru prompts, the row you hold grows a little and floats on a shadow while the rows around it make room.
- **A stock's price line draws itself in** from left to right when its chart opens or you pick another range.

### Things arrive instead of popping in

- **Images fade in.** Avatars, card images, link previews, video posters, the media grid, poll options, the profile banner and the faces on group boards wait on a quiet surface and fade in once fetched, instead of a spinner. One already loaded shows at once.
- **Opening a post** fades its replies and the reply order bar in where the placeholders were, and the post you opened stays still. An opened post's poll bars fill in to their shares.
- **First pages fade in.** A feed's first page, a profile's posts and a profile itself fade in over their placeholders. Posts that replace cached ones already on screen do not blink.
- **Notices drop in and lift away**, and "New posts" floats over the feed, above its source chips, without moving the posts.
- **Translate eases.** The translation fades in while the post eases to its new height, so the media and footer below glide instead of jumping. Show original does the same, and a post opened with "Show more" stays open.
- **What opens inside a feed eases to its new height:** price chips, "more posts with this link", folded posts, repost runs and a revealed sensitive post. "Followed by" on a profile grows in.
- **Smaller touches.** Saving pops the bookmark. The profile's scroll-to-top button grows in and shrinks away. The video mute icon cross-fades. A tapped bottom-bar tab fades its page in. The line over a loading feed fades in and out, glides with a group's progress and fills before it goes.

With reduced motion, XTA's own setting or Android's "remove animations", all of it happens at once.

### Haptic feedback

- **Liking and saving tap lightly.** Unliking and unsaving do not, and calm and zen mode keep it quiet.
- **Settings → Accessibility → Haptic feedback** turns off every tap XTA gives: liking and saving, following from an avatar, the folder sheet, page swipes, the home filter and Pixiv's. It is on by default. Android's own buzz on a long press follows Android's touch feedback setting.

### Fixes

- **The Subscriptions list stays where you had it** when you unsubscribe someone or put them in or out of the main feed, instead of flashing to a spinner and jumping back to the top.

Everything else is as in aimdi183.

### Licence note

The downloadable voices use sherpa-onnx, whose library includes espeak-ng under GPL-3.0. The accents on mathematical letters come from a subset of Noto Sans Math under the SIL Open Font License. Both licence texts are in Settings → About → Licences. XTA's own source code stays MIT.

### Installation

For most Android phones, use **`xta-aimdi184_arm64-v8a.apk`**. Universal, ARMv7 and x86_64 APKs are also supplied. The base version code is **400001530**, above every aimdi183 variant. The app ID (`com.aimdi.xta`) and release signing identity are unchanged for in-place updates.

For Linux, use the Arch package (`sudo pacman -U xta-bin-4.12.0.184-1-x86_64.pkg.tar.zst`), the attached `PKGBUILD` (`makepkg -si`) or the portable tarball. **`SHA256SUMS-linux`** lists their checksums. See [docs/desktop.md](https://github.com/Aimdi/XTA/blob/claude/main/docs/desktop.md).

The release workflow checks translations, skill synchronization, analysis and the full Flutter test suite on the tagged source before building. **`release-build.json`** and **`SHA256SUMS`** accompany the APKs, and **`xta-aimdi184-debug-symbols.zip`** and **`xta-aimdi184-linux-debug-symbols.zip`** hold the debug symbols for reading crash traces.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi183)
