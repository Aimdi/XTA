import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_category.dart';
import 'package:xta/plugins/x/x_screen.dart';

const pluginIdX = 'x';

/// X is part of the app itself; it has no install/uninstall lifecycle.
class XPlugin extends XtaPlugin {
  @override
  String get id => pluginIdX;
  @override
  String get enabledPrefKey => 'core_x';
  @override
  bool isEnabled(BasePrefService prefs) => true;
  @override
  bool get supportsFeedStrip => true;
  @override
  IconData get icon => Icons.close;
  @override
  PluginCategory get category => PluginCategory.social;
  @override
  Color get brandColor => const Color(0xFF536471);
  @override
  String title(BuildContext context) => L10n.of(context).source_x;
  @override
  String description(BuildContext context) => L10n.of(context).foryou;
  @override
  Widget homeScreen({required ScrollController scrollController}) => XScreen(scrollController: scrollController);
}
