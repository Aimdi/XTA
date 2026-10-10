import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_api.dart';
import 'package:xta/plugins/pixiv/pixiv_in_flight.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/plugins/plugin_view_store.dart';

/// Which of the reader's bookmarks Favorites shows: public or private, and
/// one tag or (null) all of them.
typedef PixivBookmarkFilter = ({String restrict, String? tag});

const _restricts = ['public', 'private'];

/// What a tag filter is called on its chip and in the picker.
String pixivBookmarkTagLabel(L10n l10n, String? tag) => switch (tag) {
  null => l10n.plugin_pixiv_bookmark_tag_all,
  pixivUnclassifiedTag => l10n.plugin_pixiv_bookmark_tag_unclassified,
  final named => named,
};

/// Asks which bookmarks to show: Public and Private tabs of the reader's
/// tags, a field that suggests matches and takes any typed tag.
Future<PixivBookmarkFilter?> showPixivBookmarkTagPicker(BuildContext context, PixivBookmarkFilter current) {
  final api = PixivBookmarkApi.of(context);
  return showModalBottomSheet<PixivBookmarkFilter>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    useSafeArea: true,
    builder: (_) => PixivBookmarkTagPicker(api: api, current: current),
  );
}

class PixivBookmarkTagPicker extends StatefulWidget {
  final PixivBookmarkApi api;
  final PixivBookmarkFilter current;

  const PixivBookmarkTagPicker({super.key, required this.api, required this.current});

  @override
  State<PixivBookmarkTagPicker> createState() => _PixivBookmarkTagPickerState();
}

class _PixivBookmarkTagPickerState extends State<PixivBookmarkTagPicker> {
  late final Map<String, PixivPagedListStore<PixivBookmarkTag>> _tags = {
    for (final restrict in _restricts)
      restrict: PixivPagedListStore(
        ({nextUrl}) => widget.api.tags(restrict: restrict, nextUrl: nextUrl),
        keyOf: (tag) => tag.name,
      ),
  };
  final _query = PluginViewStore<String>('');
  final _field = TextEditingController();
  final _inFlight = PixivInFlight();

  @override
  void dispose() {
    _field.dispose();
    final stores = <Store<Object?>>[..._tags.values, _query];
    _inFlight.whenSettled(() {
      for (final store in stores) {
        store.destroy();
      }
    });
    super.dispose();
  }

  void _pick(String restrict, String? tag) =>
      Navigator.pop<PixivBookmarkFilter>(context, (restrict: restrict, tag: tag));

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.8,
        child: DefaultTabController(
          length: _restricts.length,
          initialIndex: _restricts.indexOf(widget.current.restrict).clamp(0, _restricts.length - 1),
          child: Builder(builder: (context) => _sheet(context, l10n)),
        ),
      ),
    );
  }

  Widget _sheet(BuildContext context, L10n l10n) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
        child: Text(l10n.plugin_pixiv_bookmark_tag_filter, style: Theme.of(context).textTheme.titleLarge),
      ),
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: TextField(
          key: const ValueKey('pixiv-bookmark-tag-search'),
          controller: _field,
          autocorrect: false,
          textInputAction: TextInputAction.search,
          decoration: InputDecoration(
            hintText: l10n.plugin_pixiv_bookmark_tag_search,
            prefixIcon: const Icon(Icons.search),
          ),
          onChanged: _query.select,
          onSubmitted: (text) {
            final tag = text.trim();
            if (tag.isNotEmpty) _pick(_restricts[DefaultTabController.of(context).index], tag);
          },
        ),
      ),
      TabBar(
        tabs: [
          Tab(text: l10n.plugin_pixiv_bookmarks_public),
          Tab(text: l10n.plugin_pixiv_bookmarks_private),
        ],
      ),
      Expanded(
        child: TabBarView(
          children: [
            for (final restrict in _restricts)
              _PixivBookmarkTagTab(
                store: _tags[restrict]!,
                inFlight: _inFlight,
                query: _query,
                selected: widget.current.restrict == restrict ? widget.current.tag : '',
                onPick: (tag) => _pick(restrict, tag),
              ),
          ],
        ),
      ),
    ],
  );
}

/// One visibility's tags: All and Unclassified on top, then the reader's tags
/// page by page, or up to eight matches and the typed tag while searching.
class _PixivBookmarkTagTab extends StatefulWidget {
  final PixivPagedListStore<PixivBookmarkTag> store;
  final PixivInFlight inFlight;
  final PluginViewStore<String> query;

  /// The tag shown now on this tab: null for All, empty when it is the other tab's.
  final String? selected;
  final ValueChanged<String?> onPick;

  const _PixivBookmarkTagTab({
    required this.store,
    required this.inFlight,
    required this.query,
    required this.selected,
    required this.onPick,
  });

  @override
  State<_PixivBookmarkTagTab> createState() => _PixivBookmarkTagTabState();
}

class _PixivBookmarkTagTabState extends State<_PixivBookmarkTagTab> {
  PixivPagedListStore<PixivBookmarkTag> get _store => widget.store;

  Future<void> _refresh() => widget.inFlight.track(_store.refresh());

  @override
  void initState() {
    super.initState();
    if (_store.state.isEmpty && !_store.isLoading) _refresh();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<PluginViewStore<String>, String>(
    store: widget.query,
    onState: (context, query) => TripleBuilder<PixivPagedListStore<PixivBookmarkTag>, List<PixivBookmarkTag>>(
      store: _store,
      builder: (context, triple) => NotificationListener<ScrollNotification>(
        onNotification: (notification) {
          if (notification.metrics.extentAfter < 600) widget.inFlight.track(_store.loadMore());
          return false;
        },
        child: ListView(
          children: query.trim().isEmpty ? _browse(context, triple) : _search(context, query.trim(), triple.state),
        ),
      ),
    ),
  );

  List<Widget> _browse(BuildContext context, Triple<List<PixivBookmarkTag>> triple) {
    final l10n = L10n.of(context);
    return [
      _tile(context, icon: Icons.collections_outlined, tag: null),
      _tile(context, icon: Icons.label_off_outlined, tag: pixivUnclassifiedTag),
      const Divider(height: 1),
      for (final tag in triple.state) _tile(context, icon: Icons.sell_outlined, tag: tag.name, count: tag.count),
      if (triple.isLoading || _store.loadingMore)
        const Padding(
          padding: EdgeInsets.all(16),
          child: Center(child: CircularProgressIndicator()),
        ),
      if (triple.error != null && !triple.isLoading && triple.state.isEmpty)
        ListTile(
          title: Text(pixivErrorMessage(l10n, triple.error as Object)),
          trailing: TextButton(onPressed: _refresh, child: Text(l10n.retry)),
        ),
      if (triple.error == null && !triple.isLoading && triple.state.isEmpty)
        ListTile(enabled: false, title: Text(l10n.plugin_pixiv_bookmark_tags_none)),
    ];
  }

  List<Widget> _search(BuildContext context, String query, List<PixivBookmarkTag> tags) {
    final matches = pixivBookmarkTagMatches(tags, query);
    return [
      for (final tag in matches) _tile(context, icon: Icons.sell_outlined, tag: tag.name, count: tag.count),
      if (!matches.any((tag) => tag.name == query))
        ListTile(
          key: const ValueKey('pixiv-bookmark-tag-use'),
          leading: const Icon(Icons.search),
          title: Text(L10n.of(context).plugin_pixiv_bookmark_tag_use(query)),
          onTap: () => widget.onPick(query),
        ),
    ];
  }

  Widget _tile(BuildContext context, {required IconData icon, required String? tag, int? count}) => ListTile(
    key: ValueKey('pixiv-bookmark-tag-pick-${tag ?? ''}'),
    leading: Icon(icon),
    title: Text(pixivBookmarkTagLabel(L10n.of(context), tag)),
    trailing: count == null ? null : Text(compactCount(count)),
    selected: widget.selected == tag,
    onTap: () => widget.onPick(tag),
  );
}
