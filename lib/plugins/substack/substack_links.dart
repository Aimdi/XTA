import 'package:xta/plugins/substack/substack_models.dart';

/// A Substack post URL split into the publication it belongs to and its slug.
class SubstackPostLink {
  /// Origin of the publication, e.g. `https://astralcodexten.substack.com`.
  final Uri publicationBase;
  final String slug;

  const SubstackPostLink({required this.publicationBase, required this.slug});

  @override
  bool operator ==(Object other) =>
      other is SubstackPostLink &&
      other.publicationBase == publicationBase &&
      other.slug == slug;

  @override
  int get hashCode => Object.hash(publicationBase, slug);

  @override
  String toString() => 'SubstackPostLink($publicationBase, $slug)';
}

/// Path segments that are pages of a publication, never a post.
const _nonPostSegments = {
  'archive',
  'about',
  'subscribe',
  'notes',
  'note',
  'podcast',
  'people',
  's',
  'i',
};

/// Recognises a link to a readable Substack post.
///
/// Handles the three shapes Substack hands out: the publication subdomain
/// (`https://foo.substack.com/p/slug`), the share/app host used in its emails
/// (`https://open.substack.com/pub/foo/p/slug`), and a publication on its own
/// custom domain — which is only recognisable when the domain is one of
/// [knownBaseUrls], i.e. a publication the reader already follows.
///
/// Returns null for anything else, including publication home pages, archives,
/// notes and non-Substack links, so the caller can fall back to the browser.
SubstackPostLink? parseSubstackPostLink(
  String url, {
  Iterable<String> knownBaseUrls = const [],
}) {
  final uri = Uri.tryParse(url.trim());
  if (uri == null ||
      !uri.hasScheme ||
      (uri.scheme != 'http' && uri.scheme != 'https')) {
    return null;
  }

  final host = uri.host.toLowerCase();
  if (host.isEmpty) {
    return null;
  }

  final segments = uri.pathSegments.where((s) => s.isNotEmpty).toList();

  // https://open.substack.com/pub/<publication>/p/<slug>
  if (host == 'open.substack.com') {
    if (segments.length >= 4 && segments[0] == 'pub' && segments[2] == 'p') {
      final subdomain = segments[1];
      final slug = segments[3];
      if (_isSlug(slug) && _isSubdomain(subdomain)) {
        return SubstackPostLink(
          publicationBase: Uri.parse('https://$subdomain.substack.com'),
          slug: slug,
        );
      }
    }
    return null;
  }

  final onSubstackSubdomain = isSubstackPublicationHost(host);
  final knownHosts = knownBaseUrls
      .map((base) => Uri.tryParse(base)?.host.toLowerCase())
      .whereType<String>()
      .toSet();
  final onKnownCustomDomain = knownHosts.contains(host);

  if (!onSubstackSubdomain && !onKnownCustomDomain) {
    return null;
  }

  // https://<publication>/p/<slug>, optionally /comments.
  if (segments.length >= 2 && segments[0] == 'p' && _isSlug(segments[1])) {
    return SubstackPostLink(
      publicationBase: Uri.parse('https://$host'),
      slug: segments[1],
    );
  }

  return null;
}

bool _isSlug(String value) =>
    value.isNotEmpty &&
    !_nonPostSegments.contains(value.toLowerCase()) &&
    !value.startsWith('@');

bool _isSubdomain(String value) =>
    value.isNotEmpty && !value.contains('/') && !value.contains('.');

/// The minimum post the reader needs to fetch the real one: it reloads from the
/// publication and slug on open, so the title and body arrive with that
/// request. Until then the publication's name stands in, so the screen never
/// opens with an empty title bar.
SubstackPost substackPostStub(
  SubstackPostLink link, {
  String? publicationName,
}) {
  final name = publicationName ?? subdomainOf(link.publicationBase);
  return SubstackPost(
    id: link.slug,
    title: name,
    slug: link.slug,
    publicationBaseUrl: link.publicationBase.origin,
    publicationName: name,
    canonicalUrl: '${link.publicationBase.origin}/p/${link.slug}',
  );
}
