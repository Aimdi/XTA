import 'package:flutter_triple/flutter_triple.dart';

/// Small Store-backed selection state shared by Home navigation controls.
class HomeSelectionStore<T> extends Store<T> {
  HomeSelectionStore(super.initialState);

  void select(T value) {
    if (value != state) {
      update(value);
    }
  }
}
