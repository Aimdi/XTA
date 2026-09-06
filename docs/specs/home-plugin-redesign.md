# Home and plugin reader redesign

## Baseline and scope

Verified through GitHub on 2026-09-06: `claude/main`, latest production release
`aimdi126`, and its lightweight tag all identify
`4c6b8e87f4acce99b6998ac2b65a13892ac41673`. The isolated working branch is
`codex/home-plugin-redesign`; existing worktrees are untouched.

The baseline's [verification](https://github.com/Aimdi/XTA/actions/runs/33641378899)
and [Android build](https://github.com/Aimdi/XTA/actions/runs/33641378888) passed.
Local ARB integrity passes with existing placeholder warnings (1519 keys, 28
locales); skill trees match. Flutter/FVM and the Android SDK are absent from
this workspace. CI will provide executable verification. No device has been
viewed. The latest scheduled live API canary failed; that is separate from
deterministic UI verification and predates this branch.

Only Home, plugin presentation, their supporting widgets and focused tests
are in scope. Backend clients, parsers, databases, dependencies, SDK pins,
signing, plugin visibility and remote actions are frozen. No merge or release
is authorized. Existing setup probes and explicitly requested integration
actions keep their original services.

## Alternatives and decision

| Home layout | Value | Cost |
| --- | --- | --- |
| A: persistent source strip, scoped title/actions, content underneath | Following, For you and every pin remain visible and one tap away; preserves learned navigation | Two Home chrome rows |
| B: active source in a title menu, content immediately below | More vertical room for artwork/articles | Hides neighboring feeds; switching needs a menu; weakens the existing strip |

Choose A. Retain existing marks and the fixed Add timeline action. The title
identifies the active source; its action opens that plugin's full client when
the embedded entry has fewer capabilities. X account/group controls only
appear on Following/For you. Reddit keeps its actual sort/search/community
actions in the compact embedded feed. Drawer/settings and For you's explicit
refresh remain reachable. Plugin navigation is readable and scrollable.

## Navigation contract

1. The customized XTA bottom bar changes app destinations. It remains the
   only bottom navigation bar.
2. The Home source strip changes Following, For you or a pinned plugin feed.
   Pinning remains separate from enabling a plugin or showing its destination.
3. The active plugin's section row selects its own existing sections. Its
   actions affect that plugin. Standalone clients have a title/mark; embedded
   clients omit duplicate identity and top safe-area padding.

Reuse `PluginEmbedded`, `PluginHomeChrome`, `PluginLazyTabs`,
`pluginInnerScrollController`, `HomeFeedStrip`, theme tokens and existing
Store objects. Do not introduce a second router, a second bottom bar or a new
network/data cache. Section state and scroll restoration belong to the
presentation layer. Keep one active inner scrollable. All source, section and
media gestures keep distinct hit regions; no whole-screen swipe detector.

## Presentation rules

- Flat theme surfaces, restrained accents, hairline separation; retain True
  Black/OLED. Selected text uses readable foreground, with an underline/check
  as an additional signal.
- Section labels alongside existing icons, horizontally scrollable without
  shrinking 48dp targets. A crowded toolbar gives actions their own row.
- In full clients, identity and actions occupy the header, sections the next
  row. In embedded clients, omit identity; reserve room for actual controls.
- Use text-led rows for articles, ranked title/domain/comment hierarchy for
  HN, metadata outside gallery artwork, and visible creator context for video.
- Filtered emptiness keeps filters visible and offers reset. Initial emptiness
  offers the source's existing add/search action. Retained content survives a
  recoverable refresh error wherever its store exposes it.
- Connection setup presents server/identity, credentials and explicit test/save
  actions in order. Changing credentials invalidates a prior probe result.
  A successful probe describes only what the existing probe actually checked.

## Reference patterns inspected

| Reference | Observed pattern and adaptation | Not copied |
| --- | --- | --- |
| [Read You README](https://github.com/ReadYouApp/ReadYou) | Feed grouping, unread organization and article reading are first-class; RSS source/read state precedes a compact article title and excerpt | Compose code, sync providers, new reader features |
| [PixEz ranking screen source](https://github.com/Notsfsssf/pixez-flutter/blob/master/lib/page/hello/ranking/rank_page.dart) | Scrollable named ranking modes with a contextual date picker; combine mode/date selection in one artwork toolbar | Code/assets, fullscreen globals, new ranking endpoints or remote actions |
| [RedReader README](https://github.com/QuantumBadger/RedReader) | Reading/cache continuity, screen-reader support and AMOLED treatment; expose community names and selection independently of sorting/display controls | Vote endpoints, custom post swipe actions, Java code or branding |

These are source/README observations, not claims of having operated those apps.

## Pilot journeys

| Pilot | Before | Intended after and preservation checks |
| --- | --- | --- |
| Reddit | Home pin is only `RedditFeedList`; full client uses icon rails and small community chips; actions can consume most of the row | Explicit full-client entry; named Following/Popular/All, followed-community label and safe targets; same `RedditHomeStore`, sort, search, saved, post/comment paths |
| Pixiv | Five icons, Home chips and ranking controls can stack/wrap; fixed two-column grid; title/bookmark metadata compete | Readable sections, compact source/mode/date controls, adaptive artwork columns and clear title/artist hierarchy; same auth, ranking dates, favorites, mute/bookmark calls |
| RSS | Two icons and action cluster; large social-style covers; filtered empty timeline has no explanation | Named Home/Feeds, text-led articles with source/read context, persistent filters with reset, source metadata in Feeds; same read/tags/add/settings/reader paths |

## Acceptance and evidence

- Exercise actual Home source transitions, full-client/back routing, source
  disablement, pin order, section changes, selected semantics and lazy loading.
- Test 320dp, 200% text, RTL and light/dark/True Black; verify action taps and
  settings/subscription/statistics hit targets remain unobstructed.
- Every new helper must be mounted by production callers. A shared toolbar
  migration alone does not complete a plugin.
- Generate localization using the pinned tools, format new/changed Dart,
  analyze, run deterministic tests and build a debug APK. Keep live-service
  failures separate. Preserve frozen files byte-for-byte.
- Deliver per-plugin coverage and limitations, exact commits, changed files,
  and clearly labeled deterministic test renders. A debug APK makes no claim
  about production signing or upgrade compatibility.

Implementation progress and the complete registry matrix live in
`docs/home-plugin-redesign-checklist.md`.
