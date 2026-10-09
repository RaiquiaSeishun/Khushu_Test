# Validation evidence

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
