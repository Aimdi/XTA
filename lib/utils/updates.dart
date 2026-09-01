/// Update-check version comparison.
///
/// This fork publishes tags like `v4.12.0-aimdi44` while `pubspec.yaml` keeps
/// the upstream version (`4.12.0`), so `package_info` alone cannot identify
/// which release is installed. The release workflow bakes the tag in with
/// `--dart-define=XTA_RELEASE_TAG`; [buildReleaseTag] is empty for local builds.
library;

/// Release tag this binary was built from, or empty when built locally.
const String buildReleaseTag = String.fromEnvironment('XTA_RELEASE_TAG');

final RegExp _forkBuild = RegExp(r'[A-Za-z]+(\d+)$');

/// A release's comparable identity: its numeric version plus this fork's build
/// counter ([forkBuild] is -1 when the tag carries no fork suffix).
class ReleaseIdentity {
  final List<int> version;
  final int forkBuild;

  const ReleaseIdentity(this.version, this.forkBuild);
}

/// Parses `v4.12.0-aimdi44`, `4.12.1`, `v4.13.0`, `aimdi77` … Returns null for
/// anything that is not a version (a branch name, say).
ReleaseIdentity? parseReleaseTag(String tag) {
  var text = tag.trim();
  if (text.startsWith('v') || text.startsWith('V')) {
    text = text.substring(1);
  }
  if (text.isEmpty) {
    return null;
  }

  // A bare fork build, with no version in front of it. The scheme changed from
  // `v4.12.0-aimdi70` to `aimdi71` and this returned null for every tag after
  // it -- so every release since read as "not a version" and no reader was
  // offered an update. The version is whatever pubspec still says; only the
  // fork build moves.
  final bare = _forkBuild.firstMatch(text);
  if (bare != null && bare.start == 0 && bare.end == text.length) {
    return ReleaseIdentity(const [], int.parse(bare.group(1)!));
  }

  final parts = text.split('-');
  final numbers = <int>[];
  for (final segment in parts.first.split('.')) {
    final value = int.tryParse(segment);
    if (value == null) {
      return null;
    }
    numbers.add(value);
  }
  if (numbers.isEmpty) {
    return null;
  }

  var forkBuild = -1;
  if (parts.length > 1) {
    final match = _forkBuild.firstMatch(parts.sublist(1).join('-'));
    if (match != null) {
      forkBuild = int.parse(match.group(1)!);
    }
  }

  return ReleaseIdentity(numbers, forkBuild);
}

int _compareVersions(List<int> a, List<int> b) {
  for (var i = 0; i < a.length || i < b.length; i++) {
    final left = i < a.length ? a[i] : 0;
    final right = i < b.length ? b[i] : 0;
    if (left != right) {
      return left.compareTo(right);
    }
  }
  return 0;
}

/// Whether [latestTag] is genuinely newer than what is installed.
///
/// [installedTag] is [buildReleaseTag] and wins when present. Otherwise the
/// looser [installedVersion] from package_info is used, and fork builds of the
/// same version are ignored — a local build cannot know its own fork build, and
/// guessing would nag on every launch.
bool isUpdateAvailable({required String latestTag, required String installedTag, required String installedVersion}) {
  final latest = parseReleaseTag(latestTag);
  if (latest == null) {
    return false;
  }

  final installed = parseReleaseTag(installedTag) ?? parseReleaseTag(installedVersion);
  if (installed == null) {
    return false;
  }

  // A bare fork tag carries no version of its own, so there is nothing to
  // compare it by: `aimdi71` follows `v4.12.0-aimdi70` and is newer, even
  // though an empty version sorts below 4.12.0. Fall through to the build
  // counter, which is the only thing that moved.
  final versioned = latest.version.isNotEmpty && installed.version.isNotEmpty;
  if (versioned) {
    final byVersion = _compareVersions(latest.version, installed.version);
    if (byVersion != 0) {
      return byVersion > 0;
    }
  }

  if (installed.forkBuild < 0) {
    return false;
  }
  return latest.forkBuild > installed.forkBuild;
}
