# Alt Microblogging

Home's source picker groups the enabled, visible Mastodon, Bluesky and Threads
readers into one Alt Microblogging entry. Opening it returns to the active or
last-used available service. A compact, horizontally scrollable service selector
switches between their existing readers. The same grouping applies to Home's
long-press picker and the optional Networks overflow.

The Plugin Store exposes a persistent, default-on grouping switch. Turning it
off immediately restores the separate source entries in their original order.
Grouping never installs or removes a plugin, changes pins, rewrites a feed ID,
or migrates accounts, subscriptions, saved items, credentials or reader settings.
Individual plugin management and standalone clients remain available.

Only the active reader is mounted. Existing session stores, PageStorage keys,
source-specific search, filters, tabs and full-client actions are reused. Empty
groups are omitted; missing remembered services fall back to an available member.
Unread state aggregates onto the group and stays visible per service.

Validation covers reversible preferences and pin order, current/recent/missing
service selection, both Home entry points, plugin disablement, service selection,
large text, narrow screens and RTL. All new copy is localized. The pinned
toolchain, plugin API clients and database are unchanged.
