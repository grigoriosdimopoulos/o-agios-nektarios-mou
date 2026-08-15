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
