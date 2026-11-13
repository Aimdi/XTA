---
name: translate
description: load this skill everytime user asks you to do translation stuff, or when you want to add text that will be displayed on the UI or when you remove such a text
---

# translate skill

Guide for adding, changing, or removing user-visible strings in XTA Native
(`native/`). `native/SCREENS.md` ("Text") is the full reference.

## When this skill applies

Any time you touch a string that appears on the UI: adding a label, changing
copy, removing a string, or refactoring code that calls
`stringResource(Res.string.*)` or a plugin's generated `Strings`.

## Where strings live

| Part | English | Other languages |
|---|---|---|
| The screens (`:ui`) | `native/ui/src/commonMain/composeResources/values/strings_<area>.xml` | `values-<qualifier>/strings_<area>.xml` |
| A plugin | `native/plugins/<id>/strings/values/strings.xml` | `strings/values-<qualifier>/strings.xml` |
| The Android shell (`:app`) | `native/app/src/main/res/values/strings.xml` | only what Android itself shows |

Names in `:ui` are prefixed with their area (`saved_empty`, not `empty`).

## Rules

### 1. Never write UI text in Kotlin

```kotlin
// Wrong
Text("Subscribe")

// Right
Text(stringResource(Res.string.profile_subscribe))
```

A plugin uses its generated `Strings`, never a literal.

### 2. Add the English string first, then every language

The 29 languages and their folders:

| Language codes | Folder |
|---|---|
| en | `values` |
| ar be ca cs de eo es et eu fr hi id it ja ko nl pl pt ro ru tr uk vi | `values-<same>` |
| Norwegian Bokmål | `values-nb-rNO` |
| Brazilian Portuguese | `values-pt-rBR` |
| Simplified / Traditional Chinese | `values-zh-rCN` / `values-zh-rTW` |
| Latin Belarusian | `values-be-rXL` (filled by the tool, below) |

`ui/.../composeResources/values-in` is a link to `values-id` (Android names
Indonesian `in`): edit `values-id` only.

### 3. Translate naturally

For each translation, ask "does this sound natural in this language?" rather
than matching the English syntax. Where the old XTA already had the text,
`native/tool/l10n/` keeps its English and Belarusian wording for reference;
the other languages' wording is in the git history of `lib/l10n/`.

### 4. Stop and ask when the string is ambiguous

Ask the user before translating when it is unclear what is counted or
described (plurals, grammatical gender), whether a word is a verb or a noun,
which register is expected, or where the text appears (button, body, error).
A wrong translation ships silently to every reader of that language.

### 5. Plurals use `<plurals>` with each language's categories

```xml
<plurals name="saved_items">
    <item quantity="one">%1$d item</item>
    <item quantity="other">%1$d items</item>
</plurals>
```

Russian, Ukrainian, Belarusian and Polish need `one`, `few`, `many`, `other`;
Arabic all six (`zero`, `one`, `two`, `few`, `many`, `other`); Japanese,
Korean, Chinese, Vietnamese and Indonesian only `other`.

### 6. Fill Latin Belarusian with the tool

```bash
python3 -I native/tool/belarusian_latin.py
```

It adds what `values-be-rXL` lacks from `values-be`, and lists what to read
over. `LatinBelarusianTest` fails while a string is missing.

### 7. Removing a string

Remove it from `values/` and every `values-*` folder in the same change.

## Checklist

- [ ] No UI text written in Kotlin
- [ ] English string added under `values/`
- [ ] The same name in all other language folders, translated
- [ ] Plurals with the right categories per language
- [ ] Ambiguous context clarified with the user
- [ ] `belarusian_latin.py` run; the `:ui` and plugin string tests pass (`./gradlew :ui:desktopTest`)
