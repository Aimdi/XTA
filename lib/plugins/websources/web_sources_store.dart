/// The installed web sources, kept in preferences, and their updates.
library;

import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/websources/web_source_client.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';
import 'package:xta/utils/json.dart';
import 'package:xta/utils/queued_store.dart';

/// How often installed sources are read again for an update.
const webSourceUpdateInterval = Duration(hours: 23);

class InstalledWebSource {
  final WebSourceDefinition source;

  /// Where its file is read again from; null for one that cannot update.
  final Uri? origin;

  /// The sites the reader agreed it may read.
  final List<String> agreedHosts;

  /// A newer version that asks for a site not agreed to yet.
  final WebSourceDefinition? waiting;

  const InstalledWebSource({required this.source, required this.agreedHosts, this.origin, this.waiting});

  Map<String, Object?> toJson() => {
    'source': source.raw,
    'origin': origin?.toString(),
    'hosts': agreedHosts,
    if (waiting case final waiting?) 'waiting': waiting.raw,
  };

  /// A stored entry; null when its source file no longer reads, as after a
  /// format this build does not know.
  static InstalledWebSource? fromJson(Json json) {
    if (webSourceFromJson(json['source'].raw) case ReadWebSource(:final source)) {
      return InstalledWebSource(
        source: source,
        origin: Uri.tryParse(json['origin'].string ?? ''),
        agreedHosts: [for (final host in json['hosts'].list) ?host.string],
        waiting: switch (webSourceFromJson(json['waiting'].raw)) {
          ReadWebSource(:final source) => source,
          RefusedWebSource() => null,
        },
      );
    }
    return null;
  }
}

List<InstalledWebSource> readInstalledWebSources(BasePrefService prefs) {
  final Object? decoded;
  try {
    decoded = jsonDecode(prefs.get<String>(optionPluginWebSourcesInstalled) ?? '[]');
  } on FormatException {
    return const [];
  }
  return [for (final entry in Json(decoded).list) ?InstalledWebSource.fromJson(entry)];
}

/// [installed] after [fetched], its file read again: a newer version on
/// agreed sites replaces it, one asking for a new site waits; anything else
/// changes nothing (null).
InstalledWebSource? updatedWebSource(InstalledWebSource installed, WebSourceFile fetched) {
  if (fetched is! ReadWebSource) return null;
  final next = fetched.source;
  final newest = installed.waiting?.version ?? installed.source.version;
  if (next.id != installed.source.id || next.version <= newest) return null;
  if (next.hosts.every(installed.agreedHosts.contains)) {
    return InstalledWebSource(source: next, agreedHosts: installed.agreedHosts, origin: installed.origin);
  }
  return InstalledWebSource(
    source: installed.source,
    agreedHosts: installed.agreedHosts,
    origin: installed.origin,
    waiting: next,
  );
}

class WebSourcesStore extends Store<List<InstalledWebSource>> with QueuedStore<List<InstalledWebSource>> {
  final BasePrefService prefs;

  WebSourcesStore(this.prefs) : super(readInstalledWebSources(prefs));

  InstalledWebSource? installed(String id) => state.where((entry) => entry.source.id == id).firstOrNull;

  Future<void> load() => executeQueued(() async => readInstalledWebSources(prefs));

  /// Adds [source], or replaces the installed one with its id; the reader
  /// has just agreed to every site it lists.
  Future<void> install(WebSourceDefinition source, {Uri? origin}) => _change(
    (current) => [
      for (final entry in current)
        if (entry.source.id != source.id) entry,
      InstalledWebSource(source: source, agreedHosts: source.hosts, origin: origin),
    ],
  );

  Future<void> remove(String id) => _change(
    (current) => [
      for (final entry in current)
        if (entry.source.id != id) entry,
    ],
  );

  /// Takes the update waiting on [id]: the reader agreed to its sites.
  Future<void> agreeToUpdate(String id) => _change(
    (current) => [
      for (final entry in current)
        if (entry.source.id == id && entry.waiting != null)
          InstalledWebSource(source: entry.waiting!, agreedHosts: entry.waiting!.hosts, origin: entry.origin)
        else
          entry,
    ],
  );

  /// Reads every installed source's file again, at most once per
  /// [webSourceUpdateInterval].
  Future<void> checkForUpdates(WebSourceClient client, {DateTime? now}) async {
    final at = now ?? DateTime.now();
    final last = DateTime.tryParse(prefs.get<String>(optionPluginWebSourcesCheckedAt) ?? '');
    if (last != null && at.difference(last) < webSourceUpdateInterval) return;
    await prefs.set(optionPluginWebSourcesCheckedAt, at.toIso8601String());
    final fetched = <String, WebSourceFile>{
      for (final entry in state)
        if (entry.origin case final origin?) entry.source.id: await _read(client, origin),
    };
    await _change(
      (current) => [
        for (final entry in current)
          switch (fetched[entry.source.id]) {
            final file? => updatedWebSource(entry, file) ?? entry,
            null => entry,
          },
      ],
    );
  }

  static Future<WebSourceFile> _read(WebSourceClient client, Uri origin) async {
    try {
      return readWebSource(await client.file(origin));
    } on WebSourceFailure catch (failure) {
      return RefusedWebSource(WebSourceRefusal.unreadable, failure.kind.name);
    }
  }

  Future<void> _change(List<InstalledWebSource> Function(List<InstalledWebSource> current) change) =>
      executeQueued(() async {
        final next = change(readInstalledWebSources(prefs));
        await prefs.set(optionPluginWebSourcesInstalled, jsonEncode([for (final entry in next) entry.toJson()]));
        return next;
      });
}
