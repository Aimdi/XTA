"""Temporary: which marks a feed's posts carry, without their words or links."""
import re
import sys
from collections import Counter
from html.parser import HTMLParser

SKIP = {'post-title', 'permalink', 'content-href', 'author', 'icon', 'class', 'id', 'author-id',
        'subreddit-id', 'created-timestamp', 'score', 'comment-count', 'feedindex', 'upvote-ratio',
        'award-id', 'award-icon-url', 'share-button-text', 'more-posts-cursor', 'telemetry-author-profile'}


class Marks(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.posts = []
        self.blur = Counter()
        self.tags = Counter()
        self.depth = []

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == 'shreddit-post':
            self.posts.append({k: (v if k in ('post-type', 'domain', 'subreddit-name') else (v[:20] if v else None))
                               for k, v in a.items() if k not in SKIP})
        elif tag == 'shreddit-blurred-container':
            self.blur[tuple(sorted(k for k in a if k != 'class'))] += 1
            for k in a:
                if k not in ('class',):
                    self.blur[(k, (a[k] or '')[:30])] += 1
        elif tag == 'shreddit-content-tags':
            self.tags[tuple(sorted((k, (v or '')[:30]) for k, v in a.items() if k != 'class'))] += 1


m = Marks()
m.feed(open(sys.argv[1], encoding='utf-8', errors='replace').read())
print('  posts', len(m.posts))
print('  post marks', Counter(tuple(sorted(p.items())) for p in m.posts).most_common(6))
print('  blurred', m.blur.most_common(12))
print('  content-tags', m.tags.most_common(6))
body = open(sys.argv[1], encoding='utf-8', errors='replace').read()
tele = Counter(re.findall(r'&quot;nsfw&quot;:(true|false)', body))
print('  telemetry nsfw', dict(tele))
