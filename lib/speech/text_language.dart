/// Which language a piece of prose is written in, as a short code (`de`,
/// `en`, …), or null when it cannot tell.
///
/// Counting the commonest little words is crude, but it is all reading aloud
/// needs: an article's language decides which voice reads it, and a German
/// voice reading English (or the other way round) is what made Substack sound
/// broken for readers whose app language differs from what they read.
String? detectTextLanguage(String text, {int sample = 4000}) {
  final words = RegExp(r"[\p{L}']+", unicode: true)
      .allMatches(text.length > sample ? text.substring(0, sample) : text)
      .map((match) => match[0]!.toLowerCase())
      .toList(growable: false);
  if (words.length < 8) return null;

  final scores = {for (final entry in _stopWords.entries) entry.key: words.where(entry.value.contains).length};
  final ranked = scores.entries.toList()..sort((a, b) => b.value.compareTo(a.value));
  final best = ranked.first;
  final runnerUp = ranked[1].value;
  final enough = best.value >= 3 && best.value >= words.length * 0.08;
  return enough && best.value > runnerUp * 1.2 ? best.key : null;
}

/// The twenty or so commonest short words of each language reading aloud is
/// likely to meet.
final _stopWords = {for (final entry in _stopWordLists.entries) entry.key: entry.value.split(' ').toSet()};

const _stopWordLists = {
  'en':
      'the and of to is that it was for with this are but have not they '
      'you be on which',
  'de':
      'der die das und ist nicht mit ein eine zu sich auf auch den dem von '
      'für sie wir ich',
  'fr':
      'le la les et est des une du que pas pour dans qui sur avec nous '
      'vous il ce au',
  'es':
      'el los las y es que una del por para con no se su como pero más lo '
      'al está',
  'it':
      'il gli e è che una della per non sono con del di questo anche come '
      'ma nel alla più',
  'nl':
      'de het een en van is niet dat op zijn met voor ook maar wij ik je '
      'dit aan er',
  'pt':
      'o os as e é que uma do da não para com em por mais como mas se ao '
      'são',
};
