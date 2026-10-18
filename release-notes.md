## XTA — aimdi178

### Booru: safer sign-in, steadier feeds

This release fixes 17 problems in the Booru plugin.

**Your login and API key**

- **Never shown in error messages.** A failed request used to print them on screen.
- **Sent only to their own site.** A key entered for one site was sent to every site you switched to. Settings now say which site the key belongs to.
- **Sent only as a pair.** A login without its key, or a key without its login, is no longer sent at all.

**Feeds**

- **Changing the site, rating limit or blacklist starts the feeds over**, so posts from two sites no longer mix, and a post's tags, comments and wiki come from its own site.
- **A page that fails to load says so** and offers Retry, instead of asking again every time you scroll. The end of a feed is marked.
- **Following shows its failures.** When none of your tags load you get the error with Retry. When some fail, a notice says so above the rest, instead of "No recent posts".
- **No more "No posts yet" flash.** A feed shows its loading skeleton until the first page arrives.
- **Popular opens on a day the site has already reached**, so it should no longer be empty early in the day in Asia and Australia.
- **Pages that arrive late are dropped**, so a refresh is never mixed with the list it replaced, and a screen you leave stops asking for more.

**Messages and sites**

- **Clearer refusals.** A site that refuses guests, or puts a browser check in front of apps, now says so. A busy site asks you to try again later.
- **"Updated", not "Posted".** On Safebooru, Rule34 and similar sites, the only date sent is when a post last changed, and it is now labelled that way.
- **hypnohub.net** is added as a Gelbooru 0.2 site, not Moebooru.

**Downloads and videos**

- **Readable file names.** Downloads no longer contain `%20` and the like, and long names keep their beginning, where sites put their name and the post number.
- **Danbooru animations** download as a playable video, not a zip of frames.
- **Videos wait for a tap** when "Load media only on tap" is on.

Everything else is as in aimdi177.

### Licence note

The downloadable voices use sherpa-onnx, whose library includes espeak-ng under GPL-3.0. Its licence text is in Settings → About → Licences. XTA's own source code stays MIT.

### Installation

For most Android phones, use **`xta-aimdi178_arm64-v8a.apk`**. Universal, ARMv7 and x86_64 APKs are also supplied. The base version code is **400001470**, above every aimdi177 variant. The app ID (`com.aimdi.xta`) and release signing identity are unchanged for in-place updates.

For Linux, use the Arch package (`sudo pacman -U xta-bin-4.12.0.178-1-x86_64.pkg.tar.zst`), the attached `PKGBUILD` (`makepkg -si`) or the portable tarball. **`SHA256SUMS-linux`** lists their checksums. See [docs/desktop.md](https://github.com/Aimdi/XTA/blob/claude/main/docs/desktop.md).

The release workflow checks translations, skill synchronization, analysis and the full Flutter test suite on the tagged source before building. **`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi177)
