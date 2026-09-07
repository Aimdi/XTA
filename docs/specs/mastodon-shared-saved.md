# Mastodon in the shared Saved archive

Extend the existing blob-based archive with a versioned Mastodon envelope and
a URL-based namespace so statuses from different servers cannot collide. Reuse
SavedTweetModel, folders, notes and their existing table without schema changes.

Show a device-local bookmark action on Mastodon cards. A tap saves/removes;
a long press opens the existing folder picker. Stored posts render with their
content warning, quote, poll and media metadata and open their conversation.
Add an optional source filter to the existing Saved toolbar; combine it with
folder and text filters. Media-only mode must include Mastodon media, while
the existing X media grid remains intact for X-only results.

Tests cover cross-server identities, archive decoding and search, combined
filters, saving into a folder and reopening a stored post. The archive retains
post text and URLs; it does not claim all attachments have been downloaded.
