import 'package:flutter/widgets.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/plugin_gallery_layout.dart';

/// How posts are laid out and which file sizes are fetched.
@immutable
class BooruDisplay {
  /// Grid columns; 0 fits as many as the width allows.
  final int columns;
  final bool smallThumbnails;
  final bool originalInViewer;
  final bool tileDetails;
  final bool blurExplicit;

  const BooruDisplay({
    this.columns = 0,
    this.smallThumbnails = false,
    this.originalInViewer = false,
    this.tileDetails = true,
    this.blurExplicit = false,
  });

  factory BooruDisplay.fromPrefs(BasePrefService prefs) => BooruDisplay(
    columns: prefs.get<int>(optionPluginBooruGridColumns) ?? 0,
    smallThumbnails: prefs.get<bool>(optionPluginBooruSmallThumbnails) ?? false,
    originalInViewer: prefs.get<bool>(optionPluginBooruOriginalInViewer) ?? false,
    tileDetails: prefs.get<bool>(optionPluginBooruTileDetails) ?? true,
    blurExplicit: prefs.get<bool>(optionPluginBooruBlurExplicit) ?? false,
  );

  /// The reader's choices, or the defaults where no preferences are in scope.
  static BooruDisplay of(BuildContext context) {
    final prefs = context.dependOnInheritedWidgetOfExactType<PrefService>()?.service;
    return prefs == null ? const BooruDisplay() : BooruDisplay.fromPrefs(prefs);
  }

  int columnsFor(double width, TextScaler textScaler) =>
      columns > 0 ? columns : pluginGalleryColumns(width, textScaler);

  String tileUrl(BooruPost post) => smallThumbnails ? post.thumbnailUrl : post.catalogUrl;

  String viewerUrl(BooruPost post) => originalInViewer ? post.originalUrl : post.displayUrl;

  bool blurs(BooruPost post) =>
      blurExplicit && (post.rating == BooruRating.questionable || post.rating == BooruRating.explicit);
}

const booruGridColumnChoices = [0, 2, 3, 4, 5];
