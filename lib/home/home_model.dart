import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:quax/constants.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/group/group_model.dart';
import 'package:quax/home/home_screen.dart';
import 'package:quax/plugins/plugin_registry.dart';
import 'package:quax/utils/iterables.dart';
import 'package:pref/pref.dart';

class HomePage {
  final String id;
  bool selected;
  final NavigationPage page;

  HomePage(this.id, this.selected, this.page);
}

class HomeModel extends Store<List<HomePage>> {
  final BasePrefService prefs;
  final GroupsModel groupsModel;

  HomeModel(this.prefs, this.groupsModel) : super([]) {
    groupsModel.observer(onState: (state) async {
      await loadPages();
    });
  }

  Future<void> resetPages() async {
    await execute(() async {
      prefs.set(optionHomePages, defaultHomePages.map((e) => e.id).toList());
      await loadPages();
      return state;
    });
  }

  Future<void> loadPages() async {
    await execute(() async {
      var saved = prefs.getStringList(optionHomePages) ?? [];

      final pluginPages = <NavigationPage>[
        for (final plugin in builtInPlugins)
          if (plugin.isEnabled(prefs) && plugin.showsHomeTab(prefs))
            NavigationPage(
              plugin.id,
              (c) => plugin.title(c),
              Icon(plugin.icon),
              Icon(plugin.icon),
            ),
      ];

      var available = [
        ...defaultHomePages,
        ...pluginPages,
        ...groupsModel.state.map((e) =>
            NavigationPage('group-${e.id}', (c) => L10n.of(c).group_name(e.name), Icon(e.iconData), Icon(e.iconData))),
      ];

      var pages = <HomePage>[];

      for (var id in saved) {
        var page = available.firstWhereOrNull((e) => e.id == id);
        if (page == null) {
          continue;
        }
        pages.add(HomePage(id, true, page));
      }

      // Switching a plugin on should put its tab in the bar. Only Substack did
      // that, so enabling any other plugin registered a tab and then left it
      // hidden behind Settings > Home pages, looking like nothing had happened.
      // Seeded once, and remembered: a tab the reader then removes has to stay
      // removed rather than coming back on the next launch.
      final seeded = prefs.getStringList(optionSeededPluginTabs) ?? const <String>[];
      final newlySeeded = <String>[];

      for (var page in available) {
        if (saved.contains(page.id)) {
          continue;
        }

        // `available` only carries plugin pages for plugins that are enabled
        // and want a tab, so being a plugin at all is enough here. Groups are
        // deliberately excluded: they are not tabs the reader asked for.
        final autoSelect = pluginById(page.id) != null && !seeded.contains(page.id);
        if (autoSelect) {
          newlySeeded.add(page.id);
        }

        pages.add(HomePage(page.id, autoSelect, page));
      }

      if (newlySeeded.isNotEmpty) {
        await prefs.set(optionSeededPluginTabs, [...seeded, ...newlySeeded]);
      }

      final selectedIds = pages.where((e) => e.selected).map((e) => e.id).toList();
      if (selectedIds.length != saved.length || !selectedIds.every(saved.contains)) {
        await prefs.set(optionHomePages, selectedIds);
      }

      return pages;
    });
  }

  Future<void> movePage(int oldIndex, int newIndex) async {
    if (newIndex > oldIndex) {
      newIndex = newIndex - 1;
    }

    final page = state.removeAt(oldIndex);
    state.insert(newIndex, page);
    update(state);
  }

  Future<void> save() async {
    var pages = state.where((e) => e.selected).map((e) => e.id).toList();

    await prefs.set(optionHomePages, pages);
  }

  Future<void> selectPage(String id, bool selected) async {
    for (var page in state) {
      if (page.id == id) {
        page.selected = selected;
        break;
      }
    }

    update(state, force: true);
  }
}
