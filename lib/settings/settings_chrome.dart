import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/reader_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';

const double kSettingsContentWidth = 720;
const double kSettingsRowRadius = 12;

class SettingsPageScaffold extends StatelessWidget {
  final String title;
  final Widget body;
  final List<Widget> actions;
  final Widget? floatingActionButton;

  const SettingsPageScaffold({
    super.key,
    required this.title,
    required this.body,
    this.actions = const [],
    this.floatingActionButton,
  });

  @override
  Widget build(BuildContext context) {
    return XtaSystemBars(
      child: Scaffold(
        appBar: AppBar(
          title: Text(title),
          surfaceTintColor: Colors.transparent,
          elevation: 0,
          scrolledUnderElevation: 0,
          actions: actions,
        ),
        floatingActionButton: floatingActionButton,
        body: SafeArea(
          top: false,
          child: ListTileTheme.merge(
            minTileHeight: kTweetTouchTarget,
            minVerticalPadding: kTweetSpace2,
            contentPadding: const EdgeInsets.symmetric(
              horizontal: kTweetHorizontalPadding,
            ),
            iconColor: tweetSecondaryColor(context),
            selectedColor: tweetReadableAccentColor(context),
            child: body,
          ),
        ),
      ),
    );
  }
}

class SettingsList extends StatelessWidget {
  final List<Widget> children;
  final ScrollController? controller;
  final ScrollPhysics? physics;
  final EdgeInsetsGeometry? padding;

  const SettingsList({
    super.key,
    required this.children,
    this.controller,
    this.physics,
    this.padding,
  });

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.paddingOf(context).bottom;
    return Align(
      alignment: Alignment.topCenter,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: kSettingsContentWidth),
        child: ListView(
          controller: controller,
          physics: physics,
          padding:
              padding ??
              EdgeInsets.fromLTRB(0, kTweetSpace2, 0, kTweetSpace6 + bottom),
          children: children,
        ),
      ),
    );
  }
}

class SettingsSection extends StatelessWidget {
  final String? title;
  final String? description;
  final List<Widget> children;
  final bool destructive;

  const SettingsSection({
    super.key,
    this.title,
    this.description,
    required this.children,
    this.destructive = false,
  });

  @override
  Widget build(BuildContext context) {
    final color = destructive
        ? Theme.of(context).colorScheme.error
        : tweetReadableAccentColor(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: kTweetSpace3),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (title != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(
                kTweetHorizontalPadding,
                kTweetSpace4,
                kTweetHorizontalPadding,
                kTweetSpace1,
              ),
              child: Text(
                title!,
                style: tweetLabelStyle(context).copyWith(color: color),
              ),
            ),
          if (description != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(
                kTweetHorizontalPadding,
                0,
                kTweetHorizontalPadding,
                kTweetSpace2,
              ),
              child: Text(description!, style: tweetMetadataStyle(context)),
            ),
          for (var index = 0; index < children.length; index++) ...[
            children[index],
            if (index != children.length - 1)
              Padding(
                padding: const EdgeInsetsDirectional.only(
                  start: kTweetHorizontalPadding,
                ),
                child: tweetHairlineDivider(context),
              ),
          ],
        ],
      ),
    );
  }
}

class SettingsRow extends StatelessWidget {
  final IconData? icon;
  final String title;
  final String? description;
  final String? value;
  final Widget? trailing;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final bool enabled;
  final bool destructive;
  final bool showChevron;

  const SettingsRow({
    super.key,
    this.icon,
    required this.title,
    this.description,
    this.value,
    this.trailing,
    this.onTap,
    this.onLongPress,
    this.enabled = true,
    this.destructive = false,
    this.showChevron = false,
  });

  @override
  Widget build(BuildContext context) {
    final error = Theme.of(context).colorScheme.error;
    final titleColor = !enabled
        ? tweetSecondaryColor(context)
        : destructive
        ? error
        : tweetPrimaryColor(context);
    final stackValue =
        value != null && MediaQuery.textScalerOf(context).scale(1) >= 1.3;
    final resolvedTrailing =
        trailing ??
        (showChevron
            ? Icon(
                Icons.chevron_right,
                size: kTweetActionIconSize,
                color: tweetSecondaryColor(context),
              )
            : null);
    return Semantics(
      button: onTap != null,
      enabled: enabled,
      child: ListTile(
        minTileHeight: kTweetTouchTarget,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: kTweetHorizontalPadding,
          vertical: kTweetSpace1,
        ),
        enabled: enabled,
        leading: icon == null
            ? null
            : SizedBox.square(
                dimension: kTweetTouchTarget,
                child: Icon(
                  icon,
                  size: 24,
                  color: !enabled
                      ? tweetDividerColor(context)
                      : destructive
                      ? error
                      : tweetSecondaryColor(context),
                ),
              ),
        title: Text(
          title,
          style: Theme.of(context).textTheme.bodyLarge?.copyWith(
            color: titleColor,
            fontWeight: FontWeight.w600,
          ),
        ),
        subtitle: description == null && !stackValue
            ? null
            : Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (description != null) Text(description!),
                  if (description != null && stackValue)
                    const SizedBox(height: kTweetSpace1),
                  if (stackValue)
                    Text(
                      value!,
                      style: tweetMetadataStyle(
                        context,
                      ).copyWith(color: tweetReadableAccentColor(context)),
                    ),
                ],
              ),
        trailing: value == null || stackValue
            ? resolvedTrailing
            : ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 180),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Flexible(
                      child: Text(
                        value!,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        textAlign: TextAlign.end,
                        style: tweetMetadataStyle(context),
                      ),
                    ),
                    if (resolvedTrailing != null) ...[
                      const SizedBox(width: kTweetSpace2),
                      resolvedTrailing,
                    ],
                  ],
                ),
              ),
        onTap: enabled ? onTap : null,
        onLongPress: enabled ? onLongPress : null,
      ),
    );
  }
}

class SettingsNavigationRow extends SettingsRow {
  const SettingsNavigationRow({
    super.key,
    required super.icon,
    required super.title,
    super.description,
    super.value,
    required super.onTap,
    super.enabled,
  }) : super(showChevron: true);
}

class SettingsToggleRow extends StatelessWidget {
  final IconData? icon;
  final String title;
  final String? description;
  final bool value;
  final ValueChanged<bool>? onChanged;
  final EdgeInsetsGeometry contentPadding;

  const SettingsToggleRow({
    super.key,
    this.icon,
    required this.title,
    this.description,
    required this.value,
    required this.onChanged,
    this.contentPadding = const EdgeInsets.symmetric(
      horizontal: kTweetHorizontalPadding,
      vertical: kTweetSpace1,
    ),
  });

  @override
  Widget build(BuildContext context) {
    return SwitchListTile(
      minTileHeight: kTweetTouchTarget,
      contentPadding: contentPadding,
      secondary: icon == null
          ? null
          : SizedBox.square(
              dimension: kTweetTouchTarget,
              child: Icon(icon, size: 24),
            ),
      title: Text(
        title,
        style: Theme.of(
          context,
        ).textTheme.bodyLarge?.copyWith(fontWeight: FontWeight.w600),
      ),
      subtitle: description == null ? null : Text(description!),
      value: value,
      onChanged: onChanged,
    );
  }
}

class SettingsCheckboxRow extends StatelessWidget {
  final IconData? icon;
  final String title;
  final String? description;
  final bool value;
  final ValueChanged<bool>? onChanged;

  const SettingsCheckboxRow({
    super.key,
    this.icon,
    required this.title,
    this.description,
    required this.value,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return CheckboxListTile(
      minTileHeight: kTweetTouchTarget,
      contentPadding: const EdgeInsets.symmetric(
        horizontal: kTweetHorizontalPadding,
        vertical: kTweetSpace1,
      ),
      secondary: icon == null
          ? null
          : SizedBox.square(
              dimension: kTweetTouchTarget,
              child: Icon(icon, size: 24),
            ),
      title: Text(
        title,
        style: Theme.of(
          context,
        ).textTheme.bodyLarge?.copyWith(fontWeight: FontWeight.w600),
      ),
      subtitle: description == null ? null : Text(description!),
      value: value,
      onChanged: onChanged == null ? null : (next) => onChanged!(next ?? false),
    );
  }
}

@immutable
class SettingsOption<T> {
  final T value;
  final String label;
  final IconData? icon;
  final Color? color;

  const SettingsOption({
    required this.value,
    required this.label,
    this.icon,
    this.color,
  });
}

class SettingsPreferenceSelector<T> extends StatefulWidget {
  final BasePrefService prefs;
  final String pref;
  final List<SettingsOption<T>> options;
  final ValueChanged<T>? onSelected;

  const SettingsPreferenceSelector({
    super.key,
    required this.prefs,
    required this.pref,
    required this.options,
    this.onSelected,
  });

  @override
  State<SettingsPreferenceSelector<T>> createState() =>
      _SettingsPreferenceSelectorState<T>();
}

class _SettingsSelectionStore<T> extends Store<T> {
  _SettingsSelectionStore(super.initialState);

  void select(T value) => update(value);
}

class _SettingsPreferenceSelectorState<T>
    extends State<SettingsPreferenceSelector<T>> {
  late final _SettingsSelectionStore<T> _store;

  @override
  void initState() {
    super.initState();
    _store = _SettingsSelectionStore<T>(
      widget.prefs.get<T>(widget.pref) ?? widget.options.first.value,
    );
  }

  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  Future<void> _select(T value) async {
    await widget.prefs.set(widget.pref, value);
    if (!mounted) return;
    _store.select(value);
    widget.onSelected?.call(value);
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<_SettingsSelectionStore<T>, T>(
      store: _store,
      onState: (_, selected) => SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.fromLTRB(
          kTweetHorizontalPadding,
          kTweetSpace2,
          kTweetHorizontalPadding,
          kTweetSpace3,
        ),
        child: Row(
          children: [
            for (var index = 0; index < widget.options.length; index++) ...[
              _SettingsChoice<T>(
                option: widget.options[index],
                selected: widget.options[index].value == selected,
                onTap: () => _select(widget.options[index].value),
              ),
              if (index != widget.options.length - 1)
                const SizedBox(width: kTweetSpace2),
            ],
          ],
        ),
      ),
    );
  }
}

class _SettingsChoice<T> extends StatelessWidget {
  final SettingsOption<T> option;
  final bool selected;
  final VoidCallback onTap;

  const _SettingsChoice({
    required this.option,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final accent = tweetAccentColor(context);
    final readableAccent = tweetReadableAccentColor(context);
    return Semantics(
      button: true,
      selected: selected,
      child: Material(
        animationDuration: xtaMotionDuration(context, kXtaMotionFast),
        color: selected ? accent.withValues(alpha: 0.12) : Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(kSettingsRowRadius),
          side: BorderSide(
            color: selected ? readableAccent : tweetDividerColor(context),
          ),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(kSettingsRowRadius),
          onTap: onTap,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: kTweetSpace3),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (option.color != null)
                    Container(
                      width: 18,
                      height: 18,
                      decoration: BoxDecoration(
                        color: option.color,
                        shape: BoxShape.circle,
                        border: Border.all(color: tweetDividerColor(context)),
                      ),
                    )
                  else if (option.icon != null)
                    Icon(
                      option.icon,
                      size: kTweetActionIconSize,
                      color: selected
                          ? readableAccent
                          : tweetSecondaryColor(context),
                    ),
                  if (option.color != null || option.icon != null)
                    const SizedBox(width: kTweetSpace2),
                  Text(
                    option.label,
                    style: tweetMetadataStyle(context).copyWith(
                      color: selected
                          ? tweetPrimaryColor(context)
                          : tweetSecondaryColor(context),
                      fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class SettingsListSkeleton extends StatelessWidget {
  final int count;

  const SettingsListSkeleton({super.key, this.count = 7});

  @override
  Widget build(BuildContext context) {
    final tokens = XLookTokens.maybeOf(context);
    final fill = tokens == null
        ? Theme.of(context).colorScheme.surfaceContainerHighest
        : xLookSkeletonSurface(tokens);
    return SettingsList(
      children: [
        for (var index = 0; index < count; index++) ...[
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: kTweetHorizontalPadding,
              vertical: kTweetSpace3,
            ),
            child: Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: fill,
                    borderRadius: BorderRadius.circular(kSettingsRowRadius),
                  ),
                ),
                const SizedBox(width: kTweetSpace3),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      FractionallySizedBox(
                        widthFactor: 0.55,
                        child: Container(height: 12, color: fill),
                      ),
                      const SizedBox(height: kTweetSpace2),
                      FractionallySizedBox(
                        widthFactor: 0.8,
                        child: Container(height: 10, color: fill),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          if (index != count - 1) tweetHairlineDivider(context),
        ],
      ],
    );
  }
}
