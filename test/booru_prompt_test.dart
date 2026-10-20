import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_prompt.dart';

void main() {
  test('kinds in order, meta left out, underscores as spaces, parentheses escaped, emoticons kept', () {
    final prompt = booruPrompt(
      ['highres', '1girl', 'kantoku', 'cat_(animal)', 'hatsune_miku', '^_^', 'vocaloid'],
      {
        'hatsune_miku': BooruTagCategory.character,
        'vocaloid': BooruTagCategory.copyright,
        'kantoku': BooruTagCategory.artist,
        'highres': BooruTagCategory.meta,
      },
      booruPromptDefaultOrder,
    );

    expect(prompt, r'hatsune miku, vocaloid, kantoku, 1girl, cat \(animal\), ^_^');
  });

  test('the stored order reads back, kinds it does not name follow it, and it is stored the same way', () {
    final kinds = booruPromptKinds('-artist,general,character');

    expect(
      [for (final kind in kinds) kind.category],
      const [
        BooruTagCategory.artist,
        BooruTagCategory.general,
        BooruTagCategory.character,
        BooruTagCategory.copyright,
        BooruTagCategory.species,
        BooruTagCategory.meta,
      ],
    );
    expect([for (final kind in kinds) kind.included], const [false, true, true, true, true, false]);
    expect(booruPromptStored(kinds), '-artist,general,character,copyright,species,-meta');
  });

  test('with nothing stored, every kind but meta is kept, in the default order', () {
    expect(booruPromptOrder(PrefServiceCache()), booruPromptDefaultOrder);
    final prefs = PrefServiceCache(cache: {optionPluginBooruPromptKinds: 'general,-character'});
    expect(booruPromptOrder(prefs), const [
      BooruTagCategory.general,
      BooruTagCategory.artist,
      BooruTagCategory.copyright,
      BooruTagCategory.species,
    ]);
  });
}
