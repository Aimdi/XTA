/// Pure helpers for building a booru tag query out of separate tags.
library;

import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/plugin_query_words.dart';

enum BooruTagOperator {
  none(''),
  exclude('-'),
  either('~');

  final String prefix;

  const BooruTagOperator(this.prefix);

  static BooruTagOperator of(String token) => switch (token.isEmpty ? '' : token[0]) {
    '-' => exclude,
    '~' => either,
    _ => none,
  };
}

/// Metatag names the supported engines share. A colon elsewhere is part of
/// the tag itself (`re:zero`, `:d`), so only these read as metatags.
const booruMetatagNames = {
  'age',
  'date',
  'fav',
  'favcount',
  'filetype',
  'height',
  'id',
  'md5',
  'mpixels',
  'order',
  'ordfav',
  'parent',
  'pool',
  'rating',
  'ratio',
  'score',
  'sort',
  'source',
  'status',
  'tagcount',
  'user',
  'width',
};

/// One query token split into operator, metatag name and value.
class BooruQueryToken {
  final BooruTagOperator operator;
  final String? metatag;
  final String value;

  const BooruQueryToken({required this.operator, required this.metatag, required this.value});

  factory BooruQueryToken.parse(String raw) {
    final operator = BooruTagOperator.of(raw);
    final body = raw.substring(operator.prefix.length);
    final colon = body.indexOf(':');
    final name = colon > 0 ? body.substring(0, colon).toLowerCase() : null;
    if (name == null || !booruMetatagNames.contains(name)) {
      return BooruQueryToken(operator: operator, metatag: null, value: body);
    }
    return BooruQueryToken(operator: operator, metatag: name, value: body.substring(colon + 1));
  }

  /// Tags compete for one slot: `x`, `-x` and `~x` replace each other, and a
  /// query has one rating, order or sort.
  String get slot => switch (metatag) {
    null => 'tag:${value.toLowerCase()}',
    'rating' when operator == BooruTagOperator.none => 'rating',
    'order' || 'sort' => 'order',
    final name => '${operator.prefix}$name:${value.toLowerCase()}',
  };
}

List<String> booruQueryTokens(String raw) => queryWords(raw);

String booruQueryText(Iterable<String> tokens) => queryText(tokens);

/// [tags] followed by [added]. A repeat is skipped and a token takes the place
/// of the one it competes with (see [BooruQueryToken.slot]).
List<String> appendBooruTokens(List<String> tags, Iterable<String> added) => added.fold(tags, _appendOne);

List<String> _appendOne(List<String> tags, String raw) {
  final token = raw.trim();
  if (token.isEmpty || tags.contains(token)) return tags;
  final parsed = BooruQueryToken.parse(token);
  if (parsed.value.isEmpty) return tags;
  return [
    for (final tag in tags)
      if (BooruQueryToken.parse(tag).slot != parsed.slot) tag,
    token,
  ];
}

/// Canonical, lower-cased form used to store a saved search or a history
/// entry. Null when nothing is left.
String? normaliseBooruQuery(String raw) {
  final tokens = appendBooruTokens(const [], booruQueryTokens(raw.toLowerCase()));
  return tokens.isEmpty ? null : booruQueryText(tokens);
}

/// History identity: `a b` and `b a` are the same search.
String booruTagSetKey(String query) {
  final tokens = booruQueryTokens(query.toLowerCase()).toSet().toList()..sort();
  return booruQueryText(tokens);
}

/// Text typed into the tag field. Every token followed by whitespace is
/// finished; whatever follows the last whitespace is still being typed.
({List<String> done, String rest}) splitBooruInput(String text) => splitQueryInput(text);

/// What to ask the host to complete for [input], without its operator. Null
/// while it is too short or names a metatag, which hosts do not autocomplete.
String? booruSuggestionPrefix(String input) {
  final token = BooruQueryToken.parse(input.trim());
  if (token.metatag != null || token.value.length < 2) return null;
  return token.value.toLowerCase();
}

/// [suggestion] carrying over the operator typed in [input] (`-blu` → `-blue_eyes`).
String booruTokenFor(String input, String suggestion) => '${BooruTagOperator.of(input.trim()).prefix}$suggestion';

/// Tags most shared by [posts], leaving out the ones already in [query].
List<String> booruRelatedTags(List<BooruPost> posts, List<String> query, {int limit = 12}) {
  final taken = {for (final token in query) BooruQueryToken.parse(token).value.toLowerCase()};
  final counts = posts
      .expand((post) => post.tags)
      .where((tag) => !taken.contains(tag.toLowerCase()))
      .fold(<String, int>{}, (counts, tag) => counts..update(tag, (count) => count + 1, ifAbsent: () => 1));
  final shared = counts.entries.where((entry) => entry.value > 1).toList()
    ..sort((a, b) => b.value != a.value ? b.value.compareTo(a.value) : a.key.compareTo(b.key));
  return [for (final entry in shared.take(limit)) entry.key];
}

/// The metatag the host understands for [rating], or null when it has none.
/// Gelbooru itself spells ratings out; its older forks only know safe,
/// questionable and explicit.
String? booruRatingMetatag(BooruEngine engine, BooruRating rating, {required String host}) {
  final value = switch (engine) {
    BooruEngine.danbooru => rating.code,
    BooruEngine.moebooru || BooruEngine.e621 => rating == BooruRating.sensitive ? null : _safeCode(rating),
    BooruEngine.gelbooruV2 when host.contains('gelbooru.com') => rating.name,
    BooruEngine.gelbooruV2 => rating == BooruRating.sensitive ? null : _safeWord(rating),
  };
  return value == null ? null : 'rating:$value';
}

String _safeCode(BooruRating rating) => rating == BooruRating.general ? 's' : rating.code;

String _safeWord(BooruRating rating) => rating == BooruRating.general ? 'safe' : rating.name;

/// Highest score first.
String booruScoreOrderMetatag(BooruEngine engine) => engine == BooruEngine.gelbooruV2 ? 'sort:score' : 'order:score';

/// Gelbooru writes OR as `{a ~ b}`, so a leading `~` only works elsewhere.
bool booruSupportsEither(BooruEngine engine) => engine != BooruEngine.gelbooruV2;

/// [input] with [operator] put in front, or taken off when it is already there.
String toggleBooruOperator(String input, BooruTagOperator operator) {
  final current = BooruTagOperator.of(input);
  final body = input.substring(current.prefix.length);
  return current == operator ? body : '${operator.prefix}$body';
}
