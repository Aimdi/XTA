import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/video_playback_policy.dart';

bool _button({
  bool autoPlayPref = false,
  bool alwaysPlay = false,
  bool userRequestedPlay = false,
  bool alreadyCached = false,
}) => showsPlayButton(
  autoPlayPref: autoPlayPref,
  alwaysPlay: alwaysPlay,
  userRequestedPlay: userRequestedPlay,
  alreadyCached: alreadyCached,
);

bool _create({
  bool autoPlayPref = false,
  bool alwaysPlay = false,
  bool userRequestedPlay = false,
  bool alreadyCached = false,
  bool hasBeenVisible = false,
  bool isVisible = false,
}) => shouldCreatePlayer(
  autoPlayPref: autoPlayPref,
  alwaysPlay: alwaysPlay,
  userRequestedPlay: userRequestedPlay,
  alreadyCached: alreadyCached,
  hasBeenVisible: hasBeenVisible,
  isVisible: isVisible,
);

void main() {
  group('showsPlayButton', () {
    test('a plain video waits for a tap', () {
      expect(_button(), isTrue);
    });

    test('autoplay, GIFs, a tap and a cached player all skip it', () {
      expect(_button(autoPlayPref: true), isFalse);
      expect(_button(alwaysPlay: true), isFalse);
      expect(_button(userRequestedPlay: true), isFalse);
      expect(_button(alreadyCached: true), isFalse);
    });
  });

  group('shouldCreatePlayer', () {
    // The regression that forced the feed's cacheExtent back down: a looping
    // GIF below the fold allocated libmpv purely by being built.
    test('an off-screen GIF allocates nothing', () {
      expect(_create(alwaysPlay: true, hasBeenVisible: false), isFalse);
    });

    test('an off-screen autoplay video allocates nothing', () {
      expect(_create(autoPlayPref: true, hasBeenVisible: false), isFalse);
    });

    test('the same GIF allocates once it has been on screen', () {
      expect(_create(alwaysPlay: true, hasBeenVisible: true), isTrue);
    });

    test('a tap creates the player as soon as it is on screen', () {
      expect(_create(userRequestedPlay: true, hasBeenVisible: true), isTrue);
    });

    // Reattaching to a pooled player costs nothing, and waiting would flash the
    // poster again every time the reader scrolls back.
    test('an already pooled player reattaches without waiting for visibility', () {
      expect(_create(alreadyCached: true, hasBeenVisible: false, isVisible: true), isTrue);
    });

    // A hidden tile hands its player back so the pool can evict it. If a cached
    // entry were enough on its own, `build` would re-attach — and re-pin — every
    // tile the list keeps alive below the fold, which is the leak this closes.
    test('a cached player is not reattached by a tile that is off screen', () {
      expect(_create(alreadyCached: true, hasBeenVisible: false, isVisible: false), isFalse);
    });

    test('a GIF that has been let go stays let go until it is back on screen', () {
      expect(_create(alwaysPlay: true, alreadyCached: true, hasBeenVisible: false, isVisible: false), isFalse);
      expect(_create(alwaysPlay: true, alreadyCached: true, hasBeenVisible: false, isVisible: true), isTrue);
    });

    test('a video still showing its play button never allocates, visible or not', () {
      expect(_create(hasBeenVisible: true), isFalse);
      expect(_create(hasBeenVisible: false), isFalse);
      expect(_create(isVisible: true), isFalse);
    });
  });

  group('shouldReleaseHiddenPlayer', () {
    // Regression for #116: the opaque fullscreen route stops the tile being
    // painted, so VisibilityDetector reports 0. Reclaim must not fire then —
    // releasing the pool ref lets eviction dispose the player still on screen.
    test('fullscreen never reclaims, even when the tile looks fully hidden', () {
      expect(
        shouldReleaseHiddenPlayer(
          isFullscreen: true,
          mounted: true,
          hasPoolKey: true,
          anyVisible: false,
          visibleFraction: 0,
        ),
        isFalse,
      );
    });

    test('a truly off-screen tile may reclaim', () {
      expect(
        shouldReleaseHiddenPlayer(
          isFullscreen: false,
          mounted: true,
          hasPoolKey: true,
          anyVisible: false,
          visibleFraction: 0,
        ),
        isTrue,
      );
    });

    test('a half-visible tile, or one still marked visible elsewhere, stays', () {
      expect(
        shouldReleaseHiddenPlayer(
          isFullscreen: false,
          mounted: true,
          hasPoolKey: true,
          anyVisible: false,
          visibleFraction: 0.5,
        ),
        isFalse,
      );
      expect(
        shouldReleaseHiddenPlayer(
          isFullscreen: false,
          mounted: true,
          hasPoolKey: true,
          anyVisible: true,
          visibleFraction: 0,
        ),
        isFalse,
      );
    });
  });

  group('videoPoolCanCreate', () {
    test('reattaching to a cached player is always allowed', () {
      expect(
        videoPoolCanCreate(alreadyCached: true, entryCount: 8, maxSize: 3),
        isTrue,
      );
    });

    test('a new player is refused when the pool is full and nothing is free', () {
      expect(
        videoPoolCanCreate(alreadyCached: false, entryCount: 3, maxSize: 3),
        isFalse,
      );
    });

    test('a new player is allowed when an unused entry can be evicted', () {
      expect(
        videoPoolCanCreate(
          alreadyCached: false,
          entryCount: 3,
          maxSize: 3,
          hasEvictable: true,
        ),
        isTrue,
      );
    });
  });
}
