import 'package:path/path.dart' as p;
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

const pixivFileNameTemplateDefault = '{illust_id}_p{part}';

/// The page number placeholder; without it the pages of one work would share a name.
const pixivPartToken = '{part}';

/// What a template may say, in the order the editor offers them.
enum PixivNameToken { illustId, part, title, userId, userName }

extension PixivNameTokenText on PixivNameToken {
  String get token => switch (this) {
    PixivNameToken.illustId => '{illust_id}',
    PixivNameToken.part => pixivPartToken,
    PixivNameToken.title => '{title}',
    PixivNameToken.userId => '{user_id}',
    PixivNameToken.userName => '{user_name}',
  };

  String valueFor(PixivIllust illust, int page) => switch (this) {
    PixivNameToken.illustId => '${illust.id}',
    PixivNameToken.part => '$page',
    PixivNameToken.title => illust.title,
    PixivNameToken.userId => '${illust.userId}',
    PixivNameToken.userName => illust.userName,
  };
}

bool pixivTemplateHasPart(String template) => template.contains(pixivPartToken);

const _maxStem = 180;
final _illegal = RegExp(r'[\x00-\x1f/\\:*?"<>|]');
final _token = RegExp(r'\{[a-z_]+\}');

/// [template] filled in for [page] of [illust]: characters no file system
/// accepts become `_`, and [extension] (with its dot) is kept at the end.
String pixivFileName(String template, PixivIllust illust, int page, {required String extension}) {
  final values = {for (final token in PixivNameToken.values) token.token: token.valueFor(illust, page)};
  // One pass, so a title that itself reads "{user_id}" stays as written.
  final filled = template.replaceAllMapped(_token, (match) => values[match[0]] ?? match[0]!);
  var stem = filled.replaceAll(_illegal, '_').replaceAll(RegExp(r'\s+'), ' ').trim();
  stem = stem.length > _maxStem ? stem.substring(0, _maxStem).trim() : stem;
  stem = stem.replaceAll(RegExp(r'^[. ]+|[. ]+$'), '');
  final safeStem = stem.isEmpty ? '${illust.id}_p$page' : stem;
  return '$safeStem$extension';
}

/// The extension of the file at [url], `.jpg` when it has none.
String pixivExtensionOf(String url) {
  final extension = p.extension(Uri.tryParse(url)?.path ?? '').toLowerCase();
  return RegExp(r'^\.[a-z0-9]{1,5}$').hasMatch(extension) ? extension : '.jpg';
}

/// How and where Pixiv saves land, read once from the reader's settings.
class PixivSaveNaming {
  final String template;
  final bool folderPerArtist;
  final bool folderR18;

  const PixivSaveNaming({
    this.template = pixivFileNameTemplateDefault,
    this.folderPerArtist = false,
    this.folderR18 = false,
  });

  /// A stored template that lost `{part}` is not used, so pages never overwrite each other.
  factory PixivSaveNaming.of(BasePrefService prefs) {
    final stored = prefs.get<String>(optionPluginPixivFileNameTemplate) ?? '';
    return PixivSaveNaming(
      template: pixivTemplateHasPart(stored) ? stored : pixivFileNameTemplateDefault,
      folderPerArtist: prefs.get<bool>(optionPluginPixivFolderPerArtist) == true,
      folderR18: prefs.get<bool>(optionPluginPixivFolderR18) == true,
    );
  }

  /// The name for [page] of [illust], keeping the original file's extension.
  String pageName(PixivIllust illust, int page) =>
      pixivFileName(template, illust, page, extension: pixivExtensionOf(illust.downloadUrlAt(page)));

  /// A whole-work file such as an ugoira export, named as its first page.
  String workName(PixivIllust illust, String extension) => pixivFileName(template, illust, 0, extension: extension);

  /// `R-18/` first, then `<user name>_<user id>/`, as the settings ask.
  String? subfolder(PixivIllust illust) {
    final parts = [
      if (folderR18 && illust.isR18) 'R-18',
      if (folderPerArtist) '${illust.userName}_${illust.userId}'.replaceAll(_illegal, '_'),
    ];
    return parts.isEmpty ? null : safeDownloadFolder(parts.join('/'));
  }
}
