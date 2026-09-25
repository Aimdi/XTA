import 'dart:convert';
import 'package:dart_twitter_api/twitter_api.dart' show User;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_folder_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/tweet/_like_button.dart';
import 'package:xta/tweet/quotes_screen.dart';
import 'package:xta/tweet/tweet_footer.dart';

const _shareChannel = MethodChannel('dev.fluttercommunity.plus/share');

class _MemoryLikes extends LikedTweetModel {
  @override
  Future<void> likeTweet(String id, String? user, Map<String, dynamic> content) async {
    update([LikedTweet(id: id, user: user, content: jsonEncode(content))], force: true);
  }

  @override
  Future<void> unlikeTweet(String id) async {
    update([], force: true);
  }
}

class _MemorySaves extends SavedTweetModel {
  @override
  Future<void> saveTweet(String id, String? user, Map<String, dynamic> content, {String? folderId}) async {
    update([SavedTweet(id: id, user: user, content: jsonEncode(content), folderId: folderId)], force: true);
  }

  @override
  Future<void> deleteSavedTweet(String id) async {
    update([], force: true);
  }
}

TweetWithCard _post({String? id = '123', String? handle, bool hasAuthor = true}) => TweetWithCard()
  ..idStr = id
  ..user = hasAuthor ? (User()..screenName = handle) : null;

Future<({_MemoryLikes likes, _MemorySaves saves})> _pumpFooter(
  WidgetTester tester,
  TweetWithCard tweet, {
  double textScale = 1,
  String shareBaseUrl = 'https://x.com/',
  Map<String, dynamic> preferences = const {},
  Future<Uint8List?> Function()? capture,
  VoidCallback? onOpenTweet,
  RouteFactory? onGenerateRoute,
}) async {
  final likes = _MemoryLikes();
  final saves = _MemorySaves();
  final folders = SavedTweetFolderModel();
  addTearDown(likes.destroy);
  addTearDown(saves.destroy);
  addTearDown(folders.destroy);
  await tester.pumpWidget(
    MultiProvider(
      providers: [
        Provider<LikedTweetModel>.value(value: likes),
        Provider<SavedTweetModel>.value(value: saves),
        Provider<SavedTweetFolderModel>.value(value: folders),
      ],
      child: PrefService(
        service: PrefServiceCache(
          cache: {optionLikedFirstToastShown: true, optionSavedFolderHintShown: true, ...preferences},
        ),
        child: MaterialApp(
          onGenerateRoute: onGenerateRoute,
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: const [Locale('en')],
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
            child: child!,
          ),
          home: Scaffold(
            body: TweetFooterBar(
              tweet: tweet,
              tweetText: 'A useful post',
              shareBaseUrl: shareBaseUrl,
              locale: const Locale('en'),
              numberFormat: NumberFormat.compact(locale: 'en'),
              onOpenTweet: onOpenTweet ?? () {},
              onCaptureImage: capture ?? () async => null,
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return (likes: likes, saves: saves);
}

Future<void> _openShare(WidgetTester tester) async {
  await tester.tap(find.byIcon(Icons.share));
  await tester.pumpAndSettle();
}

Finder _sheetAction(IconData icon) => find.ancestor(of: find.byIcon(icon), matching: find.byType(ListTile));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final shares = <MethodCall>[];

  setUp(() {
    shares.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(_shareChannel, (
      call,
    ) async {
      shares.add(call);
      return 'shared';
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(_shareChannel, null);
  });

  for (final author in [null, '', 'reader']) {
    testWidgets('link sharing preserves a usable status path for handle $author', (tester) async {
      await _pumpFooter(tester, _post(handle: author, hasAuthor: author != null));
      await _openShare(tester);
      await tester.tap(_sheetAction(Icons.link));
      await tester.pumpAndSettle();

      expect(
        shares.single.arguments['text'],
        'https://x.com/${author == null || author.isEmpty ? 'i' : author}/status/123',
      );
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('content and link sharing works without an author handle', (tester) async {
    await _pumpFooter(tester, _post());
    await _openShare(tester);
    await tester.tap(_sheetAction(Icons.add_link));
    await tester.pumpAndSettle();

    expect(shares.single.arguments['text'], 'A useful post\n\nhttps://x.com/i/status/123');
    expect(tester.takeException(), isNull);
  });

  testWidgets('sharing still honors the configured frontend URL', (tester) async {
    await _pumpFooter(tester, _post(handle: 'reader'), shareBaseUrl: 'https://reader.example/');
    await _openShare(tester);
    await tester.tap(_sheetAction(Icons.link));
    await tester.pumpAndSettle();

    expect(shares.single.arguments['text'], 'https://reader.example/reader/status/123');
    expect(tester.takeException(), isNull);
  });

  for (final id in [null, '', '  ']) {
    testWidgets('missing identity $id disables post actions but preserves text and image sharing', (tester) async {
      var opened = 0;
      var captured = 0;
      final models = await _pumpFooter(
        tester,
        _post(id: id),
        preferences: {optionPluginDeepmarksEnabled: true, optionPluginKarakeepEnabled: true},
        onOpenTweet: () => opened++,
        capture: () async {
          captured++;
          return null;
        },
      );

      for (final icon in [Icons.chat_bubble_outline, Icons.format_quote, Icons.favorite_border]) {
        final button = find.ancestor(
          of: find.byIcon(icon),
          matching: find.byWidgetPredicate((widget) => widget is TextButton),
        );
        expect(tester.widget<TextButton>(button).onPressed, isNull);
        await tester.tap(button);
      }
      for (final icon in [Icons.bookmark_border, Icons.more_horiz]) {
        final button = find.ancestor(of: find.byIcon(icon), matching: find.byType(IconButton));
        expect(tester.widget<IconButton>(button).onPressed, isNull);
        await tester.tap(button);
      }
      await tester.longPress(find.byIcon(Icons.bookmark_border));
      await tester.pumpAndSettle();
      expect(opened, 0);
      expect(models.likes.state, isEmpty);
      expect(models.saves.state, isEmpty);
      expect(find.byType(BottomSheet), findsNothing);

      await _openShare(tester);
      for (final icon in [Icons.link, Icons.add_link, Icons.bookmarks_outlined, Icons.bookmark_add_outlined]) {
        expect(tester.widget<ListTile>(_sheetAction(icon)).enabled, isFalse);
      }
      await tester.tap(_sheetAction(Icons.text_snippet));
      await tester.pumpAndSettle();
      expect(shares.single.arguments['text'], 'A useful post');

      await _openShare(tester);
      await tester.tap(_sheetAction(Icons.screenshot));
      await tester.pumpAndSettle();
      expect(captured, 1);
      expect(find.byType(BottomSheet), findsNothing);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('a known post keeps local likes and saves working even without its author', (tester) async {
    final models = await _pumpFooter(tester, _post(hasAuthor: false));
    await tester.tap(find.byType(LikeButton));
    await tester.pumpAndSettle();
    expect(models.likes.isLiked('123'), isTrue);
    await tester.tap(find.byType(LikeButton));
    await tester.pumpAndSettle();
    expect(models.likes.isLiked('123'), isFalse);

    await tester.tap(find.byIcon(Icons.bookmark_border));
    await tester.pumpAndSettle();
    expect(models.saves.isSaved('123'), isTrue);
    await tester.tap(find.byIcon(Icons.bookmark));
    await tester.pumpAndSettle();
    expect(models.saves.isSaved('123'), isFalse);
    expect(tester.takeException(), isNull);
  });

  testWidgets('reposted-by opens the existing retweeters tab for this post', (tester) async {
    RouteSettings? destination;
    await _pumpFooter(
      tester,
      _post(hasAuthor: false),
      onGenerateRoute: (settings) {
        destination = settings;
        return MaterialPageRoute<void>(builder: (_) => const Scaffold(body: Text('Destination')));
      },
    );
    await tester.tap(find.byIcon(Icons.more_horiz));
    await tester.pumpAndSettle();
    final action = _sheetAction(Icons.repeat);
    await tester.ensureVisible(action);
    await tester.tap(action);
    await tester.pumpAndSettle();

    expect(destination?.name, routeQuotes);
    final arguments = destination!.arguments! as QuotesScreenArguments;
    expect(arguments.id, '123');
    expect(arguments.initialTab, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('share actions and cancellation remain reachable on a short screen with large text', (tester) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(360, 340);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);
    await _pumpFooter(
      tester,
      _post(handle: 'reader'),
      textScale: 2,
      preferences: {optionPluginDeepmarksEnabled: true, optionPluginKarakeepEnabled: true},
    );
    await _openShare(tester);
    expect(tester.takeException(), isNull);
    final cancel = _sheetAction(Icons.close);
    await tester.ensureVisible(cancel);
    await tester.pumpAndSettle();
    expect(cancel.hitTestable(), findsOneWidget);
    await tester.tap(cancel);
    await tester.pumpAndSettle();
    expect(find.byType(BottomSheet), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
