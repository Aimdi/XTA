import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_popular.dart';
import 'package:xta/plugins/booru/booru_tag_style.dart';

String booruEngineLabel(L10n l10n, BooruEngine engine) => switch (engine) {
  BooruEngine.danbooru => l10n.plugin_booru_engine_danbooru,
  BooruEngine.moebooru => l10n.plugin_booru_engine_moebooru,
  BooruEngine.gelbooruV2 => l10n.plugin_booru_engine_gelbooru,
  BooruEngine.e621 => l10n.plugin_booru_engine_e621,
};

String booruRatingLabel(L10n l10n, BooruRating rating) => switch (rating) {
  BooruRating.general => l10n.plugin_booru_rating_general,
  BooruRating.sensitive => l10n.plugin_booru_rating_sensitive,
  BooruRating.questionable => l10n.plugin_booru_rating_questionable,
  BooruRating.explicit => l10n.plugin_booru_rating_explicit,
};

/// Heading for a group of tags; null kind is a host that did not say.
String booruTagCategoryLabel(L10n l10n, BooruTagCategory? category) => switch (category) {
  BooruTagCategory.artist => l10n.plugin_booru_tag_artist,
  BooruTagCategory.copyright => l10n.plugin_booru_tag_copyright,
  BooruTagCategory.character => l10n.plugin_booru_tag_character,
  BooruTagCategory.species => l10n.plugin_booru_tag_species,
  BooruTagCategory.general => l10n.general,
  BooruTagCategory.meta => l10n.plugin_booru_tag_meta,
  null => l10n.plugin_booru_tags,
};

String booruPopularScaleLabel(L10n l10n, BooruPopularScale scale) => switch (scale) {
  BooruPopularScale.day => l10n.plugin_booru_popular_day,
  BooruPopularScale.week => l10n.plugin_booru_popular_week,
  BooruPopularScale.month => l10n.plugin_booru_popular_month,
};

/// How a thumbnail is read aloud: a post or a video, and its first tags.
String booruTileLabel(L10n l10n, BooruPost post) {
  final tags = booruLabelTags(post).map(booruTagDisplayName).join(', ');
  return post.isVideo ? l10n.plugin_booru_tile_video_label(tags) : l10n.plugin_booru_tile_label(tags);
}

const _namedKinds = [
  BooruTagCategory.artist,
  BooruTagCategory.character,
  BooruTagCategory.copyright,
  BooruTagCategory.species,
];

/// Up to [count] of [post]'s tags for its label: the artist, characters and
/// series first, then the rest in the host's order. Meta tags say nothing
/// about the picture.
List<String> booruLabelTags(BooruPost post, {int count = 5}) {
  BooruTagCategory kind(String tag) => post.tagCategories[tag] ?? BooruTagCategory.general;
  return [
    for (final category in _namedKinds) ...post.tags.where((tag) => kind(tag) == category),
    ...post.tags.where((tag) => !_namedKinds.contains(kind(tag)) && kind(tag) != BooruTagCategory.meta),
  ].take(count).toList();
}
