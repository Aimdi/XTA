import 'dart:ui' show lerpDouble;

import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/ui/contrast.dart';

/// X design-language tokens. Chirp is proprietary — we use Inter instead.
@immutable
class XLookTokens extends ThemeExtension<XLookTokens> {
  final Color accent;
  final Color background;
  final Color onBackground;
  final Color secondary;
  final Color divider;
  final Color card;
  final Color border;
  final double mediaRadius;
  final double avatarSize;
  final double spacing;

  const XLookTokens({
    required this.accent,
    required this.background,
    required this.onBackground,
    required this.secondary,
    required this.divider,
    required this.card,
    required this.border,
    this.mediaRadius = 16,
    this.avatarSize = 40,
    this.spacing = 4,
  });

  static const accentBlue = Color(0xFF1D9BF0);

  static const light = XLookTokens(
    accent: accentBlue,
    background: Color(0xFFFFFFFF),
    onBackground: Color(0xFF0F1419),
    secondary: Color(0xFF536471),
    divider: Color(0xFFEFF3F4),
    card: Color(0xFFFFFFFF),
    border: Color(0xFFEFF3F4),
  );

  static const dim = XLookTokens(
    accent: accentBlue,
    background: Color(0xFF15202B),
    onBackground: Color(0xFFF7F9F9),
    secondary: Color(0xFF8899A4),
    divider: Color(0xFF38444D),
    card: Color(0xFF192734),
    border: Color(0xFF38444D),
  );

  static const lightsOut = XLookTokens(
    accent: accentBlue,
    background: Color(0xFF000000),
    onBackground: Color(0xFFE7E9EA),
    secondary: Color(0xFF71767B),
    divider: Color(0xFF2F3336),
    card: Color(0xFF000000),
    border: Color(0xFF2F3336),
  );

  static XLookTokens? maybeOf(BuildContext context) =>
      Theme.of(context).extension<XLookTokens>();

  static XLookTokens of(BuildContext context) {
    final tokens = maybeOf(context);
    assert(tokens != null, 'XLookTokens missing from Theme');
    return tokens!;
  }

  @override
  XLookTokens copyWith({
    Color? accent,
    Color? background,
    Color? onBackground,
    Color? secondary,
    Color? divider,
    Color? card,
    Color? border,
    double? mediaRadius,
    double? avatarSize,
    double? spacing,
  }) {
    return XLookTokens(
      accent: accent ?? this.accent,
      background: background ?? this.background,
      onBackground: onBackground ?? this.onBackground,
      secondary: secondary ?? this.secondary,
      divider: divider ?? this.divider,
      card: card ?? this.card,
      border: border ?? this.border,
      mediaRadius: mediaRadius ?? this.mediaRadius,
      avatarSize: avatarSize ?? this.avatarSize,
      spacing: spacing ?? this.spacing,
    );
  }

  @override
  XLookTokens lerp(ThemeExtension<XLookTokens>? other, double t) {
    if (other is! XLookTokens) return this;
    return XLookTokens(
      accent: Color.lerp(accent, other.accent, t)!,
      background: Color.lerp(background, other.background, t)!,
      onBackground: Color.lerp(onBackground, other.onBackground, t)!,
      secondary: Color.lerp(secondary, other.secondary, t)!,
      divider: Color.lerp(divider, other.divider, t)!,
      card: Color.lerp(card, other.card, t)!,
      border: Color.lerp(border, other.border, t)!,
      mediaRadius: lerpDouble(mediaRadius, other.mediaRadius, t)!,
      avatarSize: lerpDouble(avatarSize, other.avatarSize, t)!,
      spacing: lerpDouble(spacing, other.spacing, t)!,
    );
  }
}

TextTheme _xLookTextTheme(Brightness brightness, Color onBg, Color secondary) {
  final base = brightness == Brightness.light
      ? Typography.material2021().black
      : Typography.material2021().white;
  return base
      .apply(fontFamily: 'Inter', bodyColor: onBg, displayColor: onBg)
      .copyWith(
        bodyLarge: base.bodyLarge?.copyWith(
          fontFamily: 'Inter',
          fontSize: 15,
          color: onBg,
          height: 1.35,
        ),
        bodyMedium: base.bodyMedium?.copyWith(
          fontFamily: 'Inter',
          fontSize: 15,
          color: onBg,
          height: 1.35,
        ),
        bodySmall: base.bodySmall?.copyWith(
          fontFamily: 'Inter',
          fontSize: 13,
          color: secondary,
        ),
        titleLarge: base.titleLarge?.copyWith(
          fontFamily: 'Inter',
          fontSize: 20,
          fontWeight: FontWeight.w700,
          color: onBg,
        ),
        titleMedium: base.titleMedium?.copyWith(
          fontFamily: 'Inter',
          fontSize: 15,
          fontWeight: FontWeight.w700,
          color: onBg,
        ),
        titleSmall: base.titleSmall?.copyWith(
          fontFamily: 'Inter',
          fontSize: 14,
          fontWeight: FontWeight.w700,
          color: onBg,
        ),
        labelLarge: base.labelLarge?.copyWith(
          fontFamily: 'Inter',
          fontWeight: FontWeight.w700,
          color: onBg,
        ),
      );
}

/// Whether the active palette is the first-class OLED treatment.
///
/// This is deliberately derived from the palette rather than a preference so
/// widgets cannot drift out of sync with the ThemeData that actually rendered
/// the screen.
bool xLookIsLightsOut(XLookTokens tokens) =>
    tokens.background == const Color(0xFF000000) &&
    tokens.card == const Color(0xFF000000);

Color _xLookLiftedSurface(XLookTokens tokens, double alpha) => Color.alphaBlend(
  tokens.onBackground.withValues(alpha: alpha),
  tokens.card,
);

/// The quiet fill for an inset control or nested surface.
///
/// In Lights Out this is only six percent above black: enough to locate an
/// input or quoted post without making the reading canvas look grey.
Color xLookInsetSurface(XLookTokens tokens) =>
    tokens.card == tokens.background
    ? _xLookLiftedSurface(tokens, 0.06)
    : tokens.card;

/// The fill for a surface that floats above the page — a menu, a dialog, a
/// sheet, a snackbar, or the home nav pill.
///
/// Lights Out keeps the page and post surfaces at true black. Floating chrome
/// gets a restrained ten-percent lift and an outline instead of elevation.
Color xLookFloatingSurface(XLookTokens tokens) =>
    tokens.card == tokens.background
    ? _xLookLiftedSurface(tokens, 0.10)
    : tokens.card;

/// Placeholder fill that remains visible without borrowing the much brighter
/// divider colour as a solid surface in Lights Out.
Color xLookSkeletonSurface(XLookTokens tokens) =>
    xLookIsLightsOut(tokens) ? xLookFloatingSurface(tokens) : tokens.border;

Color xLookSkeletonHighlight(XLookTokens tokens) =>
    xLookIsLightsOut(tokens) ? xLookInsetSurface(tokens) : tokens.divider;

ThemeData xLookThemeData(
  XLookTokens tokens,
  PageTransitionsTheme? pageTransitions,
) {
  final isLight = tokens.background.computeLuminance() > 0.5;
  final brightness = isLight ? Brightness.light : Brightness.dark;
  final insetSurface = xLookInsetSurface(tokens);
  final floatingSurface = xLookFloatingSurface(tokens);
  final isLightsOut = xLookIsLightsOut(tokens);
  final readableAccent = ensureContrast(
    ensureContrast(tokens.accent, tokens.background),
    floatingSurface,
  );
  final onAccent = contrastingForeground(tokens.accent);
  final onReadableAccent = contrastingForeground(readableAccent);
  final error = ensureContrast(
    ensureContrast(const Color(0xFFF4212E), tokens.background),
    floatingSurface,
  );
  final errorContainer = Color.alphaBlend(
    error.withValues(alpha: 0.14),
    tokens.background,
  );
  final scheme = ColorScheme(
    brightness: brightness,
    primary: readableAccent,
    onPrimary: onReadableAccent,
    secondary: readableAccent,
    onSecondary: onReadableAccent,
    error: error,
    onError: contrastingForeground(error),
    errorContainer: errorContainer,
    onErrorContainer: ensureContrast(error, errorContainer),
    surface: tokens.card,
    onSurface: tokens.onBackground,
    onSurfaceVariant: tokens.secondary,
    outline: tokens.border,
    outlineVariant: tokens.divider,
    surfaceContainerLowest: tokens.background,
    surfaceContainerLow: tokens.card,
    surfaceContainer: isLightsOut ? insetSurface : tokens.card,
    surfaceContainerHigh: isLightsOut ? insetSurface : tokens.card,
    surfaceContainerHighest: isLightsOut ? floatingSurface : tokens.border,
    // Inset fill for FilledButton.tonal / plugin store — not a Material You wash.
    primaryContainer: Color.alphaBlend(
      tokens.accent.withValues(alpha: 0.14),
      tokens.card,
    ),
    onPrimaryContainer: tokens.onBackground,
    secondaryContainer: Color.alphaBlend(
      tokens.onBackground.withValues(alpha: 0.08),
      tokens.background,
    ),
    onSecondaryContainer: tokens.onBackground,
  );

  return ThemeData(
    useMaterial3: true,
    brightness: brightness,
    colorScheme: scheme,
    scaffoldBackgroundColor: tokens.background,
    canvasColor: tokens.background,
    cardColor: tokens.card,
    dividerColor: tokens.divider,
    fontFamily: 'Inter',
    textTheme: _xLookTextTheme(
      brightness,
      tokens.onBackground,
      tokens.secondary,
    ),
    appBarTheme: AppBarThemeData(
      backgroundColor: tokens.background,
      foregroundColor: tokens.onBackground,
      elevation: 0,
      scrolledUnderElevation: 0,
      titleTextStyle: TextStyle(
        fontFamily: 'Inter',
        fontSize: 20,
        fontWeight: FontWeight.w700,
        color: tokens.onBackground,
      ),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: tokens.background,
      indicatorColor: Colors.transparent,
      elevation: 0,
      height: 56,
      labelTextStyle: WidgetStateProperty.resolveWith((states) {
        final selected = states.contains(WidgetState.selected);
        return TextStyle(
          fontFamily: 'Inter',
          fontSize: 11,
          fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
          color: selected ? readableAccent : tokens.secondary,
        );
      }),
      iconTheme: WidgetStateProperty.resolveWith((states) {
        final selected = states.contains(WidgetState.selected);
        return IconThemeData(
          color: selected ? readableAccent : tokens.secondary,
          size: 24,
        );
      }),
    ),
    // Material 3 draws snackbars on inverseSurface, which in a dark app means a
    // light slab with dark text — the one surface that ignored the theme.
    snackBarTheme: SnackBarThemeData(
      // Lights Out makes card and background both pure black, so a snackbar
      // drawn on either would be invisible against the screen behind it.
      backgroundColor: floatingSurface,
      contentTextStyle: TextStyle(
        fontFamily: 'Inter',
        fontSize: 15,
        color: tokens.onBackground,
      ),
      actionTextColor: readableAccent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: BorderSide(color: tokens.border),
      ),
    ),
    // Everything below is a surface Material 3 would otherwise draw in its own
    // language: a tonal elevation wash, tight radii and its own typography.
    // Left unset they were tinted like X but never shaped like it, which is
    // what made the menus and the settings panel read as a different app.
    popupMenuTheme: PopupMenuThemeData(
      color: floatingSurface,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: tokens.border),
      ),
      textStyle: TextStyle(
        fontFamily: 'Inter',
        fontSize: 15,
        color: tokens.onBackground,
      ),
    ),
    menuTheme: MenuThemeData(
      style: MenuStyle(
        backgroundColor: WidgetStatePropertyAll(floatingSurface),
        elevation: const WidgetStatePropertyAll(0),
        shape: WidgetStatePropertyAll(
          RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
            side: BorderSide(color: tokens.border),
          ),
        ),
      ),
    ),
    dropdownMenuTheme: DropdownMenuThemeData(
      textStyle: TextStyle(
        fontFamily: 'Inter',
        fontSize: 15,
        color: tokens.onBackground,
      ),
      menuStyle: MenuStyle(
        backgroundColor: WidgetStatePropertyAll(floatingSurface),
        elevation: const WidgetStatePropertyAll(0),
        shape: WidgetStatePropertyAll(
          RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
            side: BorderSide(color: tokens.border),
          ),
        ),
      ),
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: floatingSurface,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: tokens.border),
      ),
      titleTextStyle: TextStyle(
        fontFamily: 'Inter',
        fontSize: 20,
        fontWeight: FontWeight.w700,
        color: tokens.onBackground,
      ),
      contentTextStyle: TextStyle(
        fontFamily: 'Inter',
        fontSize: 15,
        color: tokens.onBackground,
      ),
    ),
    bottomSheetTheme: BottomSheetThemeData(
      backgroundColor: floatingSurface,
      surfaceTintColor: Colors.transparent,
      modalBackgroundColor: floatingSurface,
      elevation: 0,
      modalElevation: 0,
      showDragHandle: true,
      dragHandleColor: tokens.border,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
    ),
    tabBarTheme: TabBarThemeData(
      dividerColor: tokens.divider,
      indicatorColor: readableAccent,
      labelColor: tokens.onBackground,
      unselectedLabelColor: tokens.secondary,
      labelStyle: const TextStyle(
        fontFamily: 'Inter',
        fontSize: 15,
        fontWeight: FontWeight.w700,
      ),
      unselectedLabelStyle: const TextStyle(
        fontFamily: 'Inter',
        fontSize: 15,
        fontWeight: FontWeight.w500,
      ),
    ),
    dividerTheme: DividerThemeData(
      color: tokens.divider,
      thickness: 1,
      space: 1,
    ),
    listTileTheme: ListTileThemeData(
      iconColor: tokens.secondary,
      textColor: tokens.onBackground,
      subtitleTextStyle: TextStyle(
        fontFamily: 'Inter',
        fontSize: 13,
        color: tokens.secondary,
      ),
    ),
    chipTheme: ChipThemeData(
      backgroundColor: Colors.transparent,
      selectedColor: tokens.accent,
      side: BorderSide(color: tokens.border),
      shape: const StadiumBorder(),
      labelStyle: TextStyle(
        fontFamily: 'Inter',
        fontSize: 14,
        color: tokens.onBackground,
      ),
      secondaryLabelStyle: TextStyle(
        fontFamily: 'Inter',
        fontSize: 14,
        color: onAccent,
      ),
      checkmarkColor: onAccent,
      showCheckmark: false,
    ),
    tooltipTheme: TooltipThemeData(
      decoration: BoxDecoration(
        color: floatingSurface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: tokens.border),
      ),
      textStyle: TextStyle(
        fontFamily: 'Inter',
        fontSize: 13,
        color: tokens.onBackground,
      ),
    ),
    inputDecorationTheme: InputDecorationThemeData(
      filled: true,
      fillColor: tokens.card == tokens.background
          ? insetSurface
          : tokens.card,
      hintStyle: TextStyle(fontFamily: 'Inter', color: tokens.secondary),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(24),
        borderSide: BorderSide(color: tokens.border),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(24),
        borderSide: BorderSide(color: tokens.border),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(24),
        borderSide: BorderSide(color: readableAccent, width: 2),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: readableAccent,
        textStyle: const TextStyle(
          fontFamily: 'Inter',
          fontWeight: FontWeight.w700,
        ),
      ),
    ),
    floatingActionButtonTheme: FloatingActionButtonThemeData(
      backgroundColor: tokens.accent,
      foregroundColor: onAccent,
      elevation: 2,
    ),
    progressIndicatorTheme: ProgressIndicatorThemeData(
      color: readableAccent,
      linearTrackColor: tokens.divider,
      circularTrackColor: Colors.transparent,
    ),
    switchTheme: SwitchThemeData(
      thumbColor: WidgetStateProperty.resolveWith(
        (states) => states.contains(WidgetState.selected)
            ? onReadableAccent
            : tokens.secondary,
      ),
      trackColor: WidgetStateProperty.resolveWith(
        (states) => states.contains(WidgetState.selected)
            ? readableAccent
            : Colors.transparent,
      ),
      trackOutlineColor: WidgetStateProperty.resolveWith(
        (states) => states.contains(WidgetState.selected)
            ? readableAccent
            : tokens.border,
      ),
    ),
    checkboxTheme: CheckboxThemeData(
      fillColor: WidgetStateProperty.resolveWith(
        (states) => states.contains(WidgetState.selected)
            ? readableAccent
            : Colors.transparent,
      ),
      checkColor: WidgetStatePropertyAll(onReadableAccent),
      side: BorderSide(color: tokens.border, width: 2),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
    ),
    radioTheme: RadioThemeData(
      fillColor: WidgetStateProperty.resolveWith(
        (states) => states.contains(WidgetState.selected)
            ? readableAccent
            : tokens.border,
      ),
    ),
    sliderTheme: SliderThemeData(
      activeTrackColor: readableAccent,
      inactiveTrackColor: tokens.divider,
      thumbColor: readableAccent,
    ),
    segmentedButtonTheme: SegmentedButtonThemeData(
      style: ButtonStyle(
        backgroundColor: WidgetStateProperty.resolveWith(
          (states) => states.contains(WidgetState.selected)
              ? tokens.accent
              : Colors.transparent,
        ),
        foregroundColor: WidgetStateProperty.resolveWith(
          (states) => states.contains(WidgetState.selected)
              ? onAccent
              : tokens.onBackground,
        ),
        side: WidgetStatePropertyAll(BorderSide(color: tokens.border)),
        shape: const WidgetStatePropertyAll(StadiumBorder()),
      ),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: tokens.accent,
        foregroundColor: onAccent,
        elevation: 0,
        shape: const StadiumBorder(),
        textStyle: const TextStyle(
          fontFamily: 'Inter',
          fontWeight: FontWeight.w700,
        ),
      ),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: tokens.accent,
        foregroundColor: onAccent,
        elevation: 0,
        shadowColor: Colors.transparent,
        shape: const StadiumBorder(),
        textStyle: const TextStyle(
          fontFamily: 'Inter',
          fontWeight: FontWeight.w700,
        ),
      ),
    ),
    iconButtonTheme: IconButtonThemeData(
      style: IconButton.styleFrom(
        foregroundColor: tokens.secondary,
        highlightColor: Colors.transparent,
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: tokens.onBackground,
        side: BorderSide(color: tokens.border),
        shape: const StadiumBorder(),
      ),
    ),
    pageTransitionsTheme:
        pageTransitions ??
        const PageTransitionsTheme(
          builders: {
            TargetPlatform.android: FadeUpwardsPageTransitionsBuilder(),
            TargetPlatform.iOS: ZoomPageTransitionsBuilder(),
            TargetPlatform.linux: FadeUpwardsPageTransitionsBuilder(),
            TargetPlatform.macOS: ZoomPageTransitionsBuilder(),
            TargetPlatform.windows: FadeUpwardsPageTransitionsBuilder(),
          },
        ),
    extensions: [tokens],
  );
}

ThemeData xLookLightTheme(PageTransitionsTheme? pageTransitions) =>
    xLookThemeData(XLookTokens.light, pageTransitions);

ThemeData xLookDimTheme(PageTransitionsTheme? pageTransitions) =>
    xLookThemeData(XLookTokens.dim, pageTransitions);

ThemeData xLookLightsOutTheme(PageTransitionsTheme? pageTransitions) =>
    xLookThemeData(XLookTokens.lightsOut, pageTransitions);

bool isXLookPreset(String preset) =>
    preset == 'x_look_light' ||
    preset == 'x_look_dim' ||
    preset == 'x_look_lights_out';

/// The accent for [accent], falling back to X's blue for a value we no longer
/// recognise rather than leaving the app without a usable colour.
Color xLookAccentColor(String accent) =>
    xLookAccents[accent] ?? xLookAccents[xLookAccentBlue]!;

/// Tokens for one background, tinted with the chosen accent.
///
/// [background] here is a concrete background — pass [xLookBackgroundLight] for
/// the light theme; use [xLookDarkTokensFor] to resolve the dark one.
XLookTokens xLookTokensFor(String background, String accent) {
  final base = switch (background) {
    xLookBackgroundLight => XLookTokens.light,
    xLookBackgroundDim => XLookTokens.dim,
    _ => XLookTokens.lightsOut,
  };

  return base.copyWith(accent: xLookAccentColor(accent));
}

/// The dark half of the theme. "System" darkens to Lights Out, which is true
/// black and the cheapest on an OLED panel; Dim is a deliberate choice.
XLookTokens xLookDarkTokensFor(String background, String accent) =>
    xLookTokensFor(
      background == xLookBackgroundDim
          ? xLookBackgroundDim
          : xLookBackgroundLightsOut,
      accent,
    );

/// Only "System" defers to the phone; every other background names a brightness.
ThemeMode xLookThemeModeFor(String background) => switch (background) {
  xLookBackgroundSystem => ThemeMode.system,
  xLookBackgroundLight => ThemeMode.light,
  _ => ThemeMode.dark,
};

/// Maps a stored theme preset onto the background that replaced it, so an
/// existing install keeps the look it had. The three retired presets (Standard,
/// Fairy Forest, Pitch Black) have no X Look equivalent and fall to System.
String xLookBackgroundForPreset(String? preset) => switch (preset) {
  themePresetXLookLight => xLookBackgroundLight,
  themePresetXLookDim => xLookBackgroundDim,
  themePresetXLookLightsOut => xLookBackgroundLightsOut,
  _ => xLookBackgroundSystem,
};
