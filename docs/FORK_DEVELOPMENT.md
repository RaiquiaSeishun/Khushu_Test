# Sukun development and release notes

## Provenance and upstream synchronization

The destination checkout was empty and had an unborn branch. Source and full upstream history were imported from `greykaizen/khushu`, main at `861c32772c0b09a2c836a4e1f65d9f6f820d3d87`. The origin remains `RaiquiaSeishun/Khushu_Test`; the read-only upstream remote points to the original repository. Local `main` and the default push remote target origin. The tested foundation commits and CI correction were pushed to origin/main on 2026-10-09. No contribution was submitted upstream.

GitHub API metadata was checked on 2026-10-09: the destination is public, but reports `fork: false` and has no parent. It is an independent repository retaining upstream Git history, not a server-linked GitHub fork. Establishing a server-side fork relationship remains an administrator decision; do not reset history or delete the destination repository to work around this.

Cloud tasks already have isolated checkouts: use the existing repository, not a new worktree.

To inspect future upstream changes:

```sh
git fetch upstream main
git log --oneline HEAD..upstream/main
git diff HEAD...upstream/main
```

With a clean checkout, integrate reviewed changes using `git merge upstream/main`, resolve conflicts, and rerun checks. Preserve fork commits and original copyright/license notices. Prepare small upstream contributions only with user approval; never open upstream pull requests automatically.

## Architecture and confirmed findings

This is a single Android Compose app, with full and fdroid distributions, ViewModels, DataStore preferences, Room databases, WorkManager, Media3, Adhan local calculations, and OkHttp/Retrofit network clients. The pinned toolchain is JDK 21, Gradle 9.5.1, AGP 9.2.1, Kotlin 2.3.20, and SDK 36; the old upstream README's tool versions are outdated.

- GPS previously refreshed once per SettingsViewModel, preferred NETWORK unconditionally, accepted unvalidated caches, and displayed the prayer-data timestamp. Foreground refresh, bounded concurrent provider requests, fix validation, approximate permission support, separate status, and cancellation now address these paths. The conservative fix limits are 10 minutes old and 1 km accuracy with precise permission or 10 km with approximate permission. Automatic attempts are at least 15 minutes apart. A stale saved coordinate is retained rather than erased on failure. Real hardware accuracy, movement, battery consumption, and permission-revocation behavior still need device trials.
- Unsupported local prayer methods previously triggered API calls silently. Local mode now stays offline and discloses a Muslim World League fallback. Online calls have explicit timeouts, validate response completeness, use a request-keyed persistent cache, and coalesce concurrent requests. Cache keys include the complete request (date, coordinates, convention/custom angles, madhab, time zone, and shafaq). Offsets are applied after lookup. Entries last 24 hours and are capped at 32. Failed fetches do not become successful cache entries.
- Malaysian method 17 is an AlAdhan convention. It does not select an official JAKIM state prayer zone. No astronomical parameters were changed; no official-timetable accuracy claim is made. Live access now works. Three independently recorded WLY01 samples show Fajr 10–11 minutes earlier and other prayers 1–3 minutes earlier than JAKIM. See [the reference comparison](PRAYER_REFERENCE_COMPARISON.md); official zone support is now implemented as a separate JAKIM source; see [the feature notes](JAKIM_AND_ICONS.md). Selecting the correct zone and phone validation remain necessary.
- The next-day calculations in prayer extras and PrayerManager previously added 24 elapsed hours. They now use calendar-day advancement, which is covered at daylight-saving and year boundaries.
- CanvasDatabase used destructive fallback. Upstream history shows unchanged Salah tables from version 3 to 5 with two new Tasbeeh tables. An explicit 3→5 migration replaces the fallback and schema 5 is exported. Other historical versions lack verified schema evidence and fail safely rather than wipe data; do not claim those upgrade paths are supported.
- The APK updater called a FileProvider absent from the only manifest. A non-exported provider now matches the application ID and exposes only the updates subdirectory. The release endpoint targets this fork. Signed update installation, installer permissions, and update-version behavior still need an actual signed release test.
- `AUDIO_BASE_URL` is a placeholder for the optional Learn topic download worker. Current Learn topics leave `audioFilename` unset, so that path is dormant. Quran playback uses separate reciter URLs and the bundled audio directory contains `fatiha.mp3`. Do not substitute arbitrary recordings without verifying topic mapping, licensing, and attribution. The dormant worker's resource/error handling remains future work if that feature is activated.
- Four existing fatal lint findings were corrected: notification runtime permission/race handling, observable locale use, an unremembered state object, and an unused BoxWithConstraints scope. Existing warnings remain visible.

## Validation scope

Existing and added JVM tests exercise fix-age/accuracy boundaries, invalid coordinates, clock skew, refresh intervals, concurrent cache successes and failures, cache persistence/corruption/expiry/cancellation, real HTTP response handling through MockWebServer, source privacy, parameter separation, retry, and calendar day transitions. Mock API fixtures validate request/response behavior, not astronomical or official Malaysian accuracy. The two original placeholder unit tests remain distinguishable from the added regression tests.

Android tests exercise version-3 canvas migration with saved user data, restricted FileProvider file access, and activity startup. The original package-context test remains. Software emulation is slow in this cloud instance; wait for `sys.boot_completed=1` and `ro.build.version.sdk=30` before starting connected tests. An ADB device listing alone is insufficient. Software emulation also needs `-Dddmlib.getprop.timeout.sec=30` to avoid the default two-second discovery timeout, and standard UI-test animation settings (window, transition, and animator scales set to zero).

CI runs builds, unit tests, and lint for both distributions, plus Android tests on API 30. Both CI jobs passed on 2026-10-09 at workflow commit `248d462`: both-variant build/JVM/lint checks and connected Android tests. See [the final run](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/37925651593). Earlier SDK setup and emulator lookup/AVD failures occurred before application tests; they were corrected by pinning command-line tools 19, omitting the legacy `tools` package, using the SDK emulator path, and aligning Android user/emulator/AVD directories. Failure diagnostics now appear in check annotations. Initial cold-emulator and source-changing-during-lint failures are not passing checks; the final reruns passed. See [validation evidence](VALIDATION.md).

## Independent release requirements

The user chose the name **Sukun**. The production app label and in-app branding use this name; debug builds now use the label Sukun Test and application ID `io.github.raiquiaseishun.sukun.debug` to coexist with earlier debug APKs; original logo geometry and legacy launcher raster assets were replaced with an independent ring mark. Attribution is retained in About and the preserved upstream README. Source package names remain `com.kaizen.khushu` to minimize upstream conflicts; the production application ID is `io.github.raiquiaseishun.sukun`.

This ID permits coexistence with Khushu, starts with separate private app data, and avoids attempting to replace an app signed by another developer. There is no implemented import of original app data. Keep one release signing identity for all Sukun updates. Debug signing is development-only.

The manual release workflow builds signed full and fdroid single APKs, checks JVM tests/lint, verifies the APK signature, and creates a **draft** release in this repository. It does not run automatically on tags. It is prepared, not executed or validated with real signing credentials. Before invoking it:

1. Resolve public-fork status, official prayer-reference verification, device GPS trials, branding review, and any required migration paths.
2. Complete both-variant checks and connected Android tests; review a physical-device installation and notification delivery test.
3. Create and back up a dedicated signing key securely. Follow [the production signing guide](RELEASE_SIGNING.md) to supply `SIGNING_KEY_STORE_BASE64`, `SIGNING_KEY_ALIAS`, `SIGNING_STORE_PASSWORD`, and `SIGNING_KEY_PASSWORD` in GitHub Actions secrets, never source or chat. The signing identity cannot be recovered from an APK.
4. Increment versionCode and versionName for each subsequent update and choose a reviewed tag such as `v0.24.11`. Invoke the manual workflow; inspect its draft artifacts, source commit, and signature before publishing.
5. Include GPLv3 license, retained copyright notices, modification disclosure, and access to complete corresponding source/build instructions at the exact release tag. Audit bundled third-party data/audio/font licensing before redistribution.
6. After publication, Obtainium can track `https://github.com/RaiquiaSeishun/Khushu_Test`; verify APK selection and update installation using the retained Sukun signing key.

Use [the phone checklist](PHONE_TESTING.md) for development APK trials. No signing secrets were requested for cloud development because debug builds do not need them. No application release is currently approved as ready for installation or publication.

## Official-source and icon follow-up

Version 0.24.12 / code 86 adds selected-zone JAKIM month downloads, persistent offline timetables, source/retrieval notices, daily revision checks and manual refresh, validated Malaysian dates, and reminders that skip missing official data. The launcher switcher now separates class namespace from application ID, uses safe alias ordering/batching, and restores saved styles. See [implementation and validation scope](JAKIM_AND_ICONS.md). CI covers both API 30 and API 35; the outcome must be verified for this change before claiming it passed.
