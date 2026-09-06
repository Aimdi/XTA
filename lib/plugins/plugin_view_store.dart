import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';

/// Session-only view choices survive a Home source remount, without writing prefs.
class PluginViewStore<T> extends Store<T> {
  PluginViewStore(super.initialState);

  bool _restored = false;
  void Function(T)? _save;

  void restore(BuildContext context, String pluginId) {
    if (_restored) return;
    _restored = true;
    final bucket = PageStorage.maybeOf(context);
    if (bucket == null) return;
    final id = 'plugin-view-$pluginId';
    final previous = bucket.readState(context, identifier: id);
    if (previous is T) update(previous);
    _save = (value) => bucket.writeState(context, value, identifier: id);
  }

  void select(T value) {
    if (value == state) return;
    update(value);
    _save?.call(value);
  }
}
