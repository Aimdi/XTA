/// A poll, read out of a card's binding values.
///
/// Kept apart from the widget so the parsing can be tested on its own, and so
/// a reshaped payload fails by rendering nothing rather than by throwing in the
/// middle of a timeline.
library;

import 'package:xta/utils/json.dart';

class PollChoice {
  final String label;
  final double count;

  /// This option's share of the vote, 0..1. Zero when nobody has voted yet.
  final double share;

  /// The option's image at the size its bar shows it, and at full size. Both
  /// null for an option without one.
  final String? thumbnailUrl;
  final String? imageUrl;

  const PollChoice({required this.label, required this.count, required this.share, this.thumbnailUrl, this.imageUrl});
}

class TweetPoll {
  final List<PollChoice> choices;
  final double total;
  final DateTime? endsAt;

  const TweetPoll({required this.choices, required this.total, required this.endsAt});

  /// The highest vote count, so the winning bar can be picked out. Zero while
  /// the poll has no votes, which matches no bar.
  double get leadingCount => choices.fold<double>(0, (max, c) => c.count > max ? c.count : max);

  /// Whether [choice] has the most votes. Nothing leads a poll nobody voted in.
  bool leads(PollChoice choice) => leadingCount > 0 && choice.count == leadingCount;

  /// The options that carry an image, in order: what the viewer swipes through.
  List<PollChoice> get choicesWithImages => [
    for (final choice in choices)
      if (choice.imageUrl != null) choice,
  ];

  /// How many options a poll with images has. Its card says so in a binding
  /// rather than in its name, as the text polls do.
  static int? choiceCountOf(Map<String, dynamic> card) {
    final count = Json(card)['binding_values']['choice_count']['string_value'].integer;
    return count != null && count > 0 ? count : null;
  }

  static TweetPoll? fromCard(Map<String, dynamic> card, int numberOfChoices) {
    final values = card['binding_values'];
    if (values is! Map) {
      return null;
    }

    String? stringAt(String key) {
      final value = values[key];
      return value is Map ? value['string_value'] as String? : null;
    }

    final labels = <String>[];
    final counts = <double>[];
    for (var i = 1; i <= numberOfChoices; i++) {
      final label = stringAt('choice${i}_label');
      if (label == null) {
        return null;
      }
      labels.add(label);
      counts.add(double.tryParse(stringAt('choice${i}_count') ?? '') ?? 0);
    }

    final total = counts.fold<double>(0, (sum, count) => sum + count);
    final endsAtRaw = stringAt('end_datetime_utc');

    return TweetPoll(
      choices: [for (var i = 0; i < labels.length; i++) _choice(values, i + 1, labels[i], counts[i], total)],
      total: total,
      endsAt: endsAtRaw == null ? null : DateTime.tryParse(endsAtRaw),
    );
  }

  /// Option [choice] (counted from 1) with its share of [total] and its image.
  /// Of the image's sizes, each stands in for the other when X sends only one.
  static PollChoice _choice(Map values, int choice, String label, double count, double total) {
    String? image(String size) => Json(values)['choice${choice}_image$size']['image_value']['url'].string;
    final thumbnail = image('');
    final original = image('_original');
    return PollChoice(
      label: label,
      count: count,
      share: total == 0 ? 0 : count / total,
      thumbnailUrl: thumbnail ?? original,
      imageUrl: original ?? thumbnail,
    );
  }
}
