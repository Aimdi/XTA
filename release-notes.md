## XTA — aimdi186

### Swipe back from a post

- **Drag a post away to go back.** An opened post slides in over the page you came from, which shrinks and darkens behind it. Drag the post to the right and it lifts off as a card that turns around your finger and swings with your speed. Let go past about a third of the screen, or throw it, and it flies off; let go sooner and it drops back into place.
- **Android's back gesture moves the card too**, from either edge of the screen.
- **Two switches in Settings → Accessibility**: "Swipe back from anywhere" (off: only from the edge of the screen) and "Tilt with your finger".
- A post's row of photos and a video's seek bar keep their own swipes. With reduced motion the post appears and leaves at once, and a drag moves it flat.

### Choosing groups and folders

- **Add to group is a quick pick.** Groups are chips with their marks, the account's own first, then the ones you picked last. "Search or create" finds a group or makes one ("Create “Japan” and add"). A line at the foot says where the account is, and the choice applies as the sheet closes: there is no Save, and closing it unchanged follows no one.
- **Save to folder works the same way.** A long press on the bookmark shows the folders as chips: a tap files the post at once, "Create “Japan” and save" makes a folder from the search, and the foot says where the post is. Remember this folder stays.

### Sort ungrouped, one by one

- **Accounts Sort ungrouped could not place can be sorted one at a time.** A card per account with Open profile and how far along you are; tap a group to place it and move on, or use Several groups, Create group, Skip and Undo. Every choice is saved as you make it, and after Apply the screen goes on to the accounts left over.

### Pixiv profile

- **The header in the "Kanten" style.** Follow spans its own row, filled until you follow and outlined after, with Add to group beside it; the Premium badge is outlined, counts lead with the number in bold, and a line sits above the tabs.

Everything else is as in aimdi185.

### Licence note

The downloadable voices use sherpa-onnx, whose library includes espeak-ng under GPL-3.0. The accents on mathematical letters come from a subset of Noto Sans Math under the SIL Open Font License. Both licence texts are in Settings → About → Licences. XTA's own source code stays MIT.

### Installation

For most Android phones, use **`xta-aimdi186_arm64-v8a.apk`**. Universal, ARMv7 and x86_64 APKs are also supplied. The base version code is **400001550**, above every aimdi185 variant. The app ID (`com.aimdi.xta`) and release signing identity are unchanged for in-place updates.

For Linux, use the Arch package (`sudo pacman -U xta-bin-4.12.0.186-1-x86_64.pkg.tar.zst`), the attached `PKGBUILD` (`makepkg -si`) or the portable tarball. **`SHA256SUMS-linux`** lists their checksums. See [docs/desktop.md](https://github.com/Aimdi/XTA/blob/claude/main/docs/desktop.md).

The release workflow checks translations, skill synchronization, analysis and the full Flutter test suite on the tagged source before building. **`release-build.json`** and **`SHA256SUMS`** accompany the APKs, and **`xta-aimdi186-debug-symbols.zip`** and **`xta-aimdi186-linux-debug-symbols.zip`** hold the debug symbols for reading crash traces.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi185)
