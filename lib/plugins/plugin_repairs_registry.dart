/// Keeps the published repairs file and puts it in force.
library;

import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/plugin_repairs.dart';
import 'package:xta/utils/published_documents.dart';

class PluginRepairsRegistry {
  final PublishedDocument document;

  PluginRepairsRegistry(BasePrefService prefs, {http.Client? client})
    : document = PublishedDocument(
        prefs,
        url: Uri.parse(defaultPluginRepairsUrl),
        keptKey: optionPluginRepairsKept,
        readAtKey: optionPluginRepairsReadAt,
        client: client,
      );

  /// Puts the copy last read in force, before any request goes out. How
  /// many repairs it holds that fit.
  int applyKept() => PluginRepairs.apply(parsePluginRepairs(document.kept ?? ''));

  /// Reads the file again when it is due and puts it in force; null when
  /// nothing new was read, so what is in force stays.
  Future<int?> refresh({DateTime? now}) async {
    final body = await document.refresh(now: now);
    return body == null ? null : PluginRepairs.apply(parsePluginRepairs(body));
  }
}
