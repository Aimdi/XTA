import 'package:xta/plugins/booru/booru_models.dart';

/// Whether [post] answers the metatag `name:value` the way the hosts' own
/// searches read it, for blacklist entries checked on the device. Null when
/// the post does not carry what the metatag asks about (its uploader, say),
/// or the app cannot read the value; an entry using it then hides nothing.
bool? booruMetatagMatches(BooruPost post, String name, String value) => switch (name) {
  'score' => _numberMatches(post.score, value),
  'id' => _numberMatches(int.tryParse(post.id), value),
  'width' => _numberMatches(post.width, value),
  'height' => _numberMatches(post.height, value),
  'favcount' => _numberMatches(post.favCount, value),
  'tagcount' => _numberMatches(post.tags.length, value),
  'user' => _textMatches(post.uploader, value),
  'filetype' => _textMatches(post.fileExt, value),
  'md5' => _textMatches(post.md5, value),
  'parent' => _presenceMatches(post.parentId, value) ?? _textMatches(post.parentId, value),
  'source' => _presenceMatches(post.source, value) ?? _textMatches(post.source, value, prefix: true),
  _ => null,
};

final _range = RegExp(r'^(-?\d+)?\.\.(-?\d+)?$');
final _comparison = RegExp(r'^(<=|>=|<|>)?(-?\d+)$');

/// `5`, `>5`, `>=5`, `<5`, `<=5`, `1..9`, `..9` or `1..`.
bool? _numberMatches(int? number, String value) {
  if (number == null) return null;
  if (_range.firstMatch(value) case final range?) {
    final (low, high) = (int.tryParse(range[1] ?? ''), int.tryParse(range[2] ?? ''));
    if (low == null && high == null) return null;
    return (low == null || number >= low) && (high == null || number <= high);
  }
  final comparison = _comparison.firstMatch(value);
  if (comparison == null) return null;
  final bound = int.parse(comparison[2]!);
  return switch (comparison[1]) {
    '<' => number < bound,
    '<=' => number <= bound,
    '>' => number > bound,
    '>=' => number >= bound,
    _ => number == bound,
  };
}

/// `none` and `any` ask whether the post has the field at all.
bool? _presenceMatches(String? field, String value) => switch (value.toLowerCase()) {
  'none' => field == null || field.isEmpty,
  'any' => field != null && field.isNotEmpty,
  _ => null,
};

/// Case-insensitive, with `*` standing for any text; with [prefix], the
/// value only has to start the field, as a source search reads it.
bool? _textMatches(String? field, String value, {bool prefix = false}) {
  if (field == null || field.isEmpty || value.isEmpty) return null;
  final pattern = value.split('*').map(RegExp.escape).join('.*');
  return RegExp('^$pattern${prefix ? '' : r'$'}', caseSensitive: false).hasMatch(field);
}
