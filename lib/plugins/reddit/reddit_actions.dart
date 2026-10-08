import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/plugins/plugin_home_dock.dart';
import 'package:xta/plugins/reddit/reddit_account.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_listing_screen.dart';
import 'package:xta/plugins/reddit/reddit_read_session.dart';
import 'package:xta/plugins/reddit/reddit_search_screen.dart';
import 'package:xta/plugins/reddit/reddit_settings_screen.dart';
import 'package:xta/plugins/reddit/reddit_sort_sheet.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/plugins/reddit/reddit_subreddit_avatar.dart';
import 'package:xta/subscriptions/users_model.dart';

/// The controls a Reddit feed needs, wherever it is being shown.
///
/// One set for the standalone reader and Home, so they cannot drift. Search
/// comes first because Home keeps the first action visible beside its options
/// button; sort and the menu move into that options sheet there.
///
/// Subreddits are added from search, not a second plus next to the lens.
/// Sign-in stays in Reddit settings — the menu is for how Reddit is read.
class RedditFeedActions extends StatelessWidget {
  /// Called after a setting changes what the active Reddit body should fetch.
  final Future<void> Function() onRefresh;
  final VoidCallback onOpenSaved;
  final Widget Function(List<Widget> actions) builder;

  const RedditFeedActions({super.key, required this.onRefresh, required this.onOpenSaved, required this.builder});

  /// Values the menu uses for the actions that are not a source choice.
  static const _menuPluginSettings = '_pluginSettings';
  static const _menuSaved = '_saved';
  static const _menuCommunities = '_communities';

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    // Listening repaints the sort icon after the sort sheet saves a new one.
    final sort = storedRedditSort(PrefService.of(context));
    return builder([
      IconButton(
        tooltip: l10n.plugin_reddit_search_hint,
        icon: const Icon(Icons.search),
        onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const RedditSearchScreen())),
      ),
      IconButton(
        tooltip: l10n.plugin_reddit_sort,
        icon: Icon(redditSortLabel(context, sort).icon),
        onPressed: () async {
          if (await openRedditSortSheet(context) != null && context.mounted) await onRefresh();
        },
      ),
      PluginHomeMenu(
        tooltip: '${l10n.plugin_reddit_title} · ${MaterialLocalizations.of(context).moreButtonTooltip}',
        onSelected: (value) => _onMenuSelected(context, value),
        itemBuilder: (_) => _menuItems(context),
      ),
    ]);
  }

  /// Which route Reddit is read through.
  ///
  /// The client would otherwise decide silently from whatever credentials
  /// happen to be stored, so a reader who would rather not be identified had no
  /// way to say so while a sign-in existed. Read when the menu opens, so it
  /// always answers what is currently in force.
  List<PopupMenuEntry<String>> _menuItems(BuildContext context) {
    final l10n = L10n.of(context);
    final public = redditPrefersPublic(PrefService.of(context, listen: false));
    return [
      _item(_menuSaved, Icons.bookmark_border, l10n.saved),
      _item(_menuCommunities, Icons.list, l10n.subscriptions),
      const PopupMenuDivider(),
      _route(redditSourceAuto, l10n.plugin_reddit_source_auto, l10n.plugin_reddit_source_auto_description, !public),
      _route(
        redditSourcePublic,
        l10n.plugin_reddit_source_public,
        l10n.plugin_reddit_source_public_description,
        public,
      ),
      const PopupMenuDivider(),
      _item(_menuPluginSettings, Icons.forum_outlined, '${l10n.plugin_reddit_title} · ${l10n.settings}'),
    ];
  }

  PopupMenuItem<String> _item(String value, IconData icon, String label) => PopupMenuItem(
    value: value,
    child: ListTile(contentPadding: EdgeInsets.zero, leading: Icon(icon), title: Text(label)),
  );

  PopupMenuItem<String> _route(String value, String label, String description, bool selected) => PopupMenuItem(
    value: value,
    child: ListTile(
      contentPadding: EdgeInsets.zero,
      trailing: selected ? const Icon(Icons.check) : null,
      title: Text(label),
      subtitle: Text(description),
    ),
  );

  Future<void> _onMenuSelected(BuildContext context, String value) async {
    switch (value) {
      case _menuSaved:
        onOpenSaved();
      case _menuCommunities:
        await showRedditCommunitiesSheet(context);
      case _menuPluginSettings:
        await Navigator.push(context, MaterialPageRoute(builder: (_) => const RedditSettingsScreen()));
      default:
        await PrefService.of(context, listen: false).set(optionPluginRedditSource, value);
        if (context.mounted) await onRefresh();
    }
  }
}

/// Opens a followed community without leaving the Reddit home chrome.
class RedditCommunitySwitcher extends StatelessWidget {
  const RedditCommunitySwitcher({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<RedditSubredditsStore, List<String>>(
      store: context.read<RedditSubredditsStore>(),
      onState: (context, names) {
        return IconButton(
          tooltip: l10n.plugin_reddit_communities,
          icon: const Icon(Icons.forum_outlined),
          onPressed: () => _open(context, names),
        );
      },
    );
  }

  Future<void> _open(BuildContext context, List<String> names) async {
    if (names.isEmpty) {
      await addRedditSubreddit(context);
      return;
    }
    await showRedditCommunitiesSheet(context);
  }
}

/// Followed communities: open one, or drop it.
///
/// The list icon used to be delete-only rows with no tap target, so a reader
/// could not actually visit r/foo from the sheet that listed it.
Future<void> showRedditCommunitiesSheet(BuildContext context) {
  final opener = context;
  return showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (sheetContext) => DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.52,
      minChildSize: 0.32,
      maxChildSize: 0.88,
      builder: (context, controller) => _RedditCommunitiesSheet(controller: controller, opener: opener),
    ),
  );
}

class _RedditCommunitiesSheet extends StatelessWidget {
  final ScrollController controller;
  final BuildContext opener;

  const _RedditCommunitiesSheet({required this.controller, required this.opener});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

    return SafeArea(
      top: false,
      child: ScopedBuilder<RedditSubredditsStore, List<String>>(
        store: context.read<RedditSubredditsStore>(),
        onState: (context, names) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
                child: Row(
                  children: [
                    Icon(Icons.forum_outlined, color: scheme.primary),
                    const SizedBox(width: 12),
                    Expanded(child: Text(l10n.plugin_reddit_communities, style: theme.textTheme.titleLarge)),
                    if (names.isNotEmpty)
                      Text(
                        '${names.length}',
                        style: theme.textTheme.titleMedium!.copyWith(
                          color: scheme.primary,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                  ],
                ),
              ),
              Expanded(
                child: ListView(
                  controller: controller,
                  padding: const EdgeInsets.fromLTRB(4, 0, 4, 8),
                  children: [
                    if (names.isEmpty)
                      Padding(
                        padding: const EdgeInsets.fromLTRB(24, 32, 24, 24),
                        child: Column(
                          children: [
                            Icon(Icons.forum_outlined, size: 40, color: scheme.onSurfaceVariant),
                            const SizedBox(height: 12),
                            Text(
                              l10n.plugin_reddit_empty,
                              textAlign: TextAlign.center,
                              style: theme.textTheme.bodyMedium!.copyWith(color: scheme.onSurfaceVariant),
                            ),
                          ],
                        ),
                      )
                    else
                      for (final name in names) _RedditCommunityTile(name: name, opener: opener),
                  ],
                ),
              ),
              const Divider(height: 1),
              ListTile(
                leading: Icon(Icons.add, color: scheme.primary),
                title: Text(l10n.plugin_reddit_add, style: TextStyle(color: scheme.primary)),
                onTap: () async {
                  Navigator.pop(context);
                  if (opener.mounted) {
                    await addRedditSubreddit(opener);
                  }
                },
              ),
            ],
          );
        },
      ),
    );
  }
}

class _RedditCommunityTile extends StatefulWidget {
  final String name;
  final BuildContext opener;

  const _RedditCommunityTile({required this.name, required this.opener});

  @override
  State<_RedditCommunityTile> createState() => _RedditCommunityTileState();
}

class _RedditCommunityTileState extends State<_RedditCommunityTile> {
  Future<RedditSubredditAbout?>? _about;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _about ??= _communityAbout(context, widget.name);
  }

  @override
  void didUpdateWidget(_RedditCommunityTile oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.name != widget.name) {
      _about = _communityAbout(context, widget.name);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final store = context.read<RedditSubredditsStore>();
    final name = widget.name;
    final opener = widget.opener;

    return FutureBuilder<RedditSubredditAbout?>(
      future: _about,
      builder: (context, snapshot) {
        final about = snapshot.data;
        return ListTile(
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
          leading: RedditSubredditAvatar(
            subreddit: name,
            size: 44,
            url: redditAvatarUrlFromAbout(hasData: snapshot.hasData, iconUrl: about?.iconUrl),
          ),
          title: Text(
            'r/$name',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.titleSmall!.copyWith(fontWeight: FontWeight.w600),
          ),
          subtitle: _communitySubscriberLine(context, about),
          trailing: IconButton(
            tooltip: l10n.delete,
            icon: const Icon(Icons.delete_outline),
            onPressed: () async {
              await store.remove(name);
              if (context.mounted) {
                await refreshAfterRedditChange(context);
              }
            },
          ),
          onTap: () {
            Navigator.pop(context);
            if (!opener.mounted) {
              return;
            }
            Navigator.push(opener, MaterialPageRoute(builder: (_) => RedditListingScreen.subreddit(name)));
          },
        );
      },
    );
  }
}

Future<RedditSubredditAbout?> _communityAbout(BuildContext context, String name) async {
  try {
    final client = context.read<RedditClient>();
    final session = await RedditReadSession.resolve(prefs: PrefService.of(context, listen: false));
    return session.fetchSubredditAbout(client, name);
  } catch (_) {
    return null;
  }
}

Widget _communitySubscriberLine(BuildContext context, RedditSubredditAbout? about) {
  final count = about?.subscribers;
  if (count == null) {
    return const SizedBox(height: 16);
  }
  final theme = Theme.of(context);
  return DefaultTextStyle.merge(
    style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
    child: Row(
      children: [
        const Icon(Icons.people_outline, size: 16),
        const SizedBox(width: 4),
        Flexible(
          child: Text(
            '${compactCount(count)} ${L10n.of(context).followers.toLowerCase()}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ],
    ),
  );
}

/// Asks for a subreddit and follows it.
///
/// A function rather than a method: the app bar offers it, and so does the
/// empty feed, which is the screen a reader with no subreddits actually sees.
Future<void> addRedditSubreddit(BuildContext context) async {
  final entered = await showDialog<String>(context: context, builder: (_) => const _AddSubredditDialog());

  if (entered == null || entered.isEmpty || !context.mounted) return;

  if (normaliseSubreddit(entered) == null) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(L10n.of(context).plugin_reddit_error_not_found)));
    return;
  }

  final subs = context.read<RedditSubredditsStore>();
  await subs.add(entered);
  if (context.mounted) {
    await refreshAfterRedditChange(context);
  }
}

/// The feed and the subscription list both have to hear about a change: a
/// subreddit is a group member too, and the group editor reads that list rather
/// than the store the Reddit screens keep.
Future<void> refreshAfterRedditChange(BuildContext context) async {
  final feed = context.read<RedditFeedStore>();
  final subscriptions = context.read<SubscriptionsModel>();
  await feed.refresh();
  await subscriptions.reloadSubscriptions();
}

/// Owns the field so the controller is not disposed while the route is still
/// animating out — `whenComplete(controller.dispose)` crashed the empty pane.
class _AddSubredditDialog extends StatefulWidget {
  const _AddSubredditDialog();

  @override
  State<_AddSubredditDialog> createState() => _AddSubredditDialogState();
}

class _AddSubredditDialogState extends State<_AddSubredditDialog> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AlertDialog(
      title: Text(l10n.plugin_reddit_add),
      content: TextField(
        controller: _controller,
        autofocus: true,
        autocorrect: false,
        decoration: const InputDecoration(hintText: 'r/dartlang'),
        onSubmitted: (value) => Navigator.pop(context, value.trim()),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
        TextButton(onPressed: () => Navigator.pop(context, _controller.text.trim()), child: Text(l10n.ok)),
      ],
    );
  }
}
