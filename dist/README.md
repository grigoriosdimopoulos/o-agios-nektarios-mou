# Installable build

`agios-nektarios.apk` is here for one reason: the person testing this app has
an Android phone and no computer, the chat window we use will not deliver an
`.apk`, and `api.github.com` is blocked from the build environment, so a proper
GitHub Release cannot be created from here.

A public repository serves this file over plain HTTPS with no sign-in, which a
phone browser can follow:

<https://github.com/grigoriosdimopoulos/o-agios-nektarios-mou/raw/claude/agios-nektarios-village-app-w645ea/dist/agios-nektarios.apk>

This is not how binaries should normally be shipped. A 16 MiB blob stays in git
history for good, and the history cannot be trimmed without a force push. Once
releases can be created — or once there is a machine that can run
`firebase-tools` and `adb` — delete this directory and publish the APK as a
release asset instead.

The build is the `preview` variant: shrunk by R8, arm64 only, signed with the
committed debug key so it installs over previous copies. `app/preview-mapping/`
holds the rename table for decoding its crash reports.

## Telling one build from another

`BUILD.txt` next to the APK records the version string, the SHA-256 and the
build time of whatever is currently published. The same version string is
shown inside the app under Settings > About, so a phone can be checked against
this without reinstalling.

This matters because the URL is fixed: two different builds are downloaded from
the same link, arrive with the same filename, and — until the version was
stamped from git — installed as the same version. There was no signal
anywhere that anything had changed.

The raw endpoint also sends `cache-control: max-age=300`, so a download made
within five minutes of a previous one can be served from cache. Appending a
query string (`?v=<anything>`) defeats that.
