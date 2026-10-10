import 'package:flutter/material.dart';

/// A full-width choice between a few views of one list, such as
/// Illustrations / Manga or Public / Private, above that list.
class PixivSegmentedSwitch<T> extends StatelessWidget {
  final List<T> values;
  final String Function(T value) label;
  final T selected;
  final ValueChanged<T> onSelected;

  const PixivSegmentedSwitch({
    super.key,
    required this.values,
    required this.label,
    required this.selected,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
    child: SizedBox(
      width: double.infinity,
      child: SegmentedButton<T>(
        segments: [
          for (final value in values)
            ButtonSegment(
              value: value,
              label: Text(label(value), maxLines: 1, overflow: TextOverflow.ellipsis),
            ),
        ],
        selected: {selected},
        showSelectedIcon: false,
        onSelectionChanged: (selection) => onSelected(selection.first),
      ),
    ),
  );
}
