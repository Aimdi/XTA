import 'package:flutter/widgets.dart';
import 'package:xta/tweet/video_controller_pool.dart';

class VideoMemoryObserver with WidgetsBindingObserver {
  final VideoControllerPool pool;
  VideoMemoryObserver(this.pool) {
    WidgetsBinding.instance.addObserver(this);
  }
  @override
  void didHaveMemoryPressure() => pool.releaseUnused();
  void dispose() => WidgetsBinding.instance.removeObserver(this);
}
