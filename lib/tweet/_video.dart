import 'dart:async';

import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter/material.dart';
import 'package:media_kit/media_kit.dart' as mk;
import 'package:media_kit_video/media_kit_video.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/_video_controls.dart';
import 'package:xta/tweet/video_audio_focus.dart';
import 'package:xta/tweet/video_controller_pool.dart';
import 'package:xta/tweet/video_fullscreen.dart';
import 'package:xta/tweet/video_playback_policy.dart';
import 'package:xta/tweet/video_quality.dart';
import 'package:xta/utils/iterables.dart';
import 'package:xta/utils/media_quality.dart';
import 'package:xta/ui/capped_network_image.dart';
import 'package:provider/provider.dart';
import 'package:visibility_detector/visibility_detector.dart';

class TweetVideoUrls {
  final String streamUrl;
  final String? downloadUrl;
  final List<TweetVideoQuality> qualities;
  final Map<String, String>? httpHeaders;

  TweetVideoUrls(
    this.streamUrl,
    this.downloadUrl, {
    this.qualities = const [],
    this.httpHeaders,
  });
}

class TweetVideoMetadata {
  final double aspectRatio;
  final String? imageUrl;
  final Future<TweetVideoUrls> Function() streamUrlsBuilder;

  TweetVideoMetadata(this.aspectRatio, this.imageUrl, this.streamUrlsBuilder);

  static Future<TweetVideoUrls> Function() streamUrlsBuilderFromVariants(
    List<Variant> variants,
  ) {
    // Use progressive MP4, not X's HLS master playlist (variants[0]): libmpv
    // plays the .m3u8 poorly (delayed start, bad seek, phantom subtitle tracks).
    // Fall back to variants[0] only when no MP4 exists (e.g. live broadcasts).
    var mp4Variants = variants
        .where((e) => e.bitrate != null)
        .where((e) => e.url != null)
        .where((e) => e.contentType == 'video/mp4')
        .sorted((a, b) => -(a.bitrate!.compareTo(b.bitrate!)))
        .toList();

    var qualities = mp4Variants
        .map((e) => TweetVideoQuality(e.url!, _qualityLabel(e.url!, e.bitrate)))
        .toList();

    var mp4Url = qualities.isNotEmpty ? qualities.first.url : null;
    var streamUrl = mp4Url ?? variants[0].url!;

    return () async => TweetVideoUrls(streamUrl, mp4Url, qualities: qualities);
  }

  // Resolution tag from X's MP4 URL path (`.../1280x720/...`), else the bitrate.
  static String _qualityLabel(String url, int? bitrate) {
    var match = RegExp(r'/(\d+)x(\d+)/').firstMatch(url);
    if (match != null) {
      return '${match.group(2)}p';
    }
    if (bitrate != null) {
      return '${(bitrate / 1000000).toStringAsFixed(1)} Mbps';
    }
    return '—';
  }

  factory TweetVideoMetadata.fromMedia(Media media) {
    var aspectRatio = media.videoInfo?.aspectRatio == null
        ? 1.0
        : media.videoInfo!.aspectRatio![0] / media.videoInfo!.aspectRatio![1];

    var variants = media.videoInfo?.variants ?? [];
    var imageUrl = media.mediaUrlHttps!;

    return TweetVideoMetadata(
      aspectRatio,
      imageUrl,
      streamUrlsBuilderFromVariants(variants),
    );
  }
}

class TweetVideo extends StatefulWidget {
  final String username;
  final bool loop;
  final TweetVideoMetadata metadata;
  final bool alwaysPlay;
  final bool disableControls;
  final String? tweetId;
  final int mediaIndex;

  /// Called once when playback fails before the first frame (e.g. CDN 403).
  final VoidCallback? onPlaybackError;

  const TweetVideo({
    super.key,
    required this.username,
    required this.loop,
    required this.metadata,
    this.alwaysPlay = false,
    this.disableControls = false,
    this.tweetId,
    this.mediaIndex = 0,
    this.onPlaybackError,
  });

  @override
  State<StatefulWidget> createState() => _TweetVideoState();
}

class _TweetVideoState extends State<TweetVideo> {
  VideoControllerPool? _pool;
  PooledVideo? _pooled;
  Future<PooledVideo>? _acquireFuture;
  bool _ownsControllers = false;
  bool _holdsPoolRef = false;

  bool _autoPlay = false;
  bool _userRequestedPlay = false;
  bool _isFullscreen = false;
  bool _playbackError = false;
  bool _firstFrameRendered = false;
  bool _posterGone = false;
  bool _subtitlesEnabled = false;
  bool _prefLoop = false;
  bool _mixWithOthers = false;
  int _autoRetries = 0;
  final Key _visibilityKey = UniqueKey();
  final Key _creationGateKey = UniqueKey();
  bool _hasBeenVisible = false;
  double _lastVisibleFraction = 0.0;
  Timer? _pauseTimer;
  Timer? _releaseTimer;
  Timer? _creationGateTimer;
  StreamSubscription<double>? _muteSub;
  StreamSubscription<String>? _errorSub;
  StreamSubscription<bool>? _playingSub;

  String? get _cacheKey =>
      widget.tweetId == null ? null : '${widget.tweetId}:${widget.mediaIndex}';

  @override
  void initState() {
    super.initState();
    try {
      _pool = context.read<VideoControllerPool>();
    } on ProviderNotFoundException {
      _pool = null;
    }
  }

  // Default variant from [optionMediaVideoQuality]; qualities are sorted highest-first.
  static String _defaultQualityUrl(TweetVideoUrls urls, String quality) {
    final q = urls.qualities;
    if (q.isEmpty) return urls.streamUrl;
    final i = switch (MediaQuality.fromStored(
      quality,
      fallback: MediaQuality.large,
    )) {
      MediaQuality.thumb => q.length - 1,
      MediaQuality.small => (q.length * 3) ~/ 4,
      MediaQuality.medium => q.length ~/ 2,
      MediaQuality.large => 0,
    };
    return q[i.clamp(0, q.length - 1)].url;
  }

  Future<PooledVideo> _createPooled(
    bool prefLoop,
    bool startMuted,
    String quality,
    int prefetchSeconds,
    bool directHwdec,
  ) async {
    var urls = await widget.metadata.streamUrlsBuilder();
    var streamUrl = _defaultQualityUrl(urls, quality);

    var player = mk.Player();
    var videoController = VideoController(player);

    var platform = player.platform;
    if (platform is mk.NativePlayer) {
      // AAudio sounds better than the default opensles and avoids the
      // audiotrack JNI crash; falls back to opensles below Android 8.
      await platform.setProperty('ao', 'aaudio,opensles');
      // System MediaCodec decoders, with libmpv's software decoders as fallback.
      //
      // `mediacodec-copy` copies every decoded frame back into system memory
      // before it is uploaded to a texture; `mediacodec` hands the decoder's own
      // surface over and copies nothing. The direct path is much cheaper and is
      // what makes a feed scroll while a video plays, but it renders black on
      // some devices — hence a setting rather than a default.
      await platform.setProperty(
        'hwdec',
        directHwdec ? 'mediacodec' : 'mediacodec-copy',
      );

      // How far ahead a feed video reads.
      //
      // libmpv is built for a player you sit down in front of, so it reads far
      // ahead and keeps a long way back — sensible for one film, wasteful for a
      // timeline where most videos are watched for seconds and many are never
      // watched at all. cache-secs only bounded that when the reader had set a
      // prefetch, and it is zero by default, so out of the box nothing capped
      // it at all.
      //
      // The demuxer bounds are what actually govern this: cache-secs limits
      // time, these limit the bytes behind it. Both are set, and the reader's
      // own prefetch still overrides the time.
      await platform.setProperty(
        'cache-secs',
        '${prefetchSeconds > 0 ? prefetchSeconds : kVideoReadaheadSeconds}',
      );
      await platform.setProperty(
        'demuxer-readahead-secs',
        '$kVideoReadaheadSeconds',
      );
      await platform.setProperty('demuxer-max-bytes', '$kVideoDemuxerMaxBytes');
      // What is kept of what has already played, for scrubbing back. Small: a
      // feed is not somewhere anyone rewinds far.
      await platform.setProperty(
        'demuxer-max-back-bytes',
        '$kVideoDemuxerMaxBackBytes',
      );
    }

    await player.setPlaylistMode(
      (widget.loop || prefLoop) ? mk.PlaylistMode.single : mk.PlaylistMode.none,
    );
    await player.setVolume(startMuted ? 0.0 : 100.0);
    await player.open(
      mk.Media(streamUrl, httpHeaders: urls.httpHeaders),
      play: widget.alwaysPlay || _userRequestedPlay,
    );

    return PooledVideo(
      player: player,
      videoController: videoController,
      downloadUrl: urls.downloadUrl,
      qualities: urls.qualities,
      currentStreamUrl: streamUrl,
      pausableByPolicy: !widget.disableControls,
      httpHeaders: urls.httpHeaders,
    );
  }

  Future<PooledVideo> _acquire(bool prefLoop) async {
    var startMuted = context.read<VideoContextState>().isMuted;
    var prefs = PrefService.of(context, listen: false);
    var quality = prefs.get(optionMediaVideoQuality);
    var prefetchSeconds = prefs.get<int>(optionMediaVideoPrefetchSeconds) ?? 0;
    var directHwdec =
        prefs.get<bool>(optionMediaDirectHardwareDecoding) ?? false;
    create() => _createPooled(
      prefLoop,
      startMuted,
      quality,
      prefetchSeconds,
      directHwdec,
    );

    final key = _cacheKey;
    final pool = _pool;
    PooledVideo pooled;
    if (key == null || pool == null) {
      _ownsControllers = true;
      pooled = await create();
      if (!mounted) {
        await pooled.dispose();
        return pooled;
      }
    } else {
      pooled = await pool.acquire(key, create);
      if (!mounted) {
        pool.release(key);
        return pooled;
      }
      _holdsPoolRef = true;
    }

    _pooled = pooled;
    _attachListeners(pooled);
    return pooled;
  }

  void _attachListeners(PooledVideo pooled) {
    var model = context.read<VideoContextState>();
    pooled.player.setVolume(model.isMuted ? 0.0 : 100.0);
    _muteSub = pooled.player.stream.volume.listen((volume) {
      if (!mounted) return;
      model.setIsMuted(volume);
    });
    _playingSub = pooled.player.stream.playing.listen((playing) {
      if (!mounted) return;
      if (widget.disableControls) return;
      if (playing) {
        _autoRetries = 0;
        if (_playbackError) setState(() => _playbackError = false);
        _pool?.pauseOthers(pooled);
        VideoAudioFocus.instance.onStartedPlaying(
          pooled.player,
          mixWithOthers: _mixWithOthers,
        );
      } else {
        VideoAudioFocus.instance.onStoppedPlaying(pooled.player);
      }
    });
    _errorSub = pooled.player.stream.error.listen((_) {
      if (!mounted) return;
      // GIFs must keep looping — retry silently instead of showing an error.
      if (widget.disableControls) {
        if (_autoRetries < 3) {
          _autoRetries++;
          _restartVideo(_prefLoop);
        }
        return;
      }
      // Ignore transient mid-playback errors libmpv recovers from; only a video
      // that never rendered a frame is a real failure.
      if (!_firstFrameRendered && !_playbackError) {
        setState(() => _playbackError = true);
        widget.onPlaybackError?.call();
      }
    });

    pooled.videoController.waitUntilFirstFrameRendered.then((_) {
      if (mounted) setState(() => _firstFrameRendered = true);
    });
  }

  void _detachListeners() {
    _muteSub?.cancel();
    _muteSub = null;
    _errorSub?.cancel();
    _errorSub = null;
    _playingSub?.cancel();
    _playingSub = null;
  }

  void _cancelVisibilityTimers() {
    _pauseTimer?.cancel();
    _pauseTimer = null;
    _releaseTimer?.cancel();
    _releaseTimer = null;
  }

  void _onVisibilityChanged(VisibilityInfo info, PooledVideo pooled) {
    if (!mounted) return;
    final key = _cacheKey;
    final wasVisible = _lastVisibleFraction >= 0.5;
    final isVisible = info.visibleFraction >= 0.5;
    _lastVisibleFraction = info.visibleFraction;

    // Native fullscreen covers this tile (opaque route → not painted). Treat that
    // as still "in use": reclaim must not release the pool ref under the route.
    if (_isFullscreen) {
      _cancelVisibilityTimers();
      return;
    }

    if (isVisible) {
      if (key != null) _pool?.markVisible(key, this);
      _cancelVisibilityTimers();
      if ((_autoPlay || widget.alwaysPlay) &&
          !wasVisible &&
          !pooled.player.state.playing) {
        pooled.player.play();
      }
    } else if (wasVisible) {
      // `alwaysPlay` says a looping GIF needs no tap to start, not that it may
      // keep decoding once nobody can see it. Exempting it here left every GIF
      // scrolled past still decoding, and each one pinned its pooled player so
      // the pool could not evict it either — the feed accumulated live libmpv
      // instances for as long as it was scrolled.
      if (key != null) _pool?.markHidden(key, this);
      _pauseTimer ??= Timer(const Duration(milliseconds: 100), () {
        _pauseTimer = null;
        if (key != null && (_pool?.anyVisible(key) ?? false)) return;
        if (mounted && !_isFullscreen) {
          pooled.player.pause();
        }
      });
      // Pausing stops the decoding; it does not give back the MediaCodec
      // session, the demuxer thread or the cache behind them. Only letting go
      // of the pool reference does, and only then can the pool evict. Held off
      // long enough that a scroll that overshoots and comes back re-attaches to
      // the same player at the same position instead of restarting it.
      _releaseTimer ??= Timer(kVideoHiddenReleaseDelay, _releaseWhileHidden);
    }
  }

  /// Hand the pooled player back while this tile is off screen, and fall back to
  /// the poster. The pool keeps the entry cached, so scrolling back re-attaches
  /// to it — but with no reference held it is now evictable, which is what keeps
  /// the number of live players bounded by the pool's size.
  void _releaseWhileHidden() {
    _releaseTimer = null;
    final key = _cacheKey;
    if (!shouldReleaseHiddenPlayer(
      isFullscreen: _isFullscreen,
      mounted: mounted,
      hasPoolKey: key != null && _pool != null,
      anyVisible: key != null && (_pool?.anyVisible(key) ?? false),
      visibleFraction: _lastVisibleFraction,
    )) {
      return;
    }

    _detachListeners();
    if (_holdsPoolRef) {
      _pool!.release(key!);
      _holdsPoolRef = false;
    }

    setState(() {
      _pooled = null;
      _acquireFuture = null;
      _firstFrameRendered = false;
      _posterGone = false;
      // Re-arms the creation gate, so nothing is allocated again until this tile
      // is actually back on screen.
      _hasBeenVisible = false;
    });
  }

  Future<void> _openFullscreen(
    PooledVideo pooled,
    bool prefBackgroundPlayback,
  ) async {
    if (_isFullscreen) return;
    _isFullscreen = true;
    _cancelVisibilityTimers();
    // Capture navigator before awaits — orientation change / list recycle may
    // unmount this tile while fullscreen is still showing.
    final navigator = Navigator.of(context, rootNavigator: true);
    final accent = Theme.of(context).colorScheme.secondary;
    try {
      await enterTweetVideoFullscreenUi(widget.metadata.aspectRatio);
      await pushTweetVideoFullscreen(
        navigator: navigator,
        pooled: pooled,
        username: widget.username,
        accentColor: accent,
        subtitlesEnabled: _subtitlesEnabled,
        onToggleSubtitles: () => _toggleSubtitles(pooled),
        pauseUponEnteringBackgroundMode: !prefBackgroundPlayback,
        aspectRatio: widget.metadata.aspectRatio,
      );
    } finally {
      _isFullscreen = false;
      await defaultExitNativeFullscreen();
    }
  }

  Future<void> _restartVideo(bool prefLoop) async {
    _detachListeners();
    final key = _cacheKey;
    if (key != null && _pool != null) {
      if (_holdsPoolRef) {
        _pool!.release(key);
        _holdsPoolRef = false;
      }
      _pool!.invalidate(key);
    } else {
      await _pooled?.player.pause();
      await _pooled?.dispose();
    }

    setState(() {
      _pooled = null;
      _acquireFuture = null;
      _playbackError = false;
      _firstFrameRendered = false;
      _posterGone = false;
    });
  }

  void _toggleSubtitles(PooledVideo pooled) {
    final enable = !_subtitlesEnabled;
    _subtitlesEnabled = enable;
    if (mounted) setState(() {});
    if (enable) {
      final subs = pooled.player.state.tracks.subtitle;
      final track = subs.firstWhere(
        (t) => t.id != 'no' && t.id != 'auto',
        orElse: () => mk.SubtitleTrack.auto(),
      );
      pooled.player.setSubtitleTrack(track);
    } else {
      pooled.player.setSubtitleTrack(mk.SubtitleTrack.no());
    }
  }

  Widget _buildVideo(PooledVideo pooled, bool prefBackgroundPlayback) {
    final accent = Theme.of(context).colorScheme.secondary;
    final video = Video(
      controller: pooled.videoController,
      aspectRatio: widget.metadata.aspectRatio,
      controls: widget.disableControls
          ? null
          : (state) => XtaControls(
              pooled: pooled,
              username: widget.username,
              allowMuting: true,
              accentColor: accent,
              subtitlesEnabled: _subtitlesEnabled,
              onToggleSubtitles: () => _toggleSubtitles(pooled),
              onToggleFullscreen: () =>
                  _openFullscreen(pooled, prefBackgroundPlayback),
            ),
      wakelock: !widget.disableControls,
      pauseUponEnteringBackgroundMode: !prefBackgroundPlayback,
      subtitleViewConfiguration: SubtitleViewConfiguration(
        visible: _subtitlesEnabled,
      ),
    );

    if (_posterGone) {
      return video;
    }

    // Poster + spinner over the always-painting video texture, fading out on the
    // first frame so there's no black flash on the swap.
    return Stack(
      fit: StackFit.expand,
      children: [
        video,
        IgnorePointer(
          child: AnimatedOpacity(
            opacity: _firstFrameRendered ? 0.0 : 1.0,
            duration: const Duration(milliseconds: 200),
            onEnd: () {
              if (_firstFrameRendered && !_posterGone)
                setState(() => _posterGone = true);
            },
            child: Stack(
              fit: StackFit.expand,
              alignment: Alignment.center,
              children: [
                if (widget.metadata.imageUrl != null)
                  CappedNetworkImage(url: widget.metadata.imageUrl!),
                if (!widget.disableControls)
                  const Center(child: CircularProgressIndicator()),
              ],
            ),
          ),
        ),
      ],
    );
  }

  /// The thumbnail X ships with the video, at the video's own aspect ratio, so
  /// the tile occupies its final size before any player exists.
  Widget _poster({Widget? child}) {
    return AspectRatio(
      aspectRatio: widget.metadata.aspectRatio,
      child: Stack(
        alignment: Alignment.center,
        children: [
          if (widget.metadata.imageUrl != null)
            Positioned.fill(
              child: CappedNetworkImage(url: widget.metadata.imageUrl!),
            ),
          ?child,
        ],
      ),
    );
  }

  /// Opens the gate only for a tile that has come to rest on screen.
  ///
  /// One visible pixel used to be enough, so a fling allocated a libmpv player
  /// and a native texture for every video it swept past — the tiles most
  /// certainly not being watched. Requiring half the tile, and requiring it to
  /// still be there a moment later, means a fling costs nothing and only the
  /// video the reader stopped at is built.
  void _onCreationGateVisibilityChanged(VisibilityInfo info) {
    if (!mounted || _hasBeenVisible) return;
    _lastVisibleFraction = info.visibleFraction;

    if (info.visibleFraction < 0.5) {
      _creationGateTimer?.cancel();
      _creationGateTimer = null;
      return;
    }

    _creationGateTimer ??= Timer(kVideoCreationSettleDelay, () {
      _creationGateTimer = null;
      if (!mounted || _hasBeenVisible || _lastVisibleFraction < 0.5) return;
      setState(() => _hasBeenVisible = true);
    });
  }

  @override
  Widget build(BuildContext context) {
    final prefs = PrefService.of(context, listen: false);
    final prefLoop = prefs.get(optionMediaDefaultLoop);
    final prefAutoPlay = prefs.get(optionMediaDefaultAutoPlay);
    final prefBackgroundPlayback = prefs.get(optionMediaBackgroundPlayback);
    _prefLoop = prefLoop;
    _mixWithOthers = prefs.get(optionMediaAllowBackgroundPlayOtherApps);

    final key = _cacheKey;
    final alreadyCached = key != null && (_pool?.contains(key) ?? false);

    if (showsPlayButton(
      autoPlayPref: prefAutoPlay,
      alwaysPlay: widget.alwaysPlay,
      userRequestedPlay: _userRequestedPlay,
      alreadyCached: alreadyCached,
    )) {
      return GestureDetector(
        onTap: () => setState(() => _userRequestedPlay = true),
        child: _poster(
          child: FritterCenterPlayButton(
            backgroundColor: Colors.black54,
            iconColor: Colors.white,
            show: true,
            isPlaying: false,
            isFinished: false,
            onPressed: () => setState(() => _userRequestedPlay = true),
          ),
        ),
      );
    }

    // Autoplaying videos and looping GIFs never reach the tap gate above, so
    // without this an off-screen tile would allocate a libmpv player and a
    // native texture purely by being built.
    if (!shouldCreatePlayer(
      autoPlayPref: prefAutoPlay,
      alwaysPlay: widget.alwaysPlay,
      userRequestedPlay: _userRequestedPlay,
      alreadyCached: alreadyCached,
      hasBeenVisible: _hasBeenVisible,
      isVisible: _lastVisibleFraction >= 0.5,
    )) {
      return VisibilityDetector(
        key: _creationGateKey,
        onVisibilityChanged: _onCreationGateVisibilityChanged,
        child: _poster(),
      );
    }

    _autoPlay = prefAutoPlay;
    _acquireFuture ??= _acquire(prefLoop);

    return FutureBuilder(
      future: _acquireFuture,
      builder: (context, snapshot) {
        final hasError = snapshot.hasError || _playbackError;
        final isLoading = snapshot.connectionState == ConnectionState.waiting;
        final pooled = _pooled ?? (key != null ? _pool?.peek(key) : null);
        final hasVideo = pooled != null;

        if (isLoading && !hasVideo) {
          return _poster(child: const CircularProgressIndicator());
        }

        if (hasError && !_firstFrameRendered) {
          return AspectRatio(
            aspectRatio: widget.metadata.aspectRatio,
            child: Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(
                    Icons.error_outline,
                    color: Colors.white,
                    size: 48,
                  ),
                  const SizedBox(height: 12),
                  Text(L10n.of(context).failed_to_load_video),
                  const SizedBox(height: 12),
                  ElevatedButton(
                    onPressed: () => _restartVideo(prefLoop),
                    child: Text(L10n.of(context).restart_video_player),
                  ),
                ],
              ),
            ),
          );
        }

        return AspectRatio(
          aspectRatio: widget.metadata.aspectRatio,
          child: hasVideo
              ? VisibilityDetector(
                  key: _visibilityKey,
                  onVisibilityChanged: (info) =>
                      _onVisibilityChanged(info, pooled),
                  child: _buildVideo(pooled, prefBackgroundPlayback),
                )
              : const SizedBox.shrink(),
        );
      },
    );
  }

  @override
  void dispose() {
    _pauseTimer?.cancel();
    _releaseTimer?.cancel();
    _creationGateTimer?.cancel();
    _detachListeners();
    final key = _cacheKey;
    if (key != null) _pool?.markHidden(key, this);
    // Keep the pool ref across the native fullscreen handoff. The fullscreen
    // route uses the same player; releasing here would let eviction dispose it
    // while it is still on screen. Detaching listeners is always safe.
    if (!_isFullscreen) {
      if (_ownsControllers) {
        _pooled?.dispose();
      } else if (key != null && _holdsPoolRef) {
        // A fast fling can dispose this widget before the debounced pause timer
        // fires; releasing the pool ref alone leaves the player running off-screen.
        // Pause it now, unless the same video is still on screen in another widget.
        if (!(_pool?.anyVisible(key) ?? false)) {
          _pooled?.player.pause();
        }
        _pool?.release(key);
        _holdsPoolRef = false;
      }
    }
    super.dispose();
  }
}

/// Mute is an app-wide toggle: muting one video keeps the next one muted, on
/// every screen. Tweet tiles each sit under their own [VideoContextState]
/// provider, so a single shared [ValueNotifier] is the source of truth and every
/// per-scope instance forwards its changes — that way all scopes stay in sync and
/// rebuild together (a plain static field only notified the one scope that fired).
class VideoContextState extends ChangeNotifier {
  static final ValueNotifier<bool> _muted = ValueNotifier(false);
  static bool _initialised = false;

  VideoContextState(bool initialMuted) {
    // The pref is only the initial default; once set, mute is user-controlled.
    if (!_initialised) {
      _initialised = true;
      _muted.value = initialMuted;
    }
    _muted.addListener(notifyListeners);
  }

  @override
  void dispose() {
    _muted.removeListener(notifyListeners);
    super.dispose();
  }

  bool get isMuted => _muted.value;

  void setIsMuted(double volume) {
    final muted = _muted.value;
    if (muted && volume > 0 || !muted && volume == 0) {
      _muted.value = !muted;
    }
  }
}
