# Release APK names

The aimdi150 build produced four signed APKs, but Android still hard-coded
`aimdi149` in their filenames. The unchanged release verifier rejected them.

Make the release recorder own asset names, deriving the requested tag and ABI
from inspected APK metadata. Preflight the complete four-variant set and reject
collisions or unsupported ABIs before renaming; preserve every APK byte. Remove
hard-coded output names from Gradle so future builds use Flutter defaults.
Keep source, version, checksum and certificate verification unchanged. Untagged
review artifacts retain their original names.

The existing attach workflow copies the current helper before checking out the
immutable aimdi150 tag. Use it to rebuild and publish that exact app source with
correct filenames; do not move the tag or modify its checked-out source.
