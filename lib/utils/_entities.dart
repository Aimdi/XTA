import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:xta/utils/urls.dart';

/// What an entity needs to become a span: the link colour of the theme it will
/// be painted in, and a recognizer factory that registers what it hands out so
/// the owner of the spans can dispose them. TapGestureRecognizers are not
/// garbage-collected resources — every one handed to a TextSpan must be
/// disposed by whoever keeps the span.
class EntitySpanContext {
  final Color linkColor;
  final GestureRecognizer Function(VoidCallback onTap) recognizer;

  const EntitySpanContext({required this.linkColor, required this.recognizer});

  TextStyle get linkStyle => TextStyle(color: linkColor);
}

/// One tappable region of a post's text, positioned by X's rune indices.
abstract class Entity {
  final List<int>? indices;

  const Entity(this.indices);

  InlineSpan getContent(EntitySpanContext context);

  int getEntityStart() => indices![0];

  int getEntityEnd() => indices![1];
}

class HashtagEntity extends Entity {
  final Hashtag hashtag;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  HashtagEntity(this.hashtag, this.onTap, this.onLongPress)
    : super(hashtag.indices);

  @override
  InlineSpan getContent(EntitySpanContext context) {
    return WidgetSpan(
      alignment: PlaceholderAlignment.baseline,
      baseline: TextBaseline.alphabetic,
      child: GestureDetector(
        onTap: onTap,
        onLongPress: onLongPress,
        child: Text('#${hashtag.text}', style: context.linkStyle),
      ),
    );
  }
}

/// A cashtag — `\$AAPL` — which X sends as a symbol entity.
///
/// Takes the ticker text rather than the API's own symbol class: that class is
/// named `Symbol`, which `dart:core` already uses.
class SymbolEntity extends Entity {
  final String text;
  final VoidCallback onTap;
  final VoidCallback? onLongPress;

  SymbolEntity({
    required this.text,
    required List<int>? indices,
    required this.onTap,
    this.onLongPress,
  }) : super(indices);

  @override
  InlineSpan getContent(EntitySpanContext context) {
    return WidgetSpan(
      alignment: PlaceholderAlignment.baseline,
      baseline: TextBaseline.alphabetic,
      child: GestureDetector(
        onTap: onTap,
        onLongPress: onLongPress,
        child: Text('\$$text', style: context.linkStyle),
      ),
    );
  }
}

class UserMentionEntity extends Entity {
  final UserMention mention;
  final VoidCallback onTap;

  UserMentionEntity(this.mention, this.onTap) : super(mention.indices);

  @override
  InlineSpan getContent(EntitySpanContext context) {
    return TextSpan(
      text: '@${mention.screenName}',
      style: context.linkStyle,
      recognizer: context.recognizer(onTap),
    );
  }
}

class UrlEntity extends Entity {
  final Url url;
  final VoidCallback onTap;

  UrlEntity(this.url, this.onTap) : super(url.indices);

  @override
  InlineSpan getContent(EntitySpanContext context) {
    // An article or broadcast link is shown as its own block under the text,
    // so leaving the URL in the text too would say the same thing twice.
    if (articleIdIn(url.expandedUrl) != null ||
        broadcastIdIn(url.expandedUrl) != null) {
      return const TextSpan(text: '');
    }

    return TextSpan(
      text: url.displayUrl,
      style: context.linkStyle,
      recognizer: context.recognizer(onTap),
    );
  }
}

/// Media is rendered as its own block under the text; its entity only marks
/// where the t.co link sat so the text can drop it.
class MediaEntity extends Entity {
  final Media media;

  MediaEntity(this.media) : super(media.indices);

  @override
  InlineSpan getContent(EntitySpanContext context) => const TextSpan(text: '');
}
