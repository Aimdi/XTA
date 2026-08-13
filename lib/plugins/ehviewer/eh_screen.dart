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
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';

/// EhViewer-inspired home: Popular / Front / Favorites.
class EhScreen extends StatefulWidget {
  final ScrollController scrollController;

  const EhScreen({super.key, required this.scrollController});

  @override
  State<EhScreen> createState() => _EhScreenState();
}

class _EhScreenState extends State<EhScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  late final EhFeedStore _popular;
  late final EhFeedStore _front;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 3, vsync: this);
    final client = context.read<EhClient>();
    _popular = EhFeedStore(({pageUrl}) => client.popular(pageUrl: pageUrl));
    _front = EhFeedStore(({pageUrl}) => client.frontPage(pageUrl: pageUrl));

    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      await context.read<EhFavoritesStore>().load();
      if (!mounted) return;
      await _popular.refresh();
    });

    _tabs.addListener(() {
      if (_tabs.indexIsChanging) return;
      if (_tabs.index == 1 && _front.state.isEmpty) _front.refresh();
      if (_tabs.index == 2) context.read<EhFavoritesStore>().load();
    });
  }

  @override
  void dispose() {
    _tabs.dispose();
    _popular.destroy();
    _front.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      appBar: pluginHomeTabAppBar(
        tabs: TabBar(
          controller: _tabs,
          isScrollable: true,
          tabAlignment: TabAlignment.start,
          tabs: [
            Tab(text: l10n.plugin_eh_tab_popular),
            Tab(text: l10n.plugin_eh_tab_front),
            Tab(text: l10n.plugin_eh_tab_favorites),
          ],
        ),
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
      body: TabBarView(
        controller: _tabs,
        children: [
          _FeedTab(
            store: _popular,
            empty: l10n.plugin_eh_empty_list,
            scrollController: widget.scrollController,
          ),
          _FeedTab(store: _front, empty: l10n.plugin_eh_empty_list),
          _FavoritesTab(),
        ],
      ),
    );
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
