import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_comments_store.dart';
import 'package:xta/plugins/substack/substack_comments_screen.dart';
import 'package:xta/plugins/substack/substack_discussion_text.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_note_card.dart';
import 'package:xta/plugins/substack/substack_note_screen.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'support/bluesky_reading_images.dart';

const _publication = SubstackPublication(
  subdomain: 'fieldnotes',
  baseUrl: 'https://fieldnotes.substack.com',
  name: 'Field Notes',
);
const _post = SubstackPost(
  id: '42',
  title: 'On noticing the small things',
  slug: 'small-things',
  publicationBaseUrl: 'https://fieldnotes.substack.com',
  publicationName: 'Field Notes',
);
SubstackComment _comment(String id, {String? parent, int depth = 0, String? date, String? body, String? author}) =>
    SubstackComment(
      id: id,
      parentId: parent,
      depth: depth,
      body: body ?? id,
      author: author ?? 'Reader $id',
      at: date == null ? null : DateTime.parse(date),
    );
final _comments = [
  _comment('root', body: 'Do you keep a notebook on your walks?', author: 'Alex'),
  _comment('child', parent: 'root', depth: 1, body: 'Yes. It helps me remember the light and colours.', author: 'Maya'),
  _comment(
    'nested',
    parent: 'child',
    depth: 2,
    body: 'That is a lovely idea. I will try it this weekend.',
    author: 'Robin',
  ),
  _comment('sibling', body: 'Thank you for this thoughtful piece.', author: 'Sam'),
];

class _DiscussionClient extends SubstackClient {
  Completer<List<SubstackComment>>? next;
  List<SubstackComment> comments = _comments;
  bool fail = false;
  int calls = 0;
  @override
  Future<List<SubstackComment>> fetchComments(SubstackPublication publication, String postId) async {
    calls++;
    if (next != null) return next!.future;
    if (fail) throw StateError('offline');
    return comments;
  }
}

class _Publications extends SubstackPublicationsStore {
  _Publications(super.prefs);
  @override
  Future<void> add(SubstackPublication publication) async => update([...state, publication]);
}

class _Harness {
  final prefs = PrefServiceCache(
    defaults: {optionZenMode: false, optionCalmMode: false, optionDisableAnimations: true},
  );
  final client = _DiscussionClient();
  late final pubs = _Publications(prefs);
  Widget app(Widget child, {bool rtl = false, double scale = 1}) => PrefService(
    service: prefs,
    child: MultiProvider(
      providers: [
        Provider<SubstackClient>.value(value: client),
        Provider<SubstackPublicationsStore>.value(value: pubs),
      ],
      child: MaterialApp(
        theme: xLookLightTheme(null),
        debugShowCheckedModeBanner: false,
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        builder: (context, child) => RepaintBoundary(
          key: const ValueKey('substack-discussion-window'),
          child: MediaQuery(
            data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale), disableAnimations: true),
            child: Directionality(textDirection: rtl ? TextDirection.rtl : TextDirection.ltr, child: child!),
          ),
        ),
        home: child,
      ),
    ),
  );
  Future<void> close(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    final closing = pubs.destroy();
    await tester.pump();
    await closing;
    client.httpClient.close();
  }
}

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });
  test('comment outline honors parent IDs and keeps duplicate, orphan and cyclic inputs safe', () {
    final comments = [
      _comment('a'),
      _comment('b'),
      _comment('a1', parent: 'a'),
      _comment('a'),
      _comment('orphan', parent: 'missing'),
      _comment('c1', parent: 'c2'),
      _comment('c2', parent: 'c1'),
    ];
    final rows = substackCommentRows(comments);
    expect(rows.map((row) => row.comment.id), ['a', 'a1', 'b', 'orphan', 'c1', 'c2']);
    expect(rows.first.descendants, 1);
    expect(substackCommentRows(comments, collapsed: {'a'}).map((row) => row.comment.id), isNot(contains('a1')));
  });
  test('legacy depth rows preserve nesting without a parent field', () {
    final rows = substackCommentRows([
      _comment('a'),
      _comment('a1', depth: 1),
      _comment('a2', depth: 2),
      _comment('b'),
    ]);
    expect(rows.map((row) => row.depth), [0, 1, 2, 0]);
    expect(rows.first.descendants, 2);
  });
  test('local search matches author and body while retaining connector context', () {
    final rows = substackCommentRows(_comments, query: 'robin weekend');
    expect(rows.map((row) => row.comment.id), ['root', 'child', 'nested']);
    expect(rows.map((row) => row.contextOnly), [true, true, false]);
    expect(substackCommentRows(_comments, query: 'missing words'), isEmpty);
  });
  test('date order sorts siblings stably without detaching replies', () {
    final comments = [
      _comment('a', date: '2026-01-01'),
      _comment('b', date: '2026-01-03'),
      _comment('a1', parent: 'a', date: '2026-01-05'),
      _comment('c', date: '2026-01-03'),
      _comment('undated'),
    ];
    expect(substackCommentRows(comments, order: SubstackCommentOrder.newest).map((row) => row.comment.id), [
      'b',
      'c',
      'a',
      'a1',
      'undated',
    ]);
    expect(substackCommentRows(comments, order: SubstackCommentOrder.oldest).map((row) => row.comment.id), [
      'a',
      'a1',
      'b',
      'c',
      'undated',
    ]);
  });
  test('ten thousand nested comments use iterative counting and traversal', () {
    final comments = [for (var i = 0; i < 10000; i++) _comment('$i', parent: i == 0 ? null : '${i - 1}')];
    final rows = substackCommentRows(comments);
    expect(rows, hasLength(10000));
    expect(rows.first.descendants, 9999);
    expect(rows.last.depth, 9999);
    expect(substackCommentRows(comments, collapsed: {'0'}), hasLength(1));
  });
  test('failed refresh retains comments and controls, retry prunes removed collapse IDs', () async {
    final client = _DiscussionClient();
    final store = SubstackCommentsStore(client, _post);
    addTearDown(store.destroy);
    addTearDown(client.httpClient.close);
    await store.refresh();
    store.setExpanded(false);
    store.sort(SubstackCommentOrder.oldest);
    expect(store.state.rows, hasLength(2));
    client.fail = true;
    await store.refresh();
    expect(store.state.comments, _comments);
    expect(store.state.error, isNotNull);
    expect(store.state.order, SubstackCommentOrder.oldest);
    store.search('weekend');
    expect(store.state.collapsed, isEmpty);
    expect(store.state.rows, hasLength(3));
    client.fail = false;
    client.comments = [_comment('fresh')];
    await store.refresh();
    expect(store.state.error, isNull);
    expect(store.state.collapsed, isEmpty);
  });
  test('newer comment request wins and disposal ignores late data and actions', () async {
    final client = _DiscussionClient();
    final store = SubstackCommentsStore(client, _post);
    addTearDown(client.httpClient.close);
    final old = Completer<List<SubstackComment>>();
    client.next = old;
    final first = store.refresh();
    client.next = Completer<List<SubstackComment>>();
    final second = store.refresh();
    client.next!.complete([_comment('new')]);
    await second;
    old.complete([_comment('old')]);
    await first;
    expect(store.state.comments.single.id, 'new');
    client.next = Completer<List<SubstackComment>>();
    final pending = store.refresh();
    await store.destroy();
    client.next!.complete([_comment('late')]);
    await pending;
    final calls = client.calls;
    await store.refresh();
    store.search('no');
    store.toggle('new');
    store.sort(SubstackCommentOrder.oldest);
    store.setExpanded(false);
    expect(client.calls, calls);
    expect(store.state.comments.single.id, 'new');
    expect(store.state.query, '');
  });
  test('discussion link parsing preserves punctuation and rejects non-web schemes', () {
    const text = 'See (https://example.org/a), then https://example.net/path_(topic).';
    final parts = substackDiscussionTextParts(text);
    expect(parts.map((part) => part.text).join(), text);
    expect(parts.map((part) => part.url).whereType<String>(), [
      'https://example.org/a',
      'https://example.net/path_(topic)',
    ]);
    expect(substackDiscussionUrl('javascript:alert(1)'), isNull);
    expect(substackDiscussionUrl('https://'), isNull);
    expect(substackDiscussionTextParts('No link here').single.url, isNull);
  });

  testWidgets('discussion text opens the tapped URL and updates link targets', (tester) async {
    final h = _Harness();
    addTearDown(() => h.close(tester));
    final opened = <String>[];
    for (final url in ['https://example.org/first', 'https://example.net/second']) {
      await tester.pumpWidget(
        h.app(
          Scaffold(
            body: SubstackDiscussionText(text: 'See $url.', onOpenLink: opened.add),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final rich = find.descendant(of: find.byType(SubstackDiscussionText), matching: find.byType(RichText)).first;
      final paragraph = tester.renderObject<RenderParagraph>(rich);
      final box = paragraph.getBoxesForSelection(TextSelection(baseOffset: 4, extentOffset: 4 + url.length)).first;
      await tester.tapAt(paragraph.localToGlobal(box.toRect().center));
      expect(opened.last, url);
    }
    expect(opened, hasLength(2));
  });

  testWidgets('discussion supports collapsing, searching and clearing with parent context', (tester) async {
    tester.view.physicalSize = const Size(390, 1100);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final h = _Harness();
    addTearDown(() => h.close(tester));
    await tester.pumpWidget(h.app(const SubstackCommentsScreen(post: _post)));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('substack-comment-nested')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('substack-comment-collapse-root')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('substack-comment-nested')), findsNothing);
    await tester.enterText(find.byKey(const ValueKey('substack-comment-search')), 'weekend');
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('substack-comment-nested')), findsOneWidget);
    expect(find.text('Reply context'), findsNWidgets(2));
    expect(find.byKey(const ValueKey('substack-comment-sibling')), findsNothing);
    await tester.enterText(find.byKey(const ValueKey('substack-comment-search')), 'absent');
    await tester.pumpAndSettle();
    expect(find.text('No loaded comments match your search.'), findsOneWidget);
    await tester.tap(find.byTooltip('Clear'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('substack-comment-sibling')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
  testWidgets('missing Note author metadata is safe and local Follow updates visibly', (tester) async {
    final h = _Harness();
    addTearDown(() => h.close(tester));
    const note = SubstackNote(id: 'note', body: 'A quiet place to read.', publication: _publication);
    await tester.pumpWidget(h.app(const SubstackNoteScreen(note: note)));
    await tester.pumpAndSettle();
    expect(find.text('Unknown'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('substack-note-follow-fieldnotes')));
    await tester.pumpAndSettle();
    expect(find.text('Following'), findsOneWidget);
    expect(h.pubs.state.single.id, _publication.id);
    expect(tester.takeException(), isNull);
  });
  testWidgets('Note reactions respect calm mode and have readable semantics', (tester) async {
    final h = _Harness();
    addTearDown(() => h.close(tester));
    final semantics = tester.ensureSemantics();
    const note = SubstackNote(id: 'note', body: 'A quiet place to read.', reactionCount: 1200);
    await tester.pumpWidget(h.app(const Scaffold(body: SubstackNoteCard(note: note))));
    await tester.pumpAndSettle();
    expect(find.bySemanticsLabel(RegExp('Likes: 1200')), findsOneWidget);
    await h.prefs.set(optionCalmMode, true);
    await tester.pumpAndSettle();
    expect(find.bySemanticsLabel(RegExp('Likes: 1200')), findsNothing);
    semantics.dispose();
  });
  testWidgets('Note images open the shared fullscreen viewer', (tester) async {
    final h = _Harness();
    addTearDown(() => h.close(tester));
    await installBlueskyReadingImages(tester);
    const note = SubstackNote(
      id: 'image',
      body: 'A picture from the coast.',
      imageUrl: 'https://review.example/note.png',
    );
    await tester.pumpWidget(h.app(const SubstackNoteScreen(note: note)));
    await settleBlueskyReadingImages(tester);
    await tester.tap(find.descendant(of: find.byType(PluginPostMedia), matching: find.byType(InkWell)).first);
    await settleBlueskyReadingImages(tester);
    expect(find.byType(PluginImageViewer), findsOneWidget);
    expect(tester.widget<PluginImageViewer>(find.byType(PluginImageViewer)).items.single.url, note.imageUrl);
  });
  testWidgets('compact enlarged RTL discussion controls remain usable', (tester) async {
    tester.view.physicalSize = const Size(320, 1100);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final h = _Harness();
    addTearDown(() => h.close(tester));
    await tester.pumpWidget(h.app(const SubstackCommentsScreen(post: _post), rtl: true, scale: 1.7));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('substack-comment-sort')));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(CheckedPopupMenuItem<SubstackCommentOrder>, 'Newest first'));
    await tester.pumpAndSettle();
    expect(find.text('Newest first'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('substack-comment-branches')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Collapse all comments'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('substack-comment-child')), findsNothing);
    expect(tester.takeException(), isNull);
  });
  for (final variant in ['comments', 'comments-rtl', 'note-rtl']) {
    testWidgets('Substack discussion layout $variant', (tester) async {
      final rtl = variant.endsWith('rtl');
      tester.view.physicalSize = Size(rtl ? 320 : 390, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final h = _Harness();
      addTearDown(() => h.close(tester));
      const note = SubstackNote(
        id: 'note',
        body: 'Good writing starts with paying attention. A notebook, a walk, and a little time to think.',
        authorName: 'Maya Chen',
        authorHandle: 'mayachen',
        publication: _publication,
        reactionCount: 42,
      );
      await tester.pumpWidget(
        h.app(
          variant == 'note-rtl' ? const SubstackNoteScreen(note: note) : const SubstackCommentsScreen(post: _post),
          rtl: rtl,
          scale: rtl ? 1.7 : 1,
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await expectLater(
        find.byKey(const ValueKey('substack-discussion-window')),
        matchesGoldenFile('../review-artifacts/renders/substack-$variant.png'),
      );
    });
  }
}
