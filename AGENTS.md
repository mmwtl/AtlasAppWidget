# AtlasAppWidget Repository Guide

## Scope

These instructions apply to the entire repository.

## Project purpose

AtlasAppWidget is an Android 11 launcher-overlay application for a portrait automotive head unit.
It shows a configurable `TYPE_APPLICATION_OVERLAY` shortcut panel only while a HOME activity is in
the foreground. The package name is `com.mmwtl.atlasappwidget`; do not change it without an explicit
migration request.

## Required behavior

- Keep the screen outside the overlay window interactive by preserving the non-focusable,
  non-touch-modal window flags.
- Keep app shortcuts clickable and keep the panel hidden outside HOME/launcher activities.
- When the drag handle is hidden, dragging starts only after a one-second hold on empty panel space.
- Preserve selected activities, custom icons, panel position, and appearance preferences across
  ordinary app upgrades.
- Treat overlay permission, usage access, foreground-service behavior, boot start, and OEM power
  restrictions as separate concerns. Do not claim that the Android API can grant OEM permissions.

## Version and artifact naming

- Keep `appVersionCode` and `appVersionName` at the top of `app/build.gradle` as the single version
  source.
- For every completed application, resource, or build-system improvement, increment
  `appVersionCode`. Increment the semantic patch component of `appVersionName` unless the user
  requests a different release number.
- A single user-requested batch is one version increment even when it contains several related
  files or commits.
- Preserve the archive base name `<versionName>[<versionCode>]AtlasAppWidget`; do not allow Gradle
  to fall back to the module-derived `app-*.apk` name.

## Build and verification

Use the repository wrapper. Before handing off any completed improvement, run at minimum:

```sh
sh gradlew --offline clean check assembleRelease
```

For emulator QA, additionally build/install the debug variant as needed. Verify the release output
under `app/build/outputs/apk/release/`, inspect its package/version metadata, and run `apksigner
verify` when the artifact is signed. Release signing may be supplied by the ignored local
`secure.signing.gradle` and keystore files. If they are absent, report the unsigned artifact
explicitly; never disguise a debug-signed artifact as a production release and never commit
keystores or credentials.

For UI or overlay changes, validate on Android 11 at 1440x1920 portrait when an emulator is
available. Exercise both states of every affected toggle, verify HOME/non-HOME visibility, and
check the crash log.

## Source and UI guidelines

- Keep Android framework behavior at the service/activity edges and small layout calculations in
  focused classes such as `PanelConfig` and `PanelView`.
- Maintain the graphite visual system: `#171717` background, `#262626` cards, `#333333` nested
  surfaces, `#F5F5F5` primary text, `#D4D4D4` secondary text, and `#7893A0` accent.
- Keep launcher icons adaptive with normal, round, and Android 13 monochrome resources.
- Avoid dependencies unless they materially simplify behavior that cannot remain small and local.

## Repository hygiene

- After completing and verifying each improvement, create a Git commit unless the user explicitly
  asks to leave it uncommitted.
- Stage only files belonging to the current improvement. Do not amend, rebase, push, or rewrite
  existing history unless explicitly requested.
- Never commit generated APKs, Gradle caches, local SDK paths, signing files, keystores, or secrets.
- Preserve unrelated user changes in a dirty worktree.
