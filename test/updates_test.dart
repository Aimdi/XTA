import 'package:flutter_test/flutter_test.dart';
import 'package:xta/utils/updates.dart';

void main() {
  group('parseReleaseTag', () {
    test('reads this fork\'s tags', () {
      final tag = parseReleaseTag('v4.12.0-aimdi44')!;
      expect(tag.version, [4, 12, 0]);
      expect(tag.forkBuild, 44);
    });

    test('reads a plain upstream tag', () {
      final tag = parseReleaseTag('v4.12.1')!;
      expect(tag.version, [4, 12, 1]);
      expect(tag.forkBuild, -1);
    });

    test('tolerates a missing v and extra segments', () {
      expect(parseReleaseTag('4.12.0')!.version, [4, 12, 0]);
      expect(parseReleaseTag('4.12.0.1')!.version, [4, 12, 0, 1]);
    });

    test('rejects non-versions', () {
      expect(parseReleaseTag(''), isNull);
      expect(parseReleaseTag('v'), isNull);
      expect(parseReleaseTag('claude/xta-repo-setup-shh2mn'), isNull);
      expect(parseReleaseTag('nightly'), isNull);
    });
  });

  group('isUpdateAvailable', () {
    test('the installed fork build is not an update to itself', () {
      // The reported bug: every launch showed a popup.
      expect(
        isUpdateAvailable(latestTag: 'v4.12.0-aimdi44', installedTag: 'v4.12.0-aimdi44', installedVersion: '4.12.0'),
        isFalse,
      );
    });

    test('a newer fork build of the same version is an update', () {
      expect(
        isUpdateAvailable(latestTag: 'v4.12.0-aimdi45', installedTag: 'v4.12.0-aimdi44', installedVersion: '4.12.0'),
        isTrue,
      );
    });

    test('an older fork build is not an update', () {
      expect(
        isUpdateAvailable(latestTag: 'v4.12.0-aimdi43', installedTag: 'v4.12.0-aimdi44', installedVersion: '4.12.0'),
        isFalse,
      );
    });

    test('a higher version is an update even across fork builds', () {
      expect(
        isUpdateAvailable(latestTag: 'v4.13.0-aimdi1', installedTag: 'v4.12.0-aimdi44', installedVersion: '4.12.0'),
        isTrue,
      );
    });

    test('a lower version is never an update', () {
      expect(
        isUpdateAvailable(latestTag: 'v4.11.9-aimdi99', installedTag: 'v4.12.0-aimdi44', installedVersion: '4.12.0'),
        isFalse,
      );
    });

    test('builds without a baked tag ignore fork builds of their own version', () {
      expect(isUpdateAvailable(latestTag: 'v4.12.0-aimdi44', installedTag: '', installedVersion: '4.12.0'), isFalse);
    });

    test('builds without a baked tag still see a real version bump', () {
      expect(isUpdateAvailable(latestTag: 'v4.12.1', installedTag: '', installedVersion: '4.12.0'), isTrue);
    });

    test('a branch name baked in by workflow_dispatch falls back to the version', () {
      expect(
        isUpdateAvailable(
          latestTag: 'v4.12.0-aimdi44',
          installedTag: 'claude/xta-repo-setup-shh2mn',
          installedVersion: '4.12.0',
        ),
        isFalse,
      );
    });

    test('an unparseable latest release never nags', () {
      expect(isUpdateAvailable(latestTag: 'nightly', installedTag: '', installedVersion: '4.12.0'), isFalse);
    });
  });

  group('bare fork tags', () {
    // The scheme changed from v4.12.0-aimdi70 to aimdi71 at release 71.
    // parseReleaseTag returned null for every tag after it, so isUpdateAvailable
    // was false and nobody was offered an update for seven releases.
    test('a bare aimdiNN tag parses', () {
      expect(parseReleaseTag('aimdi77')?.forkBuild, 77);
      expect(parseReleaseTag('aimdi77')?.version, isEmpty);
    });

    test('the release that changed the scheme is offered', () {
      expect(
        isUpdateAvailable(latestTag: 'aimdi71', installedTag: 'v4.12.0-aimdi70', installedVersion: '4.12.0'),
        isTrue,
      );
    });

    test('later bare builds are offered, earlier ones are not', () {
      expect(isUpdateAvailable(latestTag: 'aimdi77', installedTag: 'aimdi76', installedVersion: '4.12.0'), isTrue);
      expect(isUpdateAvailable(latestTag: 'aimdi76', installedTag: 'aimdi77', installedVersion: '4.12.0'), isFalse);
      expect(isUpdateAvailable(latestTag: 'aimdi77', installedTag: 'aimdi77', installedVersion: '4.12.0'), isFalse);
    });

    test('a real version bump still wins over a bare tag', () {
      expect(
        isUpdateAvailable(latestTag: 'v4.13.0', installedTag: 'v4.12.0-aimdi70', installedVersion: '4.12.0'),
        isTrue,
      );
    });

    test('a branch name is still not a version', () {
      expect(parseReleaseTag('main'), isNull);
    });
  });
}
