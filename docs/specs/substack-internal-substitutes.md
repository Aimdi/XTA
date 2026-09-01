# Substack — internal substitutes for account features

Read-only public client. No Substack login, no write APIs, no paid unlock.
TTS already covers “listen”; this pass covers the other account-shaped gaps
with device-local stand-ins (same spirit as Threads local likes / X saved).

## Gap → substitute

| Substack app | Why missing | Internal substitute |
|---|---|---|
| React / like | Write API | **Local like** — heart stays on device; counts from Substack still shown |
| Save / bookmark | Needs account | **Local saved** — post snapshot in prefs; reopen in reader |
| Inbox | Email / account feed | **Inbox tab** — unread posts from local follows |
| Profile | Account | **Library tab** — Following · Saved · Liked + cross-search |
| Restack | Write API | Share / open in browser (already in reader) |
| Following Notes | Session | Public Notes discovery (already) |
| Cross-pub search | Account | Library search over Following / Saved / Liked |

## Out of scope

- Compose, comment write, restack write
- Login / paid unlock / push / email digest
- DB schema (likes + saves use prefs, like read ids)
