import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_account_subscription.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/plugins/websources/web_source_client.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';
import 'package:xta/plugins/websources/web_source_dialogs.dart';
import 'package:xta/plugins/websources/web_source_feed_store.dart';
import 'package:xta/plugins/websources/web_source_follows_store.dart';
import 'package:xta/plugins/websources/web_source_list_pane.dart';
import 'package:xta/plugins/websources/web_source_mark.dart';
import 'package:xta/plugins/websources/web_source_search_screen.dart';
import 'package:xta/plugins/websources/web_sources_store.dart';

/// A source's lists, one labelled tab each (their names are the source's,
/// with no icon to tell them apart), and its search when it has one.
class WebSourceScreen extends StatefulWidget {
  final WebSourceDefinition source;

  /// The list to open on, by id; the first when absent.
  final String? listId;

  const WebSourceScreen({super.key, required this.source, this.listId});

  @override
  State<WebSourceScreen> createState() => _WebSourceScreenState();
}

class _WebSourceScreenState extends State<WebSourceScreen> {
  late final List<WebSourceFeedStore> _lists;
  late final List<ScrollController> _scrolls;

  WebSourceDefinition get _source => widget.source;

  @override
  void initState() {
    super.initState();
    final client = context.read<WebSourceClient>();
    _lists = [for (final list in _source.feeds) WebSourceFeedStore(client, _source, list)];
    _scrolls = [for (final _ in _source.feeds) ScrollController()];
  }

  @override
  void dispose() {
    for (final store in _lists) {
      store.destroy();
    }
    for (final scroll in _scrolls) {
      scroll.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final initial = _source.feeds.indexWhere((list) => list.id == widget.listId);
    return DefaultTabController(
      length: _lists.length,
      initialIndex: initial < 0 ? 0 : initial,
      child: Scaffold(
        appBar: AppBar(
          title: Row(
            children: [
              WebSourceMark(source: _source),
              const SizedBox(width: 12),
              Flexible(child: Text(_source.name, overflow: TextOverflow.ellipsis)),
            ],
          ),
          actions: [
            _FollowButton(source: _source),
            if (_source.search != null)
              IconButton(
                tooltip: L10n.of(context).search_in_plugin(_source.name),
                icon: const Icon(Icons.search),
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute<void>(builder: (_) => WebSourceSearchScreen(source: _source)),
                ),
              ),
            _SourceMenu(source: _source),
          ],
          bottom: _lists.length < 2
              ? null
              : TabBar(
                  isScrollable: true,
                  tabAlignment: TabAlignment.start,
                  indicatorColor: webSourceColor(context, _source),
                  tabs: [for (final list in _source.feeds) Tab(text: list.name)],
                ),
        ),
        body: TabBarView(
          children: [
            for (final (index, store) in _lists.indexed)
              WebSourceListPane(store: store, scrollController: _scrolls[index]),
          ],
        ),
      ),
    );
  }
}

/// Follows the list on show: it rebuilds when the tab or the follows change.
class _FollowButton extends StatelessWidget {
  final WebSourceDefinition source;

  const _FollowButton({required this.source});

  @override
  Widget build(BuildContext context) {
    final follows = context.read<WebSourceFollowsStore>();
    final tabs = DefaultTabController.of(context);
    final l10n = L10n.of(context);
    return ListenableBuilder(
      listenable: tabs,
      builder: (context, _) => ScopedBuilder<WebSourceFollowsStore, List<PluginAccountSubscription>>(
        store: follows,
        onState: (context, _) {
          final list = source.feeds[tabs.index];
          final following = follows.isFollowing(source.id, list.id);
          return IconButton(
            tooltip: following ? l10n.plugin_websources_unfollow : l10n.plugin_websources_follow,
            isSelected: following,
            icon: const Icon(Icons.add_circle_outline),
            selectedIcon: const Icon(Icons.check_circle),
            onPressed: () => following
                ? follows.unfollow(webSourceSubscriptionId(source.id, list.id))
                : follows.follow(source, list),
          );
        },
      ),
    );
  }
}

enum _SourceAction { openSite, remove }

class _SourceMenu extends StatelessWidget {
  final WebSourceDefinition source;

  const _SourceMenu({required this.source});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PopupMenuButton<_SourceAction>(
      onSelected: (action) => switch (action) {
        _SourceAction.openSite => openLink(context, source.homepage.toString()),
        _SourceAction.remove => removeWebSource(context, source),
      },
      itemBuilder: (_) => [
        PopupMenuItem(value: _SourceAction.openSite, child: Text(l10n.open_in_browser)),
        PopupMenuItem(value: _SourceAction.remove, child: Text(l10n.plugin_websources_remove)),
      ],
    );
  }
}

/// Removes [source] and its follows once the reader confirms, and leaves
/// its screen.
Future<void> removeWebSource(BuildContext context, WebSourceDefinition source) async {
  final sources = context.read<WebSourcesStore>();
  final follows = context.read<WebSourceFollowsStore>();
  final navigator = Navigator.of(context);
  if (!await confirmWebSourceRemoval(context, source)) return;
  await follows.removeSource(source.id);
  await sources.remove(source.id);
  if (navigator.canPop()) navigator.pop();
}
