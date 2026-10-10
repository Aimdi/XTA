import 'dart:math' as math;
import 'dart:typed_data';
// intl also exports a TextDirection, so the painting one is qualified.
import 'dart:ui' as ui;

import 'package:dart_twitter_api/twitter_api.dart' show Media, Url;
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/folder_picker.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/status.dart';
import 'package:xta/tweet/_like_button.dart';
import 'package:xta/tweet/engagement_count.dart';
import 'package:xta/tweet/tweet_action_style.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_open.dart';
import 'package:xta/tweet/quote_actions.dart';
import 'package:xta/utils/urls.dart';
import 'package:xta/database/entities.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/plugins/karakeep/karakeep_save.dart';
import 'package:xta/plugins/karakeep/karakeep_title.dart';
import 'package:xta/plugins/deepmarks/deepmarks_save.dart';

/// Footer buttons should feel flat: no ripple and no pressed/hover background.
/// Material's default text button reserves a 64dp minimum width and 16dp of
/// horizontal padding. Seven of those never fit a phone's width, which is what
/// pushed the view count off the end of the strip.
/// Horizontal padding either side of a footer glyph.
const double kFooterButtonPadding = 6;

/// How tall a footer button is. The glyphs are small on purpose, but the thing
/// you press should not be: 44dp is a finger, 36dp was a guess.
const double kFooterButtonHeight = kTweetTouchTarget;

const footerButtonStyle = ButtonStyle(
  overlayColor: WidgetStatePropertyAll(Colors.transparent),
  splashFactory: NoSplash.splashFactory,
  padding: WidgetStatePropertyAll(EdgeInsets.symmetric(horizontal: kFooterButtonPadding)),
  minimumSize: WidgetStatePropertyAll(Size(kTweetTouchTarget, kFooterButtonHeight)),
  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
);

/// Gap between a count's glyph and its number, as X draws it.
const double kFooterIconLabelGap = 4;

/// Fixed cost of one count action: padding, the glyph and the gap before its
/// number. The action is never narrower than a 48dp touch target.
const double kFooterCountItemBase = kFooterButtonPadding * 2 + kTweetActionGlyphSize + kFooterIconLabelGap;

/// One icon-only action (bookmark, share).
const double kFooterIconItem = kTweetTouchTarget;

/// Gap between the counts group and the bookmark/share group.
const double kFooterGroupGap = 8;

/// Size the count labels are drawn at, and therefore measured at: a step
/// below the post text, like X's.
const double kFooterLabelFontSize = 13;

/// Width one count action takes with a number [labelWidth] wide.
double footerCountItemWidth(double labelWidth) => math.max(kTweetTouchTarget, kFooterCountItemBase + labelWidth);

/// What the footer can afford to show at the width it was given.
@immutable
class FooterFit {
  /// Whether the reply/repost/like counts are shown next to their glyphs.
  final bool showCounts;

  /// Whether the (non-interactive) view count is shown at all.
  final bool showViews;

  /// Set when even a bare row of glyphs does not fit, so the caller lets the
  /// actions wrap rather than shrinking their touch targets.
  final bool mustScaleDown;

  const FooterFit({required this.showCounts, required this.showViews, required this.mustScaleDown});
}

double _stripWidth(List<double> labelWidths, int iconButtons) =>
    labelWidths.fold<double>(0, (sum, width) => sum + footerCountItemWidth(width)) +
    iconButtons * kFooterIconItem +
    kFooterGroupGap;

/// Drops what costs least first: the view count is a read-only number, so it
/// goes before any label, and labels go before any action disappears.
///
/// [countLabelWidths] are the measured widths of the reply/repost/like labels
/// and [viewsLabelWidth] that of the view count, all at the ambient text scale.
FooterFit resolveFooterFit({
  required double available,
  required List<double> countLabelWidths,
  required double? viewsLabelWidth,
  required int iconButtons,
}) {
  final counts = countLabelWidths.length;

  if (viewsLabelWidth != null && _stripWidth([...countLabelWidths, viewsLabelWidth], iconButtons) <= available) {
    return const FooterFit(showCounts: true, showViews: true, mustScaleDown: false);
  }
  if (_stripWidth(countLabelWidths, iconButtons) <= available) {
    return const FooterFit(showCounts: true, showViews: false, mustScaleDown: false);
  }
  final bare = _stripWidth(List.filled(counts, 0), iconButtons);
  return FooterFit(showCounts: false, showViews: false, mustScaleDown: bare > available);
}

enum TranslationStatus { original, translating, translationFailed, translated }

/// The translate control, which lives at the post's top-right rather than in
/// the footer strip.
///
/// It is not an engagement action, and it was the seventh thing competing for a
/// phone's width down there — the row it left has room for the counts again.
class TweetTranslateButton extends StatelessWidget {
  final TranslationStatus status;
  final VoidCallback onTranslate;
  final VoidCallback onShowOriginal;
  final VoidCallback? onLongPress;

  const TweetTranslateButton({
    super.key,
    required this.status,
    required this.onTranslate,
    required this.onShowOriginal,
    this.onLongPress,
  });

  /// Sits right before the ⋯ menu, so its glyph is pushed towards it.
  static final EdgeInsetsDirectional _glyphPadding = tweetActionGlyphPadding(towardEnd: true, alignTop: true);

  @override
  Widget build(BuildContext context) {
    if (status == TranslationStatus.translating) {
      return SizedBox.square(
        dimension: kTweetTouchTarget,
        child: Padding(
          padding: _glyphPadding,
          child: const Center(child: SizedBox.square(dimension: 16, child: CircularProgressIndicator(strokeWidth: 2))),
        ),
      );
    }

    final theme = Theme.of(context);
    final (color, tooltip, onPressed) = switch (status) {
      TranslationStatus.translated => (
        tweetReadableAccentColor(context),
        L10n.of(context).action_show_original_post,
        onShowOriginal,
      ),
      TranslationStatus.translationFailed => (
        theme.colorScheme.error,
        L10n.of(context).action_translate_post,
        onTranslate,
      ),
      _ => (tweetFooterButtonsColorOf(context), L10n.of(context).action_translate_post, onTranslate),
    };

    return GestureDetector(
      onLongPress: onLongPress ?? onTranslate,
      child: tweetActionIconButton(
        context,
        icon: Icons.translate,
        color: color,
        onPressed: onPressed,
        tooltip: tooltip,
        style: tweetActionButtonStyle(_glyphPadding),
      ),
    );
  }
}

// Memoized footer action tint (HSL round-trip is too expensive per button per frame).
Color? _buttonsColorCache;
Color? _buttonsColorBase;

Color? tweetFooterButtonsColor(Color? base) {
  if (base == null) return null;
  if (base != _buttonsColorBase) {
    final hsl = HSLColor.fromColor(base);
    const lightnessFactorDark = 0.5;
    const lightnessFactorLight = 4.0;
    final adjustedLightness = (hsl.lightness * (hsl.lightness > 0.5 ? lightnessFactorDark : lightnessFactorLight))
        .clamp(0.0, 1.0);
    final adjustedSaturation = (hsl.saturation * 0.2).clamp(0.0, 1.0);
    _buttonsColorBase = base;
    _buttonsColorCache = hsl.withLightness(adjustedLightness).withSaturation(adjustedSaturation).toColor();
  }
  return _buttonsColorCache;
}

Color? tweetFooterButtonsColorOf(BuildContext context) => Theme.of(context).colorScheme.onSurfaceVariant;

/// A status is still shareable when X omits its author.
String? shareableTweetUrl(TweetWithCard tweet, String baseUrl) {
  final target = openablePost(tweet);
  if (target == null) return null;
  final handle = target.username?.trim();
  final authorPath = handle == null || handle.isEmpty ? 'i' : Uri.encodeComponent(handle);
  return '${baseUrl.replaceFirst(RegExp(r'/+$'), '')}/$authorPath/status/${Uri.encodeComponent(target.id)}';
}

/// Replace t.co redirectors with destinations so shares skip X click tracking.
String shareableTweetText(TweetWithCard tweet, String text, {bool clean = true}) {
  var result = text;
  for (Url url in tweet.entities?.urls ?? []) {
    final short = url.url;
    final expanded = url.expandedUrl;
    if (short != null && expanded != null) {
      result = result.replaceAll(short, clean ? cleanUrl(expanded) : expanded);
    }
  }
  for (Media media in tweet.extendedEntities?.media ?? tweet.entities?.media ?? []) {
    final short = media.url;
    final expanded = media.expandedUrl;
    if (short != null && expanded != null) {
      result = result.replaceAll(short, clean ? cleanUrl(expanded) : expanded);
    }
  }
  return result;
}

void maybeShowFolderHint(BuildContext context) {
  var prefs = PrefService.of(context, listen: false);
  if (prefs.get<bool>(optionSavedFolderHintShown) ?? false) {
    return;
  }
  prefs.set(optionSavedFolderHintShown, true);
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(L10n.of(context).long_press_folder_hint)));
}

void maybeShowLikeToast(BuildContext context) {
  var prefs = PrefService.of(context, listen: false);
  if (prefs.get<bool>(optionLikedFirstToastShown) ?? false) {
    return;
  }
  prefs.set(optionLikedFirstToastShown, true);
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text(L10n.of(context).likes_stay_on_device_notice), duration: const Duration(seconds: 6)),
  );
}

/// [tooltip] doubles as the button's accessibility label: an icon-only button
/// without one is announced as an unnamed "button", which is what a screen
/// reader used to get for every share, save and translate control in a feed.
Widget tweetFooterIconButton(
  BuildContext context,
  IconData icon, [
  Color? color,
  double? fill,
  VoidCallback? onPressed,
  String? tooltip,
]) => tweetActionIconButton(context, icon: icon, color: color, fill: fill, onPressed: onPressed, tooltip: tooltip);

/// An icon-only post action. [style] decides where the glyph sits in its 48dp
/// target; the footer's centred style is the default.
Widget tweetActionIconButton(
  BuildContext context, {
  required IconData icon,
  Color? color,
  double? fill,
  VoidCallback? onPressed,
  String? tooltip,
  ButtonStyle style = footerButtonStyle,
}) {
  final button = IconButton(
    icon: Icon(icon, fill: fill),
    color: color ?? Theme.of(context).colorScheme.primary,
    iconSize: kTweetActionGlyphSize,
    onPressed: onPressed,
    tooltip: tooltip,
    style: style,
  );

  // A tooltip triggers on long press by default, and that recogniser sits
  // *inside* the callers' GestureDetectors, so it won the gesture arena and
  // quietly ate "long press to file a post in a folder". Manual keeps the name
  // in the semantics tree — which is all the tooltip was added for — without
  // claiming the gesture.
  return TooltipTheme(
    data: const TooltipThemeData(triggerMode: TooltipTriggerMode.manual),
    child: button,
  );
}

Widget tweetFooterTextButton(
  IconData icon,
  String label, [
  Color? color,
  VoidCallback? onPressed,
  String? semanticLabel,
]) {
  final button = TextButton(
    onPressed: onPressed,
    style: footerButtonStyle,
    child: footerCountContent(Icon(icon, size: kTweetActionGlyphSize, color: color), label, color),
  );
  if (label.trim().isNotEmpty || semanticLabel == null) {
    return button;
  }
  return Semantics(
    button: true,
    enabled: onPressed != null,
    label: semanticLabel,
    child: ExcludeSemantics(child: button),
  );
}

/// A count action's glyph followed, when there is one, by its number.
Widget footerCountContent(Widget icon, String label, Color? color) => Row(
  mainAxisSize: MainAxisSize.min,
  children: [
    icon,
    if (label.isNotEmpty) ...[
      const SizedBox(width: kFooterIconLabelGap),
      Text(
        label,
        maxLines: 1,
        style: TextStyle(color: color, fontSize: kFooterLabelFontSize),
      ),
    ],
  ],
);

/// X's read-only view count: the bar-chart glyph and the shortened number,
/// announced as "77K Views". Not a button — there is nothing behind it to open.
class TweetViewsCount extends StatelessWidget {
  final int views;
  final String label;
  final Color? color;

  const TweetViewsCount({super.key, required this.views, required this.label, required this.color});

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: L10n.of(context).post_views_count(viewsPluralCount(views), label),
      excludeSemantics: true,
      child: ConstrainedBox(
        constraints: const BoxConstraints(minWidth: kTweetTouchTarget, minHeight: kFooterButtonHeight),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: kFooterButtonPadding),
          child: Center(
            widthFactor: 1,
            child: footerCountContent(Icon(Icons.bar_chart, size: kTweetActionGlyphSize, color: color), label, color),
          ),
        ),
      ),
    );
  }
}

/// Engagement / save / share strip under a tweet tile, laid out as X does:
/// reply, repost, like and views spread across, bookmark and share grouped at
/// the end. Translate and the ⋯ menu live in the post header.
///
/// XTA is a read-oriented frontend: these controls must not post to X.
/// Comment opens the conversation, quote opens quotes and retweeters,
/// heart/bookmark are local-only, share uses the OS sheet.
class TweetFooterBar extends StatelessWidget {
  final TweetWithCard tweet;
  final String tweetText;
  final String shareBaseUrl;
  final Locale locale;
  final String Function(num value) formatCount;
  final bool isArticle;

  /// Off on an opened post, which states its views in the line above instead.
  final bool showViews;
  final VoidCallback onOpenTweet;
  final Future<Uint8List?> Function() onCaptureImage;

  const TweetFooterBar({
    super.key,
    required this.tweet,
    required this.tweetText,
    required this.shareBaseUrl,
    required this.locale,
    required this.onOpenTweet,
    required this.onCaptureImage,
    this.formatCount = formatEngagementCount,
    this.isArticle = false,
    this.showViews = true,
  });

  /// Bookmark and share sit together at the end: bookmark's glyph is pushed
  /// towards share's, as X groups them.
  static final ButtonStyle _bookmarkStyle = tweetActionButtonStyle(tweetActionGlyphPadding(towardEnd: true));

  void _showShareSheet(BuildContext context) {
    final url = shareableTweetUrl(tweet, shareBaseUrl);
    ListTile createSheetButton(String title, IconData icon, VoidCallback? onTap) =>
        ListTile(enabled: onTap != null, onTap: onTap, leading: Icon(icon), title: Text(title));

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (sheetContext) {
        return SafeArea(
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (!isArticle)
                  createSheetButton(L10n.of(sheetContext).share_tweet_content, Icons.text_snippet, () async {
                    final clean = cleanLinksEnabled(PrefService.of(context, listen: false));
                    Share.share(shareableTweetText(tweet, tweetText, clean: clean));
                    Navigator.pop(sheetContext);
                  }),
                createSheetButton(
                  isArticle ? L10n.of(sheetContext).share_article_link : L10n.of(sheetContext).share_tweet_link,
                  Icons.link,
                  url == null
                      ? null
                      : () {
                          Share.share(url);
                          Navigator.pop(sheetContext);
                        },
                ),
                if (!isArticle)
                  createSheetButton(
                    L10n.of(sheetContext).share_tweet_content_and_link,
                    Icons.add_link,
                    url == null
                        ? null
                        : () {
                            final clean = cleanLinksEnabled(PrefService.of(context, listen: false));
                            Share.share('${shareableTweetText(tweet, tweetText, clean: clean)}\n\n$url');
                            Navigator.pop(sheetContext);
                          },
                  ),
                createSheetButton(
                  isArticle ? L10n.of(sheetContext).share_article_as_image : L10n.of(sheetContext).share_tweet_as_image,
                  Icons.screenshot,
                  () async {
                    final imgBytes = await onCaptureImage();
                    if (imgBytes != null) {
                      Share.shareXFiles([XFile.fromData(imgBytes, mimeType: 'image/png')]);
                    }
                    if (sheetContext.mounted) {
                      Navigator.pop(sheetContext);
                    }
                  },
                ),
                if (deepmarksEnabled(PrefService.of(sheetContext, listen: false)))
                  createSheetButton(
                    L10n.of(sheetContext).plugin_deepmarks_save_action,
                    Icons.bookmarks_outlined,
                    url == null
                        ? null
                        : () async {
                            Navigator.pop(sheetContext);
                            await saveToDeepmarks(context, url: url, title: karakeepTitleFor(tweet, tweetText));
                          },
                  ),
                if (karakeepEnabled(PrefService.of(sheetContext, listen: false)))
                  createSheetButton(
                    L10n.of(sheetContext).plugin_karakeep_save_action,
                    Icons.bookmark_add_outlined,
                    url == null
                        ? null
                        : () async {
                            Navigator.pop(sheetContext);
                            await saveToKarakeep(context, url: url, title: karakeepTitleFor(tweet, tweetText));
                          },
                  ),
                const Padding(padding: EdgeInsets.symmetric(horizontal: 16), child: Divider(thickness: 1.0)),
                createSheetButton(L10n.of(sheetContext).cancel, Icons.close, () => Navigator.pop(sheetContext)),
              ],
            ),
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final tweetId = openablePost(tweet)?.id;
    final prefs = PrefService.of(context, listen: false);
    final hideCounts = prefs.get(optionZenMode) == true || prefs.get(optionCalmMode) == true;
    final tint = tweetFooterButtonsColorOf(context);
    // Both stores are registered with a plain Provider, so a Consumer over them
    // would depend on a value whose identity never changes and never rebuild.
    // ScopedBuilder listens to the Store itself, which is what actually
    // changes — and it rebuilds only this button, not the whole tile.
    final likedModel = context.read<LikedTweetModel>();
    final savedModel = context.read<SavedTweetModel>();

    return Container(
      alignment: Alignment.center,
      // No end margin: the share glyph, centred in its target, then lines up
      // with the header's ⋯ above it.
      margin: isArticle ? EdgeInsets.zero : const EdgeInsetsDirectional.only(start: kTweetSpace2),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final replyLabel = hideCounts || tweet.replyCount == null ? '' : formatCount(tweet.replyCount!);
          // Missing either count used to hide the whole quotes control. Treat a
          // null as zero so the button still opens QuotesScreen.
          final repostTotal = (tweet.retweetCount ?? 0) + (tweet.quoteCount ?? 0);
          final repostLabel = hideCounts ? '' : formatCount(repostTotal);
          final likeLabel = hideCounts || tweet.favoriteCount == null ? '' : formatCount(tweet.favoriteCount!);
          // Like X, a post without views (or before X counted them) shows no
          // views item at all rather than a zero.
          final views = tweet.viewCount ?? 0;
          final viewsLabel = hideCounts || !showViews || views <= 0 ? null : formatCount(views);

          final measure = _LabelMeasure(context);
          final fit = resolveFooterFit(
            available: constraints.maxWidth,
            countLabelWidths: [measure.of(replyLabel), measure.of(repostLabel), measure.of(likeLabel)],
            viewsLabelWidth: viewsLabel == null ? null : measure.of(viewsLabel),
            // Bookmark and share. Translate and the ⋯ menu sit in the header.
            iconButtons: 2,
          );

          String label(String? value) => fit.showCounts ? (value ?? '') : '';

          void openQuotes() {
            if (tweetId == null) {
              return;
            }
            openQuotesAndRetweets(context, tweetId: tweetId);
          }

          final counts = <Widget>[
            GestureDetector(
              onLongPress: tweetId == null
                  ? null
                  : () {
                      try {
                        context.read<ZenRepliesState>().reveal();
                      } catch (_) {
                        onOpenTweet();
                      }
                    },
              child: tweetFooterTextButton(
                Icons.chat_bubble_outline,
                label(replyLabel),
                tint,
                tweetId == null ? null : onOpenTweet,
                L10n.of(context).open_post,
              ),
            ),
            GestureDetector(
              onLongPressStart: tweetId == null
                  ? null
                  : (details) =>
                        showQuoteActionMenu(context: context, tweet: tweet, globalPosition: details.globalPosition),
              child: tweetFooterTextButton(
                Icons.format_quote,
                label(repostLabel),
                tint,
                tweetId == null ? null : openQuotes,
                L10n.of(context).quotes,
              ),
            ),
            ScopedBuilder<LikedTweetModel, List<LikedTweet>>(
              store: likedModel,
              // Every footer on screen hears every like; only the one whose own
              // post changed has anything to redraw. Through the model's index —
              // a map lookup — not a scan of the whole liked list per footer per
              // emission, which is what this was.
              distinct: (_) => tweetId != null && likedModel.isLiked(tweetId),
              onState: (context, _) {
                final isLiked = tweetId != null && likedModel.isLiked(tweetId);

                return LikeButton(
                  isLiked: isLiked,
                  label: label(likeLabel),
                  color: isLiked ? tweetReadableAccentColor(context) : tint,
                  tooltip: isLiked ? L10n.of(context).unlike_on_this_device : L10n.of(context).like_on_this_device,
                  onPressed: tweetId == null
                      ? null
                      : () async {
                          if (isLiked) {
                            await likedModel.unlikeTweet(tweetId);
                          } else {
                            await likedModel.likeTweet(tweetId, tweet.user?.idStr, tweet.toJson());
                          }
                          if (!isLiked && context.mounted) {
                            maybeShowLikeToast(context);
                          }
                        },
                );
              },
            ),
            if (viewsLabel != null && fit.showViews) TweetViewsCount(views: views, label: viewsLabel, color: tint),
          ];
          final grouped = <Widget>[
            ScopedBuilder<SavedTweetModel, List<SavedTweet>>(
              store: savedModel,
              distinct: (_) => tweetId != null && savedModel.isSaved(tweetId),
              onState: (context, _) {
                final isSaved = tweetId != null && savedModel.isSaved(tweetId);
                final button = isSaved
                    ? tweetActionIconButton(
                        context,
                        icon: Icons.bookmark,
                        color: tweetReadableAccentColor(context),
                        fill: 1,
                        onPressed: () async {
                          await savedModel.deleteSavedTweet(tweetId);
                        },
                        tooltip: L10n.of(context).unsave_from_this_device,
                        style: _bookmarkStyle,
                      )
                    : tweetActionIconButton(
                        context,
                        icon: Icons.bookmark_border,
                        color: tint,
                        fill: 0,
                        style: _bookmarkStyle,
                        onPressed: tweetId == null
                            ? null
                            : () async {
                                // Goes wherever the reader last chose, when they have
                                // asked for that to be remembered; unfiled otherwise, as
                                // before. Routed through the shared save so a folder set
                                // to auto-download does so on a plain tap too --
                                // inserting the row here skipped that entirely.
                                await fileSavedTweet(
                                  context,
                                  tweetId: tweetId,
                                  userId: tweet.user?.idStr,
                                  content: tweet.toJson(),
                                  folderId: rememberedSaveFolder(PrefService.of(context, listen: false)),
                                );
                                if (context.mounted) {
                                  maybeShowFolderHint(context);
                                }
                              },
                        tooltip: L10n.of(context).save_on_this_device,
                      );

                return GestureDetector(
                  onLongPress: tweetId == null
                      ? null
                      : () async {
                          await showSaveToFolderSheet(
                            context,
                            tweetId: tweetId,
                            userId: tweet.user?.idStr,
                            content: tweet.toJson(),
                          );
                        },
                  child: button,
                );
              },
            ),
            tweetFooterIconButton(
              context,
              Icons.share,
              tint,
              null,
              () => _showShareSheet(context),
              L10n.of(context).action_share_post,
            ),
          ];

          // Last resort for a narrow window: preserve 48dp targets and let the
          // actions take a second line. Scaling the strip made the controls fit
          // visually while making every hit target too small.
          if (fit.mustScaleDown) {
            return Wrap(alignment: WrapAlignment.spaceEvenly, children: [...counts, ...grouped]);
          }

          return Row(
            children: [
              Expanded(
                child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: counts),
              ),
              const SizedBox(width: kFooterGroupGap),
              ...grouped,
            ],
          );
        },
      ),
    );
  }
}

/// Measures footer labels at the ambient text scale, so the fit decision uses
/// the width the label will actually occupy.
///
/// A whole feed only ever shows a few hundred distinct labels (compact counts
/// like "1.2K"), and every one of them used to be shaped again on every layout
/// of every footer, so the widths are memoized. The memo holds only what the
/// measurement depends on — the label, the scaled font size and the reading
/// direction — and is dropped whole when either of the latter two changes.
class _LabelMeasure {
  static final Map<String, double> _widths = {};
  static double? _memoFontSize;
  static ui.TextDirection? _memoDirection;

  /// Guards against a pathological feed growing the memo without bound; the
  /// realistic working set is far below this.
  static const int _maxEntries = 512;

  final TextScaler _scaler;
  final ui.TextDirection _direction;

  _LabelMeasure(BuildContext context)
    : _scaler = MediaQuery.textScalerOf(context),
      _direction = Directionality.of(context);

  double of(String label) {
    if (label.isEmpty) {
      return 0;
    }
    final fontSize = _scaler.scale(kFooterLabelFontSize);
    if (fontSize != _memoFontSize || _direction != _memoDirection || _widths.length > _maxEntries) {
      _widths.clear();
      _memoFontSize = fontSize;
      _memoDirection = _direction;
    }
    return _widths[label] ??= _measure(label);
  }

  double _measure(String label) {
    final painter = TextPainter(
      text: TextSpan(
        text: label,
        style: const TextStyle(fontSize: kFooterLabelFontSize),
      ),
      textScaler: _scaler,
      textDirection: _direction,
      maxLines: 1,
    )..layout();
    final width = painter.width;
    painter.dispose();
    return width;
  }
}
