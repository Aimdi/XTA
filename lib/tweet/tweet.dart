import 'dart:typed_data';
import 'dart:ui' as ui;
import 'dart:io' show Platform;
import 'package:auto_direction/auto_direction.dart';
import 'package:dart_twitter_api/twitter_api.dart' show User;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/status.dart';
import 'package:xta/tweet/_ExpandableTweetText.dart';
import 'package:xta/tweet/_card.dart';
import 'package:xta/tweet/_media.dart';
import 'package:xta/tweet/thread_rail.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_open.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/tweet/article_link_card.dart';
import 'package:xta/tweet/article_screen.dart';
import 'package:xta/tweet/cashtag_quotes.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/article/article.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/rich_text.dart';
import 'package:xta/utils/translation.dart';
import 'package:intl/intl.dart';
import 'package:logging/logging.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';

class TweetTile extends StatefulWidget {
  final bool clickable;
  final String? currentUsername;
  final TweetWithCard tweet;
  final bool isPinned;
  final bool isThread;
  final bool isQuotedTweet;

  // Whether to draw a connector line above/below the avatar, linking this tile to the
  // previous/next tweet of the same thread.
  final bool threadConnectTop;
  final bool threadConnectBottom;

  final bool tweetOpened;
  final bool addSeparator;
  final bool isBirdwatchQuote;
  final int initialMediaIndex;

  const TweetTile({
    super.key,
    required this.clickable,
    this.currentUsername,
    required this.tweet,
    this.isPinned = false,
    this.isThread = false,
    this.tweetOpened = false,
    this.addSeparator = true,
    this.isQuotedTweet = false,
    this.isBirdwatchQuote = false,
    this.threadConnectTop = false,
    this.threadConnectBottom = false,
    this.initialMediaIndex = 0,
  });

  @override
  TweetTileState createState() => TweetTileState();
}

class TweetTileState extends State<TweetTile>
    with SingleTickerProviderStateMixin {
  static final log = Logger('TweetTile');

  // Short K/M suffixes: locale-specific compact forms like "12 Tsd." or
  // "1,2 Mio." eat the footer's width and push the trailing buttons away.
  static final NumberFormat _numberFormat = NumberFormat.compact(
    locale: 'en_US',
  );
  static final RegExp _localeSeparator = RegExp(r'[-_]');

  late bool clickable;
  late String? currentUsername;
  late TweetWithCard tweet;
  late bool isPinned;
  late bool isThread;
  late bool isQuotedTweet;
  late bool addSeparator;
  late bool isBirdwatchQuote;

  TranslationStatus _translationStatus = TranslationStatus.original;
  TranslationBroadcast? _translationBroadcast;

  List<RichTextPart> _originalParts = [];
  List<RichTextPart> _translatedParts = [];
  // The spans currently on display, rebuilt when the parts behind them change
  // (translate / show original) rather than on every build of the tile.
  List<InlineSpan> _displaySpans = const [];

  /// The post actually shown: a retweet displays the post it carries.
  TweetWithCard get _displayedTweet => tweet.retweetedStatusWithCard ?? tweet;

  /// A link to a long-form X article, which carries nothing a preview could be
  /// built from and so rendered as a bare truncated URL.
  String? get _articleLink => _displayedTweet.article != null
      ? null
      : firstArticleLink(
          _displayedTweet.entities?.urls?.map((e) => e.expandedUrl) ?? const [],
        );

  /// When the retweet happened, for the "X retweeted" banner. Relative dates
  /// are pinned at first display here, exactly as [Timestamp] pins its own.
  String? get _retweetRelativeDate =>
      tweet.retweetedStatusWithCard == null || tweet.createdAt == null
      ? null
      : createRelativeDate(tweet.createdAt!);

  bool _isInitialized = false;

  @override
  void initState() {
    super.initState();

    clickable = widget.clickable;
    currentUsername = widget.currentUsername;
    tweet = widget.tweet;
    isPinned = widget.isPinned;
    isThread = widget.isThread;
    isQuotedTweet = widget.isQuotedTweet;
    addSeparator = widget.addSeparator;
    isBirdwatchQuote = widget.isBirdwatchQuote;
  }

  /// The element can be reused for a different tweet — a soft refresh prepends
  /// chains and the list re-associates by index — and a State that froze its
  /// widget's fields in initState would keep showing the old post's text.
  @override
  void didUpdateWidget(TweetTile oldWidget) {
    super.didUpdateWidget(oldWidget);
    final sameId = widget.tweet.idStr == oldWidget.tweet.idStr;
    if (!sameId || !identical(widget.tweet, oldWidget.tweet)) {
      clickable = widget.clickable;
      currentUsername = widget.currentUsername;
      tweet = widget.tweet;
      isPinned = widget.isPinned;
      isThread = widget.isThread;
      isQuotedTweet = widget.isQuotedTweet;
      addSeparator = widget.addSeparator;
      isBirdwatchQuote = widget.isBirdwatchQuote;
      if (!sameId) {
        _translationStatus = TranslationStatus.original;
        disposeRichTextParts(_translatedParts);
        _translatedParts = [];
      }
      _initializeTweetParts();
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();

    if (!_isInitialized) {
      _initializeTweetParts();
      _isInitialized = true;
    }

    // A conversation screen provides a broadcast so one long-press can
    // translate every loaded tweet; feeds don't, and that's fine.
    TranslationBroadcast? broadcast;
    try {
      broadcast = context.read<TranslationBroadcast>();
    } on ProviderNotFoundException {
      broadcast = null;
    }
    if (!identical(broadcast, _translationBroadcast)) {
      _translationBroadcast?.removeListener(_onTranslationBroadcast);
      _translationBroadcast = broadcast;
      _translationBroadcast?.addListener(_onTranslationBroadcast);
    }
  }

  @override
  void dispose() {
    disposeRichTextParts(_originalParts);
    disposeRichTextParts(_translatedParts);
    _translationBroadcast?.removeListener(_onTranslationBroadcast);
    super.dispose();
  }

  void _initializeTweetParts() {
    // Get the text to display from the actual tweet, i.e. the retweet if there is one, otherwise we end up with "RT @" crap in our text
    var actualTweet = _displayedTweet;
    // get the longest tweet between legacy (still used most of the time) and noteText (mostly ny premium users?)
    var tweetTextFinal =
        actualTweet.noteText ?? actualTweet.fullText ?? actualTweet.text ?? '';
    var entitiesFinal = actualTweet.noteEntities ?? actualTweet.entities;

    List<RichTextPart> tweetParts = buildRichText(
      context,
      tweetTextFinal,
      entitiesFinal,
    );
    // A re-initialisation (element reused for another tweet) replaces the
    // lists; the old ones' recognizers go with them.
    if (!identical(_originalParts, tweetParts)) {
      disposeRichTextParts(_originalParts);
      disposeRichTextParts(_translatedParts);
    }
    setState(() {
      _showParts(tweetParts);
      _originalParts = tweetParts;
    });
  }

  /// Displays [parts]. Call from inside a `setState`.
  void _showParts(List<RichTextPart> parts) =>
      _displaySpans = displayRichText(parts);

  /// X's no-linguistic-content codes: media-only, mentions-only, cashtags-only,
  /// short/undetermined text. Nothing there to translate.
  static const _untranslatableLangs = {
    'zxx',
    'qme',
    'qam',
    'qct',
    'qht',
    'qst',
    'und',
  };

  /// Whether the post is (or might be) in a language other than [locale]. An
  /// absent code keeps the offer — better an idle button than a stranded
  /// reader.
  bool _offerTranslation(Locale locale) {
    final lang = tweet.lang;
    if (lang == null || lang.isEmpty) {
      return true;
    }
    if (_untranslatableLangs.contains(lang)) {
      return false;
    }
    return lang.split('-').first != locale.languageCode;
  }

  Locale _effectiveLocale() {
    var localeStr = PrefService.of(
      context,
      listen: false,
    ).get<String>(optionLocale);
    final isSystemLocale =
        (localeStr ?? optionLocaleDefault) == optionLocaleDefault;
    if (isSystemLocale) {
      localeStr = Platform.localeName;
    }

    final splitLocale = localeStr!.split(_localeSeparator);
    return splitLocale.length == 1
        ? Locale(splitLocale[0])
        : Locale(splitLocale[0], splitLocale[1]);
  }

  // Translates this tile when a long-press on any translate button in the
  // same scope (e.g. the whole conversation) broadcasts a request.
  void _onTranslationBroadcast() {
    if (!mounted || _translationStatus != TranslationStatus.original) {
      return;
    }
    onClickTranslate(context, _effectiveLocale());
  }

  Future<void> onClickTranslate(BuildContext context, Locale locale) async {
    // If we've already translated this text before, use those results instead of translating again
    if (_translatedParts.isNotEmpty) {
      return setState(() {
        _showParts(_translatedParts);
        _translationStatus = TranslationStatus.translated;
      });
    }

    setState(() {
      _translationStatus = TranslationStatus.translating;
    });

    var originalText = _originalParts.map((e) => e.toString()).toList();
    var res = await TranslationAPI.translate(
      locale,
      tweet.idStr!,
      originalText,
      tweet.lang ?? "",
    );
    if (res.success) {
      if (!context.mounted) return;
      final List<RichTextPart> translatedParts = buildRichText(
        context,
        res.body['result']['text'],
        res.body['result']['entities'],
      );

      // We cache the translated parts in a property in case the user swaps back and forth
      return setState(() {
        _showParts(translatedParts);
        _translatedParts = translatedParts;
        _translationStatus = TranslationStatus.translated;
      });
    } else {
      return showTranslationError(
        res.errorMessage ?? 'An unknown error occurred while translating',
      );
    }
  }

  void showTranslationError(String message) {
    setState(() {
      _translationStatus = TranslationStatus.translationFailed;
    });

    showSnackBar(context, icon: '💥', message: message);
  }

  Future<void> onClickShowOriginal() async {
    setState(() {
      _showParts(_originalParts);
      _translationStatus = TranslationStatus.original;
    });
  }

  void onClickOpenTweet(TweetWithCard tweet) {
    final target = openablePost(tweet);
    if (target == null) {
      return;
    }
    Navigator.pushNamed(
      context,
      routeStatus,
      arguments: StatusScreenArguments(
        id: target.id,
        username: target.username,
        tweetOpened: true,
        initialTweet: tweet,
      ),
    );
  }

  bool _canSubscribeTo(User? user) =>
      user != null &&
      user.idStr != null &&
      user.screenName != null &&
      user.name != null;

  UserSubscription _subscriptionFor(User user) => UserSubscription(
    id: user.idStr!,
    screenName: user.screenName!,
    name: user.name!,
    profileImageUrlHttps: user.profileImageUrlHttps,
    verified: user.verified ?? false,
    createdAt: user.createdAt ?? DateTime.now(),
    inFeed: true,
  );

  /// Offers to subscribe to a not-yet-followed author, or to file them into
  /// groups right away (which subscribes them too).
  void _showSubscribeSheet(BuildContext context, UserSubscription user) {
    showModalBottomSheet(
      context: context,
      builder: (sheetContext) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: const Icon(Icons.person_add),
                title: Text(L10n.of(context).subscribe),
                onTap: () async {
                  Navigator.pop(sheetContext);
                  await context.read<SubscriptionsModel>().toggleSubscribe(
                    user,
                    false,
                  );
                },
              ),
              ListTile(
                leading: const Icon(Icons.group_add),
                title: Text(L10n.of(context).add_to_group),
                onTap: () {
                  Navigator.pop(sheetContext);
                  pickUserGroups(
                    context,
                    user: user,
                    followed: false,
                    groupsForUser: const [],
                  );
                },
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildErrorTweet(String text) {
    // create the layout of tombstones (deleted tweets) and other possible errors that we want to display as a tweet
    return SizedBox(
      width: double.infinity,
      child: Card(
        child: Container(
          padding: const EdgeInsets.all(16),
          child: Text(
            text,
            style: const TextStyle(fontStyle: FontStyle.italic),
          ),
        ),
      ),
    );
  }

  Future<Uint8List?> captureWidget() async {
    // The RepaintBoundary is the root of this State's build, so the State's own
    // render object is it — no per-tile GlobalKey needed (each one costs a trip
    // through the global registry on every mount and unmount while scrolling).
    if (!mounted) {
      return null;
    }
    final boundary = context.findRenderObject();
    if (boundary is! RenderRepaintBoundary) {
      return null;
    }
    final ui.Image image = await boundary.toImage(pixelRatio: 3.0);
    final ByteData? byteData = await image.toByteData(
      format: ui.ImageByteFormat.png,
    );
    if (byteData == null) {
      return null;
    }
    final Uint8List pngBytes = byteData.buffer.asUint8List();

    return pngBytes;
  }

  @override
  Widget build(BuildContext context) {
    final prefs = PrefService.of(context, listen: false);

    var shareBaseUrlOption = prefs.get(optionShareBaseUrl);
    var shareBaseUrl =
        shareBaseUrlOption != null && shareBaseUrlOption.isNotEmpty
        ? shareBaseUrlOption
        : 'https://x.com';

    TweetWithCard tweet = _displayedTweet;

    // If the user is on a profile, all the shown tweets are from that profile, so it makes no sense to hide it
    final isTweetOnSameProfile =
        currentUsername != null &&
        tweet.user != null &&
        currentUsername == tweet.user!.screenName;
    final hideAuthorInformation =
        !isTweetOnSameProfile && prefs.get(optionNonConfirmationBiasMode);

    var theme = Theme.of(context);

    if (tweet.isTombstone ?? false) {
      return _buildErrorTweet(tweet.text!);
    }

    Widget media = Container();
    if (tweet.extendedEntities?.media != null &&
        tweet.extendedEntities!.media!.isNotEmpty) {
      media = TweetMedia(
        sensitive: tweet.possiblySensitive,
        media: tweet.extendedEntities!.media!,
        username: tweet.user!.screenName!,
        initialMediaIndex: widget.initialMediaIndex,
        tweetId: tweet.idStr,
        tweet: tweet,
      );
    }

    Widget retweetBanner = Container();
    Widget retweetSidebar = Container();
    if (this.tweet.retweetedStatusWithCard != null) {
      retweetBanner = _TweetTileLeading(
        icon: Icons.repeat,
        onTap: () => Navigator.pushNamed(
          context,
          routeProfile,
          arguments: ProfileScreenArguments.fromScreenName(
            this.tweet.user!.screenName!,
            null,
          ),
        ),
        children: [
          TextSpan(
            text: L10n.of(context).this_tweet_user_name_retweeted(
              this.tweet.user!.name!,
              _retweetRelativeDate ?? '',
            ),
            style: theme.textTheme.bodySmall,
          ),
        ],
      );

      retweetSidebar = Container(color: theme.secondaryHeaderColor, width: 4);
    }

    // "Replying to @someone" belongs under the header and above the text, where
    // X puts it: above the header it announced a reply before saying whose post
    // it was, so a conversation read backwards. A quoted post never shows it —
    // the quote already carries one conversation, and a second one inside the
    // card made it impossible to tell which post was being read.
    Widget replyToTile = Container();
    var replyTo = tweet.inReplyToScreenName;
    // Mid-thread the post being replied to is already on screen just above,
    // so naming it again is noise.
    if (replyTo != null && !isQuotedTweet && !widget.threadConnectTop) {
      replyToTile = _ReplyingToLine(
        screenName: replyTo,
        onTap: () {
          var replyToId = tweet.inReplyToStatusIdStr;
          if (replyToId == null) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(
                  L10n.of(context).sorry_the_replied_tweet_could_not_be_found,
                ),
              ),
            );
          } else {
            Navigator.pushNamed(
              context,
              routeStatus,
              arguments: StatusScreenArguments(
                id: replyToId,
                username: replyTo,
              ),
            );
          }
        },
      );
    }

    var tweetText = tweet.fullText ?? tweet.text;
    if (tweetText == null) {
      return Text(
        L10n.of(context).the_tweet_did_not_contain_any_text_this_is_unexpected,
      );
    }

    if (isBirdwatchQuote) {
      return Card(
        child: Container(
          // Fill the width so both RTL and LTR text are displayed correctly
          width: double.infinity,
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 16),
          child: Column(
            children: [
              Container(
                alignment: Alignment.centerLeft,
                padding: const EdgeInsets.only(
                  bottom: 0,
                  left: 24,
                  right: 16,
                  top: 0,
                ),
                child: RichText(
                  text: TextSpan(
                    children: [
                      WidgetSpan(
                        child: Icon(
                          Icons.group_rounded,
                          size: 16,
                          color: Theme.of(context).hintColor,
                        ),
                        alignment: PlaceholderAlignment.middle,
                      ),
                      const WidgetSpan(child: SizedBox(width: 16)),
                      TextSpan(
                        text: L10n.of(context).community_notes_header,
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          color: Theme.of(context).textTheme.titleSmall?.color,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              SizedBox(height: 8),
              AutoDirection(
                text: tweetText,
                child: SelectableText.rich(TextSpan(children: _displaySpans)),
              ),
            ],
          ),
        ),
      );
    }

    var birdwatchQuoted = Container();
    if (tweet.birdwatchQuotedStatus != null) {
      birdwatchQuoted = Container(
        margin: const EdgeInsets.all(8),
        child: TweetTile(
          clickable: false,
          tweet: tweet.birdwatchQuotedStatus!,
          isBirdwatchQuote: true,
        ),
      );
    }

    var quotedTweet = Container();

    // don't display a nested quoted tweet if we are already building a quoted tweet
    if (!isQuotedTweet && (tweet.isQuoteStatus ?? false)) {
      Widget quotedContent;
      if (tweet.quotedStatusWithCard != null) {
        // Open the quoted post from the card chrome; its own text tap is off
        // (isQuotedTweet) so "show more" is not fighting an open-on-tap handler.
        final quoted = tweet.quotedStatusWithCard!;
        quotedContent = GestureDetector(
          behavior: HitTestBehavior.translucent,
          onTap: () => onClickOpenTweet(quoted),
          child: TweetTile(
            clickable: true,
            tweet: quoted,
            currentUsername: currentUsername,
            addSeparator: false,
            isQuotedTweet: true,
          ),
        );
      } else if (tweet.quotedStatusIdStr != null) {
        // If twitter did not gave us the full tweet for some reason, we show a clickable tile to the tweet
        // There always seem to be an actual link to the quoted tweet that we can display (showing username + id)
        String? msg =
            tweet.quotedStatusPermalink?.display ??
            L10n.of(context).view_quoted_tweet;
        quotedContent = GestureDetector(
          onTap: () => Navigator.pushNamed(
            context,
            routeStatus,
            arguments: StatusScreenArguments(
              id: tweet.quotedStatusIdStr!,
              username: null,
            ),
          ),
          child: _buildErrorTweet(msg),
        );
      } else {
        // If we have a quote tweet we should at least have quotedStatusIdStr, but just in case twitter is being weird
        quotedContent = _buildErrorTweet(
          L10n.of(context).could_not_retrieve_quoted_tweet,
        );
      }
      quotedTweet = Container(
        decoration: quoteCardDecoration(context),
        clipBehavior: Clip.antiAlias,
        margin: const EdgeInsets.all(8),
        child: quotedContent,
      );
    }

    // Only create the tweet content if the tweet contains text. X often omits
    // display_text_range on reshaped payloads — treat null as "has text".
    Widget content = Container();

    final textEnd = tweet.displayTextRange?.elementAtOrNull(1);
    if (textEnd == null || textEnd != 0) {
      content = DefaultTextStyle.merge(
        style: theme.textTheme.bodyLarge ?? theme.textTheme.bodyMedium,
        child: Container(
          // Fill the width so both RTL and LTR text are displayed correctly
          width: double.infinity,
          padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
          child: AutoDirection(
            text: tweetText,
            child: ExpandableTweetText(
              textSpans: _displaySpans,
              // Quoted tiles open from the wrapping card GestureDetector so the
              // expand control is never a rival for the same tap.
              onTap: (!widget.tweetOpened && !isQuotedTweet)
                  ? () => onClickOpenTweet(tweet)
                  : null,
              selectable: widget.tweetOpened,
              maxLines:
                  PrefService.of(
                    context,
                    listen: false,
                  ).get(alwaysShowFullTweetContents)
                  ? null
                  : kTweetTextMaxLines,
            ),
          ),
        ),
      );
    }

    final locale = _effectiveLocale();

    // The post's top-right, next to the timestamp — not in the footer strip,
    // which is for engagement and was one control too wide on a phone. Only on
    // posts there is something to translate: X offers nothing on a post
    // already in your language, and a button on every card was chrome.
    final translateButton = tweet.article != null || !_offerTranslation(locale)
        ? null
        : TweetTranslateButton(
            status: _translationStatus,
            onTranslate: () => onClickTranslate(context, locale),
            onShowOriginal: onClickShowOriginal,
            onLongPress: () {
              final broadcast = _translationBroadcast;
              if (broadcast != null) {
                broadcast.requestAll();
              } else {
                onClickTranslate(context, locale);
              }
            },
          );

    final footerBar = TweetFooterBar(
      tweet: tweet,
      tweetText: tweetText,
      shareBaseUrl: shareBaseUrl,
      locale: locale,
      numberFormat: _numberFormat,
      isArticle: tweet.article != null,
      onOpenTweet: () => onClickOpenTweet(tweet),
      onCaptureImage: captureWidget,
    );

    var article = Container();
    if (tweet.article != null) {
      article = Container(
        child: ArticleWidget(
          article: tweet.article!,
          expand: widget.tweetOpened,
          onTap: () => onClickOpenTweet(tweet),
          bottomBar: widget.tweetOpened ? footerBar : null,
        ),
      );
    }

    DateTime? createdAt;
    if (tweet.createdAt != null) {
      createdAt = tweet.createdAt;
    }

    // A quoted tweet is subordinate to its host, so it gets a smaller avatar
    // and a denser header — otherwise it reads as another post in the timeline.
    final avatarSize = isQuotedTweet ? 32.0 : 48.0;
    final plainAvatar = hideAuthorInformation
        ? Icon(Icons.account_circle, size: avatarSize)
        // UserAvatar clips itself; a second ClipRRect here was one more
        // antialiased clip layer per visible tile for nothing.
        : UserAvatar(uri: tweet.user!.profileImageUrlHttps, size: avatarSize);

    final showSubscribeBadge =
        prefs.get(optionTweetsShowSubscribeBadge) != false;
    final avatar =
        hideAuthorInformation ||
            !showSubscribeBadge ||
            !_canSubscribeTo(tweet.user)
        ? plainAvatar
        : ScopedBuilder<SubscriptionsModel, List<Subscription>>(
            store: context.read<SubscriptionsModel>(),
            distinct: (_) => context.read<SubscriptionsModel>().state.any(
              (s) => s.id == tweet.user!.idStr,
            ),
            onState: (_, subscriptions) {
              final followed = subscriptions.any(
                (s) => s.id == tweet.user!.idStr,
              );
              if (followed) {
                return plainAvatar;
              }
              return Stack(
                clipBehavior: Clip.none,
                children: [
                  plainAvatar,
                  Positioned(
                    right: -2,
                    bottom: -2,
                    child: GestureDetector(
                      // Keep the hit area comfortable even though the visible
                      // badge is small.
                      behavior: HitTestBehavior.opaque,
                      onTap: () => _showSubscribeSheet(
                        context,
                        _subscriptionFor(tweet.user!),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.all(4),
                        child: Container(
                          decoration: BoxDecoration(
                            color: Theme.of(context).colorScheme.primary,
                            shape: BoxShape.circle,
                            border: Border.all(
                              color: Theme.of(context).colorScheme.surface,
                              width: 1,
                            ),
                          ),
                          padding: const EdgeInsets.all(1.5),
                          child: Icon(
                            Icons.add,
                            size: 12,
                            color: Theme.of(context).colorScheme.onPrimary,
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
              );
            },
          );

    void onTapProfile() {
      // Null when there is no author to open, or when it is the profile already
      // on screen — tapping that would push the same profile onto itself.
      final target = openableProfile(
        tweet.user,
        currentUsername: currentUsername,
      );
      if (target == null) {
        return;
      }
      Navigator.pushNamed(
        context,
        routeProfile,
        arguments: ProfileScreenArguments(target.id, target.screenName, null),
      );
    }

    final titleRow = Row(
      children: [
        // Username
        if (!hideAuthorInformation)
          Flexible(
            child: Row(
              children: [
                Flexible(
                  child: Text(
                    tweet.user!.name!,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ),
                if (tweet.user!.verified ?? false) const SizedBox(width: 4),
                if (tweet.user!.verified ?? false)
                  Icon(
                    Icons.verified,
                    size: 18,
                    color: Theme.of(context).colorScheme.primary,
                  ),
              ],
            ),
          ),
      ],
    );

    final metaStyle = theme.textTheme.labelMedium?.copyWith(
      color: theme.colorScheme.onSurfaceVariant,
    );

    final subtitleRow = Row(
      mainAxisAlignment: hideAuthorInformation
          ? MainAxisAlignment.end
          : MainAxisAlignment.spaceBetween,
      children: [
        // Twitter name
        if (!hideAuthorInformation) ...[
          Flexible(
            child: Text(
              '@${tweet.user!.screenName!}',
              overflow: TextOverflow.ellipsis,
              style: metaStyle,
            ),
          ),
          const SizedBox(width: 4),
        ],
        if (createdAt != null)
          DefaultTextStyle(
            style: metaStyle ?? theme.textTheme.bodySmall!,
            child: Timestamp(
              timestamp: createdAt,
              absoluteTimestamp: prefs.get(optionUseAbsoluteTimestamp),
              // X-style "5m" in the timeline; the opened post keeps the
              // full wording.
              compact: !widget.tweetOpened,
            ),
          ),
      ],
    );

    final headerTile = ListTile(
      onTap: onTapProfile,
      dense: isQuotedTweet,
      visualDensity: isQuotedTweet ? VisualDensity.compact : null,
      title: titleRow,
      subtitle: subtitleRow,
      // Profile picture
      leading: avatar,
      trailing: isQuotedTweet ? null : translateButton,
    );

    final pinnedBadge = isPinned
        ? _TweetTileLeading(
            icon: Icons.push_pin,
            children: [
              TextSpan(
                text: L10n.of(context).pinned_tweet,
                style: theme.textTheme.bodySmall,
              ),
            ],
          )
        : null;
    final threadBadge = isThread
        ? _TweetTileLeading(
            icon: Icons.forum,
            children: [
              TextSpan(
                text: L10n.of(context).thread,
                style: theme.textTheme.bodySmall,
              ),
            ],
          )
        : null;

    final articleLink = _articleLink;

    final bodyChildren = <Widget>[
      replyToTile,
      if (tweet.article == null) content,
      if (tweet.article == null)
        CashtagQuotesBar(symbols: tweetCashtags(tweet)),
      if (articleLink != null)
        ArticleLinkCard(
          url: articleLink,
          // Read in XTA rather than handed to a browser: the article is the
          // post's own content.
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => ArticleScreen(url: articleLink)),
          ),
        ),
      media,
      quotedTweet,
      TweetCard(tweet: tweet, card: tweet.card),
      birdwatchQuoted,
      article,
      // A quoted tweet shows no action bar: its reply/repost/like counts belong
      // to the quoted post, not to the one being read, and a second footer row
      // makes the card look like a separate timeline entry. Tapping the quote
      // opens it, where the full bar is available.
      if (!isQuotedTweet) footerBar,
    ];

    final isThreadTile = widget.threadConnectTop || widget.threadConnectBottom;

    if (isThreadTile) {
      return RepaintBoundary(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            retweetBanner,
            ?pinnedBadge,
            ?threadBadge,
            _buildThreadBody(
              theme,
              avatar,
              Row(
                children: [
                  Expanded(
                    child: InkWell(
                      onTap: onTapProfile,
                      child: Padding(
                        padding: const EdgeInsets.only(
                          left: 16,
                          right: 16,
                          top: 12,
                          bottom: 12,
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            DefaultTextStyle.merge(
                              style: theme.textTheme.bodyMedium?.copyWith(
                                fontWeight: FontWeight.w600,
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                              child: titleRow,
                            ),
                            DefaultTextStyle.merge(
                              style: theme.textTheme.labelMedium?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                              child: subtitleRow,
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                  if (translateButton != null)
                    Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: translateButton,
                    ),
                ],
              ),
              bodyChildren,
              indentBody: widget.threadConnectBottom,
              onTapProfile: onTapProfile,
            ),
          ],
        ),
      );
    }

    return RepaintBoundary(
      child: Column(
        children: [
          _withDoubleTapToLike(
            context,
            tweet,
            tweetFlatCard(
              color: tweetCardColor(context),
              child: Row(
                children: [
                  retweetSidebar,
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        retweetBanner,
                        ?pinnedBadge,
                        ?threadBadge,
                        headerTile,
                        ...bodyChildren,
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          if (addSeparator)
            tweetHairlineDivider(context)
          else
            const Divider(
              height: 0,
              thickness: kTweetDividerThickness,
              color: Colors.transparent,
            ),
        ],
      ),
    );
  }

  Widget _buildThreadBody(
    ThemeData theme,
    Widget avatar,
    Widget header,
    List<Widget> bodyChildren, {
    required bool indentBody,
    required VoidCallback onTapProfile,
  }) {
    return ThreadRailBody(
      connectTop: widget.threadConnectTop,
      connectBottom: widget.threadConnectBottom,
      indentBody: indentBody,
      avatar: avatar,
      header: header,
      bodyChildren: bodyChildren,
      onTapProfile: onTapProfile,
    );
  }
}

// Deriving the card color constructs a whole ThemeData (and runs
// ColorScheme.fromSeed's HCT math) — far too expensive to repeat for every
// tile on every frame, so the result is memoized per theme.
Color? _cardColorCache;
Color? _cardColorSeed;
Brightness? _cardColorBrightness;

Color? tweetCardColor(BuildContext context) {
  final tokens = XLookTokens.maybeOf(context);
  if (tokens != null) {
    return tokens.card;
  }
  final theme = Theme.of(context);
  final prefs = PrefService.of(context, listen: false);
  final trueBlack =
      theme.brightness == Brightness.dark &&
      prefs.get(optionThemeTrueBlack) &&
      prefs.get(optionThemeTrueBlackTweetCards);
  if (trueBlack) {
    return Colors.black;
  }
  if (theme.colorScheme.primary != _cardColorSeed ||
      theme.brightness != _cardColorBrightness) {
    _cardColorSeed = theme.colorScheme.primary;
    _cardColorBrightness = theme.brightness;
    _cardColorCache = ThemeData(
      colorScheme: ColorScheme.fromSeed(
        seedColor: theme.colorScheme.primary,
        brightness: theme.brightness,
      ),
    ).cardColor;
  }
  return _cardColorCache;
}

class TweetHasNoContentException {
  final String? id;

  TweetHasNoContentException(this.id);

  @override
  String toString() {
    return 'The tweet has no content {id: $id}';
  }
}

/// Wraps a post so a double-tap likes it, when the reader has asked for that.
///
/// Returns [child] untouched while the gesture is off, which is the point of
/// the setting: a double-tap recogniser makes every single tap wait to see
/// whether a second one follows, and that delay would otherwise be paid by
/// everyone opening a post.
///
/// Deliberately not a swipe. A horizontal drag on a post would win the gesture
/// arena against the page view underneath it, and swiping between tabs would
/// stop working in exactly the place people swipe most.
Widget _withDoubleTapToLike(
  BuildContext context,
  TweetWithCard tweet,
  Widget child,
) {
  final enabled =
      PrefService.of(
        context,
        listen: false,
      ).get<bool>(optionGestureDoubleTapLike) ==
      true;
  final id = tweet.idStr;
  if (!enabled || id == null) {
    return child;
  }

  return GestureDetector(
    onDoubleTap: () async {
      final model = context.read<LikedTweetModel>();
      if (model.isLiked(id)) {
        return;
      }
      await model.likeTweet(id, tweet.user?.idStr, tweet.toJson());
      if (context.mounted) {
        maybeShowLikeToast(context);
      }
    },
    child: child,
  );
}

/// "Replying to @someone", sitting between the header and the text.
///
/// No icon and no indent of its own: it lines up with the post's text so it
/// reads as part of the post rather than as a banner above it, which is what
/// made a reply hard to tell apart from the post it answered.
class _ReplyingToLine extends StatelessWidget {
  final String screenName;
  final VoidCallback onTap;

  const _ReplyingToLine({required this.screenName, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final base = theme.textTheme.bodySmall!.copyWith(
      color: theme.colorScheme.onSurfaceVariant,
    );

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.only(left: 16, right: 16, top: 4),
      child: GestureDetector(
        onTap: onTap,
        child: RichText(
          text: TextSpan(
            style: base,
            children: [
              TextSpan(text: '${L10n.of(context).replying_to} '),
              TextSpan(
                text: '@$screenName',
                style: base.copyWith(color: theme.colorScheme.primary),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TweetTileLeading extends StatelessWidget {
  final Function()? onTap;
  final IconData icon;
  final Iterable<InlineSpan> children;

  const _TweetTileLeading({
    this.onTap,
    required this.icon,
    required this.children,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(top: 16),
      child: InkWell(
        onTap: onTap,
        child: Container(
          alignment: Alignment.centerLeft,
          padding: const EdgeInsets.only(
            bottom: 0,
            left: 52,
            right: 16,
            top: 0,
          ),
          child: RichText(
            text: TextSpan(
              children: [
                WidgetSpan(
                  child: Icon(
                    icon,
                    size: 12,
                    color: Theme.of(context).hintColor,
                  ),
                  alignment: PlaceholderAlignment.middle,
                ),
                const WidgetSpan(child: SizedBox(width: 16)),
                ...children,
              ],
            ),
          ),
        ),
      ),
    );
  }
}
