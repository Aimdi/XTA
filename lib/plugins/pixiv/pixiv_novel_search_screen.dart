import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_list.dart';
import 'package:xta/plugins/pixiv/pixiv_search_filters.dart';
import 'package:xta/plugins/pixiv/pixiv_search_results.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_search_shortcuts.dart';
import 'package:xta/plugins/pixiv/pixiv_search_store.dart';
import 'package:xta/plugins/plugin_search_history.dart';

/// Novel search's recent searches, kept apart from the works' own.
class PixivNovelSearchHistory extends PluginSearchHistoryStore {
  PixivNovelSearchHistory(BasePrefService prefs)
    : super(prefs, optionPluginPixivNovelSearchHistory, identity: (query) => query.toLowerCase());
}

/// Novel search as Novel mode's Search section and a screen of its own.
class PixivNovelSearchScreen extends StatelessWidget {
  final String? initialQuery;
  final bool embedded;

  const PixivNovelSearchScreen({super.key, this.initialQuery, this.embedded = false});

  @override
  Widget build(BuildContext context) =>
      PixivSearchScreen(kind: PixivSearchKind.novels, initialQuery: initialQuery, embedded: embedded);
}

/// What a query of digits offers to open in novel search: the novel, the
/// series and the author with that id.
const List<PixivNumericShortcut> pixivNovelNumericShortcuts = [
  pixivNovelShortcut,
  pixivNovelSeriesShortcut,
  pixivUserShortcut,
];

Widget pixivNovelShortcut(BuildContext context, int id) => ListTile(
  key: ValueKey('pixiv-open-novel-$id'),
  leading: const Icon(Icons.menu_book_outlined),
  title: Text(L10n.of(context).plugin_pixiv_search_open_novel('$id')),
  onTap: () => openPixivLinkOrSay(context, PixivNovelLinkRef(id)),
);

Widget pixivNovelSeriesShortcut(BuildContext context, int id) => ListTile(
  key: ValueKey('pixiv-open-novel-series-$id'),
  leading: const Icon(Icons.collections_bookmark_outlined),
  title: Text(L10n.of(context).plugin_pixiv_search_open_novel_series('$id')),
  onTap: () => openPixivLinkOrSay(context, PixivNovelSeriesLinkRef(id)),
);

/// What novel search opens for pasted [text]: a bare number is a novel, a link what it names.
PixivLinkRef? pixivNovelSearchLink(String text) => switch (pixivNumericQuery(text)) {
  final id? => PixivNovelLinkRef(id),
  null => parsePixivLink(text),
};

/// The novels a search found, under the filter bar.
class PixivSearchNovels extends StatelessWidget {
  final PixivSearchStore store;
  final PixivSearchState state;

  const PixivSearchNovels({super.key, required this.store, required this.state});

  @override
  Widget build(BuildContext context) => Column(
    children: [
      PixivSearchFilterHeader(store: store, state: state),
      Expanded(
        child: PixivNovelFeed(store: store.novels, emptyMessage: L10n.of(context).plugin_pixiv_search_empty),
      ),
    ],
  );
}
