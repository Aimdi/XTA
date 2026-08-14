import 'package:flutter/material.dart';
import 'package:xta/ui/x_look_theme.dart';

/// X-style controls: flat surfaces, full-round pills, no tonal containers and
/// no ink splashes. Used where Material's own widgets would otherwise stamp a
/// Material You look on the screen.

/// Fill for an inset control (search field, chip) — a small step off the page
/// rather than a tonal container.
Color xControlFill(BuildContext context) {
  final tokens = XLookTokens.maybeOf(context);
  if (tokens != null) {
    // X's own search fill: #EFF3F4 on white, a lifted slate on dim/lights out.
    return tokens.background.computeLuminance() > 0.5
        ? tokens.divider
        : Color.alphaBlend(
            tokens.onBackground.withValues(alpha: 0.10),
            tokens.background,
          );
  }
  final scheme = Theme.of(context).colorScheme;
  return Color.alphaBlend(
    scheme.onSurface.withValues(alpha: 0.08),
    scheme.surface,
  );
}

Color xAccent(BuildContext context) =>
    XLookTokens.maybeOf(context)?.accent ??
    Theme.of(context).colorScheme.primary;

Color xOnSurface(BuildContext context) =>
    XLookTokens.maybeOf(context)?.onBackground ??
    Theme.of(context).colorScheme.onSurface;

Color xSecondary(BuildContext context) =>
    XLookTokens.maybeOf(context)?.secondary ??
    Theme.of(context).colorScheme.onSurfaceVariant;

/// A filled pill, the reader's primary action shape. Bold label, no elevation, no
/// rounded-rectangle Material shape.
ButtonStyle xPrimaryPillStyle(BuildContext context) {
  final accent = xAccent(context);
  return FilledButton.styleFrom(
    backgroundColor: accent,
    foregroundColor: accent.computeLuminance() > 0.5
        ? Colors.black
        : Colors.white,
    elevation: 0,
    shape: const StadiumBorder(),
    padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
    textStyle: const TextStyle(
      fontWeight: FontWeight.w700,
      fontSize: 15,
      letterSpacing: -0.2,
    ),
  );
}

/// Pill-shaped search input: flat fill, no outline until focus, then X's accent
/// hairline and accent leading icon.
class XSearchField extends StatefulWidget {
  final TextEditingController controller;
  final String hintText;
  final ValueChanged<String>? onChanged;

  const XSearchField({
    super.key,
    required this.controller,
    required this.hintText,
    this.onChanged,
  });

  @override
  State<XSearchField> createState() => _XSearchFieldState();
}

class _XSearchFieldState extends State<XSearchField> {
  final FocusNode _focusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    _focusNode.addListener(() {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final accent = xAccent(context);
    final focused = _focusNode.hasFocus;
    final hasQuery = widget.controller.text.isNotEmpty;
    final border = OutlineInputBorder(
      borderRadius: BorderRadius.circular(9999),
      borderSide: BorderSide(
        color: focused ? accent : Colors.transparent,
        width: focused ? 1.4 : 1,
      ),
    );

    return TextField(
      controller: widget.controller,
      focusNode: _focusNode,
      textInputAction: TextInputAction.search,
      style: TextStyle(color: xOnSurface(context), fontSize: 15),
      cursorColor: accent,
      onChanged: (value) {
        widget.onChanged?.call(value);
        if (mounted) setState(() {});
      },
      decoration: InputDecoration(
        isDense: true,
        filled: true,
        fillColor: xControlFill(context),
        hintText: widget.hintText,
        hintStyle: TextStyle(color: xSecondary(context), fontSize: 15),
        prefixIcon: Icon(
          Icons.search,
          size: 20,
          color: focused ? accent : xSecondary(context),
        ),
        suffixIcon: hasQuery
            ? IconButton(
                icon: Icon(Icons.cancel, size: 18, color: xSecondary(context)),
                onPressed: () {
                  widget.controller.clear();
                  widget.onChanged?.call('');
                  if (mounted) setState(() {});
                },
              )
            : null,
        contentPadding: const EdgeInsets.symmetric(vertical: 12),
        border: border,
        enabledBorder: border,
        focusedBorder: border,
      ),
    );
  }
}
