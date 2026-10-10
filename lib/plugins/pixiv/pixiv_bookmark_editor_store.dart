import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_api.dart';
import 'package:xta/plugins/pixiv/pixiv_in_flight.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

/// The bookmark as the editor is shaping it: visibility, the tag checklist,
/// what the add-tag field holds and the reader's own tags it suggests from.
class PixivBookmarkDraft {
  /// Whether the bookmark detail has arrived; until then there is nothing to edit.
  final bool loaded;
  final bool isBookmarked;

  /// `public` or `private`.
  final String restrict;
  final List<PixivBookmarkTagChoice> tags;
  final String query;
  final List<PixivBookmarkTag> known;
  final bool saving;
  final Object? saveError;

  const PixivBookmarkDraft({
    this.loaded = false,
    required this.isBookmarked,
    required this.restrict,
    this.tags = const [],
    this.query = '',
    this.known = const [],
    this.saving = false,
    this.saveError,
  });

  bool get isPrivate => restrict == 'private';

  List<String> get checkedTags => [
    for (final tag in tags)
      if (tag.checked) tag.name,
  ];

  bool get allChecked => tags.isNotEmpty && tags.every((tag) => tag.checked);

  /// Up to eight of the reader's tags matching what is typed.
  List<PixivBookmarkTag> get suggestions => pixivBookmarkTagMatches(known, query);

  PixivBookmarkDraft copyWith({
    bool? loaded,
    bool? isBookmarked,
    String? restrict,
    List<PixivBookmarkTagChoice>? tags,
    String? query,
    List<PixivBookmarkTag>? known,
    bool? saving,
    Object? saveError,
    bool clearSaveError = false,
  }) => PixivBookmarkDraft(
    loaded: loaded ?? this.loaded,
    isBookmarked: isBookmarked ?? this.isBookmarked,
    restrict: restrict ?? this.restrict,
    tags: tags ?? this.tags,
    query: query ?? this.query,
    known: known ?? this.known,
    saving: saving ?? this.saving,
    saveError: clearSaveError ? null : saveError ?? this.saveError,
  );

  /// The draft a work's bookmark detail opens with. A work not bookmarked yet
  /// takes [defaultRestrict], and its own tags come pre-checked from [autoTags].
  PixivBookmarkDraft fromDetail(
    PixivBookmarkDetail detail, {
    required String defaultRestrict,
    List<String> autoTags = const [],
  }) => copyWith(
    loaded: true,
    isBookmarked: detail.isBookmarked,
    restrict: detail.isBookmarked ? detail.restrict : defaultRestrict,
    tags: [
      for (final tag in detail.tags)
        detail.isBookmarked || !autoTags.contains(tag.name) ? tag : (name: tag.name, checked: true),
    ],
  );

  PixivBookmarkDraft withTag(String name, {required bool checked}) =>
      copyWith(tags: [for (final tag in tags) tag.name == name ? (name: name, checked: checked) : tag]);

  PixivBookmarkDraft withAllChecked(bool checked) =>
      copyWith(tags: [for (final tag in tags) (name: tag.name, checked: checked)]);

  /// The tags in [input] checked: new ones on top in the order typed, ones
  /// already listed checked where they are. The field empties.
  PixivBookmarkDraft withAdded(String input) {
    final typed = pixivTagsFromInput(input).toSet();
    final listed = {for (final tag in tags) tag.name};
    return copyWith(
      query: '',
      tags: [
        for (final name in typed)
          if (!listed.contains(name)) (name: name, checked: true),
        for (final tag in tags) typed.contains(tag.name) ? (name: tag.name, checked: true) : tag,
      ],
    );
  }
}

/// The reader's public and private tags as one list, each name once.
List<PixivBookmarkTag> mergePixivBookmarkTags(Iterable<List<PixivBookmarkTag>> lists) => {
  for (final list in lists)
    for (final tag in list) tag.name: tag,
}.values.toList();

/// The bookmark editor of one work: loads its bookmark detail, edits the
/// draft and saves or removes through [PixivBookmarkActions].
class PixivBookmarkEditorStore extends Store<PixivBookmarkDraft> {
  final PixivBookmarkActions actions;
  final PixivIllust illust;
  final _inFlight = PixivInFlight();
  var _knownAsked = false;

  PixivBookmarkEditorStore(this.actions, this.illust)
    : super(
        PixivBookmarkDraft(isBookmarked: actions.bookmarks.isBookmarked(illust), restrict: actions.defaultRestrict),
      );

  Future<void> load() => _inFlight.track(
    execute(() async {
      final detail = await actions.api.detail(illust.id);
      return state.fromDetail(detail, defaultRestrict: actions.defaultRestrict, autoTags: actions.autoTags(illust));
    }),
  );

  void setPrivate(bool private) => update(state.copyWith(restrict: private ? 'private' : 'public'));

  void setTag(String name, bool checked) => update(state.withTag(name, checked: checked));

  void toggleAll() => update(state.withAllChecked(!state.allChecked));

  void addTags(String input) => update(state.withAdded(input));

  void setQuery(String query) {
    update(state.copyWith(query: query));
    if (query.trim().isNotEmpty && !_knownAsked) _inFlight.track(_loadKnown());
  }

  /// The reader's tags only feed suggestions; when they fail to load the field
  /// still takes any tag, and the next keystroke asks again.
  Future<void> _loadKnown() async {
    _knownAsked = true;
    try {
      final pages = await Future.wait([
        for (final restrict in ['public', 'private']) actions.api.tags(restrict: restrict),
      ]);
      update(state.copyWith(known: mergePixivBookmarkTags(pages.map((page) => page.items))));
    } catch (_) {
      _knownAsked = false;
    }
  }

  /// Files the bookmark as drafted; the outcome once Pixiv took it, else null
  /// with the error kept on the draft.
  Future<PixivBookmarkOutcome?> save() =>
      _write(() => actions.bookmark(illust, restrict: state.restrict, tags: state.checkedTags));

  Future<PixivBookmarkOutcome?> remove() => _write(() => actions.unbookmark(illust));

  Future<PixivBookmarkOutcome?> _write(Future<PixivBookmarkOutcome> Function() write) {
    update(state.copyWith(saving: true, clearSaveError: true));
    return _inFlight.track(
      actions.bookmarks
          .exclusive(illust.id, write)
          .then<PixivBookmarkOutcome?>(
            (outcome) {
              update(state.copyWith(saving: false));
              return outcome;
            },
            onError: (Object error) {
              update(state.copyWith(saving: false, saveError: error));
              return null;
            },
          ),
    );
  }

  /// Destroys the store once every load and write it started has landed.
  Future<void> close() => _inFlight.whenSettled(destroy);
}
