/// Searches with more tags than a host takes at once: which tokens count
/// towards its limit, how such a search is shared out between the host and
/// the device, and the limit as the host states it.
library;

import 'package:xta/plugins/booru/booru_query.dart';

/// A search shared out: [host] goes in the request, [device] is checked on
/// each post the host sends back.
typedef BooruTagSplit = ({List<String> host, List<String> device});

/// Danbooru's metatags that do not count towards its tag limit, as its
/// answers to a guest showed on 2026-10-29. `order:` counts; so does any
/// metatag not listed, to be safe.
const _freeMetatags = {'rating', 'status', 'limit', 'is', 'parent', 'filetype', 'score', 'age'};

/// The lowest limit Danbooru gives anyone: guests and Members.
const _guestLimit = 2;

final _metatag = RegExp(r'^([a-z_]+):', caseSensitive: false);
final _statedLimit = RegExp(r'more than (\d+) tags', caseSensitive: false);

String _body(String token) => token.substring(BooruTagOperator.of(token).prefix.length);

bool _isFree(String token) => _freeMetatags.contains(_metatag.firstMatch(_body(token))?[1]?.toLowerCase());

/// A token the device cannot check by a tag's name: a metatag, or a tag with
/// a wildcard. It always goes to the host.
bool _isPinned(String token) {
  final body = _body(token);
  return _metatag.hasMatch(body) || body.contains('*');
}

/// How many of [tokens] count towards a host's tag limit.
int booruCountedTokens(List<String> tokens) => tokens.where((token) => !_isFree(token)).length;

/// [tokens] shared out so that at most [limit] counted ones go to the host:
/// every metatag, then the plain tags it has the fewest posts for
/// ([uses], by lower-cased name; a tag it does not list has none), then an
/// either group if all of it fits, then excluded tags. The rest are left to
/// the device. Whole when they fit; null when the metatags alone do not.
BooruTagSplit? splitBooruQuery(List<String> tokens, {required int limit, Map<String, int> uses = const {}}) {
  if (booruCountedTokens(tokens) <= limit) return (host: tokens, device: const <String>[]);
  final pinned = tokens.where(_isPinned).toList();
  final room = limit - booruCountedTokens(pinned);
  if (room < 0) return null;
  final movable = tokens.where((token) => !_isPinned(token)).toList();
  final plain = _leastUsedFirst(_withOperator(movable, BooruTagOperator.none), uses).take(room).toList();
  final allEither = _withOperator(movable, BooruTagOperator.either);
  final either = allEither.length <= room - plain.length ? allEither : const <String>[];
  final excluded = _withOperator(movable, BooruTagOperator.exclude).take(room - plain.length - either.length);
  final toHost = {...pinned, ...plain, ...either, ...excluded};
  return (
    host: [
      for (final token in tokens)
        if (toHost.contains(token)) token,
    ],
    device: [
      for (final token in tokens)
        if (!toHost.contains(token)) token,
    ],
  );
}

List<String> _withOperator(List<String> tokens, BooruTagOperator operator) =>
    tokens.where((token) => BooruTagOperator.of(token) == operator).toList();

/// [tags] by how few posts carry them, in their own order where that is
/// the same.
List<String> _leastUsedFirst(List<String> tags, Map<String, int> uses) {
  int usesOf(String tag) => uses[tag.toLowerCase()] ?? 0;
  final ranked = tags.indexed.toList()
    ..sort((a, b) {
      final byUse = usesOf(a.$2).compareTo(usesOf(b.$2));
      return byUse != 0 ? byUse : a.$1.compareTo(b.$1);
    });
  return [for (final (_, tag) in ranked) tag];
}

/// How many tags one search may have, from a host's answer that a search
/// had too many: Danbooru says "You cannot search for more than 2 tags at a
/// time." Its error's name alone means its lowest limit. Null when the
/// answer says neither.
int? booruTagLimitIn(String body) {
  final stated = _statedLimit.firstMatch(body);
  if (stated != null) return int.tryParse(stated[1]!);
  return body.contains('TagLimitError') ? _guestLimit : null;
}
