import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/group_identity.dart';
import 'package:xta/subscriptions/group_mark_style.dart';
import 'package:xta/subscriptions/widgets/avatar_mosaic.dart';
import 'package:xta/subscriptions/widgets/group_tile.dart';

Widget _wrap(Widget child) => MaterialApp(
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      home: Material(child: Center(child: SizedBox(width: 168, height: 132, child: child))),
    );

SubscriptionGroup _group({
  String name = 'Anime',
  String? icon,
  Color? color,
  String? emoji,
  int markStyle = GroupMarkStyle.auto,
  int members = 15,
  List<GroupMemberPreview> previews = const [],
}) =>
    SubscriptionGroup(
      id: 'g1',
      name: name,
      icon: icon ?? defaultGroupIcon,
      color: color,
      numberOfMembers: members,
      createdAt: DateTime.utc(2024),
      emoji: emoji,
      markStyle: markStyle,
      memberPreviews: previews,
    );

void main() {
  test('groupFallbackColor is deterministic per name', () {
    expect(groupFallbackColor('Anime'), groupFallbackColor('Anime'));
    expect(groupFallbackColor('Anime') == groupFallbackColor('Art'), isFalse);
  });

  test('groupInitial is a single letter and skips leading non-letters', () {
    expect(groupInitial('Art (1)'), 'A');
    expect(groupInitial('Art (2)'), 'A');
    expect(groupInitial('Art NSFW'), 'A');
    expect(groupInitial('German & EU'), 'G');
    expect(groupInitial('Über'), 'Ü');
    expect(groupInitial('  '), '?');
    expect(groupInitial(''), '?');
    expect(groupInitial('42'), '4');
  });

  test('resolveGroupMarkKind follows style plus stored fields', () {
    expect(
      resolveGroupMarkKind(markStyle: GroupMarkStyle.auto, emoji: null, icon: defaultGroupIcon),
      GroupMarkKind.initial,
    );
    expect(
      resolveGroupMarkKind(markStyle: GroupMarkStyle.auto, emoji: '🎨', icon: defaultGroupIcon),
      GroupMarkKind.emoji,
    );
    expect(
      resolveGroupMarkKind(
        markStyle: GroupMarkStyle.symbol,
        emoji: null,
        icon: '{"pack":"material","key":"star"}',
      ),
      GroupMarkKind.symbol,
    );
    expect(
      resolveGroupMarkKind(markStyle: GroupMarkStyle.emoji, emoji: null, icon: defaultGroupIcon),
      GroupMarkKind.initial,
    );
    expect(
      resolveGroupMarkKind(markStyle: GroupMarkStyle.generated, emoji: '🎨', icon: defaultGroupIcon),
      GroupMarkKind.initial,
    );
  });

  test('hasExplicitGroupMark is false until the user picks emoji or icon', () {
    expect(hasExplicitGroupMark(_group()), isFalse);
    expect(hasExplicitGroupMark(_group(markStyle: GroupMarkStyle.symbol)), isFalse);
    expect(hasExplicitGroupMark(_group(emoji: '🎨', markStyle: GroupMarkStyle.emoji)), isTrue);
    expect(
      hasExplicitGroupMark(_group(
        icon: serializeCuratedGroupIcon('star', Icons.star),
        markStyle: GroupMarkStyle.symbol,
      )),
      isTrue,
    );
  });

  testWidgets('GroupMark shows one initial by default', (tester) async {
    await tester.pumpWidget(_wrap(GroupMark.forGroup(_group())));
    await tester.pumpAndSettle();

    expect(find.text('A'), findsOneWidget);
    expect(find.text('AN'), findsNothing);
  });

  testWidgets('GroupMark is a solid disc in the group colour, not a tonal chip', (tester) async {
    const chosen = Color(0xFF7856FF);
    await tester.pumpWidget(_wrap(GroupMark.forGroup(_group(color: chosen))));
    await tester.pumpAndSettle();

    final decoration = tester
        .widget<Container>(find.descendant(of: find.byType(GroupMark), matching: find.byType(Container)))
        .decoration as BoxDecoration;

    expect(decoration.shape, BoxShape.circle, reason: 'the disc matches the circular member avatars');
    expect(decoration.color, chosen, reason: 'the group colour is painted solid, not blended into a tonal container');
    expect(decoration.borderRadius, isNull, reason: 'a rounded-square chip is the Material You shape we dropped');
    expect(find.descendant(of: find.byType(GroupMark), matching: find.byType(Material)), findsNothing,
        reason: 'no Material surface inside the disc means no tonal fill and no ink');
  });

  testWidgets('the glyph stays legible on both pale and saturated colours', (tester) async {
    expect(onGroupSeed(const Color(0xFF0F1419)), Colors.white);
    expect(onGroupSeed(const Color(0xFFFFD400)), Colors.black87);
  });

  testWidgets('GroupMark shows the chosen emoji', (tester) async {
    await tester.pumpWidget(_wrap(
      GroupMark.forGroup(_group(emoji: '🎨', markStyle: GroupMarkStyle.emoji)),
    ));
    await tester.pumpAndSettle();

    expect(find.text('🎨'), findsOneWidget);
  });

  testWidgets('GroupMark shows the chosen icon', (tester) async {
    await tester.pumpWidget(_wrap(GroupMark.forGroup(_group(
      icon: serializeCuratedGroupIcon('star', Icons.star),
      markStyle: GroupMarkStyle.symbol,
    ))));
    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.star), findsOneWidget);
    expect(find.text('A'), findsNothing);
  });

  testWidgets('board tile keeps the member mosaic when no mark was chosen', (tester) async {
    await tester.pumpWidget(_wrap(GroupTile(
      group: _group(previews: const [
        GroupMemberPreview(id: 'a', name: 'Alice'),
        GroupMemberPreview(id: 'b', name: 'Bob'),
      ]),
      onTap: () {},
    )));
    await tester.pumpAndSettle();

    expect(find.byType(AvatarMosaic), findsOneWidget);
    expect(find.byType(GroupMark), findsNothing);
  });

  testWidgets('an explicit emoji outranks the member mosaic on the board', (tester) async {
    await tester.pumpWidget(_wrap(GroupTile(
      group: _group(
        emoji: '🎨',
        markStyle: GroupMarkStyle.emoji,
        previews: const [
          GroupMemberPreview(id: 'a', name: 'Alice'),
          GroupMemberPreview(id: 'b', name: 'Bob'),
        ],
      ),
      onTap: () {},
    )));
    await tester.pumpAndSettle();

    expect(find.byType(AvatarMosaic), findsNothing);
    expect(find.text('🎨'), findsOneWidget);
  });

  testWidgets('an explicit icon outranks the member mosaic on the board', (tester) async {
    await tester.pumpWidget(_wrap(GroupTile(
      group: _group(
        icon: serializeCuratedGroupIcon('star', Icons.star),
        markStyle: GroupMarkStyle.symbol,
        previews: const [GroupMemberPreview(id: 'a', name: 'Alice')],
      ),
      onTap: () {},
    )));
    await tester.pumpAndSettle();

    expect(find.byType(AvatarMosaic), findsNothing);
    expect(find.byIcon(Icons.star), findsOneWidget);
  });

  testWidgets('an explicit mark still fits at 2x text scale', (tester) async {
    await tester.pumpWidget(MediaQuery(
      data: const MediaQueryData(textScaler: TextScaler.linear(2.0)),
      child: _wrap(GroupTile(
        group: _group(name: 'Nachrichtenüberblick', emoji: '🎨', markStyle: GroupMarkStyle.emoji),
        onTap: () {},
      )),
    ));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
  });
}
