import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';

/// A full-width choice between a few views of one list, such as
/// Illustrations / Manga or Public / Private, above that list. Tapping the
/// view shown calls [onReselect] when there is one.
class PixivSegmentedSwitch<T> extends StatelessWidget {
  final List<T> values;
  final String Function(T value) label;
  final T selected;
  final ValueChanged<T> onSelected;
  final VoidCallback? onReselect;

  const PixivSegmentedSwitch({
    super.key,
    required this.values,
    required this.label,
    required this.selected,
    required this.onSelected,
    this.onReselect,
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
        // The only way SegmentedButton reports a tap on the segment shown is
        // as that segment being unselected; the selection itself stays.
        emptySelectionAllowed: onReselect != null,
        onSelectionChanged: (selection) => selection.isEmpty ? onReselect?.call() : onSelected(selection.first),
      ),
    ),
  );
}

/// Public or Private follows, for the lists of who or what the reader follows.
class PixivFollowRestrictSwitch extends StatelessWidget {
  final String restrict;
  final ValueChanged<String> onChanged;
  final VoidCallback? onReselect;

  const PixivFollowRestrictSwitch({super.key, required this.restrict, required this.onChanged, this.onReselect});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PixivSegmentedSwitch<String>(
      values: const ['public', 'private'],
      label: (value) => value == 'private' ? l10n.plugin_pixiv_follow_private : l10n.plugin_pixiv_follow_public,
      selected: restrict,
      onSelected: onChanged,
      onReselect: onReselect,
    );
  }
}
