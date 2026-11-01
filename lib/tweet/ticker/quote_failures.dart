import 'package:flutter/foundation.dart';
import 'package:flutter_triple/flutter_triple.dart';

/// The keys of a quote store whose last lookup found nothing.
///
/// A row without a quote is either still loading or has nothing to show, and
/// the two must not look alike: a shimmer, or "No data · Tap to retry". The
/// store's state holds only what it found, so the failures are kept here, and
/// a change to them notifies the store's builders like any other update.
mixin QuoteFailures<T extends Object> on Store<T> {
  final Set<String> _failed = {};

  /// Keys whose last lookup failed and that are not being retried.
  Set<String> get failed => Set.unmodifiable(_failed);

  @protected
  void recordFailure(String key) {
    if (_failed.add(key)) update(state, force: true);
  }

  /// For an answer the store is about to put in its state, which notifies.
  @protected
  void recordAnswer(String key) => _failed.remove(key);

  /// An explicit retry: those rows show that they are loading again.
  @protected
  void retrying(Iterable<String> keys) {
    final before = _failed.length;
    _failed.removeAll(keys);
    if (_failed.length != before) update(state, force: true);
  }
}
