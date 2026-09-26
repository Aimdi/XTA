# XTA design skills

This is a project-local installation for coding agents, not an Android runtime
plugin and not a global installation into the ChatGPT account.

## Scope and precedence

Read `AGENTS.md` and `CLAUDE.md` before either skill. This brief and the user's
request take precedence over generic upstream design suggestions.

XTA is an existing Android-only Flutter reader. Refine the current app rather
than replacing it with a website, marketing page, or unrelated application.
Use Impeccable's Operate mode for controls and Read mode for article surfaces.
Use UI UX Pro Max's Flutter stack explicitly; do not default to HTML/Tailwind.

## Compact layout contract

- Remove redundant chrome, repeated headings, oversized padding, and nested
  decorative cards before reducing content size. Preserve readable typography.
- Retain at least 48 x 48 logical-pixel Android touch targets, accessible labels,
  text scaling, safe areas, contrast, and predictable back navigation.
- Keep frequent actions visible and consistently placed. Secondary actions may
  move into a labelled sheet or menu, but remain discoverable and fully usable.
- Preserve all existing plugin features, accounts, subscriptions, local saved
  data, navigation/scroll state, and the reversible Alt Microblogging grouping.
- Preserve existing icons, supported themes, and the true-black option. Do not
  apply generic bans on native fonts or black backgrounds to this Android app.
- Inspect existing shared UI, including PluginHomeChrome and Substack reading
  controls, before adding a new toolbar or duplicating components.
- Keep flutter_triple Store state management. Localize UI text through ARB/L10n.
  Do not edit lib/generated, lib/client, or lib/database in a design pass. Do not
  upgrade pinned Flutter/dependencies or add X write actions.
- Work incrementally, one surface at a time. Test German/long labels, narrow
  screens, large text, empty/loading/error states and all moved actions. Separate
  code inspection, widget tests, screenshots, and real-device checks in reports.
- Installation alone is not a UI redesign, a merge, an APK build, or a release.

## Use

Start a fresh coding-agent session from the XTA checkout. Both skills are
installed under `.agents/skills/` (Codex), `.claude/skills/` (Claude Code), and
`.grok/skills/` (Grok Build). Each installed copy contains its resources; no
submodule initialization is needed. The three design-skill copies are identical.

Example request:

> Use Impeccable to critique, distill and improve XTA's Home layout. Read
> docs/xta-design-skills.md first. Then use UI UX Pro Max's Flutter guidance.
> Preserve every feature and keep accessible touch targets.

From the repository root, a focused Flutter lookup is:

```sh
python3 .agents/skills/ui-ux-pro-max/scripts/search.py "compact spacing" --stack flutter
```

In hosts exposing skill commands, invoke the names `impeccable` and
`ui-ux-pro-max`; command-prefix syntax depends on the host. Impeccable's
`critique`, `distill`, `layout`, `extract`, `harden`, and native `audit` playbooks
are the relevant starting points. Do not run browser-only checks as proof of
Android behavior.

## Provenance and runtime

Pinned upstream source commits:

- Impeccable: pbakaus/impeccable @ 9d715cc4f5564a990ca8345abfdd5df6dc9b41c8
- UI UX Pro Max: nextlevelbuilder/ui-ux-pro-max-skill @ dcc40ff5133ef78276117db0cc34e7b83cc8aeba

`UPSTREAM.md` in each skill records the original source and local adaptation.
Upstream root license/notice files accompany each installed skill. The skill
manifest receives an XTA-specific preface; UI UX Pro Max's plugin-root command
examples are adjusted for this repository-local installation.

UI UX Pro Max's search helper uses Python's standard library and local data.
Impeccable's upstream launcher may download its versioned engine into its user
cache on first use; the launcher verifies the release checksum. No global npm
installation, automatic edit hooks, approval settings, or OS packages are
changed by this project installation. Without engine/network access, use the
upstream documented direct-context/reference fallback and disclose that limit.

Updates are deliberate: inspect new upstream revisions, change the pins, vendor
again in a separate branch, retain licenses, and run the checks. Do not replace
these copies with floating downloads or weaken the existing skill sync check.

## Verification

Run `python3 scripts/check_design_skills.py` and
`bash scripts/check_skill_sync.sh`. The design check compares all three copies,
checks manifests/resources/licenses, verifies Python syntax, and runs a local
Flutter search. It does not assert Android UI quality or build an APK.
