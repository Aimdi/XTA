# X reader hub

## Problem

The X source exposes a single selected For you tab whose tap does nothing.
Its plugin descriptor does not advertise search, and readers must leave the
source to find search, saved posts, subscriptions, or account management.

## Scope

- Keep the existing X-only For you feed and its paging/refresh behavior.
- Replace the inert tab with a non-interactive, accessible For you heading.
- Provide an always-visible X search action and an accessible menu linking to
  the existing Subscriptions, Saved, and Accounts screens.
- Share this reader chrome between the embedded source and standalone client.
- Advertise search support through `XPlugin.openSearch`, preserving an initial
  query and focusing the input when no query was supplied.
- Let shortcut routes own and dispose their scroll controllers. Returning from
  Accounts refreshes the X feed so changed account selection takes effect.
- Reuse existing localized labels. Keep all new navigation local or read-only.

## Boundaries

No dependency, client, or database changes. No X write actions. No new Following
backend: the existing home Following feed includes other plugins and must not
be presented as an X-only feed. Subscriptions and Saved open the existing shared
library screens, retaining their source filters and management tools.

## Validation

- Widget-test that the X plugin opens the X results route with the intended
  query/focus arguments.
- Widget-test search/menu actions, every shortcut callback, the non-interactive
  heading, and narrow layouts with increased text size.
- Analyze the owned feature files and run the focused tests with Flutter 3.44.4.
