import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/substack/substack_html.dart';
import 'package:xta/plugins/substack/substack_reader_style.dart';

String _page({
  SubstackReaderFont font = SubstackReaderFont.serif,
  SubstackColumnWidth column = SubstackColumnWidth.medium,
}) => wrapSubstackHtml(
  title: 'T',
  body: '<p>x</p>',
  background: '#fff',
  foreground: '#000',
  muted: '#666',
  link: '#00f',
  isDark: false,
  font: font,
  column: column,
);

void main() {
  test('the defaults keep the look the reader had: Georgia in a 42rem column', () {
    final style = substackReaderStyle(PrefServiceCache());
    expect((style.font, style.column), (SubstackReaderFont.serif, SubstackColumnWidth.medium));
    expect(_page(), contains('font-family: Georgia,'));
    expect(_page(), contains('max-width: 42rem;'));
  });

  test('the chosen font and width reach the page; unknown values read as the defaults', () {
    final prefs = PrefServiceCache(
      defaults: {optionPluginSubstackReaderFont: 'sans', optionPluginSubstackColumnWidth: 'wide'},
    );
    final style = substackReaderStyle(prefs);
    final page = _page(font: style.font, column: style.column);
    expect(page, contains('body {\n    font-family: -apple-system,'));
    expect(page, contains('max-width: 52rem;'));

    final odd = substackReaderStyle(
      PrefServiceCache(defaults: {optionPluginSubstackReaderFont: 'comic', optionPluginSubstackColumnWidth: 'huge'}),
    );
    expect((odd.font, odd.column), (SubstackReaderFont.serif, SubstackColumnWidth.medium));
  });
}
