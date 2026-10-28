import 'package:flutter_triple/flutter_triple.dart';

/// How many choices a quick-pick sheet shows before its All link.
const kQuickPickShown = 6;

enum QuickPickSection { recent, all, matches, noMatch }

/// The search, whether every choice is out, and the choices created in the
/// sheet, newest first.
typedef QuickPickView = ({String query, bool showAll, List<String> createdIds});

/// What the chip area holds: the choices, under which label, whether the typed
/// name can become a new one, and whether All has more to show.
typedef QuickPickChips<T> = ({List<T> items, QuickPickSection section, bool canCreate, bool hasMore});

typedef NameOf<T> = String Function(T item);

/// The choices whose names hold [query], whatever its case.
List<T> quickPickMatches<T>(Iterable<T> items, String query, NameOf<T> nameOf) {
  final needle = query.trim().toLowerCase();
  return [
    for (final item in items)
      if (nameOf(item).toLowerCase().contains(needle)) item,
  ];
}

/// Whether a choice is already called [query], whatever its case.
bool quickPickNameTaken<T>(Iterable<T> items, String query, NameOf<T> nameOf) {
  final name = query.trim().toLowerCase();
  return items.any((item) => nameOf(item).trim().toLowerCase() == name);
}

/// The chips for [view], from [ordered], every choice in the order the sheet
/// opened with: the first [shown] until All, or the matches for a search.
QuickPickChips<T> quickPickChips<T>(
  List<T> ordered,
  QuickPickView view,
  NameOf<T> nameOf, {
  int shown = kQuickPickShown,
}) {
  final query = view.query.trim();
  if (query.isNotEmpty) {
    final matches = quickPickMatches(ordered, query, nameOf);
    return (
      items: matches,
      section: matches.isEmpty ? QuickPickSection.noMatch : QuickPickSection.matches,
      canCreate: !quickPickNameTaken(ordered, query, nameOf),
      hasMore: false,
    );
  }
  final hasMore = !view.showAll && ordered.length > shown;
  return (
    items: hasMore ? ordered.take(shown).toList() : ordered,
    section: view.showAll ? QuickPickSection.all : QuickPickSection.recent,
    canCreate: false,
    hasMore: hasMore,
  );
}

/// The choices created in the sheet lead, then [openOrder], then any choice
/// that came since, each once and only while it exists.
List<T> quickPickOrdered<T>(
  Iterable<T> items,
  List<String> openOrder,
  List<String> createdIds,
  String Function(T item) idOf,
) {
  final byId = {for (final item in items) idOf(item): item};
  final ids = <String>{...createdIds, ...openOrder, ...byId.keys};
  return [for (final id in ids) ?byId[id]];
}

class QuickPickStore extends Store<QuickPickView> {
  QuickPickStore() : super((query: '', showAll: false, createdIds: const []));

  void search(String query) => update((query: query, showAll: state.showAll, createdIds: state.createdIds));

  void showAll() => update((query: state.query, showAll: true, createdIds: state.createdIds));

  /// [id] was created from the search, which it empties.
  void created(String id) => update((query: '', showAll: state.showAll, createdIds: [id, ...state.createdIds]));
}
