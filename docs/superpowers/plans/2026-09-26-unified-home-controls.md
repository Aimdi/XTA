# Unified Home Controls Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Checkboxes track evidence, not intent.

**Goal:** Reduce stacked Home navigation and make source selection consistent without losing functionality.

**Architecture:** Add an opt-in embedded-controls host using flutter_triple. Migrate existing Home readers incrementally; standalone clients keep their current controls and stores. Source selection uses one shared sheet entry point.

**Tech Stack:** Flutter 3.44.4, Dart, flutter_triple, existing ARB/L10n.

**Spec:** docs/specs/unified-home-controls.md

## Global Constraints

Preserve lib/client, lib/database, generated files, dependencies and signing. No merge or APK release. Touch targets remain at least 48 logical pixels. Keep plugin sessions and source/section selection independent of toolbar rebuilds.

## Review Focus

- Switching sources while a menu is open must not dispatch into a disposed plugin.
- Enlarged German labels and RTL layouts must not hide targets or overflow.
- Empty/short feeds must not oscillate when secondary controls collapse.
- Search in the chooser must retain grouped microblog destinations and unread state.
- Standalone clients and restored scroll positions must not change.

## Tasks

### 1. Source chooser

Files: lib/home/home_timeline_picker.dart, shared Home chooser helper, both Home entry points; test/unified_home_picker_test.dart.

- [ ] Add failing compact-row and large-collection search tests against the current picker.
- [ ] Run them and record the expected failures.
- [ ] Implement a Store-backed query, compact adaptive rows, no-results feedback and consistent live group/unread/Add options.
- [ ] Verify selection, cancellation, grouping, add and keyboard/large-text behavior.

### 2. Shared embedded controls

Files: lib/plugins/plugin_home_chrome.dart, new opt-in controls host, lib/home/_feed.dart, lib/home/alt_microblogging_selector.dart; test/unified_home_controls_test.dart.

- [ ] Pin the required assembled header geometry and action dispatch in failing widget tests.
- [ ] Implement source-scoped controls contribution and stable context layout without remounting content.
- [ ] Keep one-tap service switching and a readable full-client menu action.
- [ ] Verify disposal, stale source contributions, large text, standalone fallback and selected state.

### 3. Reader integration

Files: Bluesky/Mastodon/Substack Home UI and shared reading widgets; existing reader integration tests.

- [ ] Add regression expectations for removed duplicate control rows and all moved actions.
- [ ] Integrate local reading controls with the shared header; preserve search/publication semantics.
- [ ] Move suggestions below initial content without changing reading anchors.
- [ ] Enable consistent secondary-row collapse only with short-feed/focus guards and an explicit keep-visible control.
- [ ] Verify loading/error/empty states, repeated service changes and independent scroll restoration.

### 4. Verify and publish the PR

- [ ] Run formatting, analyzer, ARB validation and skill synchronization.
- [ ] Run focused tests and the full existing suite on the pinned SDK in CI.
- [ ] Review combined Home captures when rendering is available.
- [ ] Inspect the final diff for scope and preservation; open/update the PR with precise evidence and remaining limitations.
