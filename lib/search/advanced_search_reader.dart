part of 'advanced_search_model.dart';

/// The control of the filters screen an operator sets. Two operators of one query setting the same control
/// contradict or combine in a way the screen cannot show, so both then stay in the words.
enum _Control { language, from, to, minReplies, minLikes, minRetweets, since, until, content, links, replies, reposts }

typedef _Reading = ({_Control control, AdvancedSearchState Function(AdvancedSearchState state) apply});

final _operator = RegExp(r'^(-?)(\w+):(\S+)$');
final _accountGroup = RegExp(r'^\((from|to):\w{1,50}( OR \1:\w{1,50})+\)$');
final _langCode = RegExp(r'^[a-z]{2,3}(-[a-z]{2,4})?$');
final _account = RegExp(r'^\w{1,50}$');
final _count = RegExp(r'^[1-9]\d{0,8}$');
final _isoDate = RegExp(r'^\d{4}-\d{2}-\d{2}$');
const _booleanWords = {'AND', 'OR', 'NOT'};

AdvancedSearchState _readQuery(String query) {
  final text = query.trim();
  final tokens = _topLevelTokens(text) ?? const <String>[];
  final readings = [for (var i = 0; i < tokens.length; i++) _standsAlone(tokens, i) ? _readToken(tokens[i]) : null];
  final readOnce = _controlsReadOnce(readings);
  bool moved(_Reading? reading) => reading != null && readOnce.contains(reading.control);
  if (!readings.any(moved)) return AdvancedSearchState(allWords: text);
  final words = [
    for (var i = 0; i < tokens.length; i++)
      if (!moved(readings[i])) tokens[i],
  ].join(' ');
  return readings.where(moved).fold(AdvancedSearchState(allWords: words), (state, reading) => reading!.apply(state));
}

/// [query] split at its top-level spaces, a quoted phrase or a bracketed group staying one token; null when its
/// quotes or brackets do not balance.
List<String>? _topLevelTokens(String query) {
  final tokens = <String>[];
  var start = 0, depth = 0;
  var quoted = false, escaped = false;
  for (var i = 0; i < query.length; i++) {
    final character = query[i];
    if (escaped) {
      escaped = false;
    } else if (character == r'\') {
      escaped = true;
    } else if (character == '"') {
      quoted = !quoted;
    } else if (!quoted && character == '(') {
      depth++;
    } else if (!quoted && character == ')') {
      if (--depth < 0) return null;
    } else if (!quoted && depth == 0 && character.trim().isEmpty) {
      if (i > start) tokens.add(query.substring(start, i));
      start = i + 1;
    }
  }
  if (quoted || escaped || depth != 0) return null;
  return [...tokens, if (query.length > start) query.substring(start)];
}

/// An operator next to OR, AND or NOT belongs to that expression, not to the filters.
bool _standsAlone(List<String> tokens, int index) =>
    !(index > 0 && _booleanWords.contains(tokens[index - 1])) &&
    !(index + 1 < tokens.length && _booleanWords.contains(tokens[index + 1]));

Set<_Control> _controlsReadOnce(List<_Reading?> readings) {
  final controls = readings.nonNulls.map((reading) => reading.control).toList();
  return controls.where((control) => controls.where((other) => other == control).length == 1).toSet();
}

_Reading? _readToken(String token) {
  final group = _accountGroup.firstMatch(token);
  if (group != null) return _readAccountGroup(group[1]!, token);
  final operator = _operator.firstMatch(token);
  return operator == null ? null : _readOperator(operator[1] == '-', operator[2]!, operator[3]!);
}

/// `(from:alice OR from:bob)`, as the form writes several accounts.
_Reading _readAccountGroup(String operator, String group) {
  final terms = group.substring(1, group.length - 1).split(' OR ');
  final accounts = terms.map((term) => term.substring(operator.length + 1)).join(' ');
  return operator == 'from'
      ? _reading(_Control.from, (s) => s.copyWith(fromAccounts: accounts))
      : _reading(_Control.to, (s) => s.copyWith(toAccounts: accounts));
}

/// Only values the form writes back unchanged are read, so a read query rebuilds to the same text.
_Reading? _readOperator(bool negated, String name, String value) => switch ((negated, name, value)) {
  (false, 'lang', _) => _readValue(value, _langCode, _Control.language, (s, v) => s.copyWith(language: v)),
  (false, 'from', _) => _readValue(value, _account, _Control.from, (s, v) => s.copyWith(fromAccounts: v)),
  (false, 'to', _) => _readValue(value, _account, _Control.to, (s, v) => s.copyWith(toAccounts: v)),
  (false, 'min_replies', _) => _readValue(value, _count, _Control.minReplies, (s, v) => s.copyWith(minReplies: v)),
  (false, 'min_faves', _) => _readValue(value, _count, _Control.minLikes, (s, v) => s.copyWith(minLikes: v)),
  (false, 'min_retweets', _) => _readValue(value, _count, _Control.minRetweets, (s, v) => s.copyWith(minRetweets: v)),
  (false, 'since', _) => _readDate(value, _Control.since, (s, date) => s.copyWith(since: date)),
  (false, 'until', _) => _readDate(value, _Control.until, (s, date) => s.copyWith(until: date)),
  (false, 'filter', 'media') => _readContent(AdvancedSearchContentFilter.media),
  (false, 'filter', 'images') => _readContent(AdvancedSearchContentFilter.photos),
  (false, 'filter', 'videos') => _readContent(AdvancedSearchContentFilter.videos),
  (_, 'filter', 'links') => _reading(_Control.links, (s) => s.copyWith(links: _inclusion(negated))),
  (_, 'filter', 'replies') => _reading(_Control.replies, (s) => s.copyWith(replies: _inclusion(negated))),
  (true, 'filter', 'retweets') => _reading(_Control.reposts, (s) => s.copyWith(excludeRetweets: true)),
  (false, 'include', 'nativeretweets') => _reading(_Control.reposts, (s) => s.copyWith(includeReposts: true)),
  _ => null,
};

_Reading _reading(_Control control, AdvancedSearchState Function(AdvancedSearchState state) apply) =>
    (control: control, apply: apply);

_Reading? _readValue(
  String value,
  RegExp pattern,
  _Control control,
  AdvancedSearchState Function(AdvancedSearchState state, String value) apply,
) => pattern.hasMatch(value) ? _reading(control, (state) => apply(state, value)) : null;

_Reading? _readDate(
  String value,
  _Control control,
  AdvancedSearchState Function(AdvancedSearchState state, DateTime date) apply,
) {
  final date = _isoDate.hasMatch(value) ? DateTime.tryParse(value) : null;
  return date == null || _formatDate(date) != value ? null : _reading(control, (state) => apply(state, date));
}

_Reading _readContent(AdvancedSearchContentFilter content) =>
    _reading(_Control.content, (state) => state.copyWith(contentFilter: content));

AdvancedSearchInclusion _inclusion(bool negated) =>
    negated ? AdvancedSearchInclusion.exclude : AdvancedSearchInclusion.only;
