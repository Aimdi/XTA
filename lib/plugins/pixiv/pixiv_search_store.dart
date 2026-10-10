import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_fetch_store.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_api.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_store.dart';
import 'package:xta/plugins/pixiv/pixiv_search_api.dart';
import 'package:xta/plugins/pixiv/pixiv_search_filters.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_search_history.dart';

/// How many recent searches show before the rest fold behind "Show all".
const pixivSearchHistoryFolded = 12;

@immutable
class PixivSearchState {
  /// The search the results belong to; empty on the landing.
  final String word;

  /// The field's text as the reader edits it.
  final String typed;
  final PixivSearchFilter filter;

  /// Whether [filter] is kept for later searches.
  final bool remembered;
  final bool isPremium;
  final List<PixivTrendTag> suggestions;

  /// Pixiv's free popular preview, drawn as a strip over date-sorted results.
  final List<PixivIllust> popular;
  final bool historyExpanded;

  const PixivSearchState({
    required this.filter,
    this.word = '',
    this.typed = '',
    this.remembered = false,
    this.isPremium = false,
    this.suggestions = const [],
    this.popular = const [],
    this.historyExpanded = false,
  });

  bool get searched => word.isNotEmpty;

  /// Popular sort without Premium: the free preview fills the grid instead.
  bool get previewMode => filter.sort == PixivSearchSort.popular && !isPremium;

  bool get showsPopularStrip => !filter.sort.byPopularity && popular.isNotEmpty;

  /// What the popular strip's works depend on; null while popularity already
  /// sorts the grid and the strip is not drawn.
  Object? get popularStripKey => filter.sort.byPopularity ? null : (word, filter.target, filter.hideAi, filter.ugoira);

  int? get numericId => pixivNumericQuery(typed);

  /// Suggestions or id shortcuts cover the results while the reader edits.
  bool get picking {
    final text = typed.trim();
    return text.isNotEmpty && text != word && (suggestions.isNotEmpty || numericId != null);
  }

  PixivSearchState copyWith({
    String? word,
    String? typed,
    PixivSearchFilter? filter,
    bool? remembered,
    List<PixivTrendTag>? suggestions,
    List<PixivIllust>? popular,
    bool? historyExpanded,
  }) => PixivSearchState(
    word: word ?? this.word,
    typed: typed ?? this.typed,
    filter: filter ?? this.filter,
    remembered: remembered ?? this.remembered,
    isPremium: isPremium,
    suggestions: suggestions ?? this.suggestions,
    popular: popular ?? this.popular,
    historyExpanded: historyExpanded ?? this.historyExpanded,
  );
}

/// Trending tags as the reader's mutes leave them: muted tags go, and a tag
/// whose picture is a muted work keeps its name without the picture.
List<PixivTrendTag> pixivVisibleTrendTags(List<PixivTrendTag> tags, PixivMuteState mute) => [
  for (final tag in tags)
    if (!mute.tags.contains(tag.name.toLowerCase()))
      switch (tag.illust) {
        final illust? when mute.isMuted(illust) => PixivTrendTag(name: tag.name, translatedName: tag.translatedName),
        _ => tag,
      },
];

/// A Pixiv search: the field, its suggestions, the filter and the results.
/// With [illustsOnly] it only fetches works and keeps no history, as a
/// favourite tag's tab needs. Of [kind] novels it finds novels through
/// [novelApi] instead of works, and its landing has no suggested creators.
class PixivSearchStore extends Store<PixivSearchState> {
  final PixivSearchApi api;
  final PixivNovelApi? novelApi;
  final PixivSearchKind kind;
  final BasePrefService prefs;
  final PixivMuteStore mute;
  final PluginSearchHistoryStore? history;
  final bool illustsOnly;
  final Duration debounce;
  final DateTime Function() clock;

  late final results = PixivIllustListStore(_nothing, filter: _visibleWorks);
  late final novels = PixivNovelListStore(_noNovels, filter: mute.filterNovels);
  late final users = PixivPagedListStore<PixivUserPreview>(
    _noUsers,
    keyOf: (preview) => preview.user.id,
    filter: _visibleUsers,
  );

  /// The landing's lists, each loading, failing and retrying on its own.
  late final trending = PixivFetchStore<List<PixivTrendTag>>(
    kind.isWorks ? api.trendingTags : novelApi!.trendingTags,
    const [],
  );
  late final creators = PixivFetchStore<List<PixivUser>>(api.recommendedUsers, const []);

  Timer? _suggestTimer;
  var _closed = false;

  PixivSearchStore({
    required this.api,
    required this.prefs,
    required this.mute,
    this.novelApi,
    this.kind = PixivSearchKind.works,
    this.history,
    this.illustsOnly = false,
    this.debounce = const Duration(milliseconds: 300),
    DateTime Function()? clock,
  }) : assert(kind == PixivSearchKind.works || novelApi != null, 'novel search needs the novel API'),
       clock = clock ?? DateTime.now,
       super(
         PixivSearchState(
           filter: pixivStartingFilter(prefs, isPremium: api.isPremium, kind: kind),
           remembered: readPixivSearchFilter(prefs, kind: kind) != null,
           isPremium: api.isPremium,
         ),
       );

  static Future<PixivIllustPage> _nothing({String? nextUrl}) async => const PixivIllustPage(illusts: []);

  static Future<PixivNovelPage> _noNovels({String? nextUrl}) async => const PixivPage([]);

  static Future<PixivPage<PixivUserPreview>> _noUsers({String? nextUrl}) async => const PixivPage([]);

  List<PixivIllust> _visibleWorks(List<PixivIllust> illusts) =>
      pixivUgoiraFiltered(mute.filter(illusts), state.filter.ugoira);

  List<PixivUserPreview> _visibleUsers(List<PixivUserPreview> previews) => [
    for (final preview in previews)
      if (!mute.state.authorIds.contains(preview.user.id)) preview,
  ];

  /// Takes the field's text and asks for tags completing its last word.
  void type(String text) {
    _suggestTimer?.cancel();
    final word = pixivTypedWord(text);
    final link = pixivNumericQuery(text) == null && parsePixivLink(text) != null;
    final quiet = word.isEmpty || link;
    update(state.copyWith(typed: text, suggestions: quiet ? const [] : null));
    if (quiet) return;
    _suggestTimer = Timer(debounce, () => _suggest(text, word));
  }

  Future<void> _suggest(String text, String word) async {
    try {
      final tags = await api.autocomplete(word);
      if (_closed || state.typed != text) return;
      update(state.copyWith(suggestions: tags));
    } catch (_) {
      // Typing goes on; suggestions are a convenience, not a gate.
    }
  }

  /// A tapped suggestion takes the place of the word being typed. After
  /// earlier words it only edits the query; on its own it searches. Returns
  /// the field's new text.
  String pick(PixivTrendTag tag) {
    _suggestTimer?.cancel();
    if (pixivHasEarlierWords(state.typed)) {
      final text = pixivReplaceTypedWord(state.typed, tag.name);
      update(state.copyWith(typed: text, suggestions: const []));
      return text;
    }
    unawaited(search(tag.name));
    return tag.name;
  }

  Future<void> search(String text) async {
    final word = text.trim();
    if (word.isEmpty || _closed) return;
    _suggestTimer?.cancel();
    update(state.copyWith(word: word, typed: word, suggestions: const [], popular: const []));
    if (!illustsOnly) await history?.remember(word);
    if (_closed) return;
    if (!illustsOnly) _loadUsers(word);
    await _loadResults(strip: true);
  }

  /// Creators depend on the words alone, so filter changes leave them be.
  void _loadUsers(String word) {
    users.useLoader(({nextUrl}) => api.users(word, nextUrl: nextUrl));
    users.setLoading(true);
    unawaited(users.refresh());
  }

  /// Fetches the works or novels under the current filter, and the popular
  /// strip too when [strip] says what it shows has changed.
  Future<void> _loadResults({required bool strip}) async {
    final search = state;
    if (!kind.isWorks) return _reload(novels, _novelLoader(search));
    if (strip && !illustsOnly) unawaited(_loadPopularStrip(search));
    await _reload(results, _worksLoader(search));
  }

  /// Puts [list] in its loading state at once, rather than a moment of "no
  /// results", and fetches its first page from [loader].
  Future<void> _reload<T>(PixivPagedListStore<T> list, PixivPageLoader<T> loader) async {
    list.useLoader(loader);
    list.setLoading(true);
    await list.refresh();
  }

  PixivPageLoader<PixivNovel> _novelLoader(PixivSearchState search) {
    final query = pixivSearchQuery(search.filter, search.word, now: clock(), kind: kind);
    final includeAi = !search.filter.hideAi;
    return ({nextUrl}) => novelApi!.search(query, nextUrl: nextUrl, includeAi: includeAi);
  }

  PixivIllustPageLoader _worksLoader(PixivSearchState search) {
    final filter = search.filter;
    final includeAi = !filter.hideAi;
    if (search.previewMode) {
      final word = pixivSearchWord(search.word, filter.usersIri);
      return ({nextUrl}) => api.popularPreview(word, filter.target, nextUrl: nextUrl, includeAi: includeAi);
    }
    final query = pixivSearchQuery(filter, search.word, now: clock());
    return ({nextUrl}) => api.illusts(query, nextUrl: nextUrl, includeAi: includeAi);
  }

  Future<void> _loadPopularStrip(PixivSearchState search) async {
    final key = search.popularStripKey;
    if (key == null) return;
    try {
      final page = await api.popularPreview(search.word, search.filter.target, includeAi: !search.filter.hideAi);
      if (_closed || state.popularStripKey != key) return;
      update(state.copyWith(popular: _visibleWorks(page.illusts)));
    } catch (_) {
      // Garnish over the grid; the grid itself is the answer.
    }
  }

  /// Uses [filter] from now on, kept for later searches when [remember] says
  /// so (unchanged when null), and fetches the current search's works under it.
  Future<void> applyFilter(PixivSearchFilter filter, {bool? remember}) async {
    final next = state.copyWith(filter: filter, remembered: remember ?? state.remembered);
    final strip = next.popularStripKey != state.popularStripKey;
    update(strip ? next.copyWith(popular: const []) : next);
    await savePixivSearchFilter(prefs, next.remembered ? filter : null, kind: kind);
    if (!_closed && state.searched) await _loadResults(strip: strip);
  }

  /// Trending tags and, for works, suggested creators, each on its own;
  /// [force] reloads lists that already loaded, as pull-to-refresh does.
  Future<void> loadLanding({bool force = false}) async {
    await Future.wait([
      if (force || trending.state.isEmpty) trending.load(),
      if (kind.isWorks && (force || creators.state.isEmpty)) creators.load(),
    ]);
  }

  void toggleHistory() => update(state.copyWith(historyExpanded: !state.historyExpanded));

  @override
  Future<void> destroy() async {
    _closed = true;
    _suggestTimer?.cancel();
    final PixivPagedListStore<Object> found = kind.isWorks ? results : novels;
    await Future.wait([found.destroy(), users.destroy(), trending.destroy(), creators.destroy()]);
    await super.destroy();
  }
}
