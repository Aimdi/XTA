import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_query.dart';

/// The metatags each engine's search understands, of those the app reads
/// as metatags ([booruMetatagNames]). Danbooru's and Gelbooru's follow the
/// lists Boorusama keeps; Moebooru's and e621's follow the sites' cheat
/// sheets and are not checked against a live host.
List<String> booruEngineMetatags(BooruEngine engine) => switch (engine) {
  BooruEngine.danbooru => const [
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
    'source',
    'status',
    'tagcount',
    'user',
    'width',
  ],
  BooruEngine.moebooru => const [
    'date',
    'height',
    'id',
    'md5',
    'mpixels',
    'order',
    'parent',
    'pool',
    'rating',
    'score',
    'source',
    'user',
    'width',
  ],
  BooruEngine.gelbooruV2 => const ['height', 'id', 'md5', 'parent', 'rating', 'score', 'sort', 'user', 'width'],
  BooruEngine.e621 => const [
    'date',
    'fav',
    'favcount',
    'height',
    'id',
    'md5',
    'mpixels',
    'order',
    'parent',
    'pool',
    'rating',
    'ratio',
    'score',
    'source',
    'status',
    'tagcount',
    'user',
    'width',
  ],
};

/// The values worth offering after `name:` on [engine]; none where any
/// value goes, such as a user name or a number.
List<String> booruMetatagValues(
  BooruEngine engine,
  String name, {
  required String host,
  required BooruRating maxRating,
}) => switch ((engine, name)) {
  (_, 'rating') => [
    for (final rating in BooruRating.values)
      if (!rating.exceeds(maxRating)) ?booruRatingMetatag(engine, rating, host: host)?.substring('rating:'.length),
  ],
  (BooruEngine.danbooru, 'order') => const [
    'score',
    'favcount',
    'rank',
    'id',
    'id_desc',
    'change',
    'comment',
    'note',
    'mpixels',
    'landscape',
    'portrait',
    'filesize',
    'random',
  ],
  (BooruEngine.moebooru, 'order') => const ['score', 'id', 'id_desc', 'mpixels', 'landscape', 'portrait', 'vote'],
  (BooruEngine.e621, 'order') => const [
    'score',
    'favcount',
    'id',
    'tagcount',
    'comment_count',
    'mpixels',
    'filesize',
    'landscape',
    'portrait',
    'random',
  ],
  (BooruEngine.gelbooruV2, 'sort') => const ['score', 'id', 'updated', 'width', 'height', 'random'],
  (BooruEngine.danbooru, 'filetype') => const ['jpg', 'png', 'gif', 'webp', 'mp4', 'webm', 'zip'],
  (BooruEngine.danbooru, 'status') => const ['pending', 'flagged', 'deleted', 'banned', 'active', 'any'],
  (BooruEngine.e621, 'status') => const ['pending', 'flagged', 'deleted', 'active', 'any'],
  (BooruEngine.danbooru || BooruEngine.e621, 'parent') => const ['none', 'any'],
  _ => const [],
};

/// What to offer while [input] is typed, without asking the host: the
/// metatags whose names it starts, from two letters on, or the values of the
/// metatag it names. A name is offered with its colon, to be finished in the
/// field; a value as the whole `name:value`.
List<BooruTagSuggestion> booruMetatagSuggestions(
  BooruEngine engine,
  String input, {
  required String host,
  required BooruRating maxRating,
}) {
  final typed = input.trim();
  final body = typed.substring(BooruTagOperator.of(typed).prefix.length).toLowerCase();
  final colon = body.indexOf(':');
  final names = booruEngineMetatags(engine);
  if (colon < 0) {
    return body.length < 2
        ? const []
        : [
            for (final name in names)
              if (name.startsWith(body)) BooruTagSuggestion(name: '$name:'),
          ];
  }
  final name = body.substring(0, colon);
  final value = body.substring(colon + 1);
  if (!names.contains(name)) return const [];
  return [
    for (final known in booruMetatagValues(engine, name, host: host, maxRating: maxRating))
      if (known.startsWith(value) && known != value) BooruTagSuggestion(name: '$name:$known'),
  ];
}

/// Whether [suggestion] names a metatag still to be finished in the field.
bool booruSuggestsMetatagName(BooruTagSuggestion suggestion) => suggestion.name.endsWith(':');
