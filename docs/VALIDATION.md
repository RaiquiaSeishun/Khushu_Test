# Validation evidence

## Quran formatting, reliable downloads and saved translation picker — 0.24.19

Nested Tajweed tags now render without leaking markup or losing Arabic characters; provider verse-end spans are removed before the app adds its single verse marker. Structured translation footnote markers are omitted from the text-only reader rather than merged into words. Both bundled translations were regenerated from the already-audited provider responses with that formatting correction, and the bundled Urdu attribution now identifies Maulana Muhammad Junagarhi. All five packaged Arabic datasets remain byte-for-byte unchanged.

The translation picker opens on Downloaded and lists validated saved editions across providers alongside bundled editions. Browse retains the full source catalogs. Fawaz, Quran Foundation bulk responses and QuranEnc's 114 chapter responses are decoded with their respective formats and checked against all 6,236 expected verse keys before atomic installation. A failed download preserves the prior valid copy and active selection, displays a retryable error and cannot appear as a complete downloaded edition. Reader loads cancel superseded requests, and Learn no longer silently substitutes another edition's lesson translation when the selected map is unavailable.

Local fdroid checks built the app and Android test APK, passed 42 JVM tests with zero failures/errors, and completed lint with zero errors/fatal issues (159 warnings and 8 hints). New regressions verify nested markup against all packaged Tajweed verses, verse/footnote formatting, complete key coverage, duplicate array entries, provider decoding and resource identity. Local evidence: `/workspace/downloads/quran-fixes-validation.log`.

[Run 38019957318](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/38019957318) passed at application commit `08c1f91df9e9c0ad985575b089e3b303e6484a5e`: both-distribution build/JVM/lint checks, Android API 30/35 suites, data-preserving installed-APK update checks, retained-key signing and artifact upload. Three new Android tests exercise interrupted/incomplete replacement, Quran Foundation and QuranEnc repository routes with deterministic provider responses, and the rendered Downloaded/Browse picker across sources. These tests do not establish availability of every live provider edition or replace physical-phone checks in [PHONE_TESTING.md](PHONE_TESTING.md).

[Download the Quran update ZIP](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/38019957318/artifacts/11658545123); test version code 100024, production baseline 93 / 0.24.19. Install over the current retained-key Sukun Test app to retain its data and saved translations. CI evidence: `/workspace/downloads/quran-fixes-ci-validation.json`. Documentation-only follow-ups do not change the tested application sources or workflow.

## Qibla bearing, declination and north marker — 0.24.18

The initial great-circle bearing now includes the destination latitude cosine in its east component. Rotation-vector magnetic heading is corrected to true heading with Android GeomagneticField at the current coordinates and date (sea-level altitude because saved settings contain no elevation). Sensor axes are remapped for screen rotation. The Qibla needle, north tick and upright N label use the same true-north frame; N moves opposite the phone heading. The label uses absolute layout positioning so displayed and accessibility positions agree in both LTR and RTL interfaces. Without a heading, directional markers are hidden while the numeric bearing and explanation remain. Home layout, prayer-time sources, icon customization and signing identity are unchanged.

Local fdroid validation built the app and Android test APK, passed 36 JVM tests with 0 failures/errors/skips and completed lint with 0 errors/fatal issues (159 existing warnings). Four new mathematical regressions compare five locations against an independent 3D-vector spherical reference, verify same-meridian directions, declination sign and wraparound, and the shared north/Qibla frame. Local evidence: `/workspace/downloads/qibla-direction-validation.log`.

The initial CI [run 38017165895](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/38017165895) exposed that drawing-only translation left the north label's reported bounds at the dial centre. The correction uses absolute layout positioning and retains the directional assertions, extending them to LTR and RTL. Final [run 38017844751](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/38017844751) passed at application commit `dcb7cb2eb0648335c38e82a884e9ef85844ebce3`: both-distribution build/JVM/lint checks, Android API 30/35 tests (including rendered north positions at four injected headings and absence without a heading), data-preserving update checks, retained-key signing and artifact upload. [Download the Qibla correction ZIP](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/38017844751/artifacts/11657331122); test version code 100023, production baseline 92 / 0.24.18. Install over the current retained-key Sukun Test app. CI evidence: `/workspace/downloads/qibla-direction-ci-validation.json`.

These checks establish the mathematical correction and rendered dial behaviour, not physical sensor calibration or real-phone directional accuracy. Follow the Qibla comparison and portrait/landscape checks in [PHONE_TESTING.md](PHONE_TESTING.md).

## Remove Mosques and Events shortcuts — 0.24.17

Explore now contains only Qibla, retaining its original tile width and bottom-left position beneath the prayer list. The unused Mosque placeholder and Events shortcut actions have been removed. Upcoming dates, the compact JAKIM notice and the rest of Home retain their existing layout.

Local fdroid validation built the app and Android test APK, passed 32 JVM tests with 0 failures/errors/skips, and completed lint with 0 errors/fatal issues (159 existing warnings). An initial lint tooling crash was resolved by rerunning against the final unchanged sources. Local evidence: `/workspace/downloads/qibla-only-validation.log`.

[Run 38006313438](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/38006313438) passed at application commit `2761253c3b1da24eb81a63e84f57dc78f101053a`: both-distribution build/JVM/lint checks, Android API 30/35 tests, data-preserving update checks, retained-key signing and artifact upload. The existing Home tests now verify that Mosques and Events shortcuts are absent, Qibla remains visible below the prayers and opens the compass, and official source Details still opens. [Download the update ZIP](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/38006313438/artifacts/11651692173); test version code 100021, production baseline 91 / 0.24.17. Install over the current retained-key Sukun Test app. CI evidence: `/workspace/downloads/qibla-only-ci-validation.json`.

## Restore bottom Explore and compact JAKIM notice — 0.24.16

The original Home section order is restored: prayer-time card, source notice, upcoming events and daily prayer list, with Explore inside the bottom of the prayer panel. Explore is rendered unconditionally; the original scroll-dependent reveal rule remains removed. Prayer-panel spacing is tightened without changing the section order or shortcut card design. The official JAKIM notice is a compact, tappable row with inline Details; a separate scrollable dialog retains source/date/retrieval and derived-night-time explanations. Approximate times and active manual offsets retain a visible summary. Guided Prayer remains removed.

Local fdroid validation built the app and Android test APK, passed 32 JVM tests with 0 failures/errors/skips and completed lint with 0 errors/fatal issues (existing warnings remain). The Home Android checks now assert that Explore is below Isha and above bottom navigation, and exercise Qibla and Events. A third check uses an explicitly synthetic, current-month timetable UI fixture to verify the healthy official row stays at most 56 dp tall, opens full Details and returns to the same Home layout. The synthetic timetable is test-only and is never included as real prayer data in phone APKs. CI uses a 480×1040 dp portrait viewport matching the proportions and compact control sizing of the reported phone screenshot; this is not a guarantee for all screen sizes, orientations, font scales or extra-timing selections. See [PHONE_TESTING.md](PHONE_TESTING.md). Local log: `/workspace/downloads/home-layout-correction-validation.log`.

[Run 38005127683](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/38005127683) passed at application/workflow commit `2ffc217ff56a62009a40fd8e7df1c71a25c664c5`: both-distribution build/JVM/lint checks, connected Android checks on API 30/35 including all three Home cases, data-preserving APK update checks, retained-key signing/verification and artifact upload. [Download the corrected Home update ZIP](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/38005127683/artifacts/11650912652); test version code 100020, production baseline 90 / 0.24.16. Install over the current retained-key Sukun Test app. CI evidence: `/workspace/downloads/home-layout-correction-ci-validation.json`. Documentation-only follow-ups do not change application sources or workflow configuration.

## Guided Prayer removal and visible Home shortcuts — 0.24.15

Guided Prayer screens, editor, presets and renderers were removed along with their navigation routes, Pray tab, startup choice, customization entry and onboarding promotion. Existing stored Pray startup choices fall back to Home. Only legacy persisted model definitions and tables remain to keep the shared CanvasDatabase compatible with existing installations; no Guided Prayer UI or renderer remains reachable. Daily prayer tracking, Tasbih, Study, icon customization and prayer-data logic remain available.

Home now puts Qibla, Mosques and Events directly under the prayer-time card, ahead of source notices and upcoming events. The scroll-dependent reveal rule and forced viewport-height prayer panel were removed. The Events jump accounts for the optional source card, and bottom content padding keeps later content clear of navigation. Mosques remains a Soon placeholder.

Local feature fdroid validation built the app and Android test APK, passed 32 JVM tests with 0 failures/errors/skips and completed lint with 0 errors/fatal issues (existing warnings remain). Two new Android regressions exercise a persisted legacy startup choice, initial shortcut visibility above bottom navigation, opening the Qibla compass, absence of Guided Prayer customization and jumping to Events with a JAKIM fallback source note. Local log: `/workspace/downloads/home-simplification-final-validation.log`. The initial CI attempt failed the new visibility checks on the existing 480×800 skin with Pixel 2 density; the follow-up explicitly sets 200 dpi for a representative 384×640 dp phone viewport, retaining the assertions and adding display diagnostics. Physical phone, large-text and landscape checks remain in [PHONE_TESTING.md](PHONE_TESTING.md).

[Run 38003148731](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/38003148731) passed at application/workflow commit `7127a572c836e834c78a18ccd8301d3378d250f9`: both-distribution build/JVM/lint checks, connected Android tests on API 30/35 (including the new Home checks) and data-preserving APK update checks, retained-key signing/verification and artifact upload. [Download the Home update ZIP](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/38003148731/artifacts/11650685652); test version code 100018, production baseline 89 / 0.24.15. Install over the current retained-key Sukun Test app. CI evidence: `/workspace/downloads/home-simplification-ci-validation.json`. The earlier failed run is diagnostic evidence rather than passing validation.

## Prayer source status card — 0.24.14

Home now presents source information in a compact card with optional Details. Valid official and cached timetables use a neutral surface; approximate fallback times remain explicit in the visible summary. Unconfirmed automatic zone selection shows the GPS status in Details without also instructing the user to choose a manual zone. Manual time adjustments remain visible in the summary. No prayer calculation, GPS lookup, signing identity or application ID behavior changed.

Local fdroid debug validation built the APK, passed 32 JVM tests with 0 failures/errors/skips, and completed lint with 0 errors/fatal issues (existing warnings remain). The additional regression covers official and cached data, manual adjustments, pending GPS selection and unavailable official data. Local evidence: `/workspace/downloads/prayer-source-card-validation.log`. Physical light/dark-theme, large-text and expansion checks are listed in [PHONE_TESTING.md](PHONE_TESTING.md).

[Run 37999588724](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/37999588724) passed at application commit `0c830192d34724956405ab19fbc7b6485e2de960`: both-distribution build/JVM/lint checks, Android API 30/35 tests and data-preserving update checks, retained-key signing/verification and artifact upload. [Download the update ZIP](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/37999588724/artifacts/11647729891); test version code 100016, production baseline 88 / 0.24.14. Install over the current retained-key Sukun Test app. CI evidence: `/workspace/downloads/prayer-source-card-ci-validation.json`.

## Optional automatic GPS prayer-zone selection — 0.24.13

[Run 37997628279](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/37997628279) completed successfully on 2026-10-09 at application commit `0ed3d65b273aae3a1dc2b4e50f9d520d34d4ee5a`. Both-distribution build/JVM/lint checks, connected Android checks on API 30/35 and data-preserving APK update checks passed. Retained-key signing/verification and phone-artifact upload passed. The download has test version code 100015; the production baseline is 87 / 0.24.13.

Local feature validation built both debug variants and the Android test APK, ran 31 JVM tests per variant with 0 failures/errors/skips, and reported 0 lint errors for both variants (existing warnings remain). The 8 added JVM regressions cover the community zone protocol/cache, uncertainty/border handling, unsupported special areas, provider state aliases, cancellation, invalid responses and exclusion of an unconfirmed automatic zone from official data/reminders. The two original placeholders remain distinguishable. Two new Android tests check persisted choices, new-fix invalidation and late-response rejection after a new fix, manual override, GPS disable or source change.

The actual compiled client, using live HTTPS and public reference coordinates, selected KDH05 in Kulim, WLY01 in Kuala Lumpur and PNG01 in George Town after checking surrounding points. New clients restored all three results with networking blocked. A Genting Highlands reference point required manual selection rather than accepting the community map's broad district zone. These examples do not validate every Malaysian boundary or physical GPS accuracy. Automatic mode remains opt-in and explicitly identifies the community service, coordinate transmission and the need to verify the detected zone; JAKIM prayer entries remain a separate direct official download.

[Download the GPS-zone update ZIP](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/37997628279/artifacts/11647567747). Install it over the retained-key Sukun Test build. No new signing identity or application ID is introduced. APK source identity, checksum, public signer certificate and phone checklist are included. See [feature behavior and limitations](AUTOMATIC_PRAYER_ZONE.md).

Evidence is retained outside the checkout in `/workspace/downloads/gps-zone-final-validation.log`, `/workspace/downloads/gps-zone-live-validation.log` and `/workspace/downloads/gps-zone-ci-validation.json`. Local full feature checks preceded the final permission guard; CI compiled and checked that final guard at the commit above. Documentation-only follow-ups do not modify application sources or workflow configuration.

## Retained test signing and APK updates

On 2026-10-09, the owner reported successful execution of the signing setup script on macOS. [Run 37946015508](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/37946015508) then completed successfully at application/workflow commit `822149b150db04926a227a3927a94eb2f20471d4`:

- The full/fdroid build, JVM and lint job passed. Local fdroid validation also executed 23 tests with 0 failures/errors/skips and built a test-version-100001 APK with the unchanged test application ID.
- The retained-key availability, APK signing/verification and phone-artifact upload steps all passed. The new download uses version code 100013 (`100000 + workflow run 13`); the production version code is unchanged.
- API 30 and API 35 connected checks passed. Each job then installed its own baseline, ran both icon tests to establish real saved settings, installed the next-version APK with `adb install -r`, and confirmed the installed version, unchanged settings DataStore bytes and retained private-file marker.
- The owner setup helper generated a real RSA4096 key, retained it on repeat runs, produced correct base64 encoding, avoided overwriting an existing secret, and refused replacement when the local backup was absent. Those helper checks mocked GitHub writes; the owner upload and successful CI signing separately establish actual repository-secret configuration. A locally signed APK using a script-generated key verified successfully. Temporary validation keys were removed.

The first update-check attempt failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: separate CI steps used different Android user directories and therefore different generated debug keys. The corrected workflow preserves the same Android directory throughout each emulator update check. Earlier failed/cancelled runs are not passing evidence.

[Download the retained-key phone-test ZIP](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/37946015508/artifacts/11624152782). It contains the APK, checksum, source commit, public signer certificate, phone checklist and license. Artifact retention is 30 days. The earlier temporary-key installations cannot accept this new signer; data transfer is not implemented. Subsequent retained-key builds support normal updates to the first consistently signed installation. Physical-phone upgrades and production signing are still distinct checks.

Selected run/job/artifact evidence is saved outside the checkout in `/workspace/downloads/stable-test-signing-ci-validation.json`; local build evidence is in `/workspace/downloads/stable-test-signing-build.log`. See [test signing setup and backup instructions](TEST_SIGNING.md). Documentation-only follow-ups do not modify the tested application sources or workflow configuration.

## JAKIM and icon follow-up — 0.24.12 / code 86

On 2026-10-09, [GitHub Actions run 37938364042](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/37938364042) completed successfully at source commit `3628228a31fe3a661b5c984c06661585fa1976d5`. The build job and both connected Android jobs passed. Documentation-only commits after this run do not change its application sources or build inputs.

| Check | Executed result |
| --- | --- |
| JVM tests, full and fdroid | 23 passed per variant locally, 0 failed/errors/skipped; both suites passed in CI. Includes 6 new JAKIM regressions and 2 original placeholders. |
| Both debug builds and lint | Passed locally and in CI; 0 lint errors locally, existing warnings remain. Android test APK compilation also passed. |
| Android 11 / API 30 | Connected suite passed, including four icon styles, exactly one launcher alias, actual alias launches, activity recreation, saved preferences, distinct rendered icons, and invalid-style safety. |
| Android 15 / API 35 | Same connected suite passed, exercising the atomic alias-switching path. |
| Live official client and offline restore | The actual repository fetched WLY01 October 2026 from e-Solat, returned October 9 Fajr at 05:51 Malaysia time, then restored identical times through a new repository with networking blocked. |
| Install identity and signature | Debug APK verified as Sukun Test, `io.github.raiquiaseishun.sukun.debug`, version 86 / 0.24.12, minimum API 30; APK signature verified. |
| Download | [Phone-test ZIP](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/37938364042/artifacts/11620761843), containing the APK, checksum, source identity, phone checklist and license. GitHub sign-in is required; artifact retention is 30 days. |

The recorded official fixtures cover WLY01 in January and October 2026 and JHR02 in October 2026. They establish exact entries for those cases and cache/request handling, not every zone/date or future revisions. The earlier AlAdhan comparison remains applicable to that separate source. Select the correct JAKIM zone and leave manual offsets at zero to match the official entries.

The first two API 35 runs exposed an unhandled operating-system notification consent dialog pausing ActivityScenario. The final tests explicitly grant that permission before launching activities; no lifecycle or icon assertions were removed. Notification consent, real launcher caching/themed icons, GPS behavior, and reminder delivery still need physical-phone checks. Emulator successes are not physical-device evidence.

Local evidence is retained outside the checkout in `/workspace/downloads/jakim-icon-final-validation.log`, `/workspace/downloads/icon-test-permissions-compile.log`, `/workspace/downloads/jakim-live-validation.log`, and `/workspace/downloads/jakim-icon-ci-validation.json`. See [feature behavior and limitations](JAKIM_AND_ICONS.md), including the separate debug app data and temporary signing-key limitations.

## Earlier foundation validation — 0.24.11

Validation ran in the cloud machine on 2026-10-09 against the final application changes. It does not establish restoration in a fresh task or a signed release.

| Check | Executed result |
| --- | --- |
| JDK / Gradle | Full JDK 21.0.12.1 and checksum-pinned Gradle 9.5.1 activated |
| Setup repeatability | Install script rerun successfully with retained dependencies and caches |
| `testFullDebugUnitTest` | 17 passed, 0 failed/errors/skipped; 15 new regressions and 2 existing placeholder tests |
| `testFdroidDebugUnitTest` | Same 17 passed, 0 failed/errors/skipped |
| `assembleFullDebug` | Passed; debug APKs produced |
| `assembleFdroidDebug` | Passed; debug APKs produced |
| `lintFullDebug` | Passed with 0 errors; existing warnings remain |
| `lintFdroidDebug` | Passed with 0 errors; existing warnings remain |
| `connectedFdroidDebugAndroidTest` | 4 passed, 0 failed/errors/skipped on Android 11 / API 30 x86_64 software emulator |
| Git integrity | Original upstream history and GPLv3 license retained; foundation commits pushed to origin/main; no upstream contribution |
| GitHub / AlAdhan / JAKIM metadata and references | Live requests succeeded on 2026-10-09; repository public, not server-linked fork; three WLY01 samples expose a timetable mismatch |
| Phone-test APK | Universal fdroid debug APK rebuilt; independent ID/label, API 30 minimum, ARM support, and APK signature verified |
| Release builds / signing / installation of updates | Not executed; manual draft-release workflow is prepared |
| GitHub CI | [Final run](https://github.com/RaiquiaSeishun/Khushu_Test/actions/runs/37925651593) passed both jobs at `248d462`: build/JVM/lint for both variants and connected Android checks |

The three new Android tests verified that a version-3 canvas database retained its saved layout/custom preset after migration to version 5, that the non-exported provider can read update files but refuses unrelated private cache files, and that MainActivity reaches RESUMED without finishing. The fourth is the existing package-context check, updated to use the fork's application ID.

The final build/JVM/lint command was run through `/workspace/sukun-install.sh`. The final connected run used the same toolchain and:

```sh
bash gradlew -Dddmlib.getprop.timeout.sec=30 \
  -Dorg.gradle.java.installations.paths=/workspace/tools/jdk21 \
  -Dorg.gradle.java.installations.auto-download=false \
  :app:connectedFdroidDebugAndroidTest --console=plain
```

Initial attempts were environment failures: Java lacked javac, tooling tried to use unwritable cache directories, the cold software emulator lost Android services, and DDMLib's two-second property query was too short. These were diagnosed and corrected. UI-test animations were disabled to let ActivityScenario become idle. The successful device run completed all four tests in six minutes; failed installation/discovery attempts executed no application tests and are not counted as passing.

Current raw evidence is outside the checkout in `/workspace/downloads/final-setup-validation.log` and `/workspace/downloads/device-timeout-validation.log`; current test/lint reports are under `app/build`. Live processes and generated reports should not be assumed to survive publication.

These checks validate development capabilities and the described regression behavior. The live [reference samples](PRAYER_REFERENCE_COMPARISON.md) show the current API method differs from the official zone timetable. These checks do not establish official accuracy, physical GPS provider accuracy/movement/battery use, notification delivery under Doze, unknown historical database versions, real signed APK upgrades, or third-party redistribution rights. Do not recommend publishing an application release until those required checks are resolved.

The remote CI evidence was checked through GitHub job results. Later documentation commits do not change application sources or build inputs. Earlier failed or cancelled CI runs are not passing validation. GitHub download redirects for detailed logs were blocked by network policy; failure annotations exposed the missing-AVD error, and the corrected final device job passed. Current API evidence is retained at `/workspace/downloads/github-ci-validation.json`.
