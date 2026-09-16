import 'package:flutter_triple/flutter_triple.dart';

/// A small lifecycle-owned value using the same Store contract as feature models.
class ReaderValueStore<T> extends Store<T> {
  ReaderValueStore(super.initialState);
}
