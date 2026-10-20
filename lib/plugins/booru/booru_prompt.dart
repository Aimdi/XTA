import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_models.dart';

/// The kinds of tag a prompt holds, in its order, when the reader has not
/// chosen: who and what is drawn, then by whom, then the rest. Meta tags
/// (resolution, file details) are left out.
const booruPromptDefaultOrder = [
  BooruTagCategory.character,
  BooruTagCategory.copyright,
  BooruTagCategory.artist,
  BooruTagCategory.species,
  BooruTagCategory.general,
];

/// One kind of tag in the reader's prompt order, and whether it is kept.
typedef BooruPromptKind = ({BooruTagCategory category, bool included});

/// Every kind of tag in the reader's order. Kept kinds are stored by name,
/// left-out ones with a leading `-`; kinds the stored order does not name
/// follow it, kept unless they are meta.
List<BooruPromptKind> booruPromptKinds(String? stored) {
  final named = <BooruPromptKind>[
    for (final token in (stored == null || stored.trim().isEmpty ? booruPromptDefaultStored : stored).split(','))
      if (_category(token.trim().replaceFirst('-', '')) case final category?)
        (category: category, included: !token.trim().startsWith('-')),
  ];
  return [
    ...named,
    for (final category in BooruTagCategory.values)
      if (!named.any((kind) => kind.category == category))
        (category: category, included: category != BooruTagCategory.meta),
  ];
}

/// [kinds] as the preference stores them.
String booruPromptStored(List<BooruPromptKind> kinds) =>
    kinds.map((kind) => '${kind.included ? '' : '-'}${kind.category.name}').join(',');

/// [kinds] with the one at [from] moved to [to].
List<BooruPromptKind> booruPromptMoved(List<BooruPromptKind> kinds, int from, int to) {
  final moved = [...kinds];
  moved.insert(to, moved.removeAt(from));
  return moved;
}

/// [kinds] with [category] kept or left out.
List<BooruPromptKind> booruPromptToggled(List<BooruPromptKind> kinds, BooruTagCategory category, bool included) => [
  for (final kind in kinds) kind.category == category ? (category: category, included: included) : kind,
];

/// The kinds the reader keeps, in their order.
List<BooruTagCategory> booruPromptOrder(BasePrefService prefs) => [
  for (final kind in booruPromptKinds(prefs.get<String>(optionPluginBooruPromptKinds)))
    if (kind.included) kind.category,
];

/// [tags] as an image-generation prompt: grouped by kind in [order], kinds
/// not in it left out, underscores read as spaces and parentheses escaped,
/// joined with commas. A tag of three characters or fewer keeps its
/// underscores: it is an emoticon such as `^_^`. A tag with no known kind
/// counts as general.
String booruPrompt(List<String> tags, Map<String, BooruTagCategory> kinds, List<BooruTagCategory> order) {
  BooruTagCategory kind(String tag) => kinds[tag] ?? BooruTagCategory.general;
  return [for (final category in order) ...tags.where((tag) => kind(tag) == category).map(_promptTag)].join(', ');
}

String _promptTag(String tag) {
  final spaced = tag.length <= 3 ? tag : tag.replaceAll('_', ' ');
  return spaced.replaceAll('(', r'\(').replaceAll(')', r'\)');
}

/// [booruPromptDefaultOrder] as stored, meta left out; what a reset writes.
final booruPromptDefaultStored = booruPromptStored([
  for (final category in booruPromptDefaultOrder) (category: category, included: true),
  (category: BooruTagCategory.meta, included: false),
]);

BooruTagCategory? _category(String name) =>
    BooruTagCategory.values.where((category) => category.name == name).firstOrNull;
