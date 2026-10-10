import 'package:flutter_triple/flutter_triple.dart';

/// Something Pixiv answers in one call: [initial] until the answer arrives,
/// and asked again on each [load], as a retry or a pull-to-refresh does.
class PixivFetchStore<T> extends Store<T> {
  final Future<T> Function() _fetch;

  PixivFetchStore(this._fetch, super.initial);

  Future<void> load() => execute(_fetch);
}
