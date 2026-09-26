import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';

const altMicrobloggingSectionId = 'alt-microblogging';
const altMicrobloggingSourceIds = {pluginIdMastodon, pluginIdBluesky, pluginIdThreads};

bool isAltMicrobloggingSource(String? id) => altMicrobloggingSourceIds.contains(id);

bool altMicrobloggingGrouped(BasePrefService prefs) => prefs.get<bool>(optionAltMicrobloggingGrouped) != false;

/// A display projection only: stored pins and real reader IDs never change.
List<String> groupedMicrobloggingIds(Iterable<String> ids, {required bool grouped}) {
  final result = <String>[];
  for (final id in ids) {
    if (!grouped || !isAltMicrobloggingSource(id)) {
      result.add(id);
    } else if (!result.contains(altMicrobloggingSectionId)) {
      result.add(altMicrobloggingSectionId);
    }
  }
  return result;
}

String? altMicrobloggingDestination(Iterable<String> ids, {String? selected, String? remembered}) {
  final members = ids.where(isAltMicrobloggingSource).toList();
  if (members.contains(selected)) return selected;
  if (members.contains(remembered)) return remembered;
  return members.firstOrNull;
}

class AltMicrobloggingStore extends Store<bool> {
  final BasePrefService prefs;

  AltMicrobloggingStore(this.prefs) : super(altMicrobloggingGrouped(prefs));

  Future<void> setGrouped(bool grouped) async {
    await prefs.set(optionAltMicrobloggingGrouped, grouped);
    update(grouped);
  }

  Future<void> remember(String id) async {
    if (!isAltMicrobloggingSource(id) || prefs.get<String>(optionAltMicrobloggingLastSource) == id) return;
    await prefs.set(optionAltMicrobloggingLastSource, id);
  }
}

/// Standalone reader/test hosts can use preferences without the app-level Store.
class AltMicrobloggingScope extends StatelessWidget {
  final Widget Function(BuildContext, bool) builder;

  const AltMicrobloggingScope({super.key, required this.builder});

  @override
  Widget build(BuildContext context) {
    final store = context.read<AltMicrobloggingStore?>();
    if (store == null) return builder(context, altMicrobloggingGrouped(PrefService.of(context, listen: false)));
    return ScopedBuilder<AltMicrobloggingStore, bool>(store: store, onState: builder);
  }
}
