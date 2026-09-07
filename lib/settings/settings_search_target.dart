import 'package:flutter/material.dart';

class SettingsSearchTarget extends InheritedWidget {
  final String id;
  const SettingsSearchTarget({super.key, required this.id, required super.child});
  static SettingsSearchTarget? maybeOf(BuildContext context) => context.dependOnInheritedWidgetOfExactType<SettingsSearchTarget>();
  @override
  bool updateShouldNotify(SettingsSearchTarget oldWidget) => oldWidget.id != id;
}

class SettingsControlTarget extends StatelessWidget {
  final String id;
  final Widget child;
  const SettingsControlTarget({super.key, required this.id, required this.child});
  @override
  Widget build(BuildContext context) => SettingsSearchTarget.maybeOf(context)?.id == id
    ? _FocusedSetting(key: ValueKey('settings-focus-$id'), child: child) : child;
}

class _FocusedSetting extends StatefulWidget {
  final Widget child;
  const _FocusedSetting({super.key, required this.child});
  @override
  State<_FocusedSetting> createState() => _FocusedSettingState();
}

class _FocusedSettingState extends State<_FocusedSetting> {
  final _focus = FocusNode();
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      Scrollable.ensureVisible(context, alignment: 0.25);
      _focus.requestFocus();
    });
  }
  @override
  void dispose() { _focus.dispose(); super.dispose(); }
  @override
  Widget build(BuildContext context) => Focus(focusNode: _focus,
    child: DecoratedBox(decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.primary.withValues(alpha: 0.06),
      border: BorderDirectional(start: BorderSide(color: Theme.of(context).colorScheme.primary, width: 3))),
      child: widget.child),
  );
}
