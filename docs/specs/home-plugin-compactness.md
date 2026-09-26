# Compact Home plugin controls

Baseline: `claude/main` at `3a48f293d268dc3a0f8873bc0f9f448e1e003d9c`.
Scope: a separate UI PR, not the unmerged design-skill installation PR #301.

## Reading-first layout

The active Home source already lives in the app-bar title picker. Do not modify
or restore the unused HomeFeedStrip dock. Preserve the Alt Microblogging member
selector, source-picker entries, grouping undo, full-client entry, and every
plugin's lazy-loading/session behavior.

- A top-level embedded PluginHomeChrome measures its actual localized section
  labels using the current text scale and remaining width after actions. Keep
  direct section tabs when they fit. Otherwise show the active section with its
  icon and a chevron; its menu exposes every existing section with full wrapping
  labels and an explicit selected check. Opening the menu must not load a feed.
- Keep standalone clients' identity and section rails, and nested filter rows.
  Do not change selection Stores, request parameters, networking or databases.
- In Home, use search plus the existing options menu for Bluesky, putting Add
  and Saved into that menu even on a normal-width phone. Full-client behavior
  remains unchanged. Mastodon similarly keeps search visible with Saved and
  Settings together in its compact Home overflow.
- In Home, collapse the secondary people-suggestions heading/chips into a
  labelled expandable row with a count. Keep profiles and follow actions inside;
  keep standalone suggestions expanded. Expansion state uses PageStorage, not
  a new feature-state mechanism. Let expanded rows grow with text scaling.
- Tighten shared Home filter-row gutters, not touch targets. Omit empty rows.
  Preserve normal standalone spacing and directional/RTL layout.

Touch targets remain at least 48 logical pixels; no body-font reduction, theme
replacement, automatic refresh on opening controls, or lost plugin destination.
This follows the installed skills' layout/refinement principles. Their native
engine was not executed in this environment; source/reference review is not a
claim of visual or device testing.

## Verification

Add focused widget coverage to the existing plugin chrome tests for actual
embedded width, all section callbacks, search independence, wider layouts,
large text/RTL, suggestion expansion/profile/follow actions, and filter height.
Update the existing accessibility journey to open the new section menu before
choosing a section. Retain its light/dim/true-black, 320/840 and 200% cases.
Run the repository's existing verify workflow through the PR; report its actual
result separately from real-device testing. No workflow/permissions changes,
merge, dependency upgrade, version bump or release are part of this work.
