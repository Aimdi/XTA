import 'package:flutter_triple/flutter_triple.dart';

/// One value the post screen loads when it is first shown: tag kinds,
/// comments, a wiki page. Null until it has loaded.
class BooruLoadStore<T extends Object> extends Store<T?> {
  final Future<T> Function() loader;
  var _started = false;

  BooruLoadStore(this.loader) : super(null);

  /// Loads once; later calls do nothing.
  Future<void> ensure() async {
    if (_started) return;
    _started = true;
    await execute(loader);
  }

  Future<void> reload() async {
    _started = true;
    await execute(loader);
  }
}
