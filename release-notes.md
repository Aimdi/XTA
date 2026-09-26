## XTA — aimdi144

### More compact Home plugins

This release combines the design-skill installation in PR #301 and the Home
plugin layout improvements in PR #302.

- Plugin section tabs stay directly visible when they fit. Crowded embedded
  Home readers use a labelled current-section picker, with every section and
  its selection state available in the menu.
- Bluesky and Mastodon keep Search visible while secondary actions use their
  options menu. Standalone clients retain their existing navigation.
- People suggestions become a collapsible, counted row inside Home. Expanding
  preserves profile and Follow actions. The suggestion list has its own saved
  scroll-position key so it cannot collide with expanded/collapsed state.
- Shared Home filter rows remove 8 logical pixels of outer vertical padding,
  use tighter horizontal gaps, and occupy no space when empty.
- Readable body text, full-sized touch targets, true-black themes, the Home
  source picker, full-client entry and reversible Alt Microblogging grouping
  are preserved. This release does not change X APIs or the database schema.

### Development tooling

Impeccable and UI UX Pro Max are included as pinned, repository-local design
skills for Codex, Claude Code and Grok. They include XTA-specific Flutter,
compact-layout and accessibility constraints. These are coding-agent skills,
not Android plugins or a global installation into a ChatGPT account.

### Installation

For most Android phones, use **`xta-aimdi144_arm64-v8a.apk`**. The universal APK,
ARMv7 and x86_64 variants are also supplied. The base version code is
**400001154**, above all aimdi143 variants. The app ID (`com.aimdi.xta`) and
release-signing configuration are unchanged for in-place updates.

The release workflow runs translation, skill synchronization, static analysis
and the full Flutter test suite on the tagged source. It verifies all four APK
variants and the published signing certificate before attaching them, together
with **`release-build.json`** and **`SHA256SUMS`**. Physical-device visual and
signed-in live-service testing remain separate from these automated checks.

XTA remains read-oriented: no remote posting, replying, reposting or liking on
X is added. Earlier loading fixes and the aimdi143 grouping improvements remain
included.

[Earlier release notes](https://github.com/Aimdi/XTA/blob/aimdi144/docs/release-notes-through-aimdi143.md)
