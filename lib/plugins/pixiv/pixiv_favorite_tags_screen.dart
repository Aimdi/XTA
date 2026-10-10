import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_tags.dart';
import 'package:xta/plugins/pixiv/pixiv_favorite_tags_store.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_sheet.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_search_api.dart';
import 'package:xta/plugins/pixiv/pixiv_search_store.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/reader_tab_view.dart';
import 'package:xta/utils/reader_value_store.dart';

/// Favourite tags as saved searches: a tab per tag, each a full search under
/// the remembered filter, and an edit mode to reorder and remove them.
class PixivFavoriteTagsScreen extends StatefulWidget {
  const PixivFavoriteTagsScreen({super.key});

  @override
  State<PixivFavoriteTagsScreen> createState() => _PixivFavoriteTagsScreenState();
}

class _PixivFavoriteTagsScreenState extends State<PixivFavoriteTagsScreen> {
  late final PixivFavoriteTagsStore _tags;
  final _editing = ReaderValueStore<bool>(false);

  @override
  void initState() {
    super.initState();
    _tags = PixivFavoriteTagsStore(PrefService.of(context, listen: false));
  }

  @override
  void dispose() {
    _tags.destroy();
    _editing.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<ReaderValueStore<bool>, bool>(
    store: _editing,
    onState: (context, editing) => ScopedBuilder<PixivFavoriteTagsStore, List<PixivTag>>(
      store: _tags,
      onState: (context, tags) => editing ? _editor(context, tags) : _browser(context, tags),
    ),
  );

  AppBar _appBar(BuildContext context, {required bool editing, required bool canEdit, PreferredSizeWidget? bottom}) {
    final l10n = L10n.of(context);
    return AppBar(
      title: Text(l10n.plugin_pixiv_search_favorite_tags),
      bottom: bottom,
      actions: [
        if (canEdit)
          IconButton(
            key: const ValueKey('pixiv-favorite-tags-edit'),
            tooltip: editing
                ? l10n.plugin_pixiv_search_favorite_tags_done
                : l10n.plugin_pixiv_search_favorite_tags_edit,
            icon: Icon(editing ? Icons.check : Icons.edit_outlined),
            onPressed: () => _editing.update(!editing),
          ),
      ],
    );
  }

  Widget _browser(BuildContext context, List<PixivTag> tags) {
    if (tags.isEmpty) {
      return Scaffold(
        appBar: _appBar(context, editing: false, canEdit: false),
        body: EmptyPane(icon: Icons.label_outline, message: L10n.of(context).plugin_pixiv_search_favorite_tags_empty),
      );
    }
    return DefaultTabController(
      length: tags.length,
      child: Scaffold(
        appBar: _appBar(
          context,
          editing: false,
          canEdit: true,
          bottom: TabBar(
            isScrollable: true,
            tabAlignment: TabAlignment.start,
            tabs: [for (final tag in tags) Tab(text: '#${tag.name}')],
          ),
        ),
        body: ReaderTabView(
          children: [
            for (final tag in tags) _PixivFavoriteTagTab(key: ValueKey('pixiv-favorite-tab-${tag.name}'), tag: tag),
          ],
        ),
      ),
    );
  }

  Widget _editor(BuildContext context, List<PixivTag> tags) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: _appBar(context, editing: true, canEdit: true),
      body: ReorderableListView.builder(
        buildDefaultDragHandles: false,
        padding: const EdgeInsets.only(bottom: 24),
        header: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
          child: Text(l10n.plugin_pixiv_search_favorite_tags_edit_hint),
        ),
        itemCount: tags.length,
        onReorderItem: _tags.reorder,
        itemBuilder: (context, index) => _editableRow(context, tags[index], index),
      ),
    );
  }

  Widget _editableRow(BuildContext context, PixivTag tag, int index) {
    final l10n = L10n.of(context);
    final scheme = Theme.of(context).colorScheme;
    return Dismissible(
      key: ValueKey('pixiv-favorite-${tag.name}'),
      direction: DismissDirection.endToStart,
      confirmDismiss: (_) => confirmPixivAction(context, l10n.plugin_pixiv_search_favorite_remove),
      onDismissed: (_) => _tags.remove(tag.name),
      background: Container(
        alignment: AlignmentDirectional.centerEnd,
        padding: const EdgeInsets.symmetric(horizontal: 24),
        color: scheme.errorContainer,
        child: Icon(Icons.delete_outline, color: scheme.onErrorContainer),
      ),
      child: ListTile(
        title: Text.rich(pixivTagSpan(context, tag), maxLines: 2, overflow: TextOverflow.ellipsis),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              tooltip: l10n.plugin_pixiv_search_favorite_remove,
              icon: const Icon(Icons.delete_outline),
              onPressed: () => _confirmRemove(context, tag),
            ),
            ReorderableDragStartListener(
              index: index,
              child: const SizedBox.square(dimension: kMinInteractiveDimension, child: Icon(Icons.drag_handle)),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _confirmRemove(BuildContext context, PixivTag tag) async {
    if (await confirmPixivAction(context, L10n.of(context).plugin_pixiv_search_favorite_remove)) {
      await _tags.remove(tag.name);
    }
  }
}

/// One favourite tag's works, searched once when its tab first shows and
/// kept while the reader moves between tabs.
class _PixivFavoriteTagTab extends StatefulWidget {
  final PixivTag tag;

  const _PixivFavoriteTagTab({super.key, required this.tag});

  @override
  State<_PixivFavoriteTagTab> createState() => _PixivFavoriteTagTabState();
}

class _PixivFavoriteTagTabState extends State<_PixivFavoriteTagTab> with AutomaticKeepAliveClientMixin {
  late final PixivSearchStore _search;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _search = PixivSearchStore(
      api: PixivSearchApi.of(context),
      prefs: PrefService.of(context, listen: false),
      mute: context.read<PixivMuteStore>(),
      illustsOnly: true,
    );
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _search.search(widget.tag.name);
    });
  }

  @override
  void dispose() {
    _search.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return PixivIllustFeed(store: _search.results, emptyMessage: L10n.of(context).plugin_pixiv_search_empty);
  }
}
