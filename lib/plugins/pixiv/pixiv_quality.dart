import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

/// The sizes Pixiv serves every work in, smallest first.
enum PixivImageQuality { medium, large, original }

String pixivQualityLabel(L10n l10n, PixivImageQuality quality) => switch (quality) {
  PixivImageQuality.medium => l10n.plugin_pixiv_quality_medium,
  PixivImageQuality.large => l10n.plugin_pixiv_quality_large,
  PixivImageQuality.original => l10n.plugin_pixiv_quality_original,
};

/// Where an image size is chosen, the sizes offered there and the one it starts at.
/// Downloads always save the original and have no slot.
enum PixivQualitySlot {
  feed(optionPluginPixivQualityFeed, [PixivImageQuality.medium, PixivImageQuality.large], PixivImageQuality.medium),
  detail(optionPluginPixivQualityDetail, PixivImageQuality.values, PixivImageQuality.large),
  reader(optionPluginPixivQualityReader, [
    PixivImageQuality.large,
    PixivImageQuality.original,
  ], PixivImageQuality.large);

  final String pref;
  final List<PixivImageQuality> choices;
  final PixivImageQuality fallback;

  const PixivQualitySlot(this.pref, this.choices, this.fallback);
}

/// The size picked for [slot]; its default when nothing usable is stored.
PixivImageQuality pixivQuality(BasePrefService? prefs, PixivQualitySlot slot) {
  final stored = prefs?.get<String>(slot.pref);
  return slot.choices.where((quality) => quality.name == stored).firstOrNull ?? slot.fallback;
}

/// A grid tile's picture: the medium preview, or the large image when Pixiv sent one.
String pixivTileUrl(PixivIllust illust, PixivImageQuality quality) {
  final large = illust.largeUrl;
  return quality == PixivImageQuality.medium || large == null || large.isEmpty ? illust.thumbnailUrl : large;
}

/// [page] of [illust] in [quality]; each size falls back to the next one Pixiv did send.
String pixivPageUrl(PixivIllust illust, int page, PixivImageQuality quality) => switch (quality) {
  PixivImageQuality.medium => illust.thumbUrlAt(page),
  PixivImageQuality.large => illust.viewerUrls[page.clamp(0, illust.viewerUrls.length - 1)],
  PixivImageQuality.original => illust.downloadUrlAt(page),
};
