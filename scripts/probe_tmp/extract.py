"""Temporary: prints the parts of a new-Reddit page a parser needs."""
import re
import sys


def chunks(text, prefix='| '):
    for i in range(0, len(text), 1500):
        print(prefix + text[i:i + 1500])


def element(body, start, name, cap):
    """The element opening at [start], to its matching close tag, capped."""
    depth = 0
    pattern = re.compile(r'<(/?)' + re.escape(name) + r'(?=[\s>/])[^>]*>')
    for m in pattern.finditer(body, start):
        depth += -1 if m.group(1) else 1
        if depth == 0:
            return body[start:m.end()][:cap]
    return body[start:start + cap]


def main(path, label):
    body = open(path, encoding='utf-8', errors='replace').read()
    title = re.search(r'<title[^>]*>([^<]*)</title>', body)
    print(f'##### {label}: {len(body)} chars, title {title.group(1).strip() if title else "-"}')
    for marker in ['<shreddit-post', '<shreddit-comment ', '<shreddit-comment-tree', '<faceplate-partial',
                   '<shreddit-player', '<gallery-carousel', '<shreddit-async-loader', 'search-post-unit',
                   '<shreddit-feed', 'post-title=', 'reddit-search-large', '<faceplate-tracker']:
        print(f'count {marker}: {body.count(marker)}')
    for i, m in enumerate(re.finditer(r'<shreddit-post(?=[\s>])[^>]*>', body)):
        if i >= 6:
            break
        print(f'--- post tag {i + 1}')
        chunks(m.group(0))
    first = next((m.start() for m in re.finditer(r'<shreddit-post(?=[\s>])', body)), -1)
    if first >= 0:
        print('--- first post element')
        chunks(element(body, first, 'shreddit-post', 25000))
    for i, m in enumerate(re.finditer(r'<faceplate-partial\b[^>]*>', body)):
        if i >= 12:
            break
        print(f'--- partial tag {i + 1}')
        chunks(m.group(0))
    tree = re.search(r'<shreddit-comment-tree\b[^>]*>', body)
    if tree:
        print('--- comment tree tag')
        chunks(tree.group(0))
    for i, m in enumerate(re.finditer(r'<shreddit-comment(?=[\s>])[^>]*>', body)):
        if i >= 5:
            break
        print(f'--- comment tag {i + 1}')
        chunks(m.group(0))
    comment = body.find('<shreddit-comment ')
    if comment >= 0:
        print('--- first comment element')
        chunks(element(body, comment, 'shreddit-comment', 20000))
    if first < 0 and comment < 0:
        at = body.find('/comments/')
        print('--- around first /comments/')
        chunks(body[max(0, at - 3000):at + 6000] if at >= 0 else body[:6000])


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
