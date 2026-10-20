## XTA — aimdi180

### Booru: fixed, faster and more complete

**Fixes**

- **Unfollowing a Booru tag no longer removes a subreddit or saved search with the same name** from your groups.
- **Posts a site sends without a file** (deleted, restricted for guests, Flash) are left out, and a feed whose first pages your rating limit or muted tags hid entirely offers **Load more** instead of ending.
- **Gelbooru and its forks get the rating limit in the form they understand.** GIFs play, tag names such as `girls'_frontline` read correctly, and Gelbooru 0.2 forks' tag suggestions work.
- **Clearer messages** when a site needs a login and API key, or a search goes past Danbooru's tag limit. A feed the site says is too deep now ends instead of failing on every scroll.

**Faster**

- **Requests are paced per site** and back off after a rate limit or a server error.
- **Danbooru tiles load the smallest picture that fills them**, Latest and Popular ask for their first page at once, and the viewer loads the next and previous pictures ahead.
- **Pictures cached more than 30 days ago are removed.** Settings → Data shows the image cache's size, with a Clear button.

**New**

- **Tap a tag to search it**, long-press for its actions. Artist, character and series tags open their own page. Danbooru pools open in pool order, and Danbooru and e621 translation notes show over the picture.
- **Copy tags as a prompt**, in the order you choose.
- **Blacklist rules** can use `score:`, `id:`, `width:`, `height:`, `favcount:`, `tagcount:`, `user:`, `filetype:`, `md5:`, `parent:` and `source:`. A feed says how many posts the blacklist hid, and **Show** puts them back.
- **Metatag suggestions** as you type, and Danbooru's and e621's related tags over search results.
- **Download all** on search results, tag pages and pools, skipping files already downloaded, with file name templates.
- **Each site keeps its own rating cap, blacklist, login and key.** Raising the cap past Sensitive asks first.
- **Following marks the posts new since your last visit.** Latest and Following open with their last page when there is no network.
- **Links to booru posts open in the Booru viewer.**

### Errors and loading no longer move what you are reading

- **Loading is a thin line** over the top of what is loading, instead of a bar or a spinner that pushed everything down.
- **A failure while the posts are still on screen floats** on a small pill over them.
- **A page that fails at the end of a list is one row**: Retry, with the reason behind ⓘ, instead of a large error block.
- **Empty footers take no space**, and a notice retrying on its own stays one row while it counts down.
- **Ticker pages keep their price and chart** while a new range loads.
- **RSS and Booru show "some feeds failed" as a warning in their chip row**, not as a row above the posts.

### Smaller and more private

- **About 6 MB smaller APKs** (arm64: 83.2 MB instead of 89.3 MB), and a smaller installed app. Debug symbols are published beside the release instead of shipped in it, and two native libraries nothing loads are left out.
- **On Android 12 and later, setting up a new phone from the old one no longer copies XTA's data**, including your accounts. Cloud backup was already off. XTA's own export remains the way to move your setup.

Everything else is as in aimdi179.

### Licence note

The downloadable voices use sherpa-onnx, whose library includes espeak-ng under GPL-3.0. Its licence text is in Settings → About → Licences. XTA's own source code stays MIT.

### Installation

For most Android phones, use **`xta-aimdi180_arm64-v8a.apk`**. Universal, ARMv7 and x86_64 APKs are also supplied. The base version code is **400001490**, above every aimdi179 variant. The app ID (`com.aimdi.xta`) and release signing identity are unchanged for in-place updates.

For Linux, use the Arch package (`sudo pacman -U xta-bin-4.12.0.180-1-x86_64.pkg.tar.zst`), the attached `PKGBUILD` (`makepkg -si`) or the portable tarball. **`SHA256SUMS-linux`** lists their checksums. See [docs/desktop.md](https://github.com/Aimdi/XTA/blob/claude/main/docs/desktop.md).

The release workflow checks translations, skill synchronization, analysis and the full Flutter test suite on the tagged source before building. **`release-build.json`** and **`SHA256SUMS`** accompany the APKs, and **`xta-aimdi180-debug-symbols.zip`** and **`xta-aimdi180-linux-debug-symbols.zip`** hold the debug symbols for reading crash traces.

[Previous release and notes](https://github.com/Aimdi/XTA/releases/tag/aimdi179)
