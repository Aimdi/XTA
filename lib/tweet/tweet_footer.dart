import 'dart:typed_data';
// intl also exports a TextDirection, so the painting one is qualified.
import 'dart:ui' as ui;

import 'package:dart_twitter_api/twitter_api.dart' show Media, Url;
import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
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
import 'package:xta/tweet/quotes_screen.dart';
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
const double kFooterButtonHeight = 44;

const footerButtonStyle = ButtonStyle(
  overlayColor: WidgetStatePropertyAll(Colors.transparent),
  splashFactory: NoSplash.splashFactory,
  padding: WidgetStatePropertyAll(
    EdgeInsets.symmetric(horizontal: kFooterButtonPadding),
  ),
  minimumSize: WidgetStatePropertyAll(Size(0, kFooterButtonHeight)),
  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
  visualDensity: VisualDensity.compact,
);

/// Fixed cost of one count action: padding, the 20dp glyph and the gap Material
/// puts between an icon and its label.
const double kFooterCountItemBase =
    kFooterButtonPadding + 20 + 8 + kFooterButtonPadding;

/// One icon-only action (bookmark, share).
const double kFooterIconItem =
    kFooterButtonPadding + 20 + kFooterButtonPadding + 8;

/// Gap between the counts group and the icon group.
const double kFooterGroupGap = 8;

/// Size the count labels are drawn at, and therefore measured at.
const double kFooterLabelFontSize = 14;

/// What the footer can afford to show at the width it was given.
@immutable
class FooterFit {
  /// Whether the reply/repost/like counts are shown next to their glyphs.
  final bool showCounts;

  /// Whether the (non-interactive) view count is shown at all.
  final bool showViews;

  /// Set when even a bare row of glyphs does not fit, so the caller scales the
  /// strip down instead of letting it clip.
  final bool mustScaleDown;

  const FooterFit({
    required this.showCounts,
    required this.showViews,
    required this.mustScaleDown,
  });
}

double _stripWidth(List<double> labelWidths, int iconButtons) =>
    labelWidths.fold<double>(
      0,
      (sum, width) => sum + kFooterCountItemBase + width,
    ) +
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

  if (viewsLabelWidth != null &&
      _stripWidth([...countLabelWidths, viewsLabelWidth], iconButtons) <=
          available) {
    return const FooterFit(
      showCounts: true,
      showViews: true,
      mustScaleDown: false,
    );
  }
  if (_stripWidth(countLabelWidths, iconButtons) <= available) {
    return const FooterFit(
      showCounts: true,
      showViews: false,
      mustScaleDown: false,
    );
  }
  final bare = _stripWidth(List.filled(counts, 0), iconButtons);
  return FooterFit(
    showCounts: false,
    showViews: false,
    mustScaleDown: bare > available,
  );
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

  @override
  Widget build(BuildContext context) {
    if (status == TranslationStatus.translating) {
      return const Padding(
        padding: EdgeInsets.all(8),
        child: SizedBox(
          width: 16,
          height: 16,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
      );
    }

    final theme = Theme.of(context);
    final (color, tooltip, onPressed) = switch (status) {
      TranslationStatus.translated => (
        theme.colorScheme.primary,
        L10n.of(context).action_show_original_post,
        onShowOriginal,
      ),
      TranslationStatus.translationFailed => (
        Colors.red.harmonizeWith(theme.colorScheme.primary),
        L10n.of(context).action_translate_post,
        onTranslate,
      ),
      _ => (
        tweetFooterButtonsColorOf(context),
        L10n.of(context).action_translate_post,
        onTranslate,
      ),
    };

    return GestureDetector(
      onLongPress: onLongPress ?? onTranslate,
      child: tweetFooterIconButton(
        context,
        Icons.translate,
        color,
        null,
        onPressed,
        tooltip,
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
    final adjustedLightness =
        (hsl.lightness *
                (hsl.lightness > 0.5
                    ? lightnessFactorDark
                    : lightnessFactorLight))
            .clamp(0.0, 1.0);
    final adjustedSaturation = (hsl.saturation * 0.2).clamp(0.0, 1.0);
    _buttonsColorBase = base;
    _buttonsColorCache = hsl
        .withLightness(adjustedLightness)
        .withSaturation(adjustedSaturation)
        .toColor();
  }
  return _buttonsColorCache;
}

Color? tweetFooterButtonsColorOf(BuildContext context) =>
    Theme.of(context).colorScheme.onSurfaceVariant;

/// Replace t.co redirectors with cleaned destinations so shares skip X click tracking.
String shareableTweetText(TweetWithCard tweet, String text) {
  var result = text;
  for (Url url in tweet.entities?.urls ?? []) {
    final short = url.url;
    final expanded = url.expandedUrl;
    if (short != null && expanded != null) {
      result = result.replaceAll(short, cleanUrl(expanded));
    }
  }
  for (Media media
      in tweet.extendedEntities?.media ?? tweet.entities?.media ?? []) {
    final short = media.url;
    final expanded = media.expandedUrl;
    if (short != null && expanded != null) {
      result = result.replaceAll(short, cleanUrl(expanded));
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
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text(L10n.of(context).long_press_folder_hint)),
  );
}

void maybeShowLikeToast(BuildContext context) {
  var prefs = PrefService.of(context, listen: false);
  if (prefs.get<bool>(optionLikedFirstToastShown) ?? false) {
    return;
  }
  prefs.set(optionLikedFirstToastShown, true);
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(
      content: Text(L10n.of(context).likes_stay_on_device_notice),
      duration: const Duration(seconds: 6),
    ),
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
]) {
  final button = IconButton(
    icon: Icon(icon, fill: fill),
    color: color ?? Theme.of(context).colorScheme.primary,
    iconSize: 20,
    onPressed: onPressed,
    tooltip: tooltip,
    style: footerButtonStyle,
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

TextButton tweetFooterTextButton(
  IconData icon,
  String label, [
  Color? color,
  VoidCallback? onPressed,
]) {
  return TextButton.icon(
    icon: Icon(icon, size: 20, color: color),
    onPressed: onPressed,
    label: Text(
      label,
      style: TextStyle(color: color, fontSize: kFooterLabelFontSize),
    ),
    style: footerButtonStyle,
  );
}

/// Engagement / save / share / translate strip under a tweet tile.
///
/// XTA is a read-oriented frontend: these controls must not post to X.
/// Comment opens the conversation, quote opens quotes and retweeters,
/// heart/bookmark are local-only, share uses the OS sheet, translate works
/// on loaded text.
class TweetFooterBar extends StatelessWidget {
  final TweetWithCard tweet;
  final String tweetText;
  final String shareBaseUrl;
  final Locale locale;
  final NumberFormat numberFormat;
  final bool isArticle;
  final VoidCallback onOpenTweet;
  final Future<Uint8List?> Function() onCaptureImage;

  const TweetFooterBar({
    super.key,
    required this.tweet,
    required this.tweetText,
    required this.shareBaseUrl,
    required this.locale,
    required this.numberFormat,
    required this.onOpenTweet,
    required this.onCaptureImage,
    this.isArticle = false,
  });

  void _showShareSheet(BuildContext context) {
    ListTile createSheetButton(
      String title,
      IconData icon,
      VoidCallback onTap,
    ) => ListTile(onTap: onTap, leading: Icon(icon), title: Text(title));

    showModalBottomSheet(
      context: context,
      builder: (sheetContext) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (!isArticle)
                createSheetButton(
                  L10n.of(sheetContext).share_tweet_content,
                  Icons.text_snippet,
                  () async {
                    Share.share(shareableTweetText(tweet, tweetText));
                    Navigator.pop(sheetContext);
                  },
                ),
              createSheetButton(
                isArticle
                    ? L10n.of(sheetContext).share_article_link
                    : L10n.of(sheetContext).share_tweet_link,
                Icons.link,
                () async {
                  Share.share(
                    '$shareBaseUrl/${tweet.user!.screenName}/status/${tweet.idStr}',
                  );
                  Navigator.pop(sheetContext);
                },
              ),
              if (!isArticle)
                createSheetButton(
                  L10n.of(sheetContext).share_tweet_content_and_link,
                  Icons.add_link,
                  () async {
                    Share.share(
                      '${shareableTweetText(tweet, tweetText)}\n\n$shareBaseUrl/${tweet.user!.screenName}/status/${tweet.idStr}',
                    );
                    Navigator.pop(sheetContext);
                  },
                ),
              createSheetButton(
                isArticle
                    ? L10n.of(sheetContext).share_article_as_image
                    : L10n.of(sheetContext).share_tweet_as_image,
                Icons.screenshot,
                () async {
                  final imgBytes = await onCaptureImage();
                  if (imgBytes != null) {
                    Share.shareXFiles([
                      XFile.fromData(imgBytes, mimeType: 'image/png'),
                    ]);
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
                  () async {
                    final url =
                        '$shareBaseUrl/${tweet.user!.screenName}/status/${tweet.idStr}';
                    Navigator.pop(sheetContext);
                    await saveToDeepmarks(
                      context,
                      url: url,
                      title: karakeepTitleFor(tweet, tweetText),
                    );
                  },
                ),
              if (karakeepEnabled(PrefService.of(sheetContext, listen: false)))
                createSheetButton(
                  L10n.of(sheetContext).plugin_karakeep_save_action,
                  Icons.bookmark_add_outlined,
                  () async {
                    final url =
                        '$shareBaseUrl/${tweet.user!.screenName}/status/${tweet.idStr}';
                    Navigator.pop(sheetContext);
                    await saveToKarakeep(
                      context,
                      url: url,
                      title: karakeepTitleFor(tweet, tweetText),
                    );
                  },
                ),
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 16),
                child: Divider(thickness: 1.0),
              ),
              createSheetButton(
                L10n.of(sheetContext).cancel,
                Icons.close,
                () => Navigator.pop(sheetContext),
              ),
            ],
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final prefs = PrefService.of(context, listen: false);
    final hideCounts =
        prefs.get(optionZenMode) == true || prefs.get(optionCalmMode) == true;
    final tint = tweetFooterButtonsColorOf(context);
    // Both stores are registered with a plain Provider, so a Consumer over them
    // would depend on a value whose identity never changes and never rebuild.
    // ScopedBuilder listens to the Store itself, which is what actually
    // changes — and it rebuilds only this button, not the whole tile.
    final likedModel = context.read<LikedTweetModel>();
    final savedModel = context.read<SavedTweetModel>();

    return Container(
      alignment: Alignment.center,
      margin: isArticle
          ? EdgeInsets.zero
          : const EdgeInsets.symmetric(horizontal: 8),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final replyLabel = hideCounts || tweet.replyCount == null
              ? ''
              : numberFormat.format(tweet.replyCount);
          // Missing either count used to hide the whole quotes control. Treat a
          // null as zero so the button still opens QuotesScreen.
          final repostTotal =
              (tweet.retweetCount ?? 0) + (tweet.quoteCount ?? 0);
          final repostLabel = hideCounts
              ? ''
              : numberFormat.format(repostTotal);
          final likeLabel = hideCounts || tweet.favoriteCount == null
              ? ''
              : numberFormat.format(tweet.favoriteCount);
          // View counts are vanity on a reader; keep the fit helper for tests
          // but do not spend strip width on them here.
          const String? viewsLabel = null;

          final measure = _LabelMeasure(context);
          final fit = resolveFooterFit(
            available: constraints.maxWidth,
            countLabelWidths: [
              measure.of(replyLabel),
              measure.of(repostLabel),
              measure.of(likeLabel),
            ],
            viewsLabelWidth: viewsLabel == null ? null : measure.of(viewsLabel),
            // Bookmark and share. Translate used to make a third here, and now
            // sits in the post header instead.
            iconButtons: 2,
          );

          String label(String? value) => fit.showCounts ? (value ?? '') : '';

          void openQuotes() {
            Navigator.pushNamed(
              context,
              routeQuotes,
              arguments: QuotesScreenArguments(id: tweet.idStr!),
            );
          }

          final actions = <Widget>[
            GestureDetector(
              onLongPress: () {
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
                onOpenTweet,
              ),
            ),
            GestureDetector(
              onLongPress: tweet.idStr == null ? null : openQuotes,
              child: tweetFooterTextButton(
                Icons.format_quote,
                label(repostLabel),
                (tweet.quoteCount ?? 0) > 0
                    ? Colors.green.harmonizeWith(
                        Theme.of(context).colorScheme.primary,
                      )
                    : tint,
                tweet.idStr == null ? null : openQuotes,
              ),
            ),
            ScopedBuilder<LikedTweetModel, List<LikedTweet>>(
              store: likedModel,
              // Every footer on screen hears every like; only the one whose own
              // post changed has anything to redraw. Through the model's index —
              // a map lookup — not a scan of the whole liked list per footer per
              // emission, which is what this was.
              distinct: (_) =>
                  tweet.idStr != null && likedModel.isLiked(tweet.idStr!),
              onState: (context, _) {
                final isLiked =
                    tweet.idStr != null && likedModel.isLiked(tweet.idStr!);

                return LikeButton(
                  isLiked: isLiked,
                  label: label(likeLabel),
                  color: isLiked ? Theme.of(context).colorScheme.primary : tint,
                  tooltip: isLiked
                      ? L10n.of(context).unlike_on_this_device
                      : L10n.of(context).like_on_this_device,
                  onPressed: () async {
                    if (isLiked) {
                      await likedModel.unlikeTweet(tweet.idStr!);
                    } else {
                      await likedModel.likeTweet(
                        tweet.idStr!,
                        tweet.user?.idStr,
                        tweet.toJson(),
                      );
                    }
                    if (!isLiked && context.mounted) {
                      maybeShowLikeToast(context);
                    }
                  },
                );
              },
            ),
            if (viewsLabel != null && fit.showViews)
              tweetFooterTextButton(Icons.bar_chart, viewsLabel, tint),
            ScopedBuilder<SavedTweetModel, List<SavedTweet>>(
              store: savedModel,
              distinct: (_) =>
                  tweet.idStr != null && savedModel.isSaved(tweet.idStr!),
              onState: (context, _) {
                final isSaved =
                    tweet.idStr != null && savedModel.isSaved(tweet.idStr!);
                final button = isSaved
                    ? tweetFooterIconButton(
                        context,
                        Icons.bookmark,
                        Theme.of(context).colorScheme.primary,
                        1,
                        () async {
                          await savedModel.deleteSavedTweet(tweet.idStr!);
                        },
                        L10n.of(context).unsave_from_this_device,
                      )
                    : tweetFooterIconButton(
                        context,
                        Icons.bookmark_border,
                        tint,
                        0,
                        () async {
                          // Goes wherever the reader last chose, when they have
                          // asked for that to be remembered; unfiled otherwise, as
                          // before. Routed through the shared save so a folder set
                          // to auto-download does so on a plain tap too --
                          // inserting the row here skipped that entirely.
                          await fileSavedTweet(
                            context,
                            tweetId: tweet.idStr!,
                            userId: tweet.user?.idStr,
                            content: tweet.toJson(),
                            folderId: rememberedSaveFolder(
                              PrefService.of(context, listen: false),
                            ),
                          );
                          if (context.mounted) {
                            maybeShowFolderHint(context);
                          }
                        },
                        L10n.of(context).save_on_this_device,
                      );

                return GestureDetector(
                  onLongPress: () async {
                    await showSaveToFolderSheet(
                      context,
                      tweetId: tweet.idStr!,
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

          // Last resort for extreme text scales: shrink the whole strip rather
          // than clip a digit off the end of it.
          if (fit.mustScaleDown) {
            return FittedBox(
              fit: BoxFit.scaleDown,
              child: Row(mainAxisSize: MainAxisSize.min, children: actions),
            );
          }

          return Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: actions,
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
    if (fontSize != _memoFontSize ||
        _direction != _memoDirection ||
        _widths.length > _maxEntries) {
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
