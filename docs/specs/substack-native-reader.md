# Substack native reader (proposal)

Status: **in progress.** The owner said to continue with Tier 2 (2026-10-17). Done:

1. the block model (`substack_blocks.dart`);
2. the native view (`substack_native_article.dart`) behind the setting *Native reader (preview)* in the new Substack
   settings screen, off by default. With it on, the reader shows blocks instead of the web view. Widget-tested,
   including 200 % text at 320 px.

3. a footnote marker opens its note in a bottom sheet; an image opens the app's shared full-screen viewer with every
   image of the post. A loading or failed image is a plain box, so a tap always reaches the viewer.

4. the place is kept by block and offset with the Pixiv reader's own code, now shared as `ArticleBlockReading`
   (`lib/reading/`): put back on open, kept across a refresh and a text size change, saved on leave. Contents and
   find are back in native mode: each block carries the anchors of the passages drawn in it
   (`substackBlockOfPassage`), and a passage folded into a neighbour (a table cell, a caption) leads to that block.

   Known gap: the web view and the native view count passages differently, so a place saved by one and put back by
   the other lands by its scroll fraction, near but not on the passage. Only someone switching the preview setting
   mid-article meets it.

Left: offline images from files (step 5), then the device gates below.

## Why

The reader renders each post in a WebView. That costs:

- a second rendering engine per open, which only a device can time (S2, S5; UNVERIFIED here);
- text selection traded against read-aloud on long-press (BUG-12, waiting on a decision);
- no footnote sheets, image viewer, highlights or sentence highlighting, each of which would have to be written in
  page JavaScript and bridged back;
- the offline page inlining every image as base64 (S7: 115 KB becomes 8.27 MB), because the page cannot reach files.

The Pixiv novel reader already reads long text natively: a parse into blocks, a lazy `SliverList.builder` inside a
`SelectionArea`, position save and restore by block index (`pixiv_novel_reader_position.dart`), and pictures as their
own blocks (`pixiv_novel_reader_blocks.dart`). The Substack reader would follow it, not copy it: shared parts move to
`lib/reading/` first, one commit each.

## Shape

- **Block model.** `SubstackArticleDocument.parse` (one parse, off the UI isolate, already in place) also yields
  blocks: heading, paragraph (inline runs: text, bold, italic, code, link, footnote marker), quote, list item, code
  block, image (URL, size hints, caption), embed (host, held iframe), divider, table (rows of plain text), unknown
  (raw sanitized HTML, shown in a small WebView on tap). Passages keep their anchors, so navigation and search stay.
- **View.** `CustomScrollView` with a `SliverList.builder` of blocks inside a `SelectionArea`. Text uses the reading
  settings already in `ArticleReadingStore` (size, line height). Images go through the existing cached image widget
  with a decode width cap.
- **Interactions.** Long-press selects text (BUG-12 resolved by construction); read-aloud from a paragraph moves to
  the paragraph's menu. A footnote marker opens its note in a bottom sheet. An image opens the existing media viewer.
  A link opens in-app when it is a Substack post, else the browser.
- **Position.** Saved and restored by block index and offset, like Pixiv; the existing journal key stays.
- **Paywall.** Unchanged: the free part only, the end-of-preview note and the paid pane (BUG-11).
- **Offline.** Kept images are read from their files by the image widget; no base64 page.
- **Fallback.** A body the parser cannot model above a threshold of unknown blocks opens in the current WebView
  reader, which stays as the fallback path during rollout.

## Gates

- Characterization first: block-model tests over the reconstructed constructs already used by the probes, then a
  recorded post set once a HAR exists (BUG-07/08 need one too).
- Device: S2 (first paragraph, fling jank), S5 (memory over ten posts) and S7 measured against the WebView reader on
  the same phone before the switch. Until then the native reader sits behind a setting, off by default.
- Accessibility: semantics for every block type, 200 % text, TalkBack order, 48 dp targets.

## Cost

The largest change in the plan (audit score 10: impact 5, effort 1, safety 2). Expected in at least five commits:
shared reading parts, block model, view, interactions, offline images. New UI strings (footnote sheet title, "open
as web page") go through l10n in all locales.
