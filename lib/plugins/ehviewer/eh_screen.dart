import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_errors.dart';
import 'package:xta/plugins/ehviewer/eh_grid.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_search_screen.dart';
import 'package:xta/plugins/ehviewer/eh_settings.dart';
import 'package:xta/plugins/ehviewer/eh_store.dart';
import 'package:xta/plugins/ehviewer/eh_ui.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';

/// EhViewer-style home: Popular / Front / Toplist / Watched / History / Favs.
class EhScreen extends StatefulWidget {
  final ScrollController scrollController;

  const EhScreen({super.key, required this.scrollController});

  @override
  State<EhScreen> createState() => _EhScreenState();
}

class _EhScreenState extends State<EhScreen> {
  var _tab = 0;
  var _toplistPeriod = EhToplistPeriod.yesterday;
  late final EhFeedStore _popular;
  late final EhFeedStore _front;
  late final EhFeedStore _toplist;
  late final EhFeedStore _watched;

  @override
  void initState() {
    super.initState();
    final client = context.read<EhClient>();
    _popular = EhFeedStore(({pageUrl}) => client.popular(pageUrl: pageUrl));
    _front = EhFeedStore(({pageUrl}) => client.frontPage(pageUrl: pageUrl));
    _toplist = EhFeedStore(
      ({pageUrl}) => client.toplist(_toplistPeriod, pageUrl: pageUrl),
    );
    _watched = EhFeedStore(({pageUrl}) => client.watched(pageUrl: pageUrl));

    final favorites = context.read<EhFavoritesStore>();
    final history = context.read<EhHistoryStore>();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      await favorites.load();
      if (!mounted) return;
      await history.load();
      if (!mounted) return;
      await _popular.refresh();
    });
  }

  @override
  void dispose() {
    _popular.destroy();
    _front.destroy();
    _toplist.destroy();
    _watched.destroy();
    super.dispose();
  }

  Future<void> _select(int tab) async {
    final history = context.read<EhHistoryStore>();
    final favorites = context.read<EhFavoritesStore>();
    setState(() => _tab = tab);
    switch (tab) {
      case 1:
        if (_front.state.isEmpty) await _front.refresh();
      case 2:
        if (_toplist.state.isEmpty) await _toplist.refresh();
      case 3:
        if (_watched.state.isEmpty) await _watched.refresh();
      case 4:
        await history.load();
      case 5:
        await favorites.load();
    }
  }

  Future<void> _setToplist(EhToplistPeriod period) async {
    if (period == _toplistPeriod) return;
    setState(() => _toplistPeriod = period);
    await _toplist.refresh();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: Column(
        children: [
          PluginHomeChrome(
            tabs: [
              PluginHomeTab(
                label: l10n.plugin_eh_tab_popular,
                icon: Icons.whatshot_outlined,
                selected: _tab == 0,
                onTap: () => _select(0),
              ),
              PluginHomeTab(
                label: l10n.plugin_eh_tab_front,
                icon: Icons.home_outlined,
                selected: _tab == 1,
                onTap: () => _select(1),
              ),
              PluginHomeTab(
                label: l10n.plugin_eh_tab_toplist,
                icon: Icons.emoji_events_outlined,
                selected: _tab == 2,
                onTap: () => _select(2),
              ),
              PluginHomeTab(
                label: l10n.plugin_eh_tab_watched,
                icon: Icons.visibility_outlined,
                selected: _tab == 3,
                onTap: () => _select(3),
              ),
              PluginHomeTab(
                label: l10n.plugin_eh_tab_history,
                icon: Icons.history,
                selected: _tab == 4,
                onTap: () => _select(4),
              ),
              PluginHomeTab(
                label: l10n.plugin_eh_tab_favorites,
                icon: Icons.favorite_border,
                selected: _tab == 5,
                onTap: () => _select(5),
              ),
            ],
            actions: [
              IconButton(
                tooltip: l10n.search,
                icon: const Icon(Icons.search),
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const EhSearchScreen()),
                ),
              ),
              IconButton(
                tooltip: l10n.settings,
                icon: const Icon(Icons.settings_outlined),
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const EhSettingsScreen()),
                ),
              ),
            ],
          ),
          if (_tab == 2)
            SizedBox(
              height: 44,
              child: ListView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 8),
                children: [
                  for (final period in EhToplistPeriod.values)
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      child: FilterChip(
                        label: Text(ehToplistLabel(l10n, period)),
                        selected: _toplistPeriod == period,
                        onSelected: (_) => _setToplist(period),
                      ),
                    ),
                ],
              ),
            ),
          Expanded(child: _body(l10n)),
        ],
      ),
    );
  }

  Widget _body(L10n l10n) {
    return switch (_tab) {
      0 => _FeedTab(
        store: _popular,
        empty: l10n.plugin_eh_empty_list,
        scrollController: widget.scrollController,
      ),
      1 => _FeedTab(store: _front, empty: l10n.plugin_eh_empty_list),
      2 => _FeedTab(store: _toplist, empty: l10n.plugin_eh_empty_list),
      3 => _FeedTab(store: _watched, empty: l10n.plugin_eh_empty_watched),
      4 => const _HistoryTab(),
      _ => const _FavoritesTab(),
    };
  }
}

class _FeedTab extends StatelessWidget {
  final EhFeedStore store;
  final String empty;
  final ScrollController? scrollController;

  const _FeedTab({
    required this.store,
    required this.empty,
    this.scrollController,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<EhFeedStore, List<EhGallery>>.transition(
      store: store,
      onLoading: (_) => const Center(child: CircularProgressIndicator()),
      onError: (_, error) => FullPageErrorWidget(
        error: error,
        stackTrace: null,
        prefix: ehErrorMessage(l10n, error),
        onRetry: store.refresh,
      ),
      onState: (context, galleries) {
        if (galleries.isEmpty) {
          return EmptyPane(
            icon: Icons.collections_outlined,
            message: empty,
            scrollController: scrollController,
            onRefresh: store.refresh,
            action: FilledButton.icon(
              onPressed: store.refresh,
              icon: const Icon(Icons.refresh),
              label: Text(l10n.retry),
            ),
          );
        }
        return EhGalleryGrid(
          galleries: galleries,
          scrollController: scrollController,
          onRefresh: store.refresh,
          loadingMore: store.loadingMore,
          onNearEnd: store.loadMore,
        );
      },
    );
  }
}

class _FavoritesTab extends StatelessWidget {
  const _FavoritesTab();

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<EhFavoritesStore, List<EhGallery>>(
      store: context.read<EhFavoritesStore>(),
      onState: (context, galleries) {
        if (galleries.isEmpty) {
          return EmptyPane(
            icon: Icons.favorite_border,
            message: l10n.plugin_eh_empty_favorites,
            action: FilledButton.icon(
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const EhSearchScreen()),
              ),
              icon: const Icon(Icons.search),
              label: Text(l10n.search),
            ),
          );
        }
        return EhGalleryGrid(
          galleries: galleries,
          onRefresh: () => context.read<EhFavoritesStore>().load(),
        );
      },
    );
  }
}

class _HistoryTab extends StatelessWidget {
  const _HistoryTab();

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<EhHistoryStore, List<EhHistoryEntry>>(
      store: context.read<EhHistoryStore>(),
      onState: (context, entries) {
        if (entries.isEmpty) {
          return EmptyPane(
            icon: Icons.history,
            message: l10n.plugin_eh_empty_history,
            action: FilledButton.icon(
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const EhSearchScreen()),
              ),
              icon: const Icon(Icons.search),
              label: Text(l10n.search),
            ),
          );
        }
        return EhGalleryGrid(
          galleries: [for (final e in entries) e.gallery],
          onRefresh: () => context.read<EhHistoryStore>().load(),
        );
      },
    );
  }
}
