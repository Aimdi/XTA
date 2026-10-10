import 'dart:math' as math;

import 'package:flutter/widgets.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_viewing_prefs.dart';
import 'package:xta/plugins/plugin_gallery_layout.dart';

/// Stored for a grid that picks its own column count from the width.
const pixivGridColumnsAuto = 0;

/// The fixed counts a reader can pick instead.
const pixivGridColumnChoices = [2, 3, 4];

/// Below this a forced count drops columns, so a narrow pane never shows slivers of art.
const pixivGridMinTileWidth = 96.0;

String pixivGridColumnsPref(Orientation orientation) =>
    orientation == Orientation.portrait ? optionPluginPixivGridColumnsPortrait : optionPluginPixivGridColumnsLandscape;

/// Columns for a works grid [width] wide: the count picked for this [orientation], else as
/// many as the shared gallery layout fits.
int pixivGridColumns(double width, Orientation orientation, TextScaler textScaler, BasePrefService? prefs) {
  final picked = prefs?.get<int>(pixivGridColumnsPref(orientation)) ?? pixivGridColumnsAuto;
  if (!pixivGridColumnChoices.contains(picked)) return pluginGalleryColumns(width, textScaler);
  return math.max(1, math.min(picked, (width / pixivGridMinTileWidth).floor()));
}

/// [pixivGridColumns] for a grid built under [context].
int pixivGridColumnsFor(BuildContext context, double width) =>
    pixivGridColumns(width, MediaQuery.orientationOf(context), MediaQuery.textScalerOf(context), pixivPrefsOf(context));
