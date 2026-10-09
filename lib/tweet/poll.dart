/// A poll, read out of a card's binding values.
///
/// Kept apart from the widget so the parsing can be tested on its own, and so
/// a reshaped payload fails by rendering nothing rather than by throwing in the
/// middle of a timeline.
library;

class PollChoice {
  final String label;
  final double count;

  /// This option's share of the vote, 0..1. Zero when nobody has voted yet.
  final double share;

  const PollChoice({required this.label, required this.count, required this.share});
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
      choices: [
        for (var i = 0; i < labels.length; i++)
          PollChoice(label: labels[i], count: counts[i], share: total == 0 ? 0 : counts[i] / total)
      ],
      total: total,
      endsAt: endsAtRaw == null ? null : DateTime.tryParse(endsAtRaw),
    );
  }
}
