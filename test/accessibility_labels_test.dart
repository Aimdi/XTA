// The analyzer comes with intl_utils, a dev dependency, so reading lib/ with it adds no dependency.
// ignore_for_file: depend_on_referenced_packages
import 'dart:io';

import 'package:analyzer/dart/analysis/utilities.dart';
import 'package:analyzer/dart/ast/ast.dart';
import 'package:analyzer/dart/ast/visitor.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/_accessibility.dart';
import 'package:xta/settings/_theme.dart';
import 'package:xta/settings/settings_export_screen.dart';
import 'package:xta/subscriptions/_import.dart';
import 'package:xta/subscriptions/_import_list.dart';
import 'package:xta/ui/labeled_pref.dart';
import 'package:xta/ui/x_look_theme.dart';

/// Buttons that show nothing but an icon, by the constructors that do so: TalkBack has nothing to read for them
/// unless they carry a name.
const _iconOnlyButtons = {
  'IconButton': {'', 'filled', 'filledTonal', 'outlined'},
  'FloatingActionButton': {'', 'small', 'large'},
};

/// Toggles of the pref package, whose switch or box TalkBack reads apart from the title unless a LabeledPref
/// merges them.
const _prefToggles = {'PrefSwitch', 'PrefCheckbox'};

/// Wrappers that name the widget they hold, by the argument that carries the name.
const _namingWrappers = {'Semantics': 'label', 'Tooltip': 'message'};

bool _isTypeName(String name) => name.startsWith(RegExp('[A-Z]'));

/// The type and constructor [node] creates. Without resolution `IconButton(...)` parses as a function call,
/// `IconButton.filled(...)` as a method call on `IconButton`, and `const IconButton(...)` as an instance creation.
(String, String)? _widgetOf(AstNode? node) => switch (node) {
  InstanceCreationExpression(:final constructorName) => _created(constructorName),
  MethodInvocation(target: null, :final methodName) => (methodName.name, ''),
  MethodInvocation(target: SimpleIdentifier(:final name), :final methodName) when _isTypeName(name) => (
    name,
    methodName.name,
  ),
  // An import prefix, as in `material.IconButton(...)`.
  MethodInvocation(target: SimpleIdentifier(), :final methodName) => (methodName.name, ''),
  _ => null,
};

/// `const FloatingActionButton.small()` parses as a type `small` behind an import prefix `FloatingActionButton`,
/// so a capitalised prefix is the type and the type is the constructor.
(String, String) _created(ConstructorName constructor) {
  final type = constructor.type.name.lexeme;
  final prefix = constructor.type.importPrefix?.name.lexeme;
  if (prefix != null && _isTypeName(prefix) && constructor.name == null) return (prefix, type);
  return (type, constructor.name?.name ?? '');
}

ArgumentList? _argumentsOf(AstNode? node) => switch (node) {
  InstanceCreationExpression(:final argumentList) || MethodInvocation(:final argumentList) => argumentList,
  _ => null,
};

bool _hasArgument(ArgumentList arguments, String name) =>
    arguments.arguments.any((argument) => argument is NamedExpression && argument.name.label.name == name);

/// Finds a `tooltip:`, a `semanticLabel:` or a naming wrapper anywhere in the arguments of a button.
class _NameVisitor extends RecursiveAstVisitor<void> {
  var found = false;

  @override
  void visitNamedExpression(NamedExpression node) {
    final name = node.name.label.name;
    final owner = _widgetOf(node.parent?.parent)?.$1;
    found = found || name == 'tooltip' || name == 'semanticLabel' || _namingWrappers[owner] == name;
    super.visitNamedExpression(node);
  }
}

bool _namesItself(ArgumentList arguments) {
  final visitor = _NameVisitor();
  arguments.accept(visitor);
  return visitor.found;
}

/// Whether [widget] is held, through `child:` arguments only, by a wrapper that names it.
bool _namedByWrapper(AstNode widget) {
  final argument = widget.parent;
  if (argument is! NamedExpression || argument.name.label.name != 'child') return false;
  final wrapper = argument.parent?.parent;
  final arguments = _argumentsOf(wrapper);
  if (wrapper == null || arguments == null) return false;
  final name = _namingWrappers[_widgetOf(wrapper)?.$1];
  return (name != null && _hasArgument(arguments, name)) || _namedByWrapper(wrapper);
}

bool _isChildOfLabeledPref(AstNode widget) {
  final argument = widget.parent;
  return argument is NamedExpression &&
      argument.name.label.name == 'child' &&
      _widgetOf(argument.parent?.parent)?.$1 == 'LabeledPref';
}

class _SilentWidgetVisitor extends RecursiveAstVisitor<void> {
  final silent = <AstNode>[];

  void _check(AstNode node, ArgumentList arguments) {
    final widget = _widgetOf(node);
    if (widget == null) return;
    final iconOnly = _iconOnlyButtons[widget.$1]?.contains(widget.$2) ?? false;
    final silentButton = iconOnly && !_namesItself(arguments) && !_namedByWrapper(node);
    final silentToggle = _prefToggles.contains(widget.$1) && widget.$2.isEmpty && !_isChildOfLabeledPref(node);
    if (silentButton || silentToggle) silent.add(node);
  }

  @override
  void visitMethodInvocation(MethodInvocation node) {
    _check(node, node.argumentList);
    super.visitMethodInvocation(node);
  }

  @override
  void visitInstanceCreationExpression(InstanceCreationExpression node) {
    _check(node, node.argumentList);
    super.visitInstanceCreationExpression(node);
  }
}

/// The widgets of [source] a screen reader would announce without a name, as `path:line`.
List<String> _silentWidgets(String source, String path) {
  final unit = parseString(content: source, path: path).unit;
  final visitor = _SilentWidgetVisitor();
  unit.accept(visitor);
  return [for (final node in visitor.silent) '$path:${unit.lineInfo.getLocation(node.offset).lineNumber}'];
}

Widget _app(Widget home, {BasePrefService? prefs}) => PrefService(
  service: prefs ?? PrefServiceCache(),
  child: MaterialApp(
    theme: xLookLightTheme(null),
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    locale: const Locale('en'),
    home: home,
  ),
);

void main() {
  test('every icon-only button and pref toggle in lib/ has a name a screen reader can read', () {
    final silent = Directory('lib')
        .listSync(recursive: true)
        .whereType<File>()
        .where((file) => file.path.endsWith('.dart') && !file.path.startsWith('lib/generated/'))
        .expand((file) => _silentWidgets(file.readAsStringSync(), file.path))
        .toList();

    expect(
      silent,
      isEmpty,
      reason:
          'TalkBack announces these without a name: give an icon-only button a translated tooltip, or a '
          'translated semanticLabel on its icon where a tooltip would be wrong, and wrap a PrefSwitch or a '
          'PrefCheckbox in a LabeledPref',
    );
  });

  test('the guard reports a widget without a name and accepts each way of naming one', () {
    const source = '''
Widget a() => IconButton(icon: Icon(Icons.add), onPressed: null);
Widget b() => IconButton(tooltip: label, icon: Icon(Icons.add), onPressed: null);
Widget c() => IconButton(icon: Icon(Icons.add, semanticLabel: label), onPressed: null);
Widget d() => Semantics(label: label, child: ExcludeSemantics(child: IconButton(icon: icon, onPressed: null)));
Widget e() => const FloatingActionButton.small(onPressed: null, child: Icon(Icons.add));
Widget f() => IconButton.filled(icon: Icon(Icons.add), onPressed: null);
Widget g() => Tooltip(message: label, child: IconButton(icon: Icon(Icons.add), onPressed: null));
Widget h() => FloatingActionButton.extended(onPressed: null, label: Text(label));
Widget i() => material.IconButton(icon: Icon(Icons.add), onPressed: null);
Widget j() => Padding(padding: EdgeInsets.zero, child: Semantics(child: IconButton(icon: icon, onPressed: null)));
Widget k() => PrefSwitch(pref: key, title: Text(label));
Widget l() => LabeledPref(child: PrefSwitch(pref: key, title: Text(label)));
Widget m() => const PrefCheckbox(pref: key);
Widget n() => Target(child: LabeledPref(child: PrefCheckbox(pref: key)));
Widget o() => PixivPrefSwitch(pref: key, title: Text(label));
''';

    expect(_silentWidgets(source, 'example.dart'), [
      'example.dart:1',
      'example.dart:5',
      'example.dart:6',
      'example.dart:9',
      'example.dart:10',
      'example.dart:11',
      'example.dart:13',
    ]);
  });

  testWidgets('a pref switch reads its title with its state only inside a LabeledPref', (tester) async {
    final semantics = tester.ensureSemantics();
    const toggle = PrefSwitch(pref: optionTickerChart, title: Text('Ticker chart'));
    final prefs = PrefServiceCache(defaults: {optionTickerChart: true});
    Future<Evaluation> evaluate(Widget body) async {
      await tester.pumpWidget(_app(Scaffold(body: body), prefs: prefs));
      return labeledTapTargetGuideline.evaluate(tester);
    }

    expect((await evaluate(toggle)).passed, isFalse);
    expect((await evaluate(const LabeledPref(child: toggle))).passed, isTrue);
    expect(
      tester.getSemantics(find.byType(Switch)),
      isSemantics(label: 'Ticker chart', hasToggledState: true, isToggled: true, hasTapAction: true),
    );
    semantics.dispose();
  });

  testWidgets('the accessibility and theme settings name every switch', (tester) async {
    tester.view.physicalSize = const Size(800, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final semantics = tester.ensureSemantics();
    final prefs = PrefServiceCache(
      defaults: {
        optionTextScaleFactor: 1.0,
        optionDisableAnimations: false,
        optionTickerChart: true,
        optionGestureDoubleTapLike: false,
        optionXLookBackground: xLookBackgroundSystem,
        optionXLookAccent: 'blue',
        optionThemeTrueBlack: false,
        optionThemeTrueBlackTweetCards: false,
        optionShowNavigationLabels: true,
        optionHideNavigationOnScroll: true,
      },
    );
    for (final screen in [const SettingsAccessibilityFragment(), const SettingsThemeFragment()]) {
      await tester.pumpWidget(_app(screen, prefs: prefs));
      await tester.pumpAndSettle();

      expect(find.byType(LabeledPref), findsWidgets);
      for (var i = 0; i < find.byType(Switch).evaluate().length; i++) {
        final node = tester.getSemantics(find.byType(Switch).at(i));
        expect(node.label, isNotEmpty, reason: '$screen');
        expect(node, isSemantics(hasToggledState: true), reason: '$screen');
      }
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    }
    semantics.dispose();
  });

  testWidgets('the import screens name the button that starts the import', (tester) async {
    final semantics = tester.ensureSemantics();
    for (final screen in [const SubscriptionImportScreen(), const ListImportScreen()]) {
      await tester.pumpWidget(_app(screen));
      await tester.pumpAndSettle();

      expect(find.byTooltip('Import'), findsOneWidget);
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    }
    semantics.dispose();
  });

  testWidgets('the export screen names its save button', (tester) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(_app(const SettingsExportScreen()));
    await tester.tap(find.text('Export settings?'));
    await tester.pumpAndSettle();

    expect(find.byTooltip('Export'), findsOneWidget);
    await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    semantics.dispose();
  });
}
