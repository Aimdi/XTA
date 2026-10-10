import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/plugin_tag_chip.dart';

typedef PixivKindedTag = ({PixivTag tag, PluginTagKind kind});

/// How a work's tags are ordered. Pixiv sends no kinds, so they are read off
/// the tags themselves.
const pixivTagKindOrder = [PluginTagKind.copyright, PluginTagKind.character, PluginTagKind.general, PluginTagKind.meta];

const _metaTags = {'R-18', 'R-18G', 'AI生成'};
const _original = 'オリジナル';
final _bookmarkMilestone = RegExp(r'^\d+users入り$');
final _characterTags = [RegExp(r'^(.+?)\((.+)\)$'), RegExp(r'^(.+?)（(.+)）$')];

/// The series of a `Name(Series)` or `Name（Series）` character tag; null for
/// any other tag.
String? pixivCharacterSeries(String tag) => _characterTags
    .map((pattern) => pattern.firstMatch(tag.trim()))
    .nonNulls
    .where((match) => match[1]!.trim().isNotEmpty && match[2]!.trim().isNotEmpty)
    .map((match) => match[2]!.trim())
    .firstOrNull;

/// A tag's kind, where [series] are the series the work's character tags name.
/// Anything unsure is general.
PluginTagKind pixivTagKind(String tag, Set<String> series) {
  if (_metaTags.contains(tag) || _bookmarkMilestone.hasMatch(tag)) return PluginTagKind.meta;
  if (tag == _original || series.contains(tag)) return PluginTagKind.copyright;
  if (pixivCharacterSeries(tag) != null) return PluginTagKind.character;
  return PluginTagKind.general;
}

/// A work's tags with their kinds, ordered by [pixivTagKindOrder] and in
/// Pixiv's own order within a kind.
List<PixivKindedTag> pixivKindedTags(List<PixivTag> tags) {
  final series = tags.map((tag) => pixivCharacterSeries(tag.name)).nonNulls.toSet();
  final kinded = [for (final tag in tags) (tag: tag, kind: pixivTagKind(tag.name, series))];
  return [for (final kind in pixivTagKindOrder) ...kinded.where((entry) => entry.kind == kind)];
}
