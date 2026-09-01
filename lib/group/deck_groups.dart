import 'package:pref/pref.dart';
import 'package:xta/constants.dart';

List<String> parseDeckGroupIds(String? raw) {
  if (raw == null || raw.trim().isEmpty) {
    return const [];
  }
  return raw.split(',').map((part) => part.trim()).where((part) => part.isNotEmpty).toList(growable: false);
}

String joinDeckGroupIds(Iterable<String> ids) => ids.join(',');

bool isDeckPinned(BasePrefService prefs, String groupId) =>
    parseDeckGroupIds(prefs.get(optionDeckGroupIds) as String?).contains(groupId);

Future<void> toggleDeckPin(BasePrefService prefs, String groupId) async {
  final ids = parseDeckGroupIds(prefs.get(optionDeckGroupIds) as String?).toList();
  if (ids.contains(groupId)) {
    ids.remove(groupId);
  } else {
    ids.add(groupId);
  }
  await prefs.set(optionDeckGroupIds, joinDeckGroupIds(ids));
}
