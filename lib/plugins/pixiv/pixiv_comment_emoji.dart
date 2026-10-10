import 'package:flutter/material.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';

/// Pixiv's comment emoji, written in a comment as `(name)`, in the five sets
/// Pixiv numbers them in: the n-th name of set s is emoji s × 100 + n.
const _emojiSets = <List<String>>[
  ['normal', 'surprise', 'serious', 'heaven', 'happy', 'excited', 'sing', 'cry'],
  ['normal2', 'shame2', 'love2', 'interesting2', 'blush2', 'fire2', 'angry2', 'shine2', 'panic2'],
  ['normal3', 'satisfaction3', 'surprise3', 'smile3', 'shock3', 'gaze3', 'wink3', 'happy3', 'excited3', 'love3'],
  ['normal4', 'surprise4', 'serious4', 'love4', 'shine4', 'sweat4', 'shame4', 'sleep4'],
  ['heart', 'teardrop', 'star'],
];

/// Every emoji name with the number Pixiv serves its image under.
final Map<String, int> pixivEmojiIds = {
  for (final (set, names) in _emojiSets.indexed)
    for (final (index, name) in names.indexed) name: (set + 1) * 100 + index + 1,
};

/// Where Pixiv's own site loads emoji [id] from, so no image ships with XTA.
String pixivEmojiUrl(int id) => 'https://s.pximg.net/common/images/emoji/$id.png';

/// A run of a comment: plain text or one emoji.
sealed class PixivCommentPart {
  const PixivCommentPart();
}

@immutable
final class PixivCommentTextPart extends PixivCommentPart {
  final String text;

  const PixivCommentTextPart(this.text);

  @override
  bool operator ==(Object other) => other is PixivCommentTextPart && other.text == text;

  @override
  int get hashCode => text.hashCode;
}

@immutable
final class PixivCommentEmojiPart extends PixivCommentPart {
  final String name;
  final int id;

  const PixivCommentEmojiPart(this.name, this.id);

  String get url => pixivEmojiUrl(id);

  /// The code as the commenter typed it.
  String get code => '($name)';

  @override
  bool operator ==(Object other) => other is PixivCommentEmojiPart && other.name == name && other.id == id;

  @override
  int get hashCode => Object.hash(name, id);
}

final _emojiCode = RegExp(r'\(([a-z0-9]+)\)');

/// Splits [text] into text and emoji runs. A `(name)` Pixiv does not know
/// stays text, as do parentheses around ordinary words.
List<PixivCommentPart> pixivCommentParts(String text) {
  final known = [
    for (final match in _emojiCode.allMatches(text))
      if (pixivEmojiIds[match.group(1)] case final id?)
        (match: match, emoji: PixivCommentEmojiPart(match.group(1)!, id)),
  ];
  final parts = <PixivCommentPart>[];
  var plainFrom = 0;
  for (final (:match, :emoji) in known) {
    if (match.start > plainFrom) parts.add(PixivCommentTextPart(text.substring(plainFrom, match.start)));
    parts.add(emoji);
    plainFrom = match.end;
  }
  if (plainFrom < text.length) parts.add(PixivCommentTextPart(text.substring(plainFrom)));
  return parts;
}

/// The side of an inline emoji; the text scale grows it with the words around it.
const pixivEmojiSize = 20.0;

/// Comment text with Pixiv's emoji drawn inline.
class PixivCommentText extends StatelessWidget {
  final String text;
  final TextStyle? style;

  const PixivCommentText({super.key, required this.text, this.style});

  @override
  Widget build(BuildContext context) {
    final pixels = (pixivEmojiSize * MediaQuery.devicePixelRatioOf(context)).ceil();
    return Text.rich(
      TextSpan(
        children: [
          for (final part in pixivCommentParts(text))
            switch (part) {
              PixivCommentTextPart(:final text) => TextSpan(text: text),
              PixivCommentEmojiPart() => _emoji(part, pixels),
            },
        ],
      ),
      style: style,
    );
  }

  InlineSpan _emoji(PixivCommentEmojiPart emoji, int pixels) => WidgetSpan(
    alignment: PlaceholderAlignment.middle,
    child: Semantics(
      label: emoji.code,
      image: true,
      child: SizedBox.square(
        dimension: pixivEmojiSize,
        child: PixivNetworkImage(url: emoji.url, fit: BoxFit.contain, cacheWidth: pixels, cacheHeight: pixels),
      ),
    ),
  );
}
