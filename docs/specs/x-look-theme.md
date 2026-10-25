# Phase 3 — X-look theme (switchable)

Apply the **current X design language** as a **switchable theme preset**.
Existing Fairy Forest / Pitch Black / seed-color themes stay. Fully revertable.

X's July 20 Android rebuild was Kotlin + Compose internally — **no new look
was published** — so tokens track the established X web/app chrome, not a
hypothetical redesign.

## Tokens (`ThemeExtension`)

`XLookTokens` on `ThemeData.extensions`:

| Token | Light | Dim | Lights-Out |
|---|---|---|---|
| accent | `#1D9BF0` | `#1D9BF0` | `#1D9BF0` |
| background | `#FFFFFF` | `#15202B` | `#000000` |
| text / onBackground | `#0F1419` | `#F7F9F9` | `#E7E9EA` |
| secondary | `#536471` | `#8899A4` | `#71767B` |
| divider / border | `#EFF3F4` | `#38444D` | `#2F3336` |
| card / surface | `#FFFFFF` | `#192734` | `#000000` |

Shape / type (shared):

- Font: **Inter** (Chirp is proprietary — never bundle it). Body ~15 sp,
  display names bold, headers 20 sp bold.
- Pill buttons, round avatars **40**, media radius **12–16**, 4-pt spacing.
- Nav: keep existing tabs; selected icon uses accent; short 200–300 ms fades.

## Settings

Add three presets under Design preset (alongside Fairy Forest / Pitch Black):

- `x_look_light` — forces light
- `x_look_dim` — forces dark (dim palette)
- `x_look_lights_out` — forces dark (true black)

Selecting any X-look preset overrides theme mode + seed color the same way
existing presets do.

## Files

| File | Role |
|---|---|
| `lib/ui/x_look_theme.dart` | `XLookTokens` + `ThemeData` builders |
| `lib/ui/theme_presets.dart` | Re-export / keep Fairy Forest + Pitch Black |
| `lib/constants.dart` | Preset string constants |
| `lib/main.dart` | Wire presets into `MaterialApp` |
| `lib/settings/_theme.dart` | Dropdown entries (ARB labels) |
| `lib/tweet/tweet_chrome.dart` | Prefer extension tokens when present |
| `assets/fonts/Inter-*.ttf` | OFL Inter Regular + Bold |
| `assets/fonts/NotoSansMath-Accents.ttf` | OFL subset of Noto Sans Math (from QuaX): the bold and italic Unicode letters and their accents, every Inter style's fallback |
| `pubspec.yaml` | Font family registration (no pinned-dep bumps) |

## Rules

- No hardcoded X colors in feature widgets — look up `XLookTokens.of(context)`
  or fall back to `Theme.of(context).colorScheme`.
- Do not remove Fairy Forest / Pitch Black.
- ARB for all new preset labels (`/translate`).

## Acceptance

- Toggle away from X-look restores previous seed-color theming behavior.
- Tweet chrome / dividers / cards use tokens under X-look.
- Analyze + test + debug APK green.
