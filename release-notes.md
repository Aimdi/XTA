## XTA — aimdi179

### Substack: steadier feeds, faster articles, more to read with

**Feeds that load, and stay yours**

- **Every publication you follow is refreshed**, not only the first 24.
- **A rate limit is said plainly** and waits before asking again, instead of trying every fallback and failing.
- **A custom domain that does not answer no longer shows another publication's posts.**
- **One post is one post**, whether it came from the API or the RSS feed, for read state, likes, saves and offline copies.
- **Following is never overwritten** by a publication found while browsing.
- **The feed opens at once** with what it showed last time, and asks again only for publications that are out of date.

**Articles**

- **Opening an article is faster**: it is read once, off the screen's thread when it is long, and the last 16 opened stay ready.
- **Paid posts show their free part** with the paid badge. A paid post without one shows its preview, not the publisher's page.
- **Embedded players load when you tap them**, so nothing contacts YouTube or Spotify until then.
- **Text can be selected and copied.**
- **Replies nested very deep are kept**, and forms and blocks that only work on the web are replaced by a note with a link.
- **New settings for the article font (serif or sans) and the column width.**
- **Read time** is shown on cards and at the top of articles.

**Native reader (preview, off by default)**

In Substack's settings (Settings → Plugin store, then the settings button on Substack), *Native reader (preview)* shows articles without a web view. Footnotes open in a sheet and images in the full-screen viewer. Your place is kept, and contents and find work. Saved posts read their images from the device. You can highlight passages and share them as Markdown.

**Library, podcasts and links**

- **Paid and Saved feed filters.**
- **Library search looks inside the text** of posts kept on the device, and shows the matching passage.
- **Podcast speed** from 0.8× to 2×, and **each episode resumes where you left it**.
- **Mark posts read as you scroll past**, an option, off by default.
- **Export and import your follows as OPML.**
- **Links to Substack posts on their own domains open in the app**, after a short check. Other links still open in the browser.

Everything else is as in aimdi178.

### Licence note

The downloadable voices use sherpa-onnx, whose library includes espeak-ng under GPL-3.0. Its licence text is in Settings → About → Licences. XTA's own source code stays MIT.

### Installation

For most Android phones, use **`xta-aimdi179_arm64-v8a.apk`**. Universal, ARMv7 and x86_64 APKs are also supplied. The base version code is **400001480**, above every aimdi178 variant. The app ID (`com.aimdi.xta`) and release signing identity are unchanged for in-place updates.

For Linux, use the Arch package (`sudo pacman -U xta-bin-4.12.0.179-1-x86_64.pkg.tar.zst`), the attached `PKGBUILD` (`makepkg -si`) or the portable tarball. **`SHA256SUMS-linux`** lists their checksums. See [docs/desktop.md](https://github.com/Aimdi/XTA/blob/claude/main/docs/desktop.md).

The release workflow checks translations, skill synchronization, analysis and the full Flutter test suite on the tagged source before building. **`release-build.json`** and **`SHA256SUMS`** accompany the APKs.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi178)
