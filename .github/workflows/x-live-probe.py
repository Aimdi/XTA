"""Temporary: what x.com and its API answer this runner, without printing secrets."""
import json
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import HTTPRedirectHandler, Request, build_opener

constants = open("lib/constants.dart", encoding="utf-8").read()
guest_bearer = re.search(r"""guestBearerToken\s*=\s*["']([^"']+)["']""", constants)
guest_bearer = guest_bearer[1] if guest_bearer else None

BROWSER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


opener = build_opener(NoRedirect)


def get(url, headers=None, data=None, method=None):
    request = Request(url, headers=headers or {}, data=data, method=method)
    try:
        response = opener.open(request, timeout=15)
    except HTTPError as error:
        response = error
    except (URLError, TimeoutError, OSError) as error:
        return None, {}, repr(error)
    with response:
        body = response.read()
        return response.status, dict(response.headers), body.decode("utf-8", errors="replace")


def page_summary(label, url, headers):
    status, head, html = get(url, headers)
    if status is None:
        print(json.dumps({"page": label, "network_error": html}))
        return
    print(json.dumps({
        "page": label,
        "status": status,
        "location": (head.get("Location") or head.get("location") or "").split("?")[0],
        "server": head.get("Server") or head.get("server"),
        "cf_mitigated": head.get("cf-mitigated") or head.get("Cf-Mitigated"),
        "bytes": len(html),
        "title": (re.search(r"<title[^>]*>([^<]*)", html) or [None, None])[1],
        "verification": bool(re.search(r'name="twitter-site-verification"\s+content="[^"]+"', html)),
        "frames": len(re.findall(r'id="loading-x-anim', html)),
        "signer_refs": sorted(set(re.findall(r"(?:sign\.o|ondemand\.s)[.-][A-Za-z0-9_-]+\.js", html)))[:5],
    }))


pages = {
    "x.com/home": "https://x.com/home",
    "x.com/search": "https://x.com/search?q=AI&f=live",
    "twitter.com/home": "https://twitter.com/home",
    "mobile.x.com/home": "https://mobile.x.com/home",
    "x.com/i/flow/login": "https://x.com/i/flow/login",
    "proxy x.com/home": "https://x-com.translate.goog/home?_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en",
    "proxy x.com/search": "https://x-com.translate.goog/search?q=AI&f=live&_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en",
}
for label, url in pages.items():
    page_summary(label, url, {"User-Agent": BROWSER, "Accept-Language": "en-US,en;q=0.9"})
    page_summary(label + " (android ua)", url, {
        "User-Agent": "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9",
    }) if label.startswith("x.com") else None

if not guest_bearer:
    print("no guest bearer found")
    sys.exit(0)

status, head, body = get(
    "https://api.x.com/1.1/guest/activate.json",
    {"Authorization": guest_bearer, "User-Agent": BROWSER},
    data=b"",
    method="POST",
)
token = None
try:
    token = json.loads(body).get("guest_token")
except Exception:
    pass
print(json.dumps({"guest_activate": status, "token": bool(token)}))
if not token:
    sys.exit(0)

endpoints = open("lib/client/endpoints.dart", encoding="utf-8").read()
entries = re.findall(
    r"name:\s*(\w+),\s*operation:\s*'(\w+)',\s*queryId:\s*'([A-Za-z0-9_-]+)',\s*host:\s*'([a-z.]+)'",
    endpoints,
)
variables = quote(json.dumps({"userId": "783214", "screenName": "X", "screen_name": "X", "count": 1}))
features = quote(json.dumps({}))
api = {"Authorization": guest_bearer, "x-guest-token": token, "User-Agent": BROWSER, "Accept": "*/*",
       "Referer": "https://x.com/", "x-twitter-active-user": "yes", "x-twitter-client-language": "en"}
for name, operation, query_id, pinned in entries:
    row = {"endpoint": operation, "pinned": pinned}
    for host in ("x.com", "twitter.com", "api.x.com"):
        path = f"/graphql/{query_id}/{operation}" if host == "api.x.com" else f"/i/api/graphql/{query_id}/{operation}"
        status, head, body = get(f"https://{host}{path}?variables={variables}&features={features}", api)
        challenge = "Just a moment" in (body or "") or bool(head.get("cf-mitigated"))
        row[host] = f"{status}{' challenge' if challenge else ''}"
        if host == pinned and status not in (None, 404) and not challenge:
            row["sample"] = re.sub(r"\s+", " ", (body or ""))[:160]
    print(json.dumps(row))
