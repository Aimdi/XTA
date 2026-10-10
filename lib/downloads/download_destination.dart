import 'package:pref/pref.dart';
import 'package:xta/constants.dart';

/// The download settings as a fresh install starts with them.
const downloadPrefDefaults = <String, Object>{
  optionDownloadPath: '',
  optionDownloadTreeUri: '',
  optionDownloadType: optionDownloadTypeAuto,
  optionDownloadTypeAutoMigrated: false,
};

/// Where the download setting sends a new download: a picked folder, the
/// shared folder for its type without asking, or wherever the user picks.
class DownloadDestination {
  final String? treeUri;
  final bool background;

  const DownloadDestination._({this.background = false}) : treeUri = null;

  static const ask = DownloadDestination._();
  static const shared = DownloadDestination._(background: true);

  const DownloadDestination.folder(String this.treeUri) : background = false;

  /// A "directory" setting without a folder still asks, as it always has.
  factory DownloadDestination.fromPrefs(BasePrefService prefs) {
    final treeUri = prefs.get<String>(optionDownloadTreeUri) ?? '';
    return switch (prefs.get(optionDownloadType)) {
      optionDownloadTypeAsk => ask,
      optionDownloadTypeDirectory => treeUri.isEmpty ? ask : DownloadDestination.folder(treeUri),
      _ => shared,
    };
  }

  bool get asks => treeUri == null && !background;
}

/// Moves readers from the former "ask" default to background saving, once.
///
/// Defaults are written to storage on every launch, so a stored "ask" alone
/// cannot be told apart from one picked on purpose. A folder ever having been
/// chosen shows the setting was visited, so that "ask" is kept, as is "directory".
Future<void> migrateDownloadTypeDefault(BasePrefService prefs) async {
  if (prefs.get<bool>(optionDownloadTypeAutoMigrated) ?? false) return;
  final folderChosen = [
    optionDownloadTreeUri,
    optionDownloadPath,
  ].any((key) => (prefs.get<String>(key) ?? '').isNotEmpty);
  if (prefs.get(optionDownloadType) == optionDownloadTypeAsk && !folderChosen) {
    await prefs.set(optionDownloadType, optionDownloadTypeAuto);
  }
  await prefs.set(optionDownloadTypeAutoMigrated, true);
}
