# Building XTA's screens in XTA Native

The screens are Compose Multiplatform in `native/ui`, one code base for the
Android app (`:app`) and the Linux desktop (`:desktop`). This guide is for
porting a part of XTA's UI (`lib/<feature>/` in the Flutter app) onto the
reading layers that are already ported: `:core` (X, feeds, session),
`:networks`, `:store` (saved, offline, downloads, reading, settings, backup,
antennas, recent searches) and `:services` (articles, links, translation,
speech text). Their KDoc says what each call does.

## Rules

- **Read-only.** Nothing is posted, liked, voted on, followed or messaged on
  any network. Following, likes, saves and notes are the device's own (the
  session and `:store`). The saves to the reader's own Immich, Karakeep and
  Deepmarks accounts are the only exception, as in XTA.
- **Your packages only**, under `ui/src/{commonMain,androidMain,desktopMain,desktopTest}/kotlin/com/aimdi/xtanative/ui/<area>/`,
  plus, where you need them: your route lines in `ui/.../ui/Navigation.kt`,
  the stores you need as properties of `data/AppGraph.kt`, and your strings
  files. Leave other screens, the build files, the workflows and the
  plain-Kotlin modules alone; if one of them needs a change, make the
  smallest one with a test and say so in your report.
- **Faithful to XTA**: the same features, choices, order and wording as the
  Flutter screens, adapted to Compose and Material 3, compact as XTA is.
  Every option XTA offers there is offered here.
- **Both platforms.** What Android alone can do (share sheets, the system
  text-to-speech, MediaStore, custom tabs) goes behind a small interface or
  `expect`/`actual`, with a desktop side that works (system browser,
  `~/Downloads`, keyboard and mouse). Nothing may crash on the desktop.
- **JDK 17.** CI and the desktop app run on Java 17, while a machine that
  checks locally may have a newer one. Use nothing that changed after 17:
  `java.text.BreakIterator` joins emoji sequences only from Java 20 on, so a
  "character" goes through `ui.common.Graphemes`.

## How a screen is built

Like `ui/feed`, `ui/profile` and `ui/post`: a `ViewModel` holding a
`StateFlow` of one immutable UI state, made with the graph
(`LocalGraph.current`), and a `@Composable` screen that collects it with
`collectAsStateWithLifecycle`. The theme is `ui/theme/Theme.kt` (true black
included); shared pieces are in `ui/common` and `ui/post` (post cards, media
grid, author line).

Decisions go in **plain Kotlin**: functions and small classes that import
neither Compose, AndroidX, Coil nor the generated resources (what to show,
filters, orders, labels' inputs, what a tap does). They are tested in
`ui/src/desktopTest/kotlin/<package>/` with `kotlin.test`, and checked here,
where Gradle cannot fetch Compose, with:

```bash
KOTLINC=<kotlinc home> LIBS=<dir of jars> bash native/tool/uilogic.sh com.aimdi.xtanative.ui.<area>
```

It compiles the plain files of `:ui` against `:core`, `:store`, `:services`
and `:networks` and runs the plain tests. The first run compiles those four
modules (minutes); later runs reuse them until their sources change. A plain file that needs a type
declared in a Compose file fails there: keep such types in plain files. The
Compose code is compiled by CI (`:ui:desktopTest`, the desktop app and the
APK); if `native/tool/uicheck.sh` exists on your base, it compiles all of
`:ui` here, so use it. Without it, keep to the Compose APIs the existing
screens already use.

## Text

No text the reader sees is written in Kotlin. Each area keeps its own
resource files, so several areas can be built at once:
`ui/src/commonMain/composeResources/values/strings_<area>.xml` (English) and
`values-<qualifier>/strings_<area>.xml` for each of XTA's other languages,
names prefixed with the area (`saved_empty`, not `empty`). Use
`stringResource(Res.string.<name>)` as the existing screens do.

Translate every string into all of them. Where XTA already has the text
(`lib/l10n/intl_en.arb`, matched by meaning, not only words), take each
language's wording from its `intl_<lang>.arb`; otherwise translate so it
sounds natural in that language, as `.claude/skills/translate/SKILL.md`
asks. Plurals use `<plurals>`, with each language's categories.

| XTA | Folder |
|---|---|
| ar be ca cs de eo es et eu fr hi id it ja ko nl pl pt ro ru tr uk vi | `values-<same>` |
| nb_NO | `values-nb-rNO` |
| pt_BR | `values-pt-rBR` |
| zh_Hans | `values-zh-rCN` |
| zh_Hant | `values-zh-rTW` |
| be_Latn | not expressible as a Compose resource qualifier; falls back to `be` |

Compose's resources match a folder by the language exactly as the platform's
`Locale` names it, and Android's still names Indonesian `in` where Java 17
(the desktop's) says `id`. So `values-in` is a link to `values-id`: edit
`values-id` alone, and a script that walks the `values-*` folders skips links.
(Hebrew and Yiddish, `iw`/`he` and `ji`/`yi`, would need the same; XTA has
neither.)

## Handing back

Commit in your worktree (never push) with a message saying what XTA screens
the commit ports, and report: the commit, what is ported against which XTA
files, what is left out and why, the plain tests and their count, and
anything you changed or need outside your area.
