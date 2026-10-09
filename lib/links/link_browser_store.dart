import 'package:flutter_triple/flutter_triple.dart';

/// What the browser chrome shows about the page underneath it.
class LinkBrowserState {
  final String url;
  final bool loading;

  /// 0–1 while [loading]; the page reports it as it goes.
  final double progress;

  const LinkBrowserState({required this.url, this.loading = true, this.progress = 0});

  bool get secure => Uri.tryParse(url)?.scheme == 'https';

  LinkBrowserState copyWith({String? url, bool? loading, double? progress}) =>
      LinkBrowserState(url: url ?? this.url, loading: loading ?? this.loading, progress: progress ?? this.progress);
}

/// Page events land here; the chrome rebuilds from it.
///
/// A webview keeps reporting for a moment after its screen is gone, so every
/// event after [destroy] is dropped rather than written to a closed store.
class LinkBrowserStore extends Store<LinkBrowserState> {
  var _closed = false;

  LinkBrowserStore(String url) : super(LinkBrowserState(url: url));

  void started(String url) => _apply(state.copyWith(url: url.isEmpty ? null : url, loading: true, progress: 0));

  void progressed(int percent) => _apply(state.copyWith(progress: (percent / 100).clamp(0.0, 1.0)));

  void finished(String url) => _apply(state.copyWith(url: url.isEmpty ? null : url, loading: false, progress: 1));

  void stopped() => _apply(state.copyWith(loading: false));

  void _apply(LinkBrowserState next) {
    if (!_closed) update(next);
  }

  @override
  Future<void> destroy() {
    _closed = true;
    return super.destroy();
  }
}
