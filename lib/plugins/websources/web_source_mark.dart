import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';

/// A source's colour, or the theme's when it names none.
Color webSourceColor(BuildContext context, WebSourceDefinition source) =>
    source.color == null ? Theme.of(context).colorScheme.primary : Color(source.color!);

/// A source's picture when it has one, else its initial on its colour.
class WebSourceMark extends StatelessWidget {
  final WebSourceDefinition source;
  final double size;

  /// False before the reader agrees to the source's sites: its picture is
  /// on one of them.
  final bool picture;

  const WebSourceMark({super.key, required this.source, this.size = 24, this.picture = true});

  @override
  Widget build(BuildContext context) {
    final initial = _Initial(source: source, size: size);
    final icon = source.icon;
    if (icon == null || !picture) return initial;
    return ClipOval(
      child: ExtendedImage.network(
        icon.toString(),
        width: size,
        height: size,
        fit: BoxFit.cover,
        cache: true,
        loadStateChanged: (state) => state.extendedImageLoadState == LoadState.completed ? null : initial,
      ),
    );
  }
}

class _Initial extends StatelessWidget {
  final WebSourceDefinition source;
  final double size;

  const _Initial({required this.source, required this.size});

  @override
  Widget build(BuildContext context) {
    final color = webSourceColor(context, source);
    final onColor = ThemeData.estimateBrightnessForColor(color) == Brightness.dark ? Colors.white : Colors.black;
    return ExcludeSemantics(
      child: SizedBox.square(
        dimension: size,
        child: DecoratedBox(
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
          child: Center(
            child: Text(
              source.name.characters.first.toUpperCase(),
              style: TextStyle(color: onColor, fontWeight: FontWeight.w700, fontSize: size * 0.5, height: 1),
            ),
          ),
        ),
      ),
    );
  }
}
