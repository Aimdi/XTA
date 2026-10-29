import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:xta/client/endpoints.dart';

import 'support/published_registries.dart';
import 'support/x_query_id_record.dart';

/// The daily report of X's query ids. Opt in with:
///   flutter test test/live/x_query_ids_test.dart --dart-define=RUN_LIVE=true
///
/// For every operation: the id builds of this repository use, the one
/// installs up to aimdi186 use (they read `endpoints.json` from `main`),
/// whether X's web still uses it or how many days ago it stopped, and the
/// web's id today, from the record fa0311/TwitterInternalAPIDocument keeps.
/// It fails only when a published file installs read cannot be read, or
/// when `main` publishes other ids than the default branch. See
/// docs/specs/x-query-id-watch.md.
const _recordRepo = 'fa0311/TwitterInternalAPIDocument';
const _recordBranch = 'develop';
const _recordPath = 'docs/json/API.json';
const _maxVersions = 40;
const _oldAfterDays = 90;

Map<String, String> get _githubHeaders {
  final token = Platform.environment['GITHUB_TOKEN'] ?? '';
  return {
    'Accept': 'application/vnd.github+json',
    'User-Agent': 'XTA-live-checks',
    if (token.isNotEmpty) 'Authorization': 'Bearer $token',
  };
}

/// The record's versions, newest first, read until each of [wanted] (id by
/// operation) is found or [_maxVersions] are read.
Future<List<RecordVersion>> _recordVersions(http.Client client, Map<String, Set<String>> wanted) async {
  final commits = await client.get(
    Uri.https('api.github.com', '/repos/$_recordRepo/commits', {
      'sha': _recordBranch,
      'path': _recordPath,
      'per_page': '$_maxVersions',
    }),
    headers: _githubHeaders,
  );
  if (commits.statusCode != 200) throw HttpException('record history: HTTP ${commits.statusCode}');
  final versions = <RecordVersion>[];
  for (final commit in jsonDecode(commits.body) as List) {
    final sha = commit['sha'] as String;
    final day = DateTime.parse(commit['commit']['committer']['date'] as String);
    final file = await client.get(Uri.https('raw.githubusercontent.com', '/$_recordRepo/$sha/$_recordPath'));
    if (file.statusCode != 200) continue;
    versions.add((day: day, ids: recordedQueryIds(jsonDecode(file.body))));
    final found = wanted.entries.every(
      (entry) => entry.value.every((id) => versions.any((version) => version.ids[entry.key] == id)),
    );
    if (found) break;
  }
  return versions;
}

String _age(RecordedAge? age, DateTime today) => switch (age) {
  null => 'age unknown',
  CurrentInRecord() => 'current',
  DroppedFromRecord(:final days) => '$days days${days > _oldAfterDays ? ', OLD' : ''}',
  OlderThanRecord(:final since) => () {
    final days = today.difference(since).inDays;
    return 'over $days days${days > _oldAfterDays ? ', OLD' : ''}';
  }(),
};

String _idWithAge(String operation, String id, List<RecordVersion> versions, DateTime today) =>
    '$id (${_age(recordedAge(operation, id, versions, today: today), today)})';

void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);

  test(
    'X query ids: what installs use, and how long ago X moved on',
    () async {
      final client = http.Client();
      final today = DateTime.now().toUtc();
      final published = await fetchPublishedRegistries(client);
      final current = published[currentBuilds];
      final legacy = published[olderInstalls];
      final inForce = {
        for (final MapEntry(key: who, value: ids) in published.entries)
          who: idsInForce(XEndpoints.shipped, ids ?? const {}),
      };

      final wanted = <String, Set<String>>{};
      for (final ids in inForce.values) {
        for (final MapEntry(key: name, value: id) in ids.entries) {
          wanted.putIfAbsent(XEndpoints.endpoint(name).operation, () => {}).add(id);
        }
      }
      List<RecordVersion> versions;
      String record;
      try {
        versions = await _recordVersions(client, wanted);
        record = versions.isEmpty
            ? 'the record has no versions'
            : 'record: ${versions.length} versions, ${versions.last.day.toIso8601String().substring(0, 10)} '
                  'to ${versions.first.day.toIso8601String().substring(0, 10)}';
      } catch (error) {
        versions = const [];
        record = 'the record could not be read ($error), so no ages';
      }

      final report = [
        'X query ids, $record. Published files: '
            '${current == null ? 'default branch UNREADABLE' : 'default branch ${current.length} entries'}, '
            '${legacy == null ? 'main not readable' : 'main ${legacy.length} entries'}.',
        for (final endpoint in XEndpoints.all)
          [
            endpoint.name.padRight(24),
            for (final MapEntry(key: who, value: ids) in inForce.entries)
              '$who: ${_idWithAge(endpoint.operation, ids[endpoint.name]!, versions, today)}',
            'web now: ${versions.isEmpty ? '?' : versions.first.ids[endpoint.operation] ?? 'not in the record'}',
          ].join('  '),
      ];
      // Printed whatever the outcome: a green run is the record of the ids
      // installs used that day.
      // ignore: avoid_print
      print('\n${report.join('\n')}\n');

      expect(
        current,
        isNotNull,
        reason:
            'Builds of this repository cannot read ${publishedRegistryUrls[currentBuilds]}, so no correction '
            'reaches them. Check that endpoints.json is on the default branch.',
      );
      expect(
        legacy == null ? const <String>[] : publishedDifferences(current ?? const {}, legacy),
        isEmpty,
        reason:
            'Installs up to aimdi186 read ${publishedRegistryUrls[olderInstalls]} and apply its ids over the '
            "ones each release ships. Copy the default branch's endpoints.json to main.",
      );
    },
    timeout: const Timeout(Duration(minutes: 5)),
    skip: enabled ? false : 'Pass --dart-define=RUN_LIVE=true to read the live files',
  );
}
