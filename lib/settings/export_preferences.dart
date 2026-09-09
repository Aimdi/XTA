import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/utils/crash_reporter.dart';

/// Preference-backed follows belong in a subscriptions export even when the
/// reader leaves unrelated application settings out of the file.
Map<String, dynamic>? preferencesForExport(
  Map<String, dynamic> preferences, {
  required bool includeSettings,
  required bool includeSubscriptions,
}) {
  final safe = prefsMapWithoutSecrets(preferences);
  if (includeSettings) return safe;
  if (!includeSubscriptions) return null;
  final keys = {
    for (final source in subscriptionSources)
      if (source.subscriptionPreferenceKey case final key?) key,
  };
  final selected = {
    for (final entry in safe.entries)
      if (keys.contains(entry.key)) entry.key: entry.value,
  };
  return selected.isEmpty ? null : selected;
}
