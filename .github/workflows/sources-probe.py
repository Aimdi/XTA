"""Temporary: raw answers behind the sweep's three failures."""
import datetime
import json
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import HTTPRedirectHandler, Request, build_opener


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


opener = build_opener(NoRedirect)
UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"


def get(url, ua=UA, accept="application/json"):
    try:
        response = opener.open(Request(url, headers={"User-Agent": ua, "Accept": accept}), timeout=20)
    except HTTPError as error:
        response = error
    except (URLError, TimeoutError, OSError) as error:
        return {"url": url, "error": repr(error)}, None
    with response:
        body = response.read().decode("utf-8", errors="replace")
        head = {k.lower(): v for k, v in response.headers.items()}
    return {
        "url": url.split("?")[0], "status": response.status, "server": head.get("server"),
        "cf": head.get("cf-mitigated"), "location": (head.get("location") or "")[:100],
        "snippet": " ".join(body[:180].split()),
    }, body


for host in ("https://public.api.bsky.app", "https://api.bsky.app"):
    for ua in (UA, "XTA Bluesky plugin"):
        summary, body = get(f"{host}/xrpc/app.bsky.feed.searchPosts?" + urlencode({"q": "bluesky", "limit": 20, "sort": "latest"}), ua)
        summary["ua"] = ua[:20]
        cursor = None
        try:
            parsed = json.loads(body or "{}")
            summary["posts"] = len(parsed.get("posts", []))
            cursor = parsed.get("cursor")
        except ValueError:
            pass
        print(json.dumps(summary))
        if cursor:
            page2, _ = get(f"{host}/xrpc/app.bsky.feed.searchPosts?" + urlencode({"q": "bluesky", "limit": 20, "sort": "latest", "cursor": cursor}), ua)
            page2["page"] = 2
            print(json.dumps(page2))
    for path in ("/xrpc/app.bsky.actor.getProfile?actor=bsky.app", "/xrpc/app.bsky.actor.searchActors?q=bluesky"):
        summary, _ = get(host + path)
        print(json.dumps(summary))

for url in (
    "https://substack.com/api/v1/publication/search?query=technology&page=0",
    "https://substack.com/api/v1/categories",
    "https://substack.com/api/v1/reader/feed?limit=5",
    "https://astralcodexten.substack.com/api/v1/archive?sort=new&limit=3",
    "https://www.astralcodexten.com/api/v1/archive?sort=new&limit=3",
    "https://www.astralcodexten.com/feed",
    "https://www.slowboring.com/api/v1/archive?sort=new&limit=3",
    "https://garbageday.substack.com/api/v1/archive?sort=new&limit=3",
    "https://substack.com/@astralcodexten",
):
    summary, _ = get(url)
    print(json.dumps(summary))
    browser, _ = get(url, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36", "text/html,application/json")
    browser["ua"] = "desktop"
    print(json.dumps(browser))

today = datetime.datetime.now(datetime.timezone.utc).date()
for date in (today, today - datetime.timedelta(days=1)):
    summary, body = get("https://danbooru.donmai.us/explore/posts/popular.json?" + urlencode({"date": date.isoformat(), "scale": "day", "page": 1, "limit": 40}))
    try:
        posts = json.loads(body or "[]")
        summary["count"] = len(posts)
        summary["ratings"] = "".join(sorted(p.get("rating", "?") for p in posts))
    except ValueError:
        pass
    summary["date"] = date.isoformat()
    summary.pop("snippet", None)
    print(json.dumps(summary))
