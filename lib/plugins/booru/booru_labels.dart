import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_popular.dart';

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
