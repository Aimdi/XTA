import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/subscriptions/group_identity.dart';

/// Colours a group can be given.
///
/// X's own six accents first, read straight from the theme's palette — they are
/// already chosen to stay legible on all three backgrounds, and picking a group
/// colour out of the same set is what makes a coloured group look like part of
/// the app rather than like a Material demo. The rest fill out the wheel where
/// X leaves gaps, at the same weight.
final groupColorChoices = <Color>[
  // X's own six, taken from the theme rather than copied: one palette, one
  // place to change it.
  ...xLookAccents.values,
  const Color(0xFFF4212E), // red — X's own error red
  const Color(0xFFFF6E40), // coral
  const Color(0xFF00BCD4), // cyan
  const Color(0xFF7BC67E), // sage
  const Color(0xFFB388FF), // lilac
  const Color(0xFF8D6E63), // clay
  const Color(0xFF536471), // slate — X's grey
  const Color(0xFFE1E8ED), // bone
];

/// Picks a group's colour, or clears it back to the generated one.
///
/// Returns the choice; `null` for "no colour of its own", which is a real
/// answer and not a cancellation — the caller distinguishes them by whether the
/// future completed with a result at all.
Future<({Color? color})?> openGroupColorPicker(BuildContext context, {Color? current, String name = ''}) {
  return showDialog<({Color? color})>(
    context: context,
    builder: (dialogContext) => _GroupColorDialog(current: current, name: name),
  );
}

class _GroupColorDialog extends StatelessWidget {
  final Color? current;
  final String name;

  const _GroupColorDialog({required this.current, required this.name});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);

    return AlertDialog(
      title: Text(l10n.pick_a_color),
      contentPadding: const EdgeInsets.fromLTRB(24, 16, 24, 0),
      content: SizedBox(
        width: 320,
        child: Wrap(
          spacing: 12,
          runSpacing: 12,
          children: [
            // The generated colour, offered as itself rather than as an empty
            // slot: it is what the group already looks like.
            _Swatch(
              color: groupFallbackColor(name),
              selected: current == null,
              label: l10n.group_mark_style_auto,
              onTap: () => Navigator.pop(context, (color: null)),
            ),
            for (final color in groupColorChoices)
              _Swatch(
                color: color,
                selected: current?.toARGB32() == color.toARGB32(),
                onTap: () => Navigator.pop(context, (color: color)),
              ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(l10n.cancel, style: TextStyle(color: theme.colorScheme.onSurfaceVariant)),
        ),
      ],
    );
  }
}

/// One colour. Tapping it is the choice — there is no second confirmation,
/// because there is nothing to adjust after picking.
class _Swatch extends StatelessWidget {
  static const double _size = 48;

  final Color color;
  final bool selected;
  final String? label;
  final VoidCallback onTap;

  const _Swatch({required this.color, required this.selected, required this.onTap, this.label});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Semantics(
      label: label,
      selected: selected,
      button: true,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(_size),
        child: Container(
          width: _size,
          height: _size,
          decoration: BoxDecoration(
            color: color,
            shape: BoxShape.circle,
            // The ring sits outside the colour rather than over it, so a
            // selected swatch still shows the whole colour it stands for.
            border: selected ? Border.all(color: theme.colorScheme.onSurface, width: 3) : null,
          ),
          child: label == null
              ? null
              : Icon(Icons.auto_awesome, size: 20, color: onGroupSeed(color)),
        ),
      ),
    );
  }
}
