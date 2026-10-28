import 'package:flutter/gestures.dart';
import 'package:xta/ui/swipe_back_card.dart';

/// Takes a drag that heads back: once it has gone [kSwipeBackSlop] it must be
/// going [direction] across, [kSwipeBackSideways] times more than up or down,
/// or it is left to the page. It then takes the pointer no sooner than a
/// sideways drag inside the card would, so a row of photos or a seek bar,
/// being deeper, wins its own drags.
///
/// Given the pointer before that, as the only one that wanted it, it still
/// waits for the drag: a touch that does not head back lifts nothing.
class SwipeBackDragRecognizer extends OneSequenceGestureRecognizer {
  SwipeBackDragRecognizer({super.debugOwner})
    : super(
        supportedDevices: const {
          PointerDeviceKind.touch,
          PointerDeviceKind.stylus,
          PointerDeviceKind.invertedStylus,
          PointerDeviceKind.mouse,
        },
      );

  /// 1 when back is to the right, -1 when it is to the left.
  double direction = 1;

  /// Whether a drag may start where [event] went down.
  bool Function(PointerDownEvent event)? canStart;

  /// Whether a pointer going down takes the card at once, as one does while
  /// the card drops back.
  bool Function()? catching;

  void Function(PointerDownEvent down)? onGrab;
  void Function(PointerEvent event)? onMove;
  void Function()? onRelease;
  void Function()? onCancel;

  PointerDownEvent? _down;
  bool _aimed = false;
  bool _ready = false;
  bool _won = false;
  bool _taken = false;

  @override
  bool isPointerAllowed(PointerDownEvent event) =>
      _down == null && super.isPointerAllowed(event) && (canStart?.call(event) ?? false);

  @override
  void addAllowedPointer(PointerDownEvent event) {
    super.addAllowedPointer(event);
    _down = event;
    if (catching?.call() ?? false) {
      _ready = true;
      resolve(GestureDisposition.accepted);
    }
  }

  @override
  void handleEvent(PointerEvent event) {
    final down = _down;
    if (down == null || event.pointer != down.pointer) return;
    if (event is PointerMoveEvent) {
      _taken ? onMove?.call(event) : _consider(event, down);
    } else if (event is PointerUpEvent || event is PointerCancelEvent) {
      _end(event);
    }
  }

  void _consider(PointerMoveEvent event, PointerDownEvent down) {
    final travel = event.position - down.position;
    final across = travel.dx * direction;
    if (!_aimed) {
      if (travel.distance < kSwipeBackSlop) return;
      _aimed = true;
      if (across <= travel.dy.abs() * kSwipeBackSideways) return _giveUp(event.pointer);
    }
    if (across <= computeHitSlop(event.kind, gestureSettings)) return;
    _ready = true;
    _won ? _take() : resolve(GestureDisposition.accepted);
    if (_taken) onMove?.call(event);
  }

  void _end(PointerEvent event) {
    if (!_taken) return _giveUp(event.pointer);
    event is PointerUpEvent ? onRelease?.call() : onCancel?.call();
    stopTrackingPointer(event.pointer);
  }

  /// Rejecting is a no-op once the arena has given this the pointer.
  void _giveUp(int pointer) {
    resolve(GestureDisposition.rejected);
    stopTrackingPointer(pointer);
  }

  void _take() {
    final down = _down;
    if (down == null || _taken) return;
    _taken = true;
    onGrab?.call(down);
  }

  @override
  void acceptGesture(int pointer) {
    _won = true;
    if (_ready) _take();
  }

  @override
  void rejectGesture(int pointer) => stopTrackingPointer(pointer);

  @override
  void didStopTrackingLastPointer(int pointer) {
    _down = null;
    _aimed = false;
    _ready = false;
    _won = false;
    _taken = false;
  }

  @override
  String get debugDescription => 'swipe back';
}
