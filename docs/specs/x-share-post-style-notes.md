# X sharing and post-style local notes

Base: Aimdi132, `d167568b` on `claude/main`.

## Share links

Register XTA for Android `ACTION_SEND` / `text/plain`. Receive plain shared
text at cold launch and while the activity is running, including links mixed
with a post caption. Queue native events until Flutter subscribes; consume
each intent once. Extract only HTTP(S) Twitter/X or supported mirror URLs,
reject deceptive hosts and non-web schemes, and use existing in-app navigation.
Unsupported shares show the existing localized link error. Never launch nested
intents or dereference shared content URIs. Do not add dependencies.

## Notes

Use a full-screen, keyboard-aware composer with a close control and compact Save
pill at the top, a local avatar, borderless writing surface, and bottom attachment
toolbar. Slide the composer up on Write; reverse on close and honor both reduced
motion settings. Share this frame with saved-post annotations. Keep drafts,
discard confirmation, attachments, replies, quotes, local persistence and errors.

Render saved local notes like timeline posts: avatar gutter, bold local author,
handle/time metadata, aligned text/media, overflow menu, reply and edit controls.
Reuse translated labels and the app theme. Notes remain local; no X writes.
Use Store for feature state. Frozen client/database paths and SDK pins stay intact.

## Checks

Cover mixed-caption URL extraction, host/scheme validation, platform delivery,
save/discard/retry, keyboard and large-text layouts, dark/RTL rendering and
composer entrance/reduced motion. Run analyzer and tests with Flutter 3.44.4,
build the Android debug APK, and inspect widget renders. Phone-only Android
share chooser and IME behavior will be reported separately from headless checks.
