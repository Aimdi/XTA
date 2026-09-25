# Mastodon reading cards

## Goal

Make posts comfortable and trustworthy to read across timelines, profiles and threads, using established Mastodon client conventions while keeping every action read-only or local.

## Scope

- Replace blur-only sensitive attachments with an explicit reveal control. Hidden attachments are neither built nor fetched. Keep warning text and a hide control after opening; reset reveal state when a card is reused for a different post or warning.
- Manage reveal state with a flutter_triple Store.
- Recognize public HTTP(S) links, mentions and Unicode hashtags in body text. Keep URL fragments, balanced URL punctuation and email addresses intact; punctuation must not become part of a hashtag or account.
- Keep link, mention and tag navigation distinct and accessible.
- Show localized poll totals, open/closed state and end time. Unavailable results stay unavailable; multiple-choice percentages use voters where supplied. Clamp malformed counts safely.
- Give compact engagement controls clear accessible labels even when counts are hidden. Add video and image-description context to the profile media grid.

## Constraints

No posting, voting, authentication changes, dependency changes, shared media rewrite or edits under lib/client or lib/database. Localized UI strings are coordinated with the main Mastodon implementation. Model edits are limited to body text tokenization; poll fields and snapshot compatibility are coordinated separately.

## Verification

Widget tests cover gated media construction, reveal/hide, reused card identity, narrow enlarged text, accessible action labels, poll states and safe link dispatch. Pure tests cover URL/entity boundaries and poll arithmetic. Existing content-warning and Mastodon reading tests must continue to pass.

## Follow-up roadmap

- Native video and audio attachment playback needs a dedicated Mastodon player; the current parser accepts images and GIF previews, and the shared plugin viewer remains an image viewer.
- Named HTML anchor destinations are currently lost during HTML-to-text conversion. A future structured text representation should retain anchor labels and destinations together, without treating arbitrary HTML as trusted widget markup. Explicit visible HTTP(S) URLs are supported by this increment.
