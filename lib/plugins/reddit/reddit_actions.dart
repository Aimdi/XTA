import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_account.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_listing_screen.dart';
import 'package:xta/plugins/reddit/reddit_search_screen.dart';
import 'package:xta/plugins/reddit/reddit_settings_screen.dart';
import 'package:xta/plugins/reddit/reddit_sort_sheet.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/subscriptions/users_model.dart';

/// The controls a Reddit feed needs, wherever it is being shown.
///
/// Reddit is two screens now — its own tab and an entry in the home switcher —
/// and the second one arrived with only the generic feed actions, so sorting,
/// searching and the list of followed communities were all missing from it.
/// They live here so there is one set rather than two that drift.
///
/// Subreddits are added from search, not a second plus next to the lens.
/// Sign-in stays in Reddit settings — the overflow is for how Reddit is read.
///
/// Returns a Row so it can sit as a single entry in an `AppBar.actions` list.
class RedditFeedActions extends StatefulWidget {
  /// Adds the app's own settings to the overflow menu, for a bar that has no
  /// other route to them.
  final bool showAppSettings;

  /// Called after a setting changes what the active Reddit body should fetch.
  final Future<void> Function()? onRefresh;

  const RedditFeedActions({
    super.key,
    this.showAppSettings = false,
    this.onRefresh,
  });

  @override
  State<RedditFeedActions> createState() => _RedditFeedActionsState();
}

class _RedditFeedActionsState extends State<RedditFeedActions> {
  /// Which route Reddit is read through.
  ///
  /// The client would otherwise decide silently from whatever credentials
  /// happen to be stored, so a reader who would rather not be identified had no
  /// way to say so while a sign-in existed.
  Widget _sourceMenu(BuildContext context) {
    final prefs = PrefService.of(context);
    final l10n = L10n.of(context);

    final public = redditPrefersPublic(prefs);

    return PopupMenuButton<String>(
      icon: const Icon(Icons.more_vert),
      tooltip: l10n.plugin_reddit_source,
      onSelected: (value) => _onMenuSelected(value, prefs),
      itemBuilder: (context) => [
        PopupMenuItem(
          value: redditSourceAuto,
          child: ListTile(
            contentPadding: EdgeInsets.zero,
            // Which of the two is in force was not shown anywhere, so the menu
            // that sets it could not answer what it was currently set to.
            trailing: public ? null : const Icon(Icons.check),
            title: Text(l10n.plugin_reddit_source_auto),
            subtitle: Text(l10n.plugin_reddit_source_auto_description),
          ),
        ),
        PopupMenuItem(
          value: redditSourcePublic,
          child: ListTile(
            contentPadding: EdgeInsets.zero,
            trailing: public ? const Icon(Icons.check) : null,
            title: Text(l10n.plugin_reddit_source_public),
            subtitle: Text(l10n.plugin_reddit_source_public_description),
          ),
        ),
        const PopupMenuDivider(),
        PopupMenuItem(
          value: _menuPluginSettings,
          child: ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.forum_outlined),
            title: Text('${l10n.plugin_reddit_title} · ${l10n.settings}'),
          ),
        ),
        if (widget.showAppSettings)
          PopupMenuItem(
            value: _menuAppSettings,
            child: ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.settings),
              title: Text(l10n.settings),
            ),
          ),
      ],
    );
  }

  /// Values the menu uses for the actions that are not a source choice.
  static const _menuPluginSettings = '_pluginSettings';
  static const _menuAppSettings = '_appSettings';

  Future<void> _onMenuSelected(String value, BasePrefService prefs) async {
    if (value == _menuPluginSettings) {
      await Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => const RedditSettingsScreen()),
      );
      // Everything on that screen — the sign-in, the client id, the route —
      // changes what this menu should say next time it opens.
      if (mounted) setState(() {});
      return;
    }
    if (value == _menuAppSettings) {
      Navigator.pushNamed(context, routeSettings);
      return;
    }

    await prefs.set(optionPluginRedditSource, value);
    if (mounted) {
      await _refreshActive();
    }
  }

  Future<void> _manageSubreddits() async {
    await showModalBottomSheet(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) {
        final store = sheetContext.read<RedditSubredditsStore>();
        return SafeArea(
          child: ScopedBuilder<RedditSubredditsStore, List<String>>(
            store: store,
            onState: (_, names) => ListView(
              shrinkWrap: true,
              children: [
                for (final name in names)
                  ListTile(
                    title: Text('r/$name'),
                    trailing: IconButton(
                      icon: const Icon(Icons.delete_outline),
                      onPressed: () async {
                        await store.remove(name);
                        if (sheetContext.mounted) {
                          await refreshAfterRedditChange(sheetContext);
                        }
                      },
                    ),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton(
          tooltip: l10n.plugin_reddit_sort,
          icon: Icon(
            redditSortLabel(
              context,
              storedRedditSort(PrefService.of(context)),
            ).icon,
          ),
          onPressed: () async {
            if (await openRedditSortSheet(context) != null && context.mounted) {
              await _refreshActive();
            }
          },
        ),
        IconButton(
          tooltip: l10n.plugin_reddit_search_hint,
          icon: const Icon(Icons.search),
          onPressed: () => Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const RedditSearchScreen()),
          ),
        ),
        IconButton(
          tooltip: l10n.subscriptions,
          icon: const Icon(Icons.list),
          onPressed: _manageSubreddits,
        ),
        _sourceMenu(context),
      ],
    );
  }

  Future<void> _refreshActive() =>
      widget.onRefresh?.call() ?? context.read<RedditFeedStore>().refresh();
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
    final l10n = L10n.of(context);
    if (names.isEmpty) {
      await addRedditSubreddit(context);
      return;
    }

    final chosen = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) {
        return SafeArea(
          child: ListView(
            shrinkWrap: true,
            children: [
              ListTile(title: Text(l10n.plugin_reddit_communities)),
              for (final name in names)
                ListTile(
                  leading: const Icon(Icons.tag),
                  title: Text('r/$name'),
                  onTap: () => Navigator.pop(sheetContext, name),
                ),
            ],
          ),
        );
      },
    );

    if (chosen == null || !context.mounted) {
      return;
    }
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => RedditListingScreen.subreddit(chosen)),
    );
  }
}

/// Asks for a subreddit and follows it.
///
/// A function rather than a method: the app bar offers it, and so does the
/// empty feed, which is the screen a reader with no subreddits actually sees.
Future<void> addRedditSubreddit(BuildContext context) async {
  final entered = await showDialog<String>(
    context: context,
    builder: (_) => const _AddSubredditDialog(),
  );

  if (entered == null || entered.isEmpty || !context.mounted) return;

  if (normaliseSubreddit(entered) == null) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(L10n.of(context).plugin_reddit_error_not_found)),
    );
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
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(l10n.cancel),
        ),
        TextButton(
          onPressed: () => Navigator.pop(context, _controller.text.trim()),
          child: Text(l10n.ok),
        ),
      ],
    );
  }
}
