# The stand-in for X

A small HTTP server that answers as X does, from recorded responses, so XTA
Native's screens can be opened on an emulator or a desktop with no X account,
no live request and no rate limit. CI's `screens` and `desktop` jobs read it.

It is the Gradle module `:mock-x`: plain Kotlin on the JDK's own
`com.sun.net.httpserver`, in no app.

```bash
cd native
./gradlew :mock-x:installDist
tool/mock-x/build/install/mock-x/bin/mock-x          # listens on http://localhost:8787
tool/mock-x/build/install/mock-x/bin/mock-x --port 8790
```

## Who reads it

- An Android build made with `-PmockX=http://10.0.2.2:8787` (an emulator
  reaches the computer it runs on at `10.0.2.2`; a phone needs
  `adb reverse tcp:8787 tcp:8787` and `-PmockX=http://localhost:8787`).
- The desktop app started with `XTA_NATIVE_MOCK_X=http://127.0.0.1:8787`.

`:ui`'s `data/MockX.kt` then sends every request for x.com, api.x.com,
twitter.com and api.twitter.com to `<server>/<host>/<path>?<query>`, signs
with a fixed key and starts with a made-up session. Everything else (images,
videos, the other networks) keeps using the network.

## What it answers

It listens on 127.0.0.1 only, reads the X address back from the path, and
answers:

| Request | Answer |
|---|---|
| `POST /1.1/guest/activate.json` | 200, `{"guest_token":"1"}` |
| `…/graphql/<id>/<operation>` (with or without `/i/api`), an operation of `MockX.ROUTES` | 200, the fixture picked by the variable that names what was asked for (a screen name, a post id, a community id; lowercased), or the route's one fixture |
| the same with a `cursor` variable | 200, that fixture with every `entries`, `moduleItems` and `items_results` emptied, which ends the list |
| a known operation, a value without a fixture | 200 with X's "does not exist" answer where the route has one, else 501 naming the values recorded |
| anything else | 501, saying what it answers |

A 200 is `application/json; charset=utf-8`, anything else
`text/plain; charset=utf-8`. Each answer is logged as
`<status> <method> <path> <- <fixture>`, with the body of one that is not 200.

## The fixtures

`src/main/resources/fixtures` holds trimmed bodies of real X answers for the
QuaX test accounts `@quax_tests` and `@quax_tests_2`, `@X`, one post and the
"Flutter" community, recorded by QuaX (MIT) and by XTA as a guest. They hold
no session, cookie or token.

`MockXTest` holds the answers above; a new route goes into `MockX.ROUTES` with
its fixture and a line there.
