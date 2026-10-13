/// Substack-shaped inputs for the performance harnesses, built rather than
/// recorded.
///
/// The markup uses the node names Substack's editor emits (captioned images,
/// footnotes, block quotes), so the reader takes the same code paths it takes
/// for a real post; the words are filler. Baseline numbers come from a recorded
/// or live post — these exist so the harness itself runs anywhere.
library;

const _words = [
  'the',
  'reader',
  'quiet',
  'river',
  'morning',
  'letter',
  'harbour',
  'lantern',
  'season',
  'garden', //
  'distance',
  'history',
  'evidence',
  'question',
  'answer',
  'window',
  'market',
  'village',
  'engine',
  'pattern',
  'measure',
  'promise',
  'shadow',
  'signal',
  'summer',
  'winter',
  'paper',
  'bridge',
  'mountain',
  'theory',
  'and',
  'of',
  'to',
  'in',
  'that',
  'with',
  'for',
  'was',
  'on',
  'is',
  'as',
  'by',
  'at',
  'from',
  'but',
  'not',
];

String syntheticWords(int count, int seed) =>
    List.generate(count, (i) => _words[(seed * 31 + i * 7) % _words.length]).join(' ');

String syntheticImageUrl(int index) =>
    'https://substackcdn.com/image/fetch/w_1456,c_limit,f_auto,q_auto:good,fl_progressive:steep/'
    'https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2Fxta-perf-${index}_1456x816.jpeg';

String syntheticImage(int index, String url) =>
    '<div class="captioned-image-container"><figure><a class="image-link image2 is-viewable-img" '
    'target="_blank" href="$url"><div class="image2-inset"><picture><source type="image/webp" '
    'srcset="$url 424w, $url 848w, $url 1456w" sizes="100vw"><img src="$url" width="1456" height="816" '
    'class="sizing-normal" alt="" srcset="$url 424w, $url 848w, $url 1456w" sizes="100vw" loading="lazy">'
    '</picture><div class="image-link-expand"><button type="button" class="restack-image"><svg></svg></button>'
    '</div></div></a><figcaption class="image-caption">Figure ${index + 1}. ${syntheticWords(12, index)}'
    '</figcaption></figure></div>';

String _footnoteAnchor(int n) =>
    '<a class="footnote-anchor" data-component-name="FootnoteAnchorToDOM" id="footnote-anchor-$n" '
    'href="#footnote-$n" target="_self">$n</a>';

String _footnote(int n) =>
    '<div class="footnote" data-component-name="FootnoteToDOM"><a id="footnote-$n" href="#footnote-anchor-$n" '
    'class="footnote-number" target="_self">$n</a><div class="footnote-content"><p>${syntheticWords(30, n)}</p>'
    '</div></div>';

String _paragraph(int seed, int words, {int? footnote}) =>
    '<p>${syntheticWords(words, seed)}${footnote == null ? '' : _footnoteAnchor(footnote)}.</p>';

/// About [sections] × [paragraphs] × [words] words with [images] figures spread
/// evenly through it, a block quote every seventh section and [footnotes]
/// footnotes collected at the end.
String syntheticSubstackBody({
  int sections = 40,
  int paragraphs = 5,
  int words = 42,
  int images = 24,
  int footnotes = 12,
  String Function(int index) imageUrl = syntheticImageUrl,
}) {
  final total = sections * paragraphs;
  final html = StringBuffer();
  var image = 0;
  for (var block = 0; block < total; block++) {
    if (block % paragraphs == 0) html.write('<h2>${syntheticWords(6, block)}</h2>');
    final note = block % (total ~/ footnotes) == 0 ? block ~/ (total ~/ footnotes) + 1 : null;
    html.write(_paragraph(block, words, footnote: note != null && note <= footnotes ? note : null));
    if (block % (paragraphs * 7) == 3) html.write('<blockquote><p>${syntheticWords(28, block)}</p></blockquote>');
    if (image < images && block % (total ~/ images) == total ~/ images - 1) {
      html.write(syntheticImage(image, imageUrl(image)));
      image++;
    }
  }
  for (var n = 1; n <= footnotes; n++) {
    html.write(_footnote(n));
  }
  return html.toString();
}

/// The `/api/v1/posts/{slug}` payload around [body], with the fields the
/// client reads.
Map<String, Object?> syntheticSubstackPostJson({required String body, String slug = 'xta-perf-long-read'}) => {
  'id': 990001,
  'slug': slug,
  'title': 'A long read for measuring the reader',
  'subtitle': syntheticWords(18, 7),
  'post_date': '2026-10-01T08:00:00.000Z',
  'canonical_url': 'https://xtaperf.substack.com/p/$slug',
  'audience': 'everyone',
  'type': 'newsletter',
  'body_html': body,
  'reaction_count': 120,
  'comment_count': 340,
  'publishedBylines': [
    {'name': 'Perf Author'},
  ],
};

Map<String, Object?> _comment(int id, List<Object?> children) => {
  'id': id,
  'body': syntheticWords(20 + (id * 13) % 60, id),
  'name': 'Reader $id',
  'handle': 'reader$id',
  'date': DateTime.utc(2026, 10, 1).add(Duration(minutes: id * 7)).toIso8601String(),
  'reaction_count': id % 17,
  'children': children,
};

/// A discussion of exactly [count] comments in the comments endpoint's
/// shape: threads of up to [depth] levels below each top-level comment.
Map<String, Object?> syntheticSubstackComments(int count, {int depth = 4}) {
  var next = 0;
  Map<String, Object?> thread(int level) {
    final id = next++;
    final children = <Object?>[];
    while (level < depth && next < count && children.length < 2 && (id + children.length) % 3 != 0) {
      children.add(thread(level + 1));
    }
    return _comment(id, children);
  }

  final roots = <Object?>[];
  while (next < count) {
    roots.add(thread(0));
  }
  return {'comments': roots};
}
