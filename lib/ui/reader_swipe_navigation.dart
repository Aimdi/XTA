import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/physics.dart';
import 'package:flutter/services.dart';
import 'package:xta/ui/motion.dart';

const double kReaderSwipeDistance = 72;
const double kReaderSwipeMinimumTravel = 24;

/// A release must agree with the travelled direction, and a flick still needs
/// enough travel to distinguish it from a slightly moving tap.
int pageAfterNavigationSwipe({
  required int current,
  required int pageCount,
  required double velocity,
  double distance = 0,
  double threshold = 650,
  double distanceThreshold = kReaderSwipeDistance,
  TextDirection textDirection = TextDirection.ltr,
}) {
  if (pageCount <= 1 || current < 0 || current >= pageCount) return current;
  if (!distance.isFinite || !velocity.isFinite || distance.abs() < kReaderSwipeMinimumTravel) return current;
  final flicked = velocity.abs() >= threshold;
  if (velocity != 0 && velocity.sign != distance.sign) return current;
  if (!flicked && distance.abs() < distanceThreshold) return current;
  final physicalDirection = distance < 0 ? 1 : -1;
  final direction = textDirection == TextDirection.rtl ? -physicalDirection : physicalDirection;
  final next = current + direction;
  return next < 0 || next >= pageCount ? current : next;
}

/// Shared release/cancellation policy for reader sections and app navigation.
/// Selection remains in the caller's store; only pointer and visual state live
/// here. Nested horizontal recognizers keep ownership, including at boundaries.
class ReaderSwipeNavigation extends StatefulWidget {
  final int index;
  final int count;
  final Object identity;
  final bool Function(int index) onChanged;
  final Widget Function(BuildContext context, int index)? previewBuilder;
  final Widget child;

  const ReaderSwipeNavigation({
    super.key,
    required this.index,
    required this.count,
    required this.identity,
    required this.onChanged,
    required this.child,
    this.previewBuilder,
  });

  @override
  State<ReaderSwipeNavigation> createState() => _ReaderSwipeNavigationState();
}

class _ReaderSwipeNavigationState extends State<ReaderSwipeNavigation> with SingleTickerProviderStateMixin {
  late final _visual = AnimationController.unbounded(vsync: this);
  final _pointers = <int>{};
  final _globalPointers = <int>{};
  Offset _origin = Offset.zero;
  Offset _travel = Offset.zero;
  double _firstDirection = 0;
  bool _tracking = false;
  bool _blocked = false;
  bool _selectionPending = false;
  bool _reduceMotion = false;
  TextDirection? _direction;

  bool get _routeActive => (ModalRoute.of(context)?.isCurrent ?? true) && TickerMode.valuesOf(context).enabled;

  @override
  void initState() {
    super.initState();
    GestureBinding.instance.pointerRouter.addGlobalRoute(_observePointers);
  }

  void _observePointers(PointerEvent event) {
    if (event is PointerDownEvent) {
      _globalPointers.add(event.pointer);
      // A second touch can land in another reader region, outside this widget.
      // Global observation only cancels; the recognizer still owns navigation.
      if (_globalPointers.length > 1 && _pointers.isNotEmpty) {
        _blocked = true;
        _cancel();
      }
    } else if (event is PointerUpEvent || event is PointerCancelEvent) {
      _globalPointers.remove(event.pointer);
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final direction = Directionality.of(context);
    if ((_direction != null && _direction != direction) || !_routeActive) _invalidate();
    _direction = direction;
    _reduceMotion = xtaReduceMotion(context);
    if (_reduceMotion && _visual.isAnimating) _visual.value = 0;
  }

  @override
  void didUpdateWidget(covariant ReaderSwipeNavigation oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.identity != oldWidget.identity || widget.index != oldWidget.index || widget.count != oldWidget.count) {
      _invalidate();
    }
    _reduceMotion = xtaReduceMotion(context);
    if (_reduceMotion && _visual.isAnimating) _visual.value = 0;
  }

  void _invalidate() {
    _tracking = false;
    _selectionPending = false;
    _blocked = _pointers.isNotEmpty;
    _visual.value = 0;
  }

  bool _canStart(PointerDownEvent event) {
    if (widget.count < 2 || _selectionPending || !_routeActive || _pointers.isNotEmpty || _globalPointers.isNotEmpty) {
      return false;
    }
    final insets = MediaQuery.systemGestureInsetsOf(context);
    final size = MediaQuery.sizeOf(context);
    final left = insets.left > 24 ? insets.left : 24.0;
    final right = insets.right > 24 ? insets.right : 24.0;
    return event.position.dx > left &&
        event.position.dx < size.width - right &&
        event.position.dy > insets.top &&
        event.position.dy < size.height - insets.bottom;
  }

  void _pointerDown(PointerDownEvent event) {
    _pointers.add(event.pointer);
    if (_pointers.length == 1) {
      _blocked = false;
      _origin = event.position;
    } else {
      _blocked = true;
      _cancel();
    }
  }

  void _start(DragStartDetails details) {
    if (_blocked || !_routeActive) return;
    // A new navigation cue cannot inherit direction or threshold progress from
    // the previous gesture's return animation.
    _visual.value = 0;
    _travel = Offset.zero;
    _firstDirection = 0;
    _tracking = true;
  }

  void _update(DragUpdateDetails details) {
    if (!_tracking || _blocked) return;
    _travel = details.globalPosition - _origin;
    if (_firstDirection == 0 && _travel.dx.abs() >= kReaderSwipeMinimumTravel) _firstDirection = _travel.dx.sign;
    if (_firstDirection != 0 && _travel.dx * _firstDirection < 0) {
      _blocked = true;
      _cancel();
      return;
    }
    _visual.value = _travel.dx;
  }

  void _end(DragEndDetails details) {
    // The global route may still include the ending pointer during onEnd.
    final valid = _tracking && !_blocked && _globalPointers.length <= 1 && _routeActive;
    _tracking = false;
    final target = valid && _travel.dx.abs() > _travel.dy.abs() * 1.3
        ? pageAfterNavigationSwipe(
            current: widget.index,
            pageCount: widget.count,
            velocity: details.primaryVelocity ?? 0,
            distance: _travel.dx,
            textDirection: Directionality.of(context),
          )
        : widget.index;
    _settle();
    if (target != widget.index && widget.onChanged(target)) {
      _selectionPending = true;
      HapticFeedback.selectionClick();
    }
  }

  void _cancel() {
    _tracking = false;
    _settle();
  }

  void _settle() {
    if (_reduceMotion || widget.previewBuilder == null || _visual.value == 0) {
      _visual.value = 0;
      return;
    }
    _visual.animateWith(
      SpringSimulation(
        SpringDescription.withDampingRatio(mass: 1, stiffness: 400, ratio: 1),
        _visual.value,
        0,
        0,
        snapToEnd: true,
      ),
    );
  }

  Widget _preview(BuildContext context) {
    final distance = _visual.value;
    if (distance.abs() < 1 || widget.previewBuilder == null) return const SizedBox.shrink();
    final physical = distance < 0 ? 1 : -1;
    final direction = Directionality.of(context) == TextDirection.rtl ? -physical : physical;
    final target = widget.index + direction;
    if (target < 0 || target >= widget.count) return const SizedBox.shrink();
    final progress = (distance.abs() / kReaderSwipeDistance).clamp(0.0, 1.0);
    final colors = Theme.of(context).colorScheme;
    return IgnorePointer(
      child: ExcludeSemantics(
        child: Align(
          alignment: distance < 0 ? Alignment.centerRight : Alignment.centerLeft,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: Opacity(
              opacity: _reduceMotion ? 1 : progress,
              child: Transform.translate(
                offset: _reduceMotion ? Offset.zero : Offset((distance < 0 ? 1 : -1) * 16 * (1 - progress), 0),
                child: ConstrainedBox(
                  constraints: BoxConstraints(maxWidth: MediaQuery.sizeOf(context).width * .65),
                  child: Material(
                    key: const ValueKey('home-swipe-preview'),
                    color: progress >= 1 ? colors.primaryContainer : colors.surfaceContainerHighest,
                    borderRadius: BorderRadius.circular(12),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                      child: IconTheme(
                        data: IconThemeData(color: progress >= 1 ? colors.onPrimaryContainer : colors.onSurface),
                        child: DefaultTextStyle(
                          style: Theme.of(context).textTheme.labelLarge!.copyWith(
                            color: progress >= 1 ? colors.onPrimaryContainer : colors.onSurface,
                          ),
                          child: widget.previewBuilder!(context, target),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Listener(
    behavior: HitTestBehavior.translucent,
    onPointerDown: _pointerDown,
    onPointerUp: (event) => _pointers.remove(event.pointer),
    onPointerCancel: (event) {
      _pointers.remove(event.pointer);
      _blocked = true;
      _cancel();
    },
    child: RawGestureDetector(
      behavior: HitTestBehavior.translucent,
      excludeFromSemantics: true,
      gestures: {
        _ReaderHorizontalDrag: GestureRecognizerFactoryWithHandlers<_ReaderHorizontalDrag>(
          () => _ReaderHorizontalDrag(),
          (recognizer) => recognizer
            ..canStart = _canStart
            ..dragStartBehavior = DragStartBehavior.down
            ..onStart = _start
            ..onUpdate = _update
            ..onEnd = _end
            ..onCancel = _cancel,
        ),
      },
      child: AnimatedBuilder(
        animation: _visual,
        child: widget.child,
        builder: (context, child) => widget.previewBuilder == null
            ? child!
            : Stack(
                fit: StackFit.passthrough,
                children: [
                  child!,
                  Positioned.fill(child: _preview(context)),
                ],
              ),
      ),
    ),
  );

  @override
  void dispose() {
    GestureBinding.instance.pointerRouter.removeGlobalRoute(_observePointers);
    _visual.dispose();
    super.dispose();
  }
}

/// Standard arena arbitration, with pointer-down gating for OS back edges.
class _ReaderHorizontalDrag extends HorizontalDragGestureRecognizer {
  _ReaderHorizontalDrag()
    : super(supportedDevices: {PointerDeviceKind.touch, PointerDeviceKind.stylus, PointerDeviceKind.invertedStylus});

  bool Function(PointerDownEvent event)? canStart;

  @override
  bool isPointerAllowed(PointerEvent event) =>
      event is PointerDownEvent && (canStart?.call(event) ?? false) && super.isPointerAllowed(event);

  @override
  bool isPointerPanZoomAllowed(PointerPanZoomStartEvent event) => false;
}
