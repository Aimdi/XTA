/// Pure parts of the daily X query id report: the public record of X's web
/// operations (fa0311/TwitterInternalAPIDocument, `docs/json/API.json`),
/// the ids in force for an install, and how long ago X's web stopped using
/// one.
library;

import 'package:xta/client/endpoints.dart';
import 'package:xta/utils/json.dart';

/// One version of the record: the day it was taken, and the web's query ids
/// by operation.
typedef RecordVersion = ({DateTime day, Map<String, String> ids});

/// The query ids in [raw], one version of the record, by operation. Entries
/// without an id are left out.
Map<String, String> recordedQueryIds(Object? raw) {
  final operations = Json(raw)['graphql'].raw;
  if (operations is! Map) return const {};
  return {
    for (final MapEntry(:key, :value) in operations.entries)
      if (key is String) key: ?_queryIdOf(value),
  };
}

String? _queryIdOf(Object? operation) => switch (Json(operation)['queryId'].string) {
  final id? when queryIdPattern.hasMatch(id) => id,
  _ => null,
};

/// The id in force for each endpoint of [shipped] once [published] is
/// applied, by registry key: as `XEndpoints.applyOverrides` does, an entry
/// for an unknown endpoint or with a malformed id changes nothing.
Map<String, String> idsInForce(Map<String, XEndpoint> shipped, Map<String, String> published) => {
  for (final MapEntry(key: name, value: endpoint) in shipped.entries)
    name: switch (published[name]) {
      final id? when queryIdPattern.hasMatch(id) => id,
      _ => endpoint.queryId,
    },
};

/// Who applies each id in force, by registry key then id: [published] holds
/// each reader's published file, applied over [shipped] as in [idsInForce].
Map<String, Map<String, List<String>>> readersOfIds(
  Map<String, XEndpoint> shipped,
  Map<String, Map<String, String>> published,
) {
  final inForce = {for (final MapEntry(key: who, value: ids) in published.entries) who: idsInForce(shipped, ids)};
  return {
    for (final name in shipped.keys)
      name: {
        for (final id in {for (final ids in inForce.values) ids[name]!})
          id: [
            for (final MapEntry(key: who, value: ids) in inForce.entries)
              if (ids[name] == id) who,
          ],
      },
  };
}

/// How [id] of [operation] stands in [versions], newest first.
sealed class RecordedAge {
  const RecordedAge();
}

/// The newest version has it.
class CurrentInRecord extends RecordedAge {
  const CurrentInRecord();
}

/// The newest version that has it was taken [days] days ago: the web has
/// used another id since.
class DroppedFromRecord extends RecordedAge {
  final int days;

  const DroppedFromRecord(this.days);
}

/// None of the versions read has it: it is older than [since].
class OlderThanRecord extends RecordedAge {
  final DateTime since;

  const OlderThanRecord(this.since);
}

/// Where [id] of [operation] last appears in [versions], newest first; null
/// when there are none.
RecordedAge? recordedAge(String operation, String id, List<RecordVersion> versions, {required DateTime today}) {
  if (versions.isEmpty) return null;
  if (versions.first.ids[operation] == id) return const CurrentInRecord();
  for (final version in versions) {
    if (version.ids[operation] == id) return DroppedFromRecord(today.difference(version.day).inDays);
  }
  return OlderThanRecord(versions.last.day);
}

/// The registry keys whose ids differ between two published files.
List<String> publishedDifferences(Map<String, String> one, Map<String, String> other) => [
  for (final name in {...one.keys, ...other.keys}.toList()..sort())
    if (one[name] != other[name]) name,
];
