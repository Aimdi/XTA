import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/antenna/antenna_widgets.dart';
import 'package:xta/generated/l10n.dart';

Widget _wrap(
  Widget child, {
  double textScale = 1,
  EdgeInsets viewInsets = EdgeInsets.zero,
  bool disableAnimations = false,
}) {
  return MaterialApp(
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    home: Builder(
      builder: (context) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: TextScaler.linear(textScale),
          viewInsets: viewInsets,
          disableAnimations: disableAnimations,
        ),
        child: Scaffold(body: child),
      ),
    ),
  );
}

({TextEditingController name, TextEditingController include, TextEditingController exclude}) _controllers() => (
  name: TextEditingController(text: 'Reading'),
  include: TextEditingController(text: 'flutter'),
  exclude: TextEditingController(),
);

void main() {
  testWidgets('empty state announces itself and opens antenna creation', (tester) async {
    var opens = 0;
    await tester.pumpWidget(
      _wrap(AntennaEmptyState(label: 'No antennas yet', actionLabel: 'New antenna', onAction: () => opens++)),
    );

    expect(find.text('No antennas yet'), findsOneWidget);
    expect(find.text('New antenna'), findsOneWidget);
    expect(
      find.descendant(
        of: find.byType(AntennaEmptyState),
        matching: find.byWidgetPredicate((widget) => widget is Semantics && widget.properties.liveRegion == true),
      ),
      findsOneWidget,
    );
    await tester.tap(find.text('New antenna'));
    expect(opens, 1);
  });

  testWidgets('antenna tile keeps open, settings, and delete actions reachable', (tester) async {
    var opens = 0;
    var settings = 0;
    var deletes = 0;
    await tester.pumpWidget(
      _wrap(
        AntennaTile(
          name: 'Weekly reads',
          terms: 'flutter, dart',
          scopeLabel: 'All of search',
          settingsLabel: 'Settings',
          deleteLabel: 'Delete',
          onOpen: () => opens++,
          onSettings: () => settings++,
          onDelete: () => deletes++,
        ),
      ),
    );

    await tester.tap(find.text('Weekly reads'));
    expect(opens, 1);

    final menu = find.byType(PopupMenuButton<AntennaTileAction>);
    expect(tester.getSize(menu).shortestSide, greaterThanOrEqualTo(48));
    await tester.tap(menu);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Settings'));
    await tester.pumpAndSettle();
    expect(settings, 1);

    await tester.tap(menu);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Delete'));
    await tester.pumpAndSettle();
    expect(deletes, 1);
  });

  testWidgets('editor stacks scope choices above keyboard at 200 percent text', (tester) async {
    tester.view.physicalSize = const Size(375, 667);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controllers = _controllers();

    await tester.pumpWidget(
      _wrap(
        AntennaEditorContent(
          title: 'New antenna',
          nameLabel: 'Name',
          includeLabel: 'Include terms',
          excludeLabel: 'Exclude terms',
          searchScopeLabel: 'All of search',
          followingScopeLabel: 'Only accounts you follow',
          saveLabel: 'OK',
          nameController: controllers.name,
          includeController: controllers.include,
          excludeController: controllers.exclude,
          scope: 'search',
          onScopeChanged: (_) {},
          onSave: () {},
        ),
        textScale: 2,
        viewInsets: const EdgeInsets.only(bottom: 200),
        disableAnimations: true,
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('antenna-editor-scroll')), findsOneWidget);
    expect(find.byKey(const Key('antenna-scope-stacked')), findsOneWidget);
    expect(find.byKey(const Key('antenna-scope-segmented')), findsNothing);
    expect(tester.widget<AnimatedPadding>(find.byType(AnimatedPadding)).duration, Duration.zero);
    expect(tester.takeException(), isNull);

    await tester.pumpWidget(const SizedBox());
    controllers.name.dispose();
    controllers.include.dispose();
    controllers.exclude.dispose();
  });

  testWidgets('editor keeps the compact segmented scope selector when it fits', (tester) async {
    tester.view.physicalSize = const Size(800, 600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controllers = _controllers();

    await tester.pumpWidget(
      _wrap(
        AntennaEditorContent(
          title: 'New antenna',
          nameLabel: 'Name',
          includeLabel: 'Include terms',
          excludeLabel: 'Exclude terms',
          searchScopeLabel: 'All of search',
          followingScopeLabel: 'Only accounts you follow',
          saveLabel: 'OK',
          nameController: controllers.name,
          includeController: controllers.include,
          excludeController: controllers.exclude,
          scope: 'search',
          onScopeChanged: (_) {},
          onSave: () {},
        ),
      ),
    );

    expect(find.byKey(const Key('antenna-scope-segmented')), findsOneWidget);
    expect(find.byKey(const Key('antenna-scope-stacked')), findsNothing);
    expect(tester.takeException(), isNull);

    await tester.pumpWidget(const SizedBox());
    controllers.name.dispose();
    controllers.include.dispose();
    controllers.exclude.dispose();
  });

  testWidgets('management list is centered, width-bounded, and lazy', (tester) async {
    tester.view.physicalSize = const Size(800, 1000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    var offscreenBuilds = 0;

    await tester.pumpWidget(
      _wrap(
        AntennaManagementList(
          itemCount: 2,
          itemBuilder: (context, index) {
            if (index == 0) return const SizedBox(height: 2000);
            offscreenBuilds++;
            return const Text('Offscreen antenna');
          },
        ),
      ),
    );

    expect(tester.getSize(find.byType(ListView)).width, kAntennaContentWidth);
    expect(offscreenBuilds, 0);
    await tester.drag(find.byType(ListView), const Offset(0, -1700));
    await tester.pump();
    expect(offscreenBuilds, 1);
  });
}
