import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/home/home_timeline_controls.dart';

const homeKeepControlsVisibleKey = 'home_keep_controls_visible';

@immutable
class HomeReadingControlsState {
  final String source;
  final bool visible;
  final bool pinned;
  const HomeReadingControlsState({this.source = '', this.visible = true, this.pinned = false});
}

/// Presentation only: never owns a reader, request or scroll controller.
class HomeReadingControlsStore extends Store<HomeReadingControlsState> {
  BasePrefService? _prefs;
  bool _closed = false;
  double _travel = 0;
  int _direction = 0;
  Future<void> _writes = Future.value();

  HomeReadingControlsStore() : super(const HomeReadingControlsState());

  void bind(String source, BasePrefService? prefs) {
    if (_closed) return;
    _prefs = prefs;
    final pinned = prefs == null
        ? state.pinned
        : prefs.getKeys().contains(homeKeepControlsVisibleKey) && prefs.get(homeKeepControlsVisibleKey) == true;
    if (source != state.source || pinned != state.pinned) {
      _travel = 0;
      _direction = 0;
      update(HomeReadingControlsState(source: source, pinned: pinned));
    }
  }

  void reveal() {
    _travel = 0;
    _direction = 0;
    if (!_closed && !state.visible) {
      update(HomeReadingControlsState(source: state.source, pinned: state.pinned));
    }
  }

  void observe({
    required String source,
    required double extentBefore,
    required double scrollExtent,
    required double controlsHeight,
    required double userDelta,
    bool protected = false,
  }) {
    if (_closed || source != state.source) return;
    // Account for the space already reclaimed; otherwise short feeds can
    // repeatedly hide/reveal as their viewport grows and shrinks.
    final expandedExtent = scrollExtent + (state.visible ? 0 : controlsHeight);
    if (protected || state.pinned || extentBefore <= 0.5 || expandedExtent <= controlsHeight + 96) {
      reveal();
      return;
    }
    if (!userDelta.isFinite || userDelta == 0) return;
    final direction = userDelta > 0 ? 1 : -1;
    if (direction != _direction) _travel = 0;
    _direction = direction;
    _travel += userDelta.abs();
    final next = direction < 0 ? (_travel >= 24 || state.visible) : (_travel < 64 && state.visible);
    if (next == state.visible) return;
    update(HomeReadingControlsState(source: state.source, pinned: state.pinned, visible: next));
  }

  Future<void> setPinned(bool pinned) {
    if (_closed) return Future.value();
    _writes = _writes.then((_) async {
      if (_closed) return;
      final prefs = _prefs;
      try {
        if (prefs != null && !await prefs.set(homeKeepControlsVisibleKey, pinned)) return;
        if (!_closed) {
          _travel = 0;
          _direction = 0;
          update(HomeReadingControlsState(source: state.source, pinned: pinned));
        }
      } catch (_) {
        // Keep the previous selection when persistence fails.
      }
    });
    return _writes;
  }

  @override
  Future<void> destroy() async {
    _closed = true;
    await _writes;
    await super.destroy();
  }
}

/// Keeps the reader mounted while only its secondary control row changes height.
class HomeReadingViewport extends StatefulWidget {
  final HomeReadingControlsStore store;
  final String source;
  final bool enabled;
  final BasePrefService? prefs;
  final Widget controls;
  final Widget child;
  const HomeReadingViewport({
    super.key,
    required this.store,
    required this.source,
    required this.enabled,
    required this.controls,
    required this.child,
    this.prefs,
  });

  @override
  State<HomeReadingViewport> createState() => _HomeReadingViewportState();
}

class _HomeReadingViewportState extends State<HomeReadingViewport> {
  final _controlsKey = GlobalKey();
  final _focus = FocusNode(debugLabel: 'Home reading controls', canRequestFocus: false);
  ScrollMetrics? _metrics;
  double _delta = 0;
  bool _queued = false;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _focus.addListener(_focusChanged);
    _bind();
  }

  @override
  void didUpdateWidget(HomeReadingViewport oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.source != oldWidget.source ||
        widget.store != oldWidget.store ||
        widget.enabled != oldWidget.enabled ||
        widget.prefs != oldWidget.prefs) {
      _generation++;
      _metrics = null;
      _delta = 0;
      _bind();
    }
  }

  void _bind() {
    final generation = _generation;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && generation == _generation) widget.store.bind(widget.source, widget.prefs);
    });
  }

  void _focusChanged() {
    if (_focus.hasFocus) widget.store.reveal();
  }

  void _observe(ScrollMetrics metrics, int depth, double delta) {
    if (!widget.enabled || depth != 0 || metrics.axis != Axis.vertical) return;
    _metrics = metrics.copyWith();
    if (delta != 0) {
      if (_delta.sign != delta.sign) _delta = 0;
      _delta += delta;
    }
    if (_queued) return;
    _queued = true;
    final generation = _generation;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _queued = false;
      if (!mounted || generation != _generation) return;
      final metrics = _metrics;
      final delta = _delta;
      _delta = 0;
      if (metrics == null) return;
      final media = MediaQuery.of(context);
      final height = _controlsKey.currentContext?.size?.height ?? 48;
      widget.store.observe(
        source: widget.source,
        extentBefore: metrics.extentBefore,
        scrollExtent: metrics.maxScrollExtent - metrics.minScrollExtent,
        controlsHeight: math.max(48, height),
        userDelta: delta,
        protected:
            _focus.hasFocus ||
            media.accessibleNavigation ||
            media.viewInsets.bottom > 0 ||
            View.of(context).viewInsets.bottom > 0 ||
            ModalRoute.of(context)?.isCurrent == false,
      );
    });
  }

  @override
  Widget build(BuildContext context) => Column(
    children: [
      ScopedBuilder<HomeReadingControlsStore, HomeReadingControlsState>(
        store: widget.store,
        onState: (context, state) => HomeCollapsingControls(
          key: const ValueKey('home-reading-context-viewport'),
          visible: !widget.enabled || state.source != widget.source || state.visible,
          child: Focus(
            focusNode: _focus,
            child: KeyedSubtree(key: _controlsKey, child: widget.controls),
          ),
        ),
      ),
      Expanded(
        child: NotificationListener<ScrollMetricsNotification>(
          onNotification: (notice) {
            _observe(notice.metrics, notice.depth, 0);
            return false;
          },
          child: NotificationListener<ScrollNotification>(
            onNotification: (notice) {
              final delta = notice is ScrollUpdateNotification && notice.dragDetails != null
                  ? notice.scrollDelta ?? 0.0
                  : 0.0;
              _observe(notice.metrics, notice.depth, delta);
              return false;
            },
            child: widget.child,
          ),
        ),
      ),
    ],
  );

  @override
  void dispose() {
    _generation++;
    _focus.removeListener(_focusChanged);
    _focus.dispose();
    super.dispose();
  }
}
