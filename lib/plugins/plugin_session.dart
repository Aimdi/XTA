import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';

/// Owns the existing readers' stores for one Home lifetime. No offstage feeds,
/// new clients, preference writes, or background loading are introduced.
class PluginSessionStore extends Store<Map<String, Object>> {
  PluginSessionStore() : super(const {});
  final _disposers = <VoidCallback>[];

  T obtain<T extends Object>(String key, T Function() create, VoidCallback Function(T) disposer) {
    final previous = state[key];
    if (previous is T) return previous;
    final value = create();
    _disposers.add(disposer(value));
    update({...state, key: value});
    return value;
  }

  @override
  Future<void> destroy() async {
    for (final dispose in _disposers.reversed) {
      dispose();
    }
    _disposers.clear();
    await super.destroy();
  }
}

/// A reader outside Home owns its resources normally; inside Home they survive
/// source switches. Each mounted screen still owns its own scroll controllers.
class PluginSessionLease {
  final PluginSessionStore? _session;
  final String pluginId;
  final _localDisposers = <VoidCallback>[];
  PluginSessionLease(BuildContext context, this.pluginId) : _session = context.read<PluginSessionStore?>();

  T obtain<T extends Object>(String slot, T Function() create, {void Function(T)? dispose}) {
    VoidCallback disposer(T value) => () {
      if (dispose != null) {
        dispose(value);
      } else if (value is Store) {
        value.destroy();
      }
    };
    if (_session != null) return _session.obtain('$pluginId/$slot', create, disposer);
    final value = create();
    _localDisposers.add(disposer(value));
    return value;
  }

  void dispose() {
    for (final dispose in _localDisposers.reversed) {
      dispose();
    }
    _localDisposers.clear();
  }
}
